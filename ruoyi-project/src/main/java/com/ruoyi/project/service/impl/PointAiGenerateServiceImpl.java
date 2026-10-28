package com.ruoyi.project.service.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.project.domain.ChapterPoint;
import com.ruoyi.project.domain.CourseChapter;
import com.ruoyi.project.domain.KnowledgePoint;
import com.ruoyi.project.domain.Position;
import com.ruoyi.project.domain.Subject;
import com.ruoyi.project.langchain.ChatModelClient;
import com.ruoyi.project.mapper.CourseChapterMapper;
import com.ruoyi.project.mapper.KnowledgePointMapper;
import com.ruoyi.project.mapper.PositionMapper;
import com.ruoyi.project.mapper.SubjectMapper;
import com.ruoyi.project.service.IPointAiGenerateService;

/**
 * 知识点 AI 生成 Service 业务层处理。
 *
 * <p>调用项目统一配置的 OpenAI 兼容大模型（百炼 / DeepSeek 等），
 * 落库时使用 new2 的 course_chapter / chapter_point / knowledge_point 结构。</p>
 *
 * @author ruoyi
 */
@Service
public class PointAiGenerateServiceImpl implements IPointAiGenerateService
{
    private static final Logger log = LoggerFactory.getLogger(PointAiGenerateServiceImpl.class);

    private static final List<String> ALLOWED_TYPES = Arrays.asList(
            "概念", "语法", "方法", "工具", "框架", "管理", "原理", "实践");

    /** 统一系统提示词：只输出 JSON，避免模型附加解释导致解析失败。 */
    private static final String SYSTEM_PROMPT =
            "你是职业教育课程知识点生成助手。严格只输出 JSON，不要输出 Markdown 代码块、解释或其他文字。";

    @Autowired
    private ChatModelClient chatModelClient;

    @Autowired
    private CourseChapterMapper courseChapterMapper;

    @Autowired
    private KnowledgePointMapper knowledgePointMapper;

    @Autowired
    private PositionMapper positionMapper;

    @Autowired
    private SubjectMapper subjectMapper;

