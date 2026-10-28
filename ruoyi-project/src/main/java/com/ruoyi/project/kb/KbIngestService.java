package com.ruoyi.project.kb;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.project.course.domain.CourseQuestion;
import com.ruoyi.project.course.mapper.CourseQuestionMapper;
import com.ruoyi.project.domain.ChapterPoint;
import com.ruoyi.project.domain.CourseChapter;
import com.ruoyi.project.domain.KbBase;
import com.ruoyi.project.domain.KbChunk;
import com.ruoyi.project.domain.KbDocument;
import com.ruoyi.project.mapper.CourseChapterMapper;
import com.ruoyi.project.mapper.KbChunkMapper;
import com.ruoyi.project.mapper.KbDocumentMapper;

/**
 * 知识库构建服务：把课程语料切块、向量化并写入向量库。
 *
 * <p>数据模型对标百炼「知识库 → 文档 → 切片」三层：
 * 一次入库的源文档写入 {@code kb_document}，切片写入 {@code kb_chunk}，
 * 切片通过 {@code doc_pk} 归属文档，通过 {@code kb_id} 归属知识库。</p>
 *
 * <p>语料来源（按章节聚合，便于回答时携带章节上下文）：
 * <ol>
 *   <li>chapter —— 章节标题与描述；</li>
 *   <li>point —— knowledge_point.content（含教材页码与说明）；</li>
 *   <li>transcript —— 视频逐字稿（按句聚合为窗口块，保留时间戳）；</li>
 *   <li>quiz —— 题库题干/答案/解析。</li>
 * </ol></p>
 *
 * <p>v2 起支持增量入库：切片带 {@code content_hash}，重建时只对内容变化的文档
 * 重新向量化，未变化的文档原样保留，避免每次全量调用 embedding 接口。</p>
 *
 * @author ruoyi
 */
@Service
public class KbIngestService
{
    private static final Logger log = LoggerFactory.getLogger(KbIngestService.class);

    public static final String TYPE_CHAPTER = "chapter";

    public static final String TYPE_POINT = "point";

    public static final String TYPE_TRANSCRIPT = "transcript";

    public static final String TYPE_QUIZ = "quiz";

    /** 关键词召回的最小查询长度（ngram_token_size=2，短于此长度无法命中） */
    private static final int MIN_KEYWORD_LENGTH = 2;

    private final KbProperties kbProperties;

    private final EmbeddingClient embeddingClient;

    private final KbVectorStoreFactory storeFactory;

    private final KbChunkMapper kbChunkMapper;

    private final KbDocumentMapper kbDocumentMapper;

    private final CourseChapterMapper courseChapterMapper;

    private final CourseQuestionMapper courseQuestionMapper;

    private final KbBaseService kbBaseService;

    private final RerankClient rerankClient;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public KbIngestService(KbProperties kbProperties, EmbeddingClient embeddingClient,
            KbVectorStoreFactory storeFactory, KbChunkMapper kbChunkMapper,
            KbDocumentMapper kbDocumentMapper, CourseChapterMapper courseChapterMapper,
            CourseQuestionMapper courseQuestionMapper, KbBaseService kbBaseService,
            RerankClient rerankClient)
    {
        this.kbProperties = kbProperties;
        this.embeddingClient = embeddingClient;
        this.storeFactory = storeFactory;
        this.kbChunkMapper = kbChunkMapper;
        this.kbDocumentMapper = kbDocumentMapper;
        this.courseChapterMapper = courseChapterMapper;
        this.courseQuestionMapper = courseQuestionMapper;
        this.kbBaseService = kbBaseService;
        this.rerankClient = rerankClient;
    }

    /**
     * 重建知识库（增量）。
     *
     * @param subjectId 学科ID（必填，按学科重建）
     * @param chapterId 章节ID（可空；为空表示该学科全部章节）
     * @param testId 题库ID（可空；为空则不灌题库语料）
     * @param transcriptLines 逐字稿句子（可空；由调用方从课程语料导入）
     * @return 执行结果统计
     */
    public Map<String, Object> rebuild(Long subjectId, Long chapterId, Long testId,
            List<TranscriptLine> transcriptLines)
    {
        // 单课时兼容入口：包装成一个没有 lessonId 的课时
        List<LessonTranscript> lessons = new ArrayList<LessonTranscript>();
        if (transcriptLines != null && !transcriptLines.isEmpty())
        {
            lessons.add(new LessonTranscript(null, null, testId, transcriptLines));
        }
        return rebuildByLessons(subjectId, chapterId, testId, lessons);
    }

