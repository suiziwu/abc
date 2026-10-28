package com.ruoyi.project.kb;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.ruoyi.project.domain.KbBase;
import com.ruoyi.project.domain.KbChunk;
import com.ruoyi.project.domain.KbDocument;
import com.ruoyi.project.kb.DocumentParser.DocSection;
import com.ruoyi.project.kb.DocumentParser.ParseResult;
import com.ruoyi.project.kb.SemanticChunker.RawChunk;
import com.ruoyi.project.mapper.KbChunkMapper;
import com.ruoyi.project.mapper.KbDocumentMapper;

/**
 * 课本/文档导入服务：上传文件 → 解析 → LLM 语义切片 → 向量化 → 写入知识库。
 *
 * <p>切片以 {@code sourceType="document"} 落库，与既有的 chapter/point/transcript/quiz
 * 语料共存于同一学科知识库，AI 问答与检索可直接命中。</p>
 *
 * @author ruoyi
 */
@Service
public class KbDocumentImportService
{
    private static final Logger log = LoggerFactory.getLogger(KbDocumentImportService.class);

    private static final String TYPE_DOCUMENT = "document";

    @Autowired
    private KbBaseService kbBaseService;

    @Autowired
    private KbDocumentMapper kbDocumentMapper;

    @Autowired
    private KbChunkMapper kbChunkMapper;

    @Autowired
    private EmbeddingClient embeddingClient;

    @Autowired
    private KbVectorStoreFactory storeFactory;

    @Autowired
    private SemanticChunker semanticChunker;

    @Autowired
    private KbProperties kbProperties;

