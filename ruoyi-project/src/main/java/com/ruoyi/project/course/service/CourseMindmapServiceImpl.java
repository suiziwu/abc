package com.ruoyi.project.course.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.project.course.domain.KnowledgeExtraction;
import com.ruoyi.project.course.domain.MindmapAnalysis;
import com.ruoyi.project.course.domain.MindmapAnalyzeRequest;
import com.ruoyi.project.course.domain.MindmapAnalyzeResult;
import com.ruoyi.project.course.domain.MindmapSyncResult;
import com.ruoyi.project.course.mapper.KnowledgeExtractionMapper;
import com.ruoyi.project.domain.ChapterPoint;
import com.ruoyi.project.domain.CourseChapter;
import com.ruoyi.project.domain.KnowledgePoint;
import com.ruoyi.project.domain.KnowledgePointRelation;
import com.ruoyi.project.langchain.ChatModelClient;
import com.ruoyi.project.langchain.LangChainWorkflowException;
import com.ruoyi.project.mapper.CourseChapterMapper;
import com.ruoyi.project.mapper.KnowledgePointMapper;
import com.ruoyi.project.mapper.KnowledgePointRelationMapper;

/**
 * 视频知识结构化梳理实现：AI 生成导图 → 标准化 → 映射已有知识点 → 写入知识图谱。
 *
 * @author ruoyi
 */
@Service
public class CourseMindmapServiceImpl implements CourseMindmapService
{
    private static final int MAX_TRANSCRIPT_CHARS = 30000;

    /**
     * 课本正文一次送模型的字符上限。
     *
     * <p>整本教材远超单次上下文，超限部分会被截断；建议按章节分别导入后再生成，
     * 这样拓扑更完整、单次耗时也更可控。</p>
     */
    private static final int MAX_TEXTBOOK_CHARS = 30000;

    private static final int MAX_NODES = 40;

    private static final int MAX_RELATIONS = 80;

    private static final int MAX_CONCLUSIONS = 8;

    private static final Pattern NAME_NOISE = Pattern.compile(
            "[\\s\\u00A0·•,，.。;；:：、（）()\\[\\]【】<>《》'\"“”‘’`~!！?？/\\\\|—\\-_]+");

    private static final Set<String> KNOWLEDGE_TYPES = new LinkedHashSet<String>(
            Arrays.asList("概念", "技术", "协议", "应用", "设备", "标准", "组织", "指标", "算法"));

    private static final Set<String> RELATION_TYPES = new LinkedHashSet<String>(
            Arrays.asList("prerequisite", "related", "extension"));

    /** 匹配时忽略的通用后缀，用于把「计算机网络概念」映射到已有「计算机网络」 */
    private static final String[] GENERIC_SUFFIXES = {
            "的基本概念", "基本概念", "的概念", "定义", "概念", "功能", "特点", "原理",
            "作用", "技术", "方法", "要素", "机制", "过程" };

    private static final String SYSTEM_PROMPT = String.join("\n",
            "你是“智程学途”的知识图谱专家，负责把课程视频逐字稿整理成层级清晰、逻辑连贯的思维导图，并输出可直接入库的结构化知识点。",
            "",
            "# 任务",
            "阅读给定的课程视频逐字稿，按“章 → 小节 → 知识点”的层级组织，完整提取视频中覆盖的核心知识点、概念关联、逻辑关系与关键结论。",
            "",
            "# 严格约束",
            "1. 只提取逐字稿中明确讲到的内容，不要编造视频未提及的知识点。",
            "2. 每个知识点必须给出：name（不超过20字）、definition（1-2句话）、type、difficulty(1-5)、importance(1-4)、section（所属小节）、evidence（1-2条带 [mm:ss] 的原文片段）。",
            "3. type 只能从：概念、技术、协议、应用、设备、标准、组织、指标、算法 中选择。",
            "4. 层级通过 parentKey 关联：先输出小节/主题节点，再输出其下知识点；根节点的 parentKey 为 null。",
            "5. relations 描述知识点之间的逻辑关系：sourceKey/targetKey 必须是 nodes 中的 key；relationType 只能是 prerequisite（前置依赖）、related（相关参考）、extension（扩展延伸）；weight 为 1-5。",
            "6. conclusions 输出 3-6 条视频中的关键结论。",
            "7. nodes 数量控制在 10-20 个（宁精勿多），relations 数量 8-20 条。",
            "8. 只输出 JSON，不要 Markdown 代码块标记，不要任何解释性文字，确保可被 JSON.parse 解析。",
            "",
            "# 输出 JSON Schema",
            "{",
            "  \"title\": \"导图标题\",",
            "  \"summary\": \"100字以内的内容摘要\",",
            "  \"nodes\": [",
            "    {\"key\":\"n1\",\"name\":\"小节或知识点名称\",\"parentKey\":null,\"section\":\"所属小节\",\"definition\":\"定义\",\"type\":\"概念\",\"difficulty\":1,\"importance\":4,\"evidence\":[\"[00:12] 原文\"]}",
            "  ],",
            "  \"relations\": [",
            "    {\"sourceKey\":\"n1\",\"targetKey\":\"n2\",\"relationType\":\"prerequisite\",\"description\":\"关系说明\",\"weight\":3}",
            "  ],",
            "  \"conclusions\": [\"关键结论\"]",
            "}");