    /**
     * 重建知识库（增量），支持一章多课时。
     *
     * @param subjectId 学科ID（必填，按学科重建）
     * @param chapterId 章节ID（可空；为空表示该学科全部章节）
     * @param testId 题库ID（可空；为空则不灌题库语料）
     * @param lessonTranscripts 本章各课时的逐字稿（可空）
     * @return 执行结果统计
     */
    public Map<String, Object> rebuildByLessons(Long subjectId, Long chapterId, Long testId,
            List<LessonTranscript> lessonTranscripts)
    {
        if (subjectId == null)
        {
            throw new IllegalArgumentException("学科ID不能为空");
        }
        if (!embeddingClient.isConfigured())
        {
            throw new IllegalStateException("向量化未配置，请设置 config/application.yml 的 kb.embedding 或 langchain.api-key");
        }

        KbVectorStore store = storeFactory.refresh();
        KbBase kbBase = kbBaseService.getBySubjectId(subjectId);
        Long kbId = kbBase == null ? null : kbBase.getId();

        // 1. 记录旧文档与旧指纹，用于计算增量与陈旧数据
        List<String> previousDocIds = kbChunkMapper.selectKbChunkDocIds(subjectId, chapterId);
        Map<String, String> existingHashes = loadExistingHashes(subjectId, chapterId);

        // 2. 构建文档与切片（此时还没有 docPk / 向量）
        Map<String, KbDocument> documents = new LinkedHashMap<>();
        Map<String, List<KbChunk>> chunksByDoc = new LinkedHashMap<>();

        buildChapterDocs(subjectId, kbId, chapterId, documents, chunksByDoc);
        buildPointDocs(subjectId, kbId, chapterId, documents, chunksByDoc);
        buildTranscriptDocs(subjectId, kbId, chapterId, lessonTranscripts, documents, chunksByDoc);
        buildQuizDocs(subjectId, kbId, chapterId, testId, lessonTranscripts, documents, chunksByDoc);

        // 2.1 回填文档级统计（切片数 / token 数 / 文档指纹），供文档列表与增量判断使用
        fillDocumentStats(documents, chunksByDoc);

        // 3. 找出内容发生变化的文档（指纹或切片数不一致）
        Map<String, List<KbChunk>> changedByDoc = new LinkedHashMap<>();
        for (Map.Entry<String, List<KbChunk>> entry : chunksByDoc.entrySet())
        {
            if (isChanged(entry.getKey(), entry.getValue(), existingHashes))
            {
                changedByDoc.put(entry.getKey(), entry.getValue());
            }
        }

        // 4. 只对变化部分向量化：失败时旧数据尚未删除，可安全重试
        List<KbChunk> toEmbed = new ArrayList<>();
        for (List<KbChunk> list : changedByDoc.values())
        {
            toEmbed.addAll(list);
        }
        if (!toEmbed.isEmpty())
        {
            List<String> texts = new ArrayList<>(toEmbed.size());
            for (KbChunk chunk : toEmbed)
            {
                texts.add(chunk.getContent());
            }
            List<float[]> vectors = embeddingClient.embedBatch(texts);
            for (int i = 0; i < toEmbed.size(); i++)
            {
                KbChunk chunk = toEmbed.get(i);
                chunk.setModel(kbProperties.getEmbedding().getModel());
                chunk.setDimensions(kbProperties.getEmbedding().getDimensions());
                chunk.setEmbedding(toJson(vectors.get(i)));
            }
        }

        // 5. 先落文档层，再让切片挂到文档上
        for (KbDocument document : documents.values())
        {
            kbDocumentMapper.upsertKbDocument(document);
            List<KbChunk> list = chunksByDoc.get(document.getDocKey());
            if (list != null)
            {
                for (KbChunk chunk : list)
                {
                    chunk.setDocPk(document.getId());
                }
            }
        }

        // 6. 变化的文档「先删后写」，顺带清掉切片数变少时残留的旧序号
        int rewritten = 0;
        if (!changedByDoc.isEmpty())
        {
            store.deleteByDocIds(new ArrayList<>(changedByDoc.keySet()));
            if (!toEmbed.isEmpty())
            {
                rewritten = store.upsert(toEmbed);
            }
        }

        // 7. 清理已不存在的文档
        Set<String> newDocIds = chunksByDoc.keySet();
        List<String> staleDocIds = new ArrayList<>();
        for (String docId : previousDocIds)
        {
            if (!newDocIds.contains(docId))
            {
                staleDocIds.add(docId);
            }
        }
        int staleRemoved = 0;
        if (!staleDocIds.isEmpty())
        {
            try
            {
                staleRemoved = store.deleteByDocIds(staleDocIds);
                kbDocumentMapper.deleteKbDocumentByKeys(staleDocIds);
            }
            catch (Exception e)
            {
                // 陈旧向量回填不到原文，检索侧已丢弃，不影响回答正确性
                log.warn("清理陈旧知识库文档失败: subjectId={}, count={}, error={}",
                        subjectId, staleDocIds.size(), e.getMessage());
            }
        }

        Map<String, Integer> stats = countByType(chunksByDoc);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("subjectId", subjectId);
        result.put("chapterId", chapterId);
        result.put("provider", store.provider());
        result.put("documents", documents.size());
        result.put("total", toEmbed.size());
        result.put("unchanged", countAll(chunksByDoc) - toEmbed.size());
        result.put("rewritten", rewritten);
        result.put("staleRemoved", staleRemoved);
        result.put("stats", stats);
        log.info("知识库重建完成: subjectId={}, chapterId={}, provider={}, docs={}, embedded={}, unchanged={}",
                subjectId, chapterId, store.provider(), documents.size(), toEmbed.size(),
                countAll(chunksByDoc) - toEmbed.size());
        return result;
    }