    /**
     * 调用 AI 按章生成知识点，并保存章节与知识点。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> generateByAi(Long positionId, Long subjectId, Integer chapterCount, String createBy)
    {
        if (positionId == null || subjectId == null)
        {
            throw new RuntimeException("岗位和学科不能为空");
        }
        Position position = positionMapper.selectPositionById(positionId);
        if (position == null)
        {
            throw new RuntimeException("岗位不存在");
        }
        Subject subject = subjectMapper.selectSubjectById(subjectId);
        if (subject == null)
        {
            throw new RuntimeException("学科不存在");
        }

        int count = (chapterCount == null || chapterCount < 1) ? 3 : chapterCount;
        if (count > 20)
        {
            count = 20;
        }

        String answer = callAi(buildPrompt(position.getName(), subject.getName(), count));
        JsonNode root = parseJson(answer);
        JsonNode chapterNodes = root.path("chapters");
        if (!chapterNodes.isArray() || chapterNodes.size() == 0)
        {
            throw new RuntimeException("AI 返回内容无法解析");
        }

        // 已有章（用于排序）
        CourseChapter query = new CourseChapter();
        query.setPositionId(positionId);
        query.setSubjectId(subjectId);
        List<CourseChapter> existing = courseChapterMapper.selectCourseChapterList(query);
        int baseSort = existing == null ? 0 : existing.size();

        List<CourseChapter> chapters = new ArrayList<CourseChapter>();
        int pointCount = 0;
        int pointSort = 0;
        for (int i = 0; i < chapterNodes.size(); i++)
        {
            JsonNode chapterNode = chapterNodes.get(i);
            String chapterName = chapterNode.path("name").asText();
            if (StringUtils.isEmpty(chapterName))
            {
                continue;
            }

            CourseChapter chapter = new CourseChapter();
            chapter.setPositionId(positionId);
            chapter.setSubjectId(subjectId);
            chapter.setTitle(chapterName.trim());
            chapter.setCourseTitle(subject.getName());
            chapter.setDescription(chapterNode.path("description").asText(null));
            chapter.setSortOrder((long) (baseSort + chapters.size() + 1));
            chapter.setStatus("0");
            chapter.setDelFlag("0");
            chapter.setCreateBy(createBy);
            chapter.setCreateTime(DateUtils.getNowDate());
            courseChapterMapper.insertCourseChapter(chapter);
            chapters.add(chapter);

            JsonNode pointNodes = chapterNode.path("points");
            if (!pointNodes.isArray())
            {
                continue;
            }
            List<ChapterPoint> links = new ArrayList<ChapterPoint>();
            int linkSort = 0;
            for (JsonNode pointNode : pointNodes)
            {
                String pointName = pointNode.path("name").asText();
                if (StringUtils.isEmpty(pointName))
                {
                    continue;
                }
                KnowledgePoint point = new KnowledgePoint();
                point.setSubjectId(subjectId);
                point.setChapterId(chapter.getId());
                point.setName(pointName.trim());
                point.setContent(pointNode.path("content").asText(null));
                point.setDifficulty(pointNode.path("difficulty").isNumber() ? pointNode.path("difficulty").asLong() : 1L);
                point.setImportance(pointNode.path("importance").isNumber() ? pointNode.path("importance").asLong() : 1L);
                String type = pointNode.path("type").asText(null);
                point.setType(StringUtils.isNotEmpty(type) ? type : "概念");
                point.setSortOrder((long) (++pointSort));
                point.setStatus("0");
                point.setDelFlag("0");
                point.setCreateBy(createBy);
                point.setCreateTime(DateUtils.getNowDate());
                knowledgePointMapper.insertKnowledgePoint(point);
                pointCount++;

                ChapterPoint link = new ChapterPoint();
                link.setChapterId(chapter.getId());
                link.setPointId(point.getId());
                link.setSortOrder((long) (++linkSort * 10));
                links.add(link);
            }
            if (!links.isEmpty())
            {
                courseChapterMapper.batchChapterPoint(links);
            }
        }

        if (chapters.isEmpty())
        {
            throw new RuntimeException("AI 返回内容无法解析");
        }

        Map<String, Object> result = new HashMap<String, Object>();
        result.put("chapterCount", chapters.size());
        result.put("pointCount", pointCount);
        result.put("chapters", chapters);
        return result;
    }

    /**
     * 根据课程视频逐字稿生成知识点草稿（不落库）。
     */
    @Override
    public List<Map<String, Object>> generateDraftsFromCourse(Long subjectId, String courseTitle, String transcript)
    {
        if (subjectId == null)
        {
            throw new RuntimeException("学科不能为空");
        }
        Subject subject = subjectMapper.selectSubjectById(subjectId);
        if (subject == null)
        {
            throw new RuntimeException("学科不存在");
        }
        if (StringUtils.isEmpty(transcript))
        {
            throw new RuntimeException("课程逐字稿不能为空");
        }

        try
        {
            String answer = callAi(buildCoursePrompt(subject.getName(), courseTitle, transcript));
            log.info("课程逐字稿 AI 原始返回：{}", answer);
            List<Map<String, Object>> drafts = parseDraftArray(answer);
            if (drafts != null && !drafts.isEmpty())
            {
                return drafts;
            }
            log.warn("课程逐字稿 AI 结果无法解析出知识点，改用本地降级提取，学科：{}", subject.getName());
        }
        catch (Exception e)
        {
            log.warn("课程逐字稿 AI 调用失败，改用本地降级提取，学科：{}，原因：{}", subject.getName(), e.getMessage());
        }
        return buildFallbackDrafts(transcript);
    }

    /**
     * 拼接课程逐字稿提取知识点的提示词。
     */
    private String buildCoursePrompt(String subjectName, String courseTitle, String transcript)
    {
        return "你是职业教育课程知识点提取助手。请基于下面的课程逐字稿，结合学科『" + subjectName + "』提取 3-8 个核心知识点。"
                + "只输出 JSON 数组，不要任何解释或代码块标记，元素格式："
                + "{\"name\":\"知识点名\",\"content\":\"50-150字说明\",\"difficulty\":3,\"importance\":2,\"type\":\"概念\"}，"
                + "其中 difficulty 取 1-5，importance 取 1-4，type 只能取 概念|语法|方法|工具|框架|管理|原理|实践。"
                + "课程标题：" + (courseTitle == null ? "" : courseTitle) + "\n逐字稿：" + transcript;
    }