    /**
     * 课本专用提示词。
     *
     * <p>与视频逐字稿的区别：没有时间轴，evidence 改为「所在小节 + 原文摘录」；
     * 课本信息密度更高，允许的节点数与关系数上调；要求按教材的章 / 节 / 知识点三级组织。</p>
     */
    private static final String TEXTBOOK_SYSTEM_PROMPT = String.join("\n",
            "你是“智程学途”的知识图谱专家，负责把课程课本整理成层级清晰、逻辑连贯的知识拓扑，并输出可直接入库的结构化知识点。",
            "",
            "# 任务",
            "阅读给定的课本正文，按“章 → 小节 → 知识点”的层级组织，完整提取教材覆盖的核心知识点、概念关联、逻辑关系与关键结论。",
            "",
            "# 严格约束",
            "1. 只提取课本正文中明确写到的内容，不要补充教材之外的知识。",
            "2. 每个知识点必须给出：name（不超过20字）、definition（1-2句话）、type、difficulty(1-5)、importance(1-4)、section（所属小节）、evidence（1-2条课本原文摘录，不要带时间戳）。",
            "3. type 只能从：概念、技术、协议、应用、设备、标准、组织、指标、算法 中选择。",
            "4. 层级通过 parentKey 关联：先输出章 / 小节节点，再输出其下知识点；根节点的 parentKey 为 null。",
            "5. relations 描述知识点之间的逻辑关系：sourceKey/targetKey 必须是 nodes 中的 key；relationType 只能是 prerequisite（前置依赖）、related（相关参考）、extension（扩展延伸）；weight 为 1-5。",
            "6. conclusions 输出 3-8 条教材中的关键结论。",
            "7. nodes 数量控制在 15-30 个（宁精勿多，优先覆盖主干），relations 数量 12-30 条。",
            "8. 只输出 JSON，不要 Markdown 代码块标记，不要任何解释性文字，确保可被 JSON.parse 解析。",
            "",
            "# 输出 JSON Schema",
            "{",
            "  \"title\": \"拓扑标题（通常是教材或章节名）\",",
            "  \"summary\": \"100字以内的内容摘要\",",
            "  \"nodes\": [",
            "    {\"key\":\"n1\",\"name\":\"小节或知识点名称\",\"parentKey\":null,\"section\":\"所属小节\",\"definition\":\"定义\",\"type\":\"概念\",\"difficulty\":1,\"importance\":4,\"evidence\":[\"课本原文摘录\"]}",
            "  ],",
            "  \"relations\": [",
            "    {\"sourceKey\":\"n1\",\"targetKey\":\"n2\",\"relationType\":\"prerequisite\",\"description\":\"关系说明\",\"weight\":3}",
            "  ],",
            "  \"conclusions\": [\"关键结论\"]",
            "}");

    private final ChatModelClient chatModelClient;

    private final KnowledgePointMapper pointMapper;

    private final KnowledgePointRelationMapper relationMapper;

    private final CourseChapterMapper chapterMapper;

    private final KnowledgeExtractionMapper extractionMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public CourseMindmapServiceImpl(ChatModelClient chatModelClient, KnowledgePointMapper pointMapper,
            KnowledgePointRelationMapper relationMapper, CourseChapterMapper chapterMapper,
            KnowledgeExtractionMapper extractionMapper)
    {
        this.chatModelClient = chatModelClient;
        this.pointMapper = pointMapper;
        this.relationMapper = relationMapper;
        this.chapterMapper = chapterMapper;
        this.extractionMapper = extractionMapper;
    }

    @Override
    public MindmapAnalyzeResult analyze(MindmapAnalyzeRequest request)
    {
        if (request == null || request.getSubjectId() == null)
        {
            throw new IllegalArgumentException("学科不能为空");
        }
        if (request.getChapters().isEmpty())
        {
            throw new IllegalArgumentException("视频逐字稿不能为空");
        }
        String transcript = buildTranscript(request.getChapters());
        if (transcript.trim().isEmpty())
        {
            throw new IllegalArgumentException("视频逐字稿内容为空");
        }
        return analyzeSource(request.getSubjectId(), request.getChapterNo(), request.getModel(),
                "video", SYSTEM_PROMPT, "【课程视频逐字稿】\n", transcript, MAX_TRANSCRIPT_CHARS);
    }