    /**
     * 知识库统计（文档数 + 分块总数 + 各语料类型数量 + 向量库状态）。
     */
    public Map<String, Object> stats(Long subjectId)
    {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("documents", kbDocumentMapper.countKbDocument(subjectId));
        result.put("total", kbChunkMapper.countKbChunk(subjectId));
        result.put("byType", kbChunkMapper.countGroupBySourceType(subjectId));
        result.put("vectorStore", storeFactory.describe());
        result.put("embedding", embeddingInfo());
        result.put("retrieval", retrievalInfo());
        return result;
    }

    /**
     * 检索策略信息（前端提示 / 排障用）。
     */
    public Map<String, Object> retrievalInfo()
    {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("hybrid", kbProperties.getRetrieval().isHybrid());
        info.put("candidateK", kbProperties.getRetrieval().getCandidateK());
        info.put("topK", kbProperties.getTopK());
        info.put("minScore", kbProperties.getMinScore());
        info.put("rerank", rerankClient.isEnabled());
        info.put("rerankModel", kbProperties.getRerank().getModel());
        return info;
    }

    /**
     * 向量化配置状态（用于前端提示）。
     */
    public Map<String, Object> embeddingInfo()
    {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("configured", embeddingClient.isConfigured());
        info.put("model", kbProperties.getEmbedding().getModel());
        info.put("dimensions", kbProperties.getEmbedding().getDimensions());
        info.put("baseUrl", embeddingClient.resolvedBaseUrl());
        return info;
    }

    /**
     * 清空知识库（按学科/章节/类型）。
     */
    public int clear(Long subjectId, Long chapterId, String sourceType)
    {
        int removed = storeFactory.active().clear(subjectId, chapterId, sourceType);
        kbDocumentMapper.deleteKbDocumentByFilter(subjectId, chapterId, sourceType);
        return removed;
    }

    /**
     * 检索：向量召回 + 关键词召回 → RRF 融合 → 可选 Rerank。
     *
     * <p>与百炼检索服务一致：两路各召回一批候选（默认 50），
     * 融合后再收敛到 topK，最后按需交给精排模型排序。</p>
     */
    public List<KbChunk> search(String query, Long subjectId, Long chapterId, Integer topK)
    {
        if (query == null || query.trim().isEmpty())
        {
            throw new IllegalArgumentException("查询内容不能为空");
        }
        if (!embeddingClient.isConfigured())
        {
            throw new IllegalStateException("向量化未配置，无法检索");
        }
        String keyword = query.trim();
        int limit = topK == null || topK <= 0 ? kbProperties.getTopK() : topK;
        int candidateK = Math.max(limit, Math.max(1, kbProperties.getRetrieval().getCandidateK()));

        // 1. 向量召回
        float[] vector = embeddingClient.embed(keyword);
        List<KbChunk> vectorHits = new ArrayList<>();
        for (KbChunk hit : storeFactory.active().search(vector, subjectId, chapterId, null, candidateK))
        {
            if (hit.getScore() != null && hit.getScore() >= kbProperties.getMinScore())
            {
                vectorHits.add(hit);
            }
        }

        // 2. 关键词召回（中文 ngram 全文索引），失败不影响主链路
        List<KbChunk> keywordHits = new ArrayList<>();
        if (kbProperties.getRetrieval().isHybrid() && keyword.length() >= MIN_KEYWORD_LENGTH)
        {
            try
            {
                keywordHits = kbChunkMapper.searchKbChunkByKeyword(subjectId, chapterId, null,
                        keyword, candidateK);
            }
            catch (Exception e)
            {
                log.warn("关键词召回失败，仅使用向量召回：{}", e.getMessage());
            }
        }

        // 3. RRF 融合
        List<KbChunk> fused = fuse(vectorHits, keywordHits, limit);

        // 4. 可选精排
        return rerankClient.rerank(keyword, fused, limit);
    }

