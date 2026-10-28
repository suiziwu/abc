package com.ruoyi.project.kb;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.project.langchain.ChatModelClient;

/**
 * LLM 语义切片器：把解析出的 {@link DocumentParser.DocSection} 交给大模型，
 * 按语义边界切成若干独立、完整、可单独理解的知识点片段。
 *
 * <p>为避免超长文本超出模型上下文，先按候选长度上限切分为若干候选块，再逐块调用
 * 大模型。大模型不可用或返回无法解析时退化为按段落的启发式切分，保证流程不中断。</p>
 *
 * @author ruoyi
 */
@Component
public class SemanticChunker
{
    private static final Logger log = LoggerFactory.getLogger(SemanticChunker.class);

    /** 单个候选块的最大字符数（约 1500 token，留足给提示词与输出） */
    private static final int MAX_CANDIDATE = 2500;

    /** 至多调用大模型切分的候选数，超出部分退化为启发式切分（控制成本与时延） */
    private static final int LLM_BUDGET = 60;

    private static final String SYSTEM_PROMPT = "你是一个教材切片专家。我会给你一段教材文本（可能带有章节上下文）。\n"
            + "请按语义将其切分为若干个独立、完整、可以单独理解的知识点片段。\n"
            + "要求：\n"
            + "1. 每个片段是一个相对完整的知识点，不要在语义中途切断；\n"
            + "2. 每个片段控制在 100-600 字；\n"
            + "3. 为每段生成不超过 20 个字的简练标题；\n"
            + "4. 保持原文表述，不要改写或补充外部知识。\n"
            + "只输出 JSON 数组，元素格式：{\"title\":\"片段标题\",\"content\":\"片段正文\"}，不要输出任何额外说明。";

    @Autowired
    private ChatModelClient chatClient;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 语义切片结果 */
    public static class RawChunk
    {
        public String title;
        public String content;
        public String sectionPath;
        public int chunkIndex;
        public Integer pageStart;

        public RawChunk(String title, String content, String sectionPath)
        {
            this.title = title;
            this.content = content;
            this.sectionPath = sectionPath == null ? "" : sectionPath;
        }
    }

    private static class Candidate
    {
        String text;
        String sectionPath;
        Integer pageStart;

        Candidate(String text, String sectionPath, Integer pageStart)
        {
            this.text = text;
            this.sectionPath = sectionPath;
            this.pageStart = pageStart;
        }
    }

    /**
     * 语义切片。
     *
     * @param sections 解析出的结构化片段
     * @param modelId  大模型 id（可空，使用默认模型）
     * @return 切片列表（按原文顺序）
     */
    public List<RawChunk> chunk(List<DocumentParser.DocSection> sections, String modelId)
    {
        List<Candidate> candidates = buildCandidates(sections);
        List<RawChunk> result = new ArrayList<>();
        int index = 0;
        for (int i = 0; i < candidates.size(); i++)
        {
            Candidate candidate = candidates.get(i);
            List<RawChunk> parts = i < LLM_BUDGET ? callLlm(candidate, modelId) : fallbackSplit(candidate);
            for (RawChunk part : parts)
            {
                part.chunkIndex = index++;
                result.add(part);
            }
        }
        return result;
    }

    private List<Candidate> buildCandidates(List<DocumentParser.DocSection> sections)
    {
        List<Candidate> candidates = new ArrayList<>();
        for (DocumentParser.DocSection section : sections)
        {
            String header = (section.title == null || section.title.isEmpty()) ? "" : ("【" + section.title + "】\n");
            String text = (header + section.content).trim();
            if (text.isEmpty())
            {
                continue;
            }
            String sectionPath = section.title == null ? "" : section.title;
            if (text.length() <= MAX_CANDIDATE)
            {
                candidates.add(new Candidate(text, sectionPath, section.pageStart));
            }
            else
            {
                for (String part : splitLong(text))
                {
                    candidates.add(new Candidate(part, sectionPath, section.pageStart));
                }
            }
        }
        return candidates;
    }