    /**
     * 课本正文 → 知识拓扑。
     *
     * <p>与视频链路共用「结构化 → 解析 → 归一化 → 落 extraction 记录」的流程，
     * 只是输入换成课本正文，并换用课本提示词（无时间戳、节点数更多）。</p>
     *
     * @param subjectId 归属学科
     * @param chapterNo 章号（可空）
     * @param model     模型ID（可空，用默认）
     * @param text      课本正文
     * @return 分析结果（含 extractionId，可继续 sync 到知识图谱）
     */
    @Override
    public MindmapAnalyzeResult analyzeTextbook(Long subjectId, String chapterNo, String model, String text)
    {
        if (subjectId == null)
        {
            throw new IllegalArgumentException("学科不能为空");
        }
        if (text == null || text.trim().isEmpty())
        {
            throw new IllegalArgumentException("课本正文为空，请先导入课本或检查文档内容");
        }
        return analyzeSource(subjectId, chapterNo, model,
                "textbook", TEXTBOOK_SYSTEM_PROMPT, "【课程课本正文】\n", text, MAX_TEXTBOOK_CHARS);
    }

    /**
     * 结构化梳理的公共流程：截断 → 调模型 → 解析 → 归一化 → 记录 extraction。
     */
    private MindmapAnalyzeResult analyzeSource(Long subjectId, String chapterNo, String model,
            String sourceType, String systemPrompt, String promptHeader, String text, int maxChars)
    {
        String content = text == null ? "" : text.trim();
        if (content.length() > maxChars)
        {
            content = content.substring(0, maxChars);
        }
        if (content.isEmpty())
        {
            throw new IllegalArgumentException("待分析内容为空");
        }

        long startedAt = System.currentTimeMillis();
        KnowledgeExtraction record = new KnowledgeExtraction();
        record.setSubjectId(subjectId);
        record.setChapterNo(trimToNull(chapterNo));
        record.setSourceType(sourceType);
        record.setModel(trimToNull(model));
        record.setTranscriptChars(content.length());
        record.setCreateBy(currentUser());
        record.setCreateTime(new Date());

        String rawResponse = "";
        try
        {
            rawResponse = chatModelClient.chat(model, systemPrompt, promptHeader + content);
            MindmapAnalysis analysis = parseAnalysis(rawResponse);
            normalize(analysis);
            if (analysis.getNodes().isEmpty())
            {
                throw new IOException("AI 未提取到有效知识点");
            }
            record.setRawResponse(rawResponse);
            record.setAnalysisJson(objectMapper.writeValueAsString(analysis));
            record.setNodeCount(analysis.getNodes().size());
            record.setRelationCount(analysis.getRelations().size());
            record.setStatus("analyzed");
            extractionMapper.insertExtraction(record);

            MindmapAnalyzeResult result = new MindmapAnalyzeResult();
            result.setExtractionId(record.getId());
            result.setModel(record.getModel());
            result.setTranscriptChars(record.getTranscriptChars());
            result.setDurationMs(System.currentTimeMillis() - startedAt);
            result.setAnalysis(analysis);
            return result;
        }
        catch (Exception e)
        {
            record.setRawResponse(rawResponse);
            record.setStatus("failed");
            record.setErrorMsg(truncate(e.getMessage(), 500));
            try
            {
                extractionMapper.insertExtraction(record);
            }
            catch (Exception ignored)
            {
                // 记录失败不影响向上抛出原始错误
            }
            throw new LangChainWorkflowException("AI 结构化梳理失败: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MindmapSyncResult sync(Long extractionId)
    {
        if (extractionId == null)
        {
            throw new IllegalArgumentException("梳理记录ID不能为空");
        }
        KnowledgeExtraction record = extractionMapper.selectExtractionById(extractionId);
        if (record == null)
        {
            throw new IllegalArgumentException("梳理记录不存在: " + extractionId);
        }
        if (record.getAnalysisJson() == null || record.getAnalysisJson().trim().isEmpty())
        {
            throw new IllegalStateException("该梳理记录没有可同步的结构化结果");
        }

        MindmapAnalysis analysis;
        try
        {
            analysis = objectMapper.readValue(record.getAnalysisJson(), MindmapAnalysis.class);
        }
        catch (IOException e)
        {
            throw new IllegalStateException("梳理结果解析失败: " + e.getMessage(), e);
        }

        Long subjectId = record.getSubjectId();
        List<KnowledgePoint> existingPoints = loadSubjectPoints(subjectId);
        Map<String, KnowledgePoint> nameIndex = new LinkedHashMap<String, KnowledgePoint>();
        Set<Long> subjectPointIds = new HashSet<Long>();
        long maxSortOrder = 0L;
        for (KnowledgePoint point : existingPoints)
        {
            if (point.getDelFlag() != null && !"0".equals(point.getDelFlag()))
            {
                continue;
            }
            subjectPointIds.add(point.getId());
            String normalized = matchName(point.getName());
            if (!normalized.isEmpty() && !nameIndex.containsKey(normalized))
            {
                nameIndex.put(normalized, point);
            }
            if (point.getSortOrder() != null && point.getSortOrder() > maxSortOrder)
            {
                maxSortOrder = point.getSortOrder();
            }
        }

        Map<String, Long> keyToPointId = new LinkedHashMap<String, Long>();
        Map<String, String> keyToMatchType = new LinkedHashMap<String, String>();
        List<MindmapSyncResult.Mapping> mappings = new ArrayList<MindmapSyncResult.Mapping>();
        int createdPoints = 0;
        int matchedPoints = 0;
        long nextSort = maxSortOrder + 10;

        for (MindmapAnalysis.Node node : analysis.getNodes())
        {
            String normalized = matchName(node.getName());
            KnowledgePoint matched = nameIndex.get(normalized);
            String matchType = "exact";
            if (matched == null)
            {
                matched = fuzzyMatch(normalized, nameIndex);
                if (matched != null)
                {
                    matchType = "fuzzy";
                }
            }
            if (matched == null)
            {
                KnowledgePoint created = buildPoint(subjectId, node, nextSort);
                pointMapper.insertKnowledgePoint(created);
                nextSort += 10;
                nameIndex.put(normalized, created);
                nameIndex.put(matchName(created.getName()), created);
                subjectPointIds.add(created.getId());
                matched = created;
                matchType = "new";
                createdPoints++;
            }
            else
            {
                matchedPoints++;
            }
            keyToPointId.put(node.getKey(), matched.getId());
            keyToMatchType.put(node.getKey(), matchType);

            MindmapSyncResult.Mapping mapping = new MindmapSyncResult.Mapping();
            mapping.setKey(node.getKey());
            mapping.setName(node.getName());
            mapping.setPointId(matched.getId());
            mapping.setMatchType(matchType);
            mappings.add(mapping);
        }

        Set<String> relationPairs = new HashSet<String>();
        // 只查本学科知识点之间的边，避免把全表关系读进内存再过滤
        if (!subjectPointIds.isEmpty())
        {
            for (KnowledgePointRelation relation : relationMapper
                    .selectRelationsByPointIds(new ArrayList<Long>(subjectPointIds)))
            {
                if (relation.getSourceId() != null && relation.getTargetId() != null)
                {
                    relationPairs.add(relation.getSourceId() + "->" + relation.getTargetId());
                }
            }
        }

        int createdRelations = 0;
        int skippedRelations = 0;
        for (MindmapAnalysis.Relation relation : analysis.getRelations())
        {
            Long sourceId = keyToPointId.get(relation.getSourceKey());
            Long targetId = keyToPointId.get(relation.getTargetKey());
            if (sourceId == null || targetId == null || sourceId.equals(targetId))
            {
                skippedRelations++;
                continue;
            }
            String pair = sourceId + "->" + targetId;
            if (!relationPairs.add(pair))
            {
                skippedRelations++;
                continue;
            }
            KnowledgePointRelation entity = new KnowledgePointRelation();
            entity.setSourceId(sourceId);
            entity.setTargetId(targetId);
            entity.setRelationType(normalizeRelationType(relation.getRelationType()));
            entity.setDescription(truncate(relation.getDescription(), 500));
            entity.setWeight((long) clamp(relation.getWeight(), 1, 5, 3));
            entity.setCreateTime(new Date());
            relationMapper.insertKnowledgePointRelation(entity);
            createdRelations++;
        }

        int chapterBound = bindChapter(record.getChapterNo(), subjectId, analysis, keyToPointId);

        record.setStatus("synced");
        record.setCreatedPoints(createdPoints);
        record.setMatchedPoints(matchedPoints);
        record.setCreatedRelations(createdRelations);
        record.setSyncBy(currentUser());
        record.setSyncTime(new Date());
        try
        {
            record.setMappingJson(objectMapper.writeValueAsString(mappings));
        }
        catch (Exception e)
        {
            record.setMappingJson("[]");
        }
        extractionMapper.updateSyncResult(record);

        MindmapSyncResult result = new MindmapSyncResult();
        result.setExtractionId(record.getId());
        result.setStatus(record.getStatus());
        result.setNodeCount(analysis.getNodes().size());
        result.setRelationCount(analysis.getRelations().size());
        result.setCreatedPoints(createdPoints);
        result.setMatchedPoints(matchedPoints);
        result.setCreatedRelations(createdRelations);
        result.setSkippedRelations(skippedRelations);
        result.setChapterBound(chapterBound);
        result.setMappings(mappings);
        result.setMessage("同步完成：新增 " + createdPoints + " 个知识点，匹配 " + matchedPoints
                + " 个已有知识点，新增 " + createdRelations + " 条关系，绑定章节 " + chapterBound + " 个");
        return result;
    }

    @Override
    public List<KnowledgeExtraction> recent(Long subjectId)
    {
        if (subjectId == null)
        {
            throw new IllegalArgumentException("学科不能为空");
        }
        return extractionMapper.selectRecentBySubject(subjectId);
    }

    // ---------------------------------------------------------------------
    // AI 输出解析与标准化
    // ---------------------------------------------------------------------

    private MindmapAnalysis parseAnalysis(String rawResponse) throws IOException
    {
        if (rawResponse == null || rawResponse.trim().isEmpty())
        {
            throw new IOException("AI 返回内容为空");
        }
        String text = rawResponse.trim();
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start)
        {
            throw new IOException("AI 未返回 JSON 结构");
        }
        JsonNode root = objectMapper.readTree(text.substring(start, end + 1));
        MindmapAnalysis analysis = new MindmapAnalysis();
        analysis.setTitle(truncate(textValue(root, "title"), 120));
        analysis.setSummary(truncate(textValue(root, "summary"), 500));

        JsonNode nodeArray = root.path("nodes");
        if (!nodeArray.isArray())
        {
            nodeArray = root.path("points");
        }
        List<MindmapAnalysis.Node> nodes = new ArrayList<MindmapAnalysis.Node>();
        if (nodeArray.isArray())
        {
            for (JsonNode item : nodeArray)
            {
                MindmapAnalysis.Node node = new MindmapAnalysis.Node();
                node.setKey(textValue(item, "key"));
                node.setName(textValue(item, "name"));
                node.setParentKey(nullable(textValue(item, "parentKey")));
                node.setSection(nullable(textValue(item, "section")));
                node.setDefinition(nullable(textValue(item, "definition")));
                node.setType(textValue(item, "type"));
                node.setDifficulty(intValue(item, "difficulty"));
                node.setImportance(intValue(item, "importance"));
                List<String> evidence = new ArrayList<String>();
                JsonNode evidenceNode = item.path("evidence");
                if (evidenceNode.isArray())
                {
                    for (JsonNode line : evidenceNode)
                    {
                        String value = line.asText("");
                        if (!value.trim().isEmpty())
                        {
                            evidence.add(value.trim());
                        }
                    }
                }
                else if (evidenceNode.isTextual() && !evidenceNode.asText().trim().isEmpty())
                {
                    evidence.add(evidenceNode.asText().trim());
                }
                node.setEvidence(evidence);
                nodes.add(node);
            }
        }
        analysis.setNodes(nodes);

        List<MindmapAnalysis.Relation> relations = new ArrayList<MindmapAnalysis.Relation>();
        JsonNode relationArray = root.path("relations");
        if (relationArray.isArray())
        {
            for (JsonNode item : relationArray)
            {
                MindmapAnalysis.Relation relation = new MindmapAnalysis.Relation();
                relation.setSourceKey(textValue(item, "sourceKey"));
                relation.setTargetKey(textValue(item, "targetKey"));
                relation.setRelationType(textValue(item, "relationType"));
                relation.setDescription(nullable(textValue(item, "description")));
                relation.setWeight(intValue(item, "weight"));
                relations.add(relation);
            }
        }
        analysis.setRelations(relations);

        List<String> conclusions = new ArrayList<String>();
        JsonNode conclusionArray = root.path("conclusions");
        if (conclusionArray.isArray())
        {
            for (JsonNode item : conclusionArray)
            {
                String value = item.isObject() ? textValue(item, "text") : item.asText("");
                if (value != null && !value.trim().isEmpty())
                {
                    conclusions.add(value.trim());
                }
            }
        }
        analysis.setConclusions(conclusions);
        return analysis;
    }

    private void normalize(MindmapAnalysis analysis)
    {
        List<MindmapAnalysis.Node> rawNodes = analysis.getNodes();
        List<MindmapAnalysis.Node> nodes = new ArrayList<MindmapAnalysis.Node>();
        Map<String, String> keyRemap = new LinkedHashMap<String, String>();
        Map<String, String> nameKeyMap = new LinkedHashMap<String, String>();
        Map<String, MindmapAnalysis.Node> uniqueNames = new LinkedHashMap<String, MindmapAnalysis.Node>();
        Set<String> usedKeys = new HashSet<String>();
        int autoIndex = 0;

        for (MindmapAnalysis.Node raw : rawNodes)
        {
            if (raw == null || !notBlank(raw.getName()))
            {
                continue;
            }
            String name = truncate(raw.getName().trim(), 60);
            String normalizedName = normalizeName(name);
            if (normalizedName.isEmpty())
            {
                continue;
            }
            String originalKey = trimToNull(raw.getKey());
            String key = originalKey == null ? "n" + (++autoIndex) : originalKey;
            if (!usedKeys.add(key))
            {
                key = key + "_" + (++autoIndex);
                usedKeys.add(key);
            }
            if (uniqueNames.containsKey(normalizedName))
            {
                if (originalKey != null)
                {
                    keyRemap.put(originalKey, uniqueNames.get(normalizedName).getKey());
                }
                nameKeyMap.put(normalizedName, uniqueNames.get(normalizedName).getKey());
                continue;
            }
            MindmapAnalysis.Node node = new MindmapAnalysis.Node();
            node.setKey(key);
            node.setName(name);
            node.setParentKey(trimToNull(raw.getParentKey()));
            node.setSection(truncate(trimToNull(raw.getSection()), 200));
            node.setDefinition(truncate(trimToNull(raw.getDefinition()), 1000));
            node.setType(normalizeType(raw.getType()));
            node.setDifficulty(clamp(raw.getDifficulty(), 1, 5, 2));
            node.setImportance(clamp(raw.getImportance(), 1, 4, 3));
            node.setEvidence(cleanEvidence(raw.getEvidence()));
            uniqueNames.put(normalizedName, node);
            nodes.add(node);
            if (originalKey != null)
            {
                keyRemap.put(originalKey, key);
            }
            nameKeyMap.put(normalizedName, key);
            if (nodes.size() >= MAX_NODES)
            {
                break;
            }
        }

        Set<String> nodeKeys = new HashSet<String>();
        for (MindmapAnalysis.Node node : nodes)
        {
            nodeKeys.add(node.getKey());
        }
        for (MindmapAnalysis.Node node : nodes)
        {
            String parentKey = node.getParentKey();
            if (parentKey == null)
            {
                continue;
            }
            String mapped = keyRemap.containsKey(parentKey) ? keyRemap.get(parentKey) : parentKey;
            if (!nodeKeys.contains(mapped) || mapped.equals(node.getKey()))
            {
                node.setParentKey(null);
            }
            else
            {
                node.setParentKey(mapped);
            }
        }
        analysis.setNodes(nodes);

        List<MindmapAnalysis.Relation> relations = new ArrayList<MindmapAnalysis.Relation>();
        Set<String> pairs = new HashSet<String>();
        for (MindmapAnalysis.Relation raw : analysis.getRelations())
        {
            if (raw == null)
            {
                continue;
            }
            String sourceKey = resolveKey(raw.getSourceKey(), keyRemap, nameKeyMap);
            String targetKey = resolveKey(raw.getTargetKey(), keyRemap, nameKeyMap);
            if (sourceKey == null || targetKey == null || sourceKey.equals(targetKey))
            {
                continue;
            }
            String pair = sourceKey + "->" + targetKey;
            if (!pairs.add(pair))
            {
                continue;
            }
            MindmapAnalysis.Relation relation = new MindmapAnalysis.Relation();
            relation.setSourceKey(sourceKey);
            relation.setTargetKey(targetKey);
            relation.setRelationType(normalizeRelationType(raw.getRelationType()));
            relation.setDescription(truncate(trimToNull(raw.getDescription()), 500));
            relation.setWeight(clamp(raw.getWeight(), 1, 5, 3));
            relations.add(relation);
            if (relations.size() >= MAX_RELATIONS)
            {
                break;
            }
        }
        analysis.setRelations(relations);

        List<String> conclusions = new ArrayList<String>();
        Set<String> uniqueConclusions = new HashSet<String>();
        for (String raw : analysis.getConclusions())
        {
            String value = truncate(trimToNull(raw), 300);
            if (value == null || !uniqueConclusions.add(value))
            {
                continue;
            }
            conclusions.add(value);
            if (conclusions.size() >= MAX_CONCLUSIONS)
            {
                break;
            }
        }
        analysis.setConclusions(conclusions);
    }

    // ---------------------------------------------------------------------
    // 入库辅助
    // ---------------------------------------------------------------------

    private List<KnowledgePoint> loadSubjectPoints(Long subjectId)
    {
        KnowledgePoint query = new KnowledgePoint();
        query.setSubjectId(subjectId);
        return pointMapper.selectKnowledgePointList(query);
    }

    private KnowledgePoint fuzzyMatch(String normalizedName, Map<String, KnowledgePoint> nameIndex)
    {
        if (normalizedName.length() < 4)
        {
            return null;
        }
        KnowledgePoint best = null;
        double bestRatio = 0D;
        for (Map.Entry<String, KnowledgePoint> entry : nameIndex.entrySet())
        {
            String existing = entry.getKey();
            if (existing.length() < 4)
            {
                continue;
            }
            if (!existing.contains(normalizedName) && !normalizedName.contains(existing))
            {
                continue;
            }
            double ratio = (double) Math.min(existing.length(), normalizedName.length())
                    / Math.max(existing.length(), normalizedName.length());
            if (ratio >= 0.75D && ratio > bestRatio)
            {
                best = entry.getValue();
                bestRatio = ratio;
            }
        }
        return best;
    }

    private KnowledgePoint buildPoint(Long subjectId, MindmapAnalysis.Node node, long sortOrder)
    {
        KnowledgePoint point = new KnowledgePoint();
        point.setSubjectId(subjectId);
        point.setName(truncate(node.getName(), 200));
        StringBuilder content = new StringBuilder();
        if (notBlank(node.getDefinition()))
        {
            content.append(node.getDefinition().trim());
        }
        if (!node.getEvidence().isEmpty())
        {
            if (content.length() > 0)
            {
                content.append("\n\n");
            }
            content.append("【视频依据】\n").append(String.join("\n", node.getEvidence()));
        }
        if (content.length() == 0)
        {
            content.append("由课程视频结构化梳理生成");
        }
        point.setContent(content.toString());
        point.setDifficulty((long) clamp(node.getDifficulty(), 1, 5, 2));
        point.setImportance((long) clamp(node.getImportance(), 1, 4, 3));
        point.setType(normalizeType(node.getType()));
        point.setSortOrder(sortOrder);
        point.setStatus("0");
        point.setDelFlag("0");
        point.setCreateBy(currentUser());
        point.setCreateTime(new Date());
        return point;
    }

    private int bindChapter(String chapterNo, Long subjectId, MindmapAnalysis analysis,
            Map<String, Long> keyToPointId)
    {
        if (!notBlank(chapterNo))
        {
            return 0;
        }
        CourseChapter chapter = chapterMapper.checkChapterNoUnique(subjectId, chapterNo.trim());
        if (chapter == null || chapter.getId() == null)
        {
            return 0;
        }
        List<ChapterPoint> bound = chapterMapper.selectChapterPointList(chapter.getId());
        Set<Long> boundIds = new HashSet<Long>();
        long maxSort = 0L;
        for (ChapterPoint item : bound)
        {
            if (item.getPointId() != null)
            {
                boundIds.add(item.getPointId());
            }
            if (item.getSortOrder() != null && item.getSortOrder() > maxSort)
            {
                maxSort = item.getSortOrder();
            }
        }

        Map<String, MindmapAnalysis.Node> nodeByKey = new LinkedHashMap<String, MindmapAnalysis.Node>();
        for (MindmapAnalysis.Node node : analysis.getNodes())
        {
            nodeByKey.put(node.getKey(), node);
        }

        List<ChapterPoint> toAdd = new ArrayList<ChapterPoint>();
        long sort = maxSort + 10;
        for (Map.Entry<String, Long> entry : keyToPointId.entrySet())
        {
            Long pointId = entry.getValue();
            if (pointId == null || boundIds.contains(pointId))
            {
                continue;
            }
            MindmapAnalysis.Node node = nodeByKey.get(entry.getKey());
            ChapterPoint chapterPoint = new ChapterPoint();
            chapterPoint.setChapterId(chapter.getId());
            chapterPoint.setPointId(pointId);
            String section = node == null ? null : trimToNull(node.getSection());
            chapterPoint.setSection(section == null ? chapter.getTitle() : section);
            chapterPoint.setSortOrder(sort);
            chapterPoint.setCreateTime(new Date());
            toAdd.add(chapterPoint);
            boundIds.add(pointId);
            sort += 10;
        }
        if (!toAdd.isEmpty())
        {
            chapterMapper.batchChapterPoint(toAdd);
        }
        return toAdd.size();
    }

    // ---------------------------------------------------------------------
    // 文本与通用工具
    // ---------------------------------------------------------------------

    private String buildTranscript(List<MindmapAnalyzeRequest.Chapter> chapters)
    {
        StringBuilder builder = new StringBuilder();
        boolean truncated = false;
        for (MindmapAnalyzeRequest.Chapter chapter : chapters)
        {
            if (chapter == null)
            {
                continue;
            }
            String title = notBlank(chapter.getTitle()) ? chapter.getTitle().trim() : "未命名章节";
            if (builder.length() + title.length() + 4 > MAX_TRANSCRIPT_CHARS)
            {
                truncated = true;
                break;
            }
            builder.append("## ").append(title).append('\n');
            for (MindmapAnalyzeRequest.Segment segment : chapter.getSegments())
            {
                if (segment == null || !notBlank(segment.getText()))
                {
                    continue;
                }
                String line = "[" + formatTime(segment.getStart()) + "] " + segment.getText().trim() + "\n";
                if (builder.length() + line.length() > MAX_TRANSCRIPT_CHARS)
                {
                    truncated = true;
                    break;
                }
                builder.append(line);
            }
            if (truncated)
            {
                break;
            }
            builder.append('\n');
        }
        if (truncated)
        {
            builder.append("（内容较长，已截断）\n");
        }
        return builder.toString();
    }

    private static String formatTime(Integer seconds)
    {
        int value = seconds == null ? 0 : Math.max(0, seconds);
        return String.format(Locale.ROOT, "%02d:%02d", value / 60, value % 60);
    }

    private static String resolveKey(String rawKey, Map<String, String> keyRemap,
            Map<String, String> nameKeyMap)
    {
        String key = trimToNull(rawKey);
        if (key == null)
        {
            return null;
        }
        if (keyRemap.containsKey(key))
        {
            return keyRemap.get(key);
        }
        String byName = nameKeyMap.get(normalizeName(key));
        return byName != null ? byName : (keyRemap.containsValue(key) ? key : null);
    }

    private static String normalizeRelationType(String raw)
    {
        if (notBlank(raw))
        {
            String value = raw.trim().toLowerCase(Locale.ROOT);
            if (RELATION_TYPES.contains(value))
            {
                return value;
            }
            if (value.contains("prereq") || value.contains("前置"))
            {
                return "prerequisite";
            }
            if (value.contains("ext") || value.contains("扩展") || value.contains("延伸"))
            {
                return "extension";
            }
        }
        return "related";
    }

    private static String normalizeType(String raw)
    {
        if (notBlank(raw))
        {
            String value = raw.trim();
            if (KNOWLEDGE_TYPES.contains(value))
            {
                return value;
            }
            if (value.contains("协议"))
            {
                return "协议";
            }
            if (value.contains("算法"))
            {
                return "算法";
            }
            if (value.contains("指标") || value.contains("性能"))
            {
                return "指标";
            }
            if (value.contains("设备"))
            {
                return "设备";
            }
            if (value.contains("标准") || value.contains("规范"))
            {
                return "标准";
            }
            if (value.contains("组织") || value.contains("机构"))
            {
                return "组织";
            }
            if (value.contains("应用") || value.contains("架构"))
            {
                return "应用";
            }
            if (value.contains("技术") || value.contains("方法"))
            {
                return "技术";
            }
        }
        return "概念";
    }

    private static List<String> cleanEvidence(List<String> evidence)
    {
        List<String> result = new ArrayList<String>();
        if (evidence == null)
        {
            return result;
        }
        for (String item : evidence)
        {
            String value = truncate(trimToNull(item), 300);
            if (value != null && !result.contains(value))
            {
                result.add(value);
            }
            if (result.size() >= 3)
            {
                break;
            }
        }
        return result;
    }

    private static String normalizeName(String name)
    {
        if (name == null)
        {
            return "";
        }
        return NAME_NOISE.matcher(name.toLowerCase(Locale.ROOT)).replaceAll("");
    }

    /**
     * 用于同名/近似匹配的名称：在归一化基础上去掉“概念/功能/技术”等通用后缀。
     */
    private static String matchName(String name)
    {
        String value = normalizeName(name);
        boolean changed = true;
        while (changed)
        {
            changed = false;
            for (String suffix : GENERIC_SUFFIXES)
            {
                if (value.length() - suffix.length() >= 4 && value.endsWith(suffix))
                {
                    value = value.substring(0, value.length() - suffix.length());
                    changed = true;
                }
            }
        }
        return value;
    }

    private static String textValue(JsonNode node, String field)
    {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }

    private static Integer intValue(JsonNode node, String field)
    {
        JsonNode value = node.path(field);
        return value.isNumber() ? value.asInt() : null;
    }

    private static int clamp(Integer value, int min, int max, int defaultValue)
    {
        if (value == null)
        {
            return defaultValue;
        }
        return Math.max(min, Math.min(max, value));
    }

    private static String nullable(String value)
    {
        return trimToNull(value);
    }

    private static String trimToNull(String value)
    {
        if (value == null)
        {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String truncate(String value, int maxLength)
    {
        if (value == null)
        {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private static boolean notBlank(String value)
    {
        return value != null && !value.trim().isEmpty();
    }

    private String currentUser()
    {
        try
        {
            return SecurityUtils.getUsername();
        }
        catch (Exception e)
        {
            return "system";
        }
    }
}