    /**
     * 列出可切换的知识库：以学科为单位（1 学科 = 1 知识库）。
     *
     * <p>数据来源为知识库主表 {@code kb_base}，因此新建学科（自动建库）后即使尚未重建
     * 也会出现在列表中（分块数为 0）；另附「全部知识库」用于跨学科检索。</p>
     */
    public List<Map<String, Object>> listBases()
    {
        List<KbBase> kbBases = kbBaseService.list();
        long totalAll = 0L;
        for (KbBase kb : kbBases)
        {
            totalAll += kb.getChunkCount() == null ? 0L : kb.getChunkCount();
        }

        List<Map<String, Object>> bases = new ArrayList<Map<String, Object>>();
        bases.add(baseItem("all", "全部知识库", "all", null, null, totalAll));
        for (KbBase kb : kbBases)
        {
            Map<String, Object> item = baseItem("s" + kb.getSubjectId(), kb.getName(), "subject",
                    kb.getSubjectId(), null, kb.getChunkCount());
            item.put("kbId", kb.getId());
            item.put("status", kb.getStatus());
            bases.add(item);
        }
        return bases;
    }

    /**
     * 知识库检索是否启用（前端用于提示）。
     */
    public boolean isEnabled()
    {
        return kbProperties.isEnabled();
    }

    // ==================== 语料构建 ====================

    private void buildChapterDocs(Long subjectId, Long kbId, Long chapterId,
            Map<String, KbDocument> documents, Map<String, List<KbChunk>> chunksByDoc)
    {
        CourseChapter query = new CourseChapter();
        query.setSubjectId(subjectId);
        for (CourseChapter chapter : courseChapterMapper.selectCourseChapterList(query))
        {
            if (chapterId != null && !chapterId.equals(chapter.getId()))
            {
                continue;
            }
            String text = buildChapterText(chapter);
            if (text.isEmpty())
            {
                continue;
            }
            String title = joinTitle(chapter.getChapterNo(), chapter.getTitle());
            String docKey = "chapter:" + chapter.getId();
            KbChunk chunk = chunk(docKey, TYPE_CHAPTER, subjectId, chapter.getId(), null,
                    title, text, 0, null);
            chunk.setKbId(kbId);
            chunk.setSectionPath(title);
            addChunk(documents, chunksByDoc, chunk,
                    doc(docKey, kbId, subjectId, chapter.getId(), TYPE_CHAPTER, title, title));
        }
    }

    private void buildPointDocs(Long subjectId, Long kbId, Long chapterId,
            Map<String, KbDocument> documents, Map<String, List<KbChunk>> chunksByDoc)
    {
        CourseChapter query = new CourseChapter();
        query.setSubjectId(subjectId);
        for (CourseChapter chapter : courseChapterMapper.selectCourseChapterList(query))
        {
            if (chapterId != null && !chapterId.equals(chapter.getId()))
            {
                continue;
            }
            String chapterPath = joinTitle(chapter.getChapterNo(), chapter.getTitle());
            for (ChapterPoint cp : courseChapterMapper.selectChapterPointList(chapter.getId()))
            {
                String text = buildPointText(cp);
                if (text.isEmpty() || cp.getPointId() == null)
                {
                    continue;
                }
                String sectionPath = cp.getSection() == null || cp.getSection().isEmpty()
                        ? chapterPath
                        : chapterPath + " > " + cp.getSection();
                String docKey = "point:" + cp.getPointId();
                KbChunk chunk = chunk(docKey, TYPE_POINT, subjectId, chapter.getId(), cp.getPointId(),
                        cp.getPointName(), text, 0, null);
                chunk.setKbId(kbId);
                chunk.setSectionPath(sectionPath);
                addChunk(documents, chunksByDoc, chunk,
                        doc(docKey, kbId, subjectId, chapter.getId(), TYPE_POINT,
                                cp.getPointName(), sectionPath));
            }
        }
    }