    private List<RawChunk> callLlm(Candidate candidate, String modelId)
    {
        String userPrompt = "章节上下文：" + (candidate.sectionPath.isEmpty() ? "（无）" : candidate.sectionPath)
                + "\n\n" + candidate.text;
        String json;
        try
        {
            json = chatClient.chat(modelId, SYSTEM_PROMPT, userPrompt);
        }
        catch (Exception e)
        {
            log.warn("语义切片调用大模型失败，退化为启发式切分: {}", e.getMessage());
            return fallbackSplit(candidate);
        }
        return parseJsonChunks(json, candidate);
    }

    private List<RawChunk> parseJsonChunks(String json, Candidate candidate)
    {
        if (json == null || json.trim().isEmpty())
        {
            return fallbackSplit(candidate);
        }
        int start = json.indexOf('[');
        int end = json.lastIndexOf(']');
        if (start < 0 || end <= start)
        {
            return fallbackSplit(candidate);
        }
        String arrayText = json.substring(start, end + 1);
        try
        {
            List<Map<String, Object>> list = objectMapper.readValue(arrayText,
                    new TypeReference<List<Map<String, Object>>>() {});
            List<RawChunk> chunks = new ArrayList<>();
            for (Map<String, Object> item : list)
            {
                Object contentObj = item.get("content");
                if (contentObj == null)
                {
                    continue;
                }
                String content = String.valueOf(contentObj).trim();
                if (content.isEmpty())
                {
                    continue;
                }
                Object titleObj = item.get("title");
                String title = titleObj == null ? "" : String.valueOf(titleObj).trim();
                RawChunk chunk = new RawChunk(title, content, candidate.sectionPath);
                chunk.pageStart = candidate.pageStart;
                chunks.add(chunk);
            }
            if (!chunks.isEmpty())
            {
                return chunks;
            }
        }
        catch (Exception e)
        {
            log.warn("解析大模型切片结果失败，退化为启发式切分: {}", e.getMessage());
        }
        return fallbackSplit(candidate);
    }

    private List<RawChunk> fallbackSplit(Candidate candidate)
    {
        List<RawChunk> chunks = new ArrayList<>();
        String[] paras = candidate.text.split("\n\\s*\n|\\n");
        StringBuilder buf = new StringBuilder();
        int counter = 0;
        for (String para : paras)
        {
            String p = para.trim();
            if (p.isEmpty())
            {
                continue;
            }
            if (buf.length() > 0)
            {
                buf.append("\n");
            }
            buf.append(p);
            if (buf.length() >= 400)
            {
                RawChunk chunk = new RawChunk(candidate.sectionPath, buf.toString().trim(), candidate.sectionPath);
                chunk.pageStart = candidate.pageStart;
                chunks.add(chunk);
                buf = new StringBuilder();
                counter++;
            }
        }
        if (buf.length() > 0)
        {
            RawChunk chunk = new RawChunk(candidate.sectionPath, buf.toString().trim(), candidate.sectionPath);
            chunk.pageStart = candidate.pageStart;
            chunks.add(chunk);
        }
        if (chunks.isEmpty())
        {
            RawChunk chunk = new RawChunk(candidate.sectionPath, candidate.text.trim(), candidate.sectionPath);
            chunk.pageStart = candidate.pageStart;
            chunks.add(chunk);
        }
        return chunks;
    }

    private List<String> splitLong(String text)
    {
        List<String> parts = new ArrayList<>();
        String[] paras = text.split("\n\\s*\n|\\n");
        StringBuilder buf = new StringBuilder();
        for (String para : paras)
        {
            String p = para.trim();
            if (p.isEmpty())
            {
                continue;
            }
            if (buf.length() + p.length() + 1 > MAX_CANDIDATE && buf.length() > 0)
            {
                parts.add(buf.toString().trim());
                buf = new StringBuilder();
            }
            if (buf.length() > 0)
            {
                buf.append("\n");
            }
            buf.append(p);
        }
        if (buf.length() > 0)
        {
            parts.add(buf.toString().trim());
        }
        if (parts.isEmpty())
        {
            // 没有任何换行，按固定长度硬切
            for (int i = 0; i < text.length(); i += MAX_CANDIDATE)
            {
                parts.add(text.substring(i, Math.min(i + MAX_CANDIDATE, text.length())));
            }
        }
        return parts;
    }
}