    /**
     * 导入课本文档到指定学科的知识库。
     *
     * @param bytes       文件字节
     * @param fileName    文件名
     * @param subjectId   学科ID（必填）
     * @param subjectName 学科名称（用于初始化知识库显示名）
     * @param chapterId   章节ID（可空，为空表示整本归属学科）
     * @param model       切片用大模型ID（可空，使用默认）
     * @param operName    操作人
     * @return 导入统计
     */
    public Map<String, Object> importDocument(byte[] bytes, String fileName, Long subjectId,
            String subjectName, Long chapterId, String model, String operName)
    {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("subjectId", subjectId);
        result.put("chapterId", chapterId);
        result.put("fileName", fileName);

        // 1. 解析
        ParseResult parsed;
        try
        {
            parsed = new DocumentParser().parse(bytes, fileName);
        }
        catch (Exception e)
        {
            log.error("课本解析失败: fileName={}", fileName, e);
            result.put("success", false);
            result.put("error", "文档解析失败: " + e.getMessage());
            return result;
        }

        // 2. LLM 语义切片
        List<RawChunk> rawChunks = semanticChunker.chunk(parsed.sections, model);
        if (rawChunks == null || rawChunks.isEmpty())
        {
            result.put("success", false);
            result.put("error", "未从文档中切出任何有效片段，请检查文件内容");
            return result;
        }

        // 3. 确保知识库存在
        KbBase kbBase = kbBaseService.ensureForSubject(subjectId,
                subjectName == null || subjectName.isEmpty() ? ("学科" + subjectId) : subjectName, operName);
        Long kbId = kbBase == null ? null : kbBase.getId();

        // 4. 组装文档与切片
        String safeName = (fileName == null ? "document" : fileName).replaceAll("[^a-zA-Z0-9_\\u4e00-\\u9fa5.\\-]", "_");
        String docKey = TYPE_DOCUMENT + ":" + subjectId + ":" + safeName;

        // 清理旧版（同一文件重复导入幂等）
        try
        {
            kbDocumentMapper.deleteKbDocumentByKeys(Collections.singletonList(docKey));
            kbChunkMapper.deleteKbChunkByDocIds(Collections.singletonList(docKey));
            storeFactory.active().deleteByDocIds(Collections.singletonList(docKey));
        }
        catch (Exception e)
        {
            log.warn("清理旧版课本切片失败（可忽略，继续导入）: {}", e.getMessage());
        }

        List<KbChunk> chunks = new ArrayList<>(rawChunks.size());
        Integer minPage = null;
        Integer maxPage = null;
        int totalTokens = 0;
        StringBuilder fingerprint = new StringBuilder();
        for (RawChunk raw : rawChunks)
        {
            KbChunk chunk = new KbChunk();
            chunk.setDocId(docKey);
            chunk.setSourceType(TYPE_DOCUMENT);
            chunk.setSubjectId(subjectId);
            chunk.setChapterId(chapterId);
            chunk.setPointId(null);
            chunk.setTitle(raw.title == null || raw.title.isEmpty() ? "课本切片" : raw.title);
            chunk.setContent(raw.content);
            chunk.setSectionPath(raw.sectionPath == null || raw.sectionPath.isEmpty() ? null : raw.sectionPath);
            chunk.setPageStart(raw.pageStart);
            chunk.setPageEnd(raw.pageStart);
            chunk.setChunkIndex(raw.chunkIndex);
            chunk.setStartTime(null);
            chunk.setStatus(1);
            chunk.setTokenCount(estimateTokens(raw.content));
            chunk.setContentHash(md5(chunk.getTitle() + "\n" + raw.content));
            chunk.setKbId(kbId);
            chunks.add(chunk);

            totalTokens += chunk.getTokenCount();
            fingerprint.append(chunk.getContentHash());
            if (raw.pageStart != null)
            {
                minPage = minPage == null ? raw.pageStart : Math.min(minPage, raw.pageStart);
                maxPage = maxPage == null ? raw.pageStart : Math.max(maxPage, raw.pageStart);
            }
        }

        // 5. 向量化
        List<String> texts = new ArrayList<>(chunks.size());
        for (KbChunk chunk : chunks)
        {
            texts.add(chunk.getContent());
        }
        List<float[]> vectors = embeddingClient.embedBatch(texts);
        for (int i = 0; i < chunks.size(); i++)
        {
            KbChunk chunk = chunks.get(i);
            chunk.setModel(kbProperties.getEmbedding().getModel());
            chunk.setDimensions(kbProperties.getEmbedding().getDimensions());
            chunk.setEmbedding(toJson(vectors.get(i)));
        }

        // 6. 落文档层，再让切片挂到文档上
        KbDocument document = new KbDocument();
        document.setKbId(kbId);
        document.setDocKey(docKey);
        document.setSubjectId(subjectId);
        document.setChapterId(chapterId);
        document.setSourceType(TYPE_DOCUMENT);
        document.setTitle(fileName);
        document.setSectionPath(parsed.title);
        document.setSourceUri(fileName);
        document.setPageStart(minPage);
        document.setPageEnd(maxPage);
        document.setChunkCount(chunks.size());
        document.setTokenCount(totalTokens);
        document.setContentHash(md5(docKey + fingerprint.toString()));
        document.setStatus("1");
        document.setVersion(1);
        kbDocumentMapper.upsertKbDocument(document);

        for (KbChunk chunk : chunks)
        {
            chunk.setDocPk(document.getId());
        }

        // 7. 写向量库（内部先 Milvus 后 MySQL，失败补偿）
        int upserted = storeFactory.active().upsert(chunks);

        result.put("success", true);
        result.put("documentId", document.getId());
        result.put("total", chunks.size());
        result.put("upserted", upserted);
        result.put("provider", storeFactory.active().provider());
        log.info("课本导入完成: subjectId={}, file={}, chunks={}, provider={}",
                subjectId, fileName, chunks.size(), storeFactory.active().provider());
        return result;
    }

    /**
     * 浏览知识库切片（供前端逐条查看）。
     */
    public List<KbChunk> listChunks(Long subjectId, Long chapterId, List<String> sourceTypes)
    {
        return kbChunkMapper.selectKbChunkList(subjectId, chapterId, sourceTypes, null);
    }

    private String toJson(float[] vector)
    {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++)
        {
            if (i > 0)
            {
                sb.append(',');
            }
            sb.append(vector[i]);
        }
        sb.append("]");
        return sb.toString();
    }

    private static int estimateTokens(String text)
    {
        if (text == null || text.isEmpty())
        {
            return 0;
        }
        return Math.max(1, (int) Math.round(text.length() / 1.5D));
    }

    private static String md5(String text)
    {
        try
        {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] bytes = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes)
            {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        }
        catch (Exception e)
        {
            throw new IllegalStateException("计算内容指纹失败", e);
        }
    }
}