    private void buildTranscriptDocs(Long subjectId, Long kbId, Long chapterId,
            List<LessonTranscript> lessonTranscripts, Map<String, KbDocument> documents,
            Map<String, List<KbChunk>> chunksByDoc)
    {
        if (lessonTranscripts == null || lessonTranscripts.isEmpty())
        {
            return;
        }
        for (LessonTranscript lesson : lessonTranscripts)
        {
            if (lesson == null || lesson.getLines() == null || lesson.getLines().isEmpty())
            {
                continue;
            }
            // 一课时 = 一篇文档；doc_key 带 lessonId，避免同一章的多个课时互相覆盖
            String docKey = "transcript:" + subjectId + ":" + (chapterId == null ? 0 : chapterId)
                    + (lesson.getLessonId() == null ? "" : ":" + lesson.getLessonId());
            String chapterPath = chapterPath(chapterId);
            List<KbChunk> windows = buildTranscriptChunks(subjectId, chapterId, docKey, lesson.getLines());
            if (windows.isEmpty())
            {
                continue;
            }
            for (KbChunk chunk : windows)
            {
                chunk.setKbId(kbId);
                chunk.setSectionPath(chapterPath);
            }
            KbDocument document = doc(docKey, kbId, subjectId, chapterId, TYPE_TRANSCRIPT,
                    lesson.getLessonCode() == null ? "视频逐字稿" : "视频逐字稿 " + lesson.getLessonCode(),
                    chapterPath);
            document.setChunkCount(windows.size());
            documents.put(docKey, document);
            chunksByDoc.put(docKey, windows);
        }
    }

    private void buildQuizDocs(Long subjectId, Long kbId, Long chapterId, Long testId,
            List<LessonTranscript> lessonTranscripts, Map<String, KbDocument> documents,
            Map<String, List<KbChunk>> chunksByDoc)
    {
        // 一章多课时：每个课时的题库各成一篇文档
        List<Long> testIds = new ArrayList<Long>();
        if (lessonTranscripts != null)
        {
            for (LessonTranscript lesson : lessonTranscripts)
            {
                if (lesson != null && lesson.getTestId() != null && !testIds.contains(lesson.getTestId()))
                {
                    testIds.add(lesson.getTestId());
                }
            }
        }
        if (testId != null && !testIds.contains(testId))
        {
            testIds.add(testId);
        }
        for (Long currentTestId : testIds)
        {
            buildQuizDoc(subjectId, kbId, chapterId, currentTestId, documents, chunksByDoc);
        }
    }

    private void buildQuizDoc(Long subjectId, Long kbId, Long chapterId, Long testId,
            Map<String, KbDocument> documents, Map<String, List<KbChunk>> chunksByDoc)
    {
        if (testId == null)
        {
            return;
        }
        List<CourseQuestion> questions = courseQuestionMapper.selectByTestId(testId);
        if (questions == null || questions.isEmpty())
        {
            return;
        }
        // 一套题库 = 一篇文档，题目按顺序编号
        String docKey = "quiz:" + testId;
        List<KbChunk> list = new ArrayList<>();
        int index = 0;
        for (CourseQuestion question : questions)
        {
            String text = buildQuizText(question);
            if (text.isEmpty())
            {
                continue;
            }
            KbChunk chunk = chunk(docKey, TYPE_QUIZ, subjectId, chapterId, null,
                    "随堂练习 " + question.getQuestionNumber(), text, index++, null);
            chunk.setKbId(kbId);
            chunk.setSectionPath(chapterPath(chapterId));
            list.add(chunk);
        }
        if (list.isEmpty())
        {
            return;
        }
        KbDocument document = doc(docKey, kbId, subjectId, chapterId, TYPE_QUIZ,
                "随堂练习题库 " + testId, chapterPath(chapterId));
        document.setChunkCount(list.size());
        documents.put(docKey, document);
        chunksByDoc.put(docKey, list);
    }