    /**
     * 去除代码块围栏并解析首个 '[' 到最后一个 ']' 之间的 JSON 数组。
     */
    private List<Map<String, Object>> parseDraftArray(String answer)
    {
        if (StringUtils.isEmpty(answer))
        {
            return null;
        }
        String text = answer.replace("```json", "").replace("```", "").trim();
        int start = text.indexOf('[');
        int end = text.lastIndexOf(']');
        if (start < 0 || end <= start)
        {
            return null;
        }
        text = text.substring(start, end + 1);

        JsonNode root;
        try
        {
            root = new ObjectMapper().readTree(text);
        }
        catch (Exception e)
        {
            return null;
        }
        if (!root.isArray())
        {
            return null;
        }

        List<Map<String, Object>> drafts = new ArrayList<Map<String, Object>>();
        for (JsonNode node : root)
        {
            String name = node.path("name").asText("").trim();
            if (StringUtils.isEmpty(name))
            {
                continue;
            }
            Map<String, Object> draft = new LinkedHashMap<String, Object>();
            draft.put("name", name);
            draft.put("content", node.path("content").asText("").trim());
            draft.put("difficulty", intValueOrDefault(node.get("difficulty"), 1));
            draft.put("importance", intValueOrDefault(node.get("importance"), 3));
            String type = node.path("type").asText("").trim();
            draft.put("type", ALLOWED_TYPES.contains(type) ? type : "概念");
            drafts.add(draft);
        }
        return drafts;
    }

    /**
     * 解析整数字段，非数字时使用默认值。
     */
    private int intValueOrDefault(JsonNode node, int defaultValue)
    {
        if (node == null || node.isNull())
        {
            return defaultValue;
        }
        if (node.isNumber())
        {
            return node.intValue();
        }
        String text = node.asText("").trim();
        try
        {
            return Integer.parseInt(text);
        }
        catch (NumberFormatException e)
        {
            return defaultValue;
        }
    }

    /**
     * 本地降级：按换行/章节切段提取知识点草稿，最多 8 条。
     */
    private List<Map<String, Object>> buildFallbackDrafts(String transcript)
    {
        List<Map<String, Object>> drafts = new ArrayList<Map<String, Object>>();
        String normalized = transcript.replace("\r\n", "\n").replace("\r", "\n")
                .replaceAll("(?=第[0-9一二三四五六七八九十百]+[章节])", "\n");
        for (String segment : normalized.split("\n"))
        {
            String text = segment.trim();
            if (text.isEmpty())
            {
                continue;
            }
            Map<String, Object> draft = new LinkedHashMap<String, Object>();
            draft.put("name", text.length() > 60 ? text.substring(0, 60) : text);
            draft.put("content", text.length() > 150 ? text.substring(0, 150) : text);
            draft.put("difficulty", 1);
            draft.put("importance", 3);
            draft.put("type", "概念");
            drafts.add(draft);
            if (drafts.size() >= 8)
            {
                break;
            }
        }
        return drafts;
    }

    /**
     * 拼接按章生成的提示词。
     */
    private String buildPrompt(String positionName, String subjectName, int chapterCount)
    {
        return "请以岗位『" + positionName + "』和学科『" + subjectName + "』为依据，将知识点按章组织，共 " + chapterCount
                + " 章，每章 3-6 个知识点。只输出 JSON，不要任何解释或代码块标记，格式："
                + "{\"chapters\":[{\"name\":\"第1章 xxx\",\"description\":\"...\",\"points\":[{\"name\":\"...\",\"content\":\"...\",\"difficulty\":1,\"importance\":3,\"type\":\"概念\"}]}]}";
    }

    /**
     * 调用项目统一配置的 OpenAI 兼容大模型（默认取 ai.chat.default-model）。
     */
    private String callAi(String prompt)
    {
        String answer = chatModelClient.chat(SYSTEM_PROMPT, prompt);
        if (StringUtils.isEmpty(answer))
        {
            throw new RuntimeException("AI 未返回有效内容");
        }
        return answer;
    }

    /**
     * 去除代码块围栏并解析首个 '{' 到最后一个 '}' 之间的 JSON。
     */
    private JsonNode parseJson(String answer)
    {
        if (StringUtils.isEmpty(answer))
        {
            throw new RuntimeException("AI 未返回有效内容");
        }
        String text = answer.replace("```json", "").replace("```", "").trim();
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start)
        {
            throw new RuntimeException("AI 返回内容无法解析");
        }
        text = text.substring(start, end + 1);
        try
        {
            return new ObjectMapper().readTree(text);
        }
        catch (Exception e)
        {
            throw new RuntimeException("AI 返回内容无法解析");
        }
    }
}