    /**
     * 逐字稿按窗口聚合：相邻句子拼接到接近 chunkSize，并记录窗口起始时间戳。
     */
    private List<KbChunk> buildTranscriptChunks(Long subjectId, Long chapterId, String docKey,
            List<TranscriptLine> lines)
    {
        List<KbChunk> result = new ArrayList<>();
        int chunkSize = Math.max(80, kbProperties.getChunkSize());
        // 相邻窗口保留少量重叠，避免跨窗口的语义被硬切断
        int overlap = Math.max(0, Math.min(kbProperties.getChunkOverlap(), 120));
        StringBuilder buffer = new StringBuilder();
        Integer windowStart = null;
        int index = 0;
        for (TranscriptLine line : lines)
        {
            if (line == null || line.getText() == null || line.getText().trim().isEmpty())
            {
                continue;
            }
            if (buffer.length() > 0 && buffer.length() + line.getText().length() > chunkSize)
            {
                String window = buffer.toString().trim();
                result.add(chunk(docKey, TYPE_TRANSCRIPT, subjectId, chapterId, null,
                        "视频逐字稿 " + formatTime(windowStart), window, index++, windowStart));
                buffer.setLength(0);
                if (overlap > 0 && window.length() > overlap)
                {
                    buffer.append(window.substring(window.length() - overlap));
                }
                windowStart = null;
            }
            if (windowStart == null)
            {
                windowStart = line.getStart();
            }
            if (buffer.length() > 0)
            {
                buffer.append(' ');
            }
            buffer.append(line.getText().trim());
        }
        if (buffer.length() > 0)
        {
            result.add(chunk(docKey, TYPE_TRANSCRIPT, subjectId, chapterId, null,
                    "视频逐字稿 " + formatTime(windowStart), buffer.toString().trim(), index, windowStart));
        }
        return result;
    }

    // ==================== 内部方法 ====================

    /**
     * 用切片结果回填文档统计：切片数、token 数、文档指纹。
     *
     * <p>文档指纹取所有切片指纹的顺序拼接再 MD5，任一子切片变化都会反映到文档上。</p>
     */
    private void fillDocumentStats(Map<String, KbDocument> documents,
            Map<String, List<KbChunk>> chunksByDoc)
    {
        for (Map.Entry<String, List<KbChunk>> entry : chunksByDoc.entrySet())
        {
            KbDocument document = documents.get(entry.getKey());
            if (document == null)
            {
                continue;
            }
            List<KbChunk> chunks = entry.getValue();
            document.setChunkCount(chunks.size());
            int tokens = 0;
            StringBuilder fingerprint = new StringBuilder();
            for (KbChunk chunk : chunks)
            {
                tokens += chunk.getTokenCount() == null ? 0 : chunk.getTokenCount();
                fingerprint.append(nullToEmpty(chunk.getContentHash()));
            }
            document.setTokenCount(tokens);
            document.setContentHash(md5(fingerprint.toString()));
        }
    }

    private Map<String, String> loadExistingHashes(Long subjectId, Long chapterId)
    {
        Map<String, String> hashes = new LinkedHashMap<>();
        try
        {
            for (Map<String, Object> row : kbChunkMapper.selectKbChunkHashes(subjectId, chapterId))
            {
                Object docId = row.get("docId");
                Object chunkIndex = row.get("chunkIndex");
                Object hash = row.get("contentHash");
                if (docId == null || hash == null)
                {
                    continue;
                }
                hashes.put(docId + "#" + (chunkIndex == null ? 0 : chunkIndex), String.valueOf(hash));
            }
        }
        catch (Exception e)
        {
            // 读不到旧指纹时退化为全量重建，不阻断入库
            log.warn("读取历史分块指纹失败，按全量重建处理：{}", e.getMessage());
        }
        return hashes;
    }

    /**
     * 文档是否需要重建：切片数变化或任一指纹不一致。
     */
    private boolean isChanged(String docKey, List<KbChunk> chunks, Map<String, String> existingHashes)
    {
        int existingCount = 0;
        for (String key : existingHashes.keySet())
        {
            if (key.startsWith(docKey + "#"))
            {
                existingCount++;
            }
        }
        if (existingCount != chunks.size())
        {
            return true;
        }
        for (KbChunk chunk : chunks)
        {
            String old = existingHashes.get(chunk.vectorKey());
            if (old == null || !old.equals(chunk.getContentHash()))
            {
                return true;
            }
        }
        return false;
    }

    private void addChunk(Map<String, KbDocument> documents, Map<String, List<KbChunk>> chunksByDoc,
            KbChunk chunk, KbDocument document)
    {
        documents.putIfAbsent(document.getDocKey(), document);
        chunksByDoc.computeIfAbsent(chunk.getDocId(), key -> new ArrayList<>()).add(chunk);
    }

    private KbDocument doc(String docKey, Long kbId, Long subjectId, Long chapterId,
            String sourceType, String title, String sectionPath)
    {
        KbDocument document = new KbDocument();
        document.setDocKey(docKey);
        document.setKbId(kbId);
        document.setSubjectId(subjectId);
        document.setChapterId(chapterId);
        document.setSourceType(sourceType);
        document.setTitle(title);
        document.setSectionPath(sectionPath);
        document.setStatus("1");
        return document;
    }

    /**
     * 关键词与向量两路结果的 RRF 融合。
     *
     * <p>RRF 只看排名不看分数，能天然抹平「余弦相似度」与「全文相关度」量纲不一致的问题。</p>
     */
    private List<KbChunk> fuse(List<KbChunk> vectorHits, List<KbChunk> keywordHits, int limit)
    {
        int k = Math.max(1, kbProperties.getRetrieval().getRrfK());
        Map<String, Double> scores = new LinkedHashMap<>();
        Map<String, KbChunk> chunks = new LinkedHashMap<>();
        accumulate(vectorHits, k, scores, chunks);
        accumulate(keywordHits, k, scores, chunks);

        List<Map.Entry<String, Double>> entries = new ArrayList<>(scores.entrySet());
        entries.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));

        boolean dedupe = kbProperties.getRetrieval().isDedupeByHash();
        Set<String> hashes = new LinkedHashSet<>();
        List<KbChunk> result = new ArrayList<>();
        for (Map.Entry<String, Double> entry : entries)
        {
            KbChunk chunk = chunks.get(entry.getKey());
            if (chunk == null)
            {
                continue;
            }
            if (dedupe && chunk.getContentHash() != null && !hashes.add(chunk.getContentHash()))
            {
                continue;
            }
            result.add(chunk);
            if (result.size() >= limit)
            {
                break;
            }
        }
        return result;
    }

    private void accumulate(List<KbChunk> hits, int k, Map<String, Double> scores,
            Map<String, KbChunk> chunks)
    {
        if (hits == null)
        {
            return;
        }
        int rank = 1;
        for (KbChunk chunk : hits)
        {
            String key = chunk.vectorKey();
            double score = 1D / (k + rank);
            scores.merge(key, score, Double::sum);
            chunks.putIfAbsent(key, chunk);
            rank++;
        }
    }

    private String buildChapterText(CourseChapter chapter)
    {
        StringBuilder sb = new StringBuilder();
        if (chapter.getChapterNo() != null)
        {
            sb.append("章节：").append(chapter.getChapterNo()).append(' ');
        }
        if (chapter.getTitle() != null)
        {
            sb.append(chapter.getTitle());
        }
        if (chapter.getCourseTitle() != null && !chapter.getCourseTitle().isEmpty())
        {
            sb.append("\n课程：").append(chapter.getCourseTitle());
        }
        if (chapter.getDescription() != null && !chapter.getDescription().isEmpty())
        {
            sb.append("\n简介：").append(chapter.getDescription());
        }
        return sb.toString().trim();
    }

    private String buildPointText(ChapterPoint cp)
    {
        StringBuilder sb = new StringBuilder();
        if (cp.getSection() != null && !cp.getSection().isEmpty())
        {
            sb.append("小节：").append(cp.getSection()).append('\n');
        }
        sb.append("知识点：").append(nullToEmpty(cp.getPointName()));
        if (cp.getPointContent() != null && !cp.getPointContent().isEmpty())
        {
            sb.append('\n').append(cp.getPointContent());
        }
        return sb.toString().trim();
    }

    private String buildQuizText(CourseQuestion question)
    {
        StringBuilder sb = new StringBuilder();
        sb.append("题目：").append(nullToEmpty(question.getContent()));
        if (question.getOptions() != null && !question.getOptions().isEmpty())
        {
            sb.append("\n选项：").append(question.getOptions());
        }
        if (question.getAnswer() != null && !question.getAnswer().isEmpty())
        {
            sb.append("\n答案：").append(question.getAnswer());
        }
        if (question.getAnalysis() != null && !question.getAnalysis().isEmpty())
        {
            sb.append("\n解析：").append(question.getAnalysis());
        }
        return sb.toString().trim();
    }

    private KbChunk chunk(String docId, String sourceType, Long subjectId, Long chapterId, Long pointId,
            String title, String content, int chunkIndex, Integer startTime)
    {
        KbChunk chunk = new KbChunk();
        chunk.setDocId(docId);
        chunk.setSourceType(sourceType);
        chunk.setSubjectId(subjectId);
        chunk.setChapterId(chapterId);
        chunk.setPointId(pointId);
        chunk.setTitle(title);
        chunk.setContent(content);
        chunk.setChunkIndex(chunkIndex);
        chunk.setStartTime(startTime);
        chunk.setStatus(1);
        chunk.setTokenCount(estimateTokens(content));
        chunk.setContentHash(md5(nullToEmpty(title) + "\n" + nullToEmpty(content)));
        return chunk;
    }

    private Map<String, Integer> countByType(Map<String, List<KbChunk>> chunksByDoc)
    {
        Map<String, Integer> stats = new LinkedHashMap<>();
        stats.put(TYPE_CHAPTER, 0);
        stats.put(TYPE_POINT, 0);
        stats.put(TYPE_TRANSCRIPT, 0);
        stats.put(TYPE_QUIZ, 0);
        for (List<KbChunk> list : chunksByDoc.values())
        {
            for (KbChunk chunk : list)
            {
                stats.merge(chunk.getSourceType(), 1, Integer::sum);
            }
        }
        return stats;
    }

    private int countAll(Map<String, List<KbChunk>> chunksByDoc)
    {
        int total = 0;
        for (List<KbChunk> list : chunksByDoc.values())
        {
            total += list.size();
        }
        return total;
    }

    private String chapterPath(Long chapterId)
    {
        if (chapterId == null)
        {
            return null;
        }
        CourseChapter chapter = courseChapterMapper.selectCourseChapterById(chapterId);
        return chapter == null ? null : joinTitle(chapter.getChapterNo(), chapter.getTitle());
    }

    private Map<String, Object> baseItem(String id, String label, String level, Long subjectId,
            Long chapterId, Long chunkCount)
    {
        Map<String, Object> item = new LinkedHashMap<String, Object>();
        item.put("id", id);
        item.put("label", label);
        item.put("level", level);
        item.put("subjectId", subjectId);
        item.put("chapterId", chapterId);
        item.put("chunkCount", chunkCount == null ? 0L : chunkCount);
        return item;
    }

    private String toJson(float[] vector)
    {
        try
        {
            return objectMapper.writeValueAsString(vector);
        }
        catch (Exception e)
        {
            throw new IllegalStateException("向量序列化失败", e);
        }
    }

    private static String joinTitle(String no, String title)
    {
        return (nullToEmpty(no) + " " + nullToEmpty(title)).trim();
    }

    /**
     * token 粗估：中文 1 字约 0.67 token，英文按 4 字符 1 token，统一按 1.5 字符/token 估算。
     */
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

    private static String nullToEmpty(String value)
    {
        return value == null ? "" : value;
    }

    private static String formatTime(Integer seconds)
    {
        int value = seconds == null ? 0 : Math.max(0, seconds);
        return String.format("%02d:%02d", value / 60, value % 60);
    }

    /**
     * 一课时的逐字稿（章下多课时时，一个课时一篇知识库文档）。
     */
    public static class LessonTranscript
    {
        private Long lessonId;

        private String lessonCode;

        private Long testId;

        private List<TranscriptLine> lines;

        public LessonTranscript()
        {
        }

        public LessonTranscript(Long lessonId, String lessonCode, Long testId, List<TranscriptLine> lines)
        {
            this.lessonId = lessonId;
            this.lessonCode = lessonCode;
            this.testId = testId;
            this.lines = lines;
        }

        public Long getLessonId()
        {
            return lessonId;
        }

        public void setLessonId(Long lessonId)
        {
            this.lessonId = lessonId;
        }

        public String getLessonCode()
        {
            return lessonCode;
        }

        public void setLessonCode(String lessonCode)
        {
            this.lessonCode = lessonCode;
        }

        public Long getTestId()
        {
            return testId;
        }

        public void setTestId(Long testId)
        {
            this.testId = testId;
        }

        public List<TranscriptLine> getLines()
        {
            return lines;
        }

        public void setLines(List<TranscriptLine> lines)
        {
            this.lines = lines;
        }
    }

    /**
     * 逐字稿句子（供调用方传入，避免后端依赖前端 JSON 文件）。
     */
    public static class TranscriptLine
    {
        private Integer start;

        private String text;

        public TranscriptLine()
        {
        }

        public TranscriptLine(Integer start, String text)
        {
            this.start = start;
            this.text = text;
        }

        public Integer getStart()
        {
            return start;
        }

        public void setStart(Integer start)
        {
            this.start = start;
        }

        public String getText()
        {
            return text;
        }

        public void setText(String text)
        {
            this.text = text;
        }
    }
}
