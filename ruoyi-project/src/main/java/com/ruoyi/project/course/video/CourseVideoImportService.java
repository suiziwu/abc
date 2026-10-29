package com.ruoyi.project.course.video;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.project.asr.AsrResult;
import com.ruoyi.project.asr.ChunkedAsrTranscriber;
import com.ruoyi.project.course.domain.CourseQuestion;
import com.ruoyi.project.course.mapper.CourseQuestionMapper;
import com.ruoyi.project.domain.ChapterPoint;
import com.ruoyi.project.domain.CourseChapter;
import com.ruoyi.project.domain.CourseLesson;
import com.ruoyi.project.domain.CourseTranscript;
import com.ruoyi.project.domain.CourseVideoImport;
import com.ruoyi.project.domain.KnowledgePoint;
import com.ruoyi.project.kb.KbIngestService;
import com.ruoyi.project.kb.KbIngestService.LessonTranscript;
import com.ruoyi.project.kb.KbIngestService.TranscriptLine;
import com.ruoyi.project.langchain.ChatModelClient;
import com.ruoyi.project.mapper.CourseChapterMapper;
import com.ruoyi.project.mapper.CourseLessonMapper;
import com.ruoyi.project.mapper.CourseTranscriptMapper;
import com.ruoyi.project.mapper.CourseVideoImportMapper;
import com.ruoyi.project.service.IKnowledgePointService;

/**
 * 视频课程自动导入：视频 → 提取音频 → ASR 逐字稿 → LLM 知识点/题目 → 入库 → 知识库重建。
 *
 * <p>解析流程为异步任务，通过 {@code course_video_import} 记录阶段与进度。</p>
 *
 * @author ruoyi
 */
@Service
public class CourseVideoImportService
{
    private static final Logger log = LoggerFactory.getLogger(CourseVideoImportService.class);

    /** 允许的视频后缀 */
    private static final String[] VIDEO_EXTENSIONS = {
            "mp4", "mov", "avi", "mkv", "webm", "flv", "wmv", "ts", "m4v", "mpg", "mpeg", "3gp" };

    private static final Pattern CHAPTER_NO_PATTERN = Pattern.compile("第\\s*(\\d+)\\s*章");

    /** 章号归一化用：从字符串开头提取阿拉伯章号（兼容「第3章」「3」「3.21」「3章」） */
    private static final Pattern ARABIC_CHAPTER_NO = Pattern.compile("第?\\s*([0-9]+)(?:[.\\-]\\d+)?");
    /** 章号归一化用：从字符串开头提取中文章号（兼容「第三章」「第三章数据链路层」） */
    private static final Pattern CN_CHAPTER_NO = Pattern.compile("第?\\s*([零〇一二两三四五六七八九十百]+)");

    private static final String STRUCTURE_PROMPT = "你是课程内容结构化助手。请阅读用户提供的课程视频逐字稿（带 [mm:ss] 时间戳），"
            + "输出严格的 JSON（不要 markdown 代码块、不要任何解释文字），结构如下：\n"
            + "{\n"
            + "  \"title\": \"本章标题（不超过 30 字）\",\n"
            + "  \"description\": \"本章简介（不超过 200 字）\",\n"
            + "  \"sections\": [\n"
            + "    {\"title\": \"小节标题（不超过 30 字）\", \"start\": 0, \"end\": 120,\n"
            + "     \"points\": [\n"
            + "       {\"name\": \"知识点名称（不超过 20 字）\", \"content\": \"讲解摘要（不超过 200 字）\",\n"
            + "        \"type\": \"概念\", \"difficulty\": 3, \"importance\": 2}\n"
            + "     ]}\n"
            + "  ]\n"
            + "}\n"
            + "要求：sections 数量按视频总时长决定，每段约 1.5~3 分钟——总时长不超过 6 分钟切 2-4 段，"
            + "6~12 分钟切 4-6 段，超过 12 分钟切 6-8 段；每段至少 10 句、约 90 秒以上的内容，"
            + "严禁切出只有几句话的碎段；sections 按时间顺序；每个 section 2-5 个知识点；"
            + "type 取值范围：概念/语法/方法/工具/框架/管理；difficulty 1-5；importance 1-4；"
            + "start/end 为相对于视频开始的秒数；只依据逐字稿内容，不要编造。";

    private static final String QUIZ_PROMPT = "你是课程出题助手。请依据用户提供的课程逐字稿，生成随堂练习题，"
            + "输出严格的 JSON（不要 markdown 代码块、不要解释），结构如下：\n"
            + "{\"questions\":[\n"
            + "  {\"type\":\"choice\", \"question\":\"题干\", "
            + "\"options\":[{\"label\":\"A\",\"text\":\"选项A\"},{\"label\":\"B\",\"text\":\"选项B\"},"
            + "{\"label\":\"C\",\"text\":\"选项C\"},{\"label\":\"D\",\"text\":\"选项D\"}], "
            + "\"answer\":[\"A\"], \"analysis\":\"解析\", \"difficulty\":2},\n"
            + "  {\"type\":\"judge\", \"question\":\"判断题干\", "
            + "\"options\":[{\"label\":\"A\",\"text\":\"正确\"},{\"label\":\"B\",\"text\":\"错误\"}], "
            + "\"answer\":[\"B\"], \"analysis\":\"解析\", \"difficulty\":1}\n"
            + "]}\n"
            + "要求：共 6-10 道；choice 为四选一单选题，judge 为判断题；answer 必须是选项 label 数组；"
            + "difficulty 1-3；题干与解析必须来自逐字稿内容。";

    private final CourseVideoImportMapper taskMapper;

    private final CourseTranscriptMapper transcriptMapper;

    private final CourseChapterMapper chapterMapper;

    private final CourseLessonMapper lessonMapper;

    private final CourseQuestionMapper questionMapper;

    private final IKnowledgePointService knowledgePointService;

    private final ChunkedAsrTranscriber transcriber;

    private final ChatModelClient chatModelClient;

    private final KbIngestService kbIngestService;

    private final SubjectLockRegistry subjectLockRegistry;

    /** 课程数据写入的事务边界：persist 内部先删后插，失败必须整体回滚 */
    private final TransactionTemplate transactionTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 导入任务线程池：有界队列 + 调用方兜底执行，避免无界队列堆积导致 OOM；
     * 容器关闭时由 {@link #shutdown()} 停止接收新任务。
     */
    private final ExecutorService executor = new ThreadPoolExecutor(2, 2, 0L, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(8), runnable -> {
                Thread thread = new Thread(runnable, "course-video-import");
                thread.setDaemon(true);
                return thread;
            }, new ThreadPoolExecutor.CallerRunsPolicy());

    @PreDestroy
    public void shutdown()
    {
        executor.shutdown();
        try
        {
            if (!executor.awaitTermination(30, TimeUnit.SECONDS))
            {
                executor.shutdownNow();
            }
        }
        catch (InterruptedException e)
        {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    @Value("${media-kit.base-url:http://127.0.0.1:8130}")
    private String mediaKitBaseUrl;

    @Value("${media-kit.token:}")
    private String mediaKitToken;

    @Value("${media-kit.timeout-seconds:600}")
    private int mediaKitTimeout;

    @Value("${ruoyi.profile:./upload}")
    private String profile;

    private RestTemplate restTemplate;

    /** 上传根目录（绝对路径，避免启动目录不同导致写到系统目录） */
    private Path uploadRoot;

    public CourseVideoImportService(CourseVideoImportMapper taskMapper, CourseTranscriptMapper transcriptMapper,
            CourseChapterMapper chapterMapper, CourseLessonMapper lessonMapper, CourseQuestionMapper questionMapper,
            IKnowledgePointService knowledgePointService, ChunkedAsrTranscriber transcriber,
            ChatModelClient chatModelClient, KbIngestService kbIngestService,
            SubjectLockRegistry subjectLockRegistry,
            org.springframework.transaction.PlatformTransactionManager transactionManager)
    {
        this.taskMapper = taskMapper;
        this.transcriptMapper = transcriptMapper;
        this.chapterMapper = chapterMapper;
        this.lessonMapper = lessonMapper;
        this.questionMapper = questionMapper;
        this.knowledgePointService = knowledgePointService;
        this.transcriber = transcriber;
        this.chatModelClient = chatModelClient;
        this.kbIngestService = kbIngestService;
        this.subjectLockRegistry = subjectLockRegistry;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @PostConstruct
    public void init()
    {
        org.springframework.http.client.SimpleClientHttpRequestFactory factory =
                new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(Math.max(60, mediaKitTimeout) * 1000);
        restTemplate = new RestTemplate(factory);

        Path configured = Paths.get(profile == null ? "./upload" : profile.trim());
        if (!configured.isAbsolute())
        {
            // 相对路径以项目启动目录为基准（常规启动：项目根目录）
            configured = Paths.get(System.getProperty("user.dir")).resolve(configured);
        }
        uploadRoot = configured.toAbsolutePath().normalize();
    }

    /**
     * 创建导入任务（异步执行）。
     *
     * <p>章节与课时两级：{@code chapterNo} 命中已有章时，本次视频作为该章的下一个课时；
     * 未命中则新建章。章名可由 {@code chapterTitle} 指定（如课程目录名「第一章 网络体系结构」），
     * 课时名由 {@code lessonTitle} 指定（如「1.1.1 概念和功能」）。</p>
     *
     * @return 任务ID
     */
    public Long startImport(MultipartFile file, Long subjectId, String chapterNo, String chapterTitle,
            String lessonCode, String lessonTitle, Long testId, String operName)
    {
        if (file == null || file.isEmpty())
        {
            throw new IllegalArgumentException("请选择要上传的视频文件");
        }
        if (subjectId == null)
        {
            throw new IllegalArgumentException("请选择学科");
        }
        String original = file.getOriginalFilename() == null ? "video.mp4" : file.getOriginalFilename();
        String extension = StringUtils.substringAfterLast(original, ".").toLowerCase();
        boolean allowed = false;
        for (String item : VIDEO_EXTENSIONS)
        {
            if (item.equals(extension))
            {
                allowed = true;
                break;
            }
        }
        if (!allowed)
        {
            throw new IllegalArgumentException("不支持的视频格式：" + extension + "，请上传 mp4/mov/mkv/avi/webm 等格式");
        }

        String safeName = Paths.get(original).getFileName().toString().replaceAll("[\\\\/:*?\"<>|]", "_");
        String storedName = System.currentTimeMillis() + "_" + safeName;
        Path dir = uploadRoot.resolve("course_video");
        try
        {
            Files.createDirectories(dir);
            File dest = dir.resolve(storedName).toFile();
            file.transferTo(dest);
        }
        catch (IOException e)
        {
            throw new IllegalStateException("保存视频失败：" + e.getMessage(), e);
        }

        CourseVideoImport task = new CourseVideoImport();
        task.setSubjectId(subjectId);
        task.setChapterNo(StringUtils.isNotEmpty(chapterNo) ? chapterNo.trim() : null);
        task.setLessonCode(StringUtils.isNotEmpty(lessonCode) ? lessonCode.trim() : null);
        task.setTitle(StringUtils.isNotEmpty(lessonTitle) ? lessonTitle.trim()
                : (StringUtils.isNotEmpty(chapterTitle) ? chapterTitle.trim() : null));
        task.setFileName(safeName);
        task.setVideoPath("/profile/course_video/" + storedName);
        task.setStatus("0");
        task.setStage("queued");
        task.setProgress(0);
        task.setTranscriptCount(0);
        task.setPointCount(0);
        task.setQuestionCount(0);
        task.setKbCount(0);
        // 未指定题库时不复用已有题库，分析完成后按课时分配独立题库（避免污染其它课程题目）
        task.setTestId(testId == null || testId <= 0 ? null : testId);
        task.setCreateBy(StringUtils.isNotEmpty(operName) ? operName : "system");

        // 章 → 课时：同一学科串行，避免并发建章/建课时撞号
        final Long subjectIdFinal = subjectId;
        final String chapterNoFinal = task.getChapterNo();
        final String chapterTitleFinal = chapterTitle;
        final String lessonCodeFinal = task.getLessonCode();
        final String lessonTitleFinal = task.getTitle();
        final String operNameFinal = task.getCreateBy();
        subjectLockRegistry.withLock(subjectIdFinal, () -> {
            CourseChapter chapter = resolveChapter(subjectIdFinal, chapterNoFinal, chapterTitleFinal,
                    lessonTitleFinal, operNameFinal);
            task.setChapterId(chapter.getId());
            task.setChapterNo(chapter.getChapterNo());
            CourseLesson lesson = resolveOrCreateLesson(chapter, task.getVideoPath(), lessonCodeFinal,
                    lessonTitleFinal, operNameFinal);
            task.setLessonId(lesson.getId());
            task.setLessonCode(lesson.getLessonCode());
            taskMapper.insert(task);
            return null;
        });

        Long taskId = task.getId();
        log.info("视频课程导入任务已提交：taskId={}, subjectId={}, chapter={}, lesson={}, video={}", taskId, subjectId,
                task.getChapterNo(), task.getLessonCode(), dir.resolve(storedName));
        executor.submit(() -> runPipeline(taskId, dir.resolve(storedName).toAbsolutePath().toString()));
        return taskId;
    }

    /**
     * 仅上传视频：保存文件并挂到章/课时上，不做 ASR / AI 解析，也不写逐字稿和题库。
     *
     * <p>用于只需要视频能播放的场景，上传后立刻可用，不再产生解析任务。</p>
     *
     * @return 课时ID
     */
    public Long attachOnly(MultipartFile file, Long subjectId, String chapterNo, String chapterTitle,
            String lessonCode, String lessonTitle, String operName)
    {
        if (file == null || file.isEmpty())
        {
            throw new IllegalArgumentException("请选择要上传的视频文件");
        }
        if (subjectId == null)
        {
            throw new IllegalArgumentException("请选择学科");
        }
        String original = file.getOriginalFilename() == null ? "video.mp4" : file.getOriginalFilename();
        String extension = StringUtils.substringAfterLast(original, ".").toLowerCase();
        boolean allowed = false;
        for (String item : VIDEO_EXTENSIONS)
        {
            if (item.equals(extension))
            {
                allowed = true;
                break;
            }
        }
        if (!allowed)
        {
            throw new IllegalArgumentException("不支持的视频格式：" + extension + "，请上传 mp4/mov/mkv/avi/webm 等格式");
        }
        String safeName = Paths.get(original).getFileName().toString().replaceAll("[\\\\/:*?\"<>|]", "_");
        String storedName = System.currentTimeMillis() + "_" + safeName;
        Path dir = uploadRoot.resolve("course_video");
        try
        {
            Files.createDirectories(dir);
            file.transferTo(dir.resolve(storedName).toFile());
        }
        catch (IOException e)
        {
            throw new IllegalStateException("保存视频失败：" + e.getMessage(), e);
        }
        final String videoPath = "/profile/course_video/" + storedName;
        return subjectLockRegistry.withLock(subjectId, () -> {
            CourseChapter chapter = resolveChapter(subjectId, chapterNo, chapterTitle, lessonTitle, operName);
            CourseLesson lesson = resolveOrCreateLesson(chapter, videoPath, lessonCode, lessonTitle, operName);
            log.info("仅上传视频：chapter={}, lesson={}, video={}", chapter.getChapterNo(),
                    lesson.getLessonCode(), videoPath);
            return lesson.getId();
        });
    }

    /**
     * 定位或创建章节：命中同章号的已有章则复用，否则新建。
     */
    private CourseChapter resolveChapter(Long subjectId, String chapterNo, String chapterTitle,
            String lessonTitle, String operName)
    {
        List<CourseChapter> chapters = chapterMapper.selectCourseChapterList(chapterQuery(subjectId));
        String requestedNo = normalizeChapterNo(chapterNo);
        CourseChapter chapter = findChapter(chapters, requestedNo);
        if (chapter != null)
        {
            // 章已存在：仅当标题为空时补一次章名；不接受目录名覆盖规范章名
            //（否则「第四章 网络层」这类目录名会把「网络层」冲掉）
            if (StringUtils.isEmpty(chapter.getTitle()) && StringUtils.isNotEmpty(chapterTitle))
            {
                chapter.setTitle(StringUtils.substring(chapterTitle.trim(), 0, 190));
                chapter.setUpdateBy(operName);
                chapterMapper.updateCourseChapter(chapter);
            }
            return chapter;
        }
        CourseChapter created = new CourseChapter();
        created.setSubjectId(subjectId);
        created.setChapterNo(requestedNo != null ? requestedNo : nextChapterNo(chapters));
        String name = StringUtils.isNotEmpty(chapterTitle) ? chapterTitle.trim()
                : (StringUtils.isNotEmpty(lessonTitle) ? lessonTitle.trim() : created.getChapterNo());
        created.setTitle(StringUtils.substring(name, 0, 190));
        created.setCourseTitle(StringUtils.substring(name, 0, 190));
        created.setSortOrder((long) chapters.size() + 1);
        created.setStatus("0");
        created.setCreateBy(operName);
        chapterMapper.insertCourseChapter(created);
        return created;
    }

    /**
     * 章号归一化：「4」「第 4 章」「第4章」统一成「第4章」。
     *
     * <p>上传入口的章号来自表单/脚本，格式不一致会导致与库中已有章精确匹配失败，
     * 每次上传都新建平行章（曾出现同课程下「4」与「第4章」并存的脏数据）。</p>
     */
    private static String normalizeChapterNo(String chapterNo)
    {
        if (StringUtils.isEmpty(chapterNo))
        {
            return null;
        }
        String cleaned = chapterNo.trim().replaceAll("\\s+", "");
        Integer num = extractChapterNumber(cleaned);
        if (num != null)
        {
            return "第" + num + "章";
        }
        return cleaned;
    }

    /**
     * 从任意章号写法中提取章序号（阿拉伯或中文），供章号归一并匹配已有章使用：
     * 「第3章」「3」「3.21」「第3章 第2节」→ 3；「第三章」「第三章数据链路层」→ 3。
     * 解析不出返回 null。这样上传入口传入「3」「第三章」「3.21」等写法时，
     * 都能命中库中已有的「第3章」，避免同章被拆成两条记录。
     */
    private static Integer extractChapterNumber(String no)
    {
        if (StringUtils.isEmpty(no))
        {
            return null;
        }
        String cleaned = no.trim().replaceAll("\\s+", "");
        Matcher arabic = ARABIC_CHAPTER_NO.matcher(cleaned);
        if (arabic.lookingAt())
        {
            return Integer.parseInt(arabic.group(1));
        }
        Matcher cn = CN_CHAPTER_NO.matcher(cleaned);
        if (cn.lookingAt())
        {
            return cnToNumber(cn.group(1));
        }
        return null;
    }

    /** 中文数字转阿拉伯数字（覆盖章节常用范围，零~九十九） */
    private static Integer cnToNumber(String text)
    {
        if (StringUtils.isEmpty(text))
        {
            return null;
        }
        int total = 0;
        int section = 0;
        for (int i = 0; i < text.length(); i++)
        {
            char ch = text.charAt(i);
            if (ch == '百')
            {
                section = (section == 0 ? 1 : section) * 100;
                total += section;
                section = 0;
            }
            else if (ch == '十')
            {
                section = (section == 0 ? 1 : section) * 10;
                total += section;
                section = 0;
            }
            else
            {
                Integer n = CN_DIGIT_MAP.get(ch);
                if (n == null)
                {
                    return null;
                }
                section = n;
            }
        }
        return total + section;
    }

    private static final java.util.Map<Character, Integer> CN_DIGIT_MAP = new java.util.HashMap<>();
    static
    {
        CN_DIGIT_MAP.put('零', 0);
        CN_DIGIT_MAP.put('〇', 0);
        CN_DIGIT_MAP.put('一', 1);
        CN_DIGIT_MAP.put('二', 2);
        CN_DIGIT_MAP.put('两', 2);
        CN_DIGIT_MAP.put('三', 3);
        CN_DIGIT_MAP.put('四', 4);
        CN_DIGIT_MAP.put('五', 5);
        CN_DIGIT_MAP.put('六', 6);
        CN_DIGIT_MAP.put('七', 7);
        CN_DIGIT_MAP.put('八', 8);
        CN_DIGIT_MAP.put('九', 9);
    }

    /**
     * 课时编号归一化：两段式且第二段为一位数时补零（「4.1」→「4.01」），
     * 与框架预建课时（章.两位序号）对齐，避免同讲产生两个课时。
     */
    private static String normalizeLessonCode(String code)
    {
        if (StringUtils.isEmpty(code))
        {
            return null;
        }
        String cleaned = code.trim();
        Matcher matcher = Pattern.compile("^(\\d+)\\.(\\d)$").matcher(cleaned);
        if (matcher.matches())
        {
            return matcher.group(1) + ".0" + matcher.group(2);
        }
        return cleaned;
    }

    /**
     * 在章节下定位或创建课时：同章下已存在相同课时编号时复用（框架预建课时被填充视频），
     * 否则新建（一章多课时）。复用时不打乱已有 lesson_no 顺序。
     */
    private CourseLesson resolveOrCreateLesson(CourseChapter chapter, String videoPath, String lessonCode,
            String lessonTitle, String operName)
    {
        String code = normalizeLessonCode(lessonCode);
        if (code != null)
        {
            for (CourseLesson existing : lessonMapper.selectByChapterId(chapter.getId()))
            {
                if (code.equals(existing.getLessonCode()))
                {
                    if (StringUtils.isNotEmpty(videoPath))
                    {
                        existing.setVideoPath(videoPath);
                    }
                    if (StringUtils.isEmpty(existing.getTitle()) && StringUtils.isNotEmpty(lessonTitle))
                    {
                        existing.setTitle(StringUtils.substring(lessonTitle, 0, 190));
                    }
                    existing.setUpdateBy(operName);
                    lessonMapper.update(existing);
                    log.info("复用已有课时：chapterId={}, lessonCode={}, lessonId={}", chapter.getId(), code,
                            existing.getId());
                    return existing;
                }
            }
        }
        CourseLesson lesson = new CourseLesson();
        lesson.setChapterId(chapter.getId());
        lesson.setSubjectId(chapter.getSubjectId());
        Integer nextNo = lessonMapper.selectNextLessonNo(chapter.getId());
        lesson.setLessonNo(nextNo == null ? 1 : nextNo);
        lesson.setLessonCode(code != null ? code : chapter.getChapterNo() + "." + lesson.getLessonNo());
        lesson.setTitle(StringUtils.isNotEmpty(lessonTitle) ? StringUtils.substring(lessonTitle, 0, 190)
                : lesson.getLessonCode());
        lesson.setVideoPath(videoPath);
        lesson.setSortOrder(lesson.getLessonNo());
        lesson.setStatus("0");
        lesson.setTranscriptCount(0);
        lesson.setPointCount(0);
        lesson.setQuestionCount(0);
        lesson.setKbCount(0);
        lesson.setCreateBy(operName);
        lessonMapper.insert(lesson);
        return lesson;
    }

    /**
     * 删除课时：连同其逐字稿一并删除，导入任务引用脱钩；磁盘视频文件保留（可人工清理）。
     *
     * @param lessonId 课时ID
     */
    public void deleteLesson(Long lessonId)
    {
        CourseLesson lesson = lessonMapper.selectById(lessonId);
        if (lesson == null)
        {
            throw new IllegalArgumentException("课时不存在或已删除");
        }
        transactionTemplate.execute(status ->
        {
            transcriptMapper.deleteByLessonId(lessonId);
            lessonMapper.deleteById(lessonId);
            taskMapper.detachLesson(lessonId);
            return null;
        });
        log.info("删除课时：lessonId={}, chapterId={}, lessonCode={}, title={}", lessonId,
                lesson.getChapterId(), lesson.getLessonCode(), lesson.getTitle());
    }

    public CourseVideoImport getTask(Long id)
    {
        return taskMapper.selectById(id);
    }

    public List<CourseVideoImport> listTasks(Long subjectId)
    {
        return taskMapper.selectList(subjectId);
    }

    // ==================================================================

    private void runPipeline(Long taskId, String videoPath)
    {
        CourseVideoImport task = taskMapper.selectById(taskId);
        if (task == null)
        {
            return;
        }
        try
        {
            update(taskId, "1", "media", 10, "正在解析视频并提取音频");
            VideoParseResult parsed = parseVideo(videoPath);
            task.setDurationSec(parsed.durationSec);
            update(taskId, "1", "asr", 25, "正在识别语音并生成逐字稿");

            AsrResult asr = transcriber.transcribe(Paths.get(parsed.audioPath));
            List<AsrResult.Segment> segments = normalizeSegments(asr, parsed.durationSec);
            if (segments.isEmpty())
            {
                throw new IllegalStateException("未能识别出有效语音内容，请确认视频包含清晰人声");
            }
            update(taskId, "1", "llm", 50, "AI 正在提取章节、知识点与随堂练习");

            String transcriptText = buildTranscriptText(segments);
            JsonNode structure = analyzeStructure(transcriptText, parsed.durationSec);
            JsonNode quiz = analyzeQuiz(transcriptText);

            update(taskId, "1", "persist", 78, "正在写入章节、知识点、逐字稿与题库");
            // 同一学科串行化：章号/题号/知识点都是「先查后写」，并发会撞号；
            // 事务只包数据库写入，不包 ASR/LLM 等远程调用
            Map<String, Object> persistResult = subjectLockRegistry.withLock(task.getSubjectId(), () -> {
                Map<String, Object> written = persistWithRetry(task, structure, quiz, segments);
                if (written == null)
                {
                    throw new IllegalStateException("写入课程数据失败");
                }
                int kb = 0;
                try
                {
                    update(taskId, "1", "kb", 90, "正在重建知识库");
                    Long chapterId = (Long) written.get("chapterId");
                    // 知识库重建范围 = 本章全部课时（逐字稿 + 随堂练习），
                    // 否则本章其它课时的切片会被当成陈旧文档清理掉
                    List<LessonTranscript> lessonTranscripts = collectLessonTranscripts(chapterId);
                    Map<String, Object> kbResult = kbIngestService.rebuildByLessons(task.getSubjectId(),
                            chapterId, (Long) written.get("testId"), lessonTranscripts);
                    kb = toInt(kbResult.get("total"));
                }
                catch (Exception e)
                {
                    log.warn("知识库重建失败（不影响课程导入）：{}", e.getMessage());
                }
                written.put("kbCount", kb);
                return written;
            });
            int kbCount = toInt(persistResult.get("kbCount"));

            CourseVideoImport done = new CourseVideoImport();
            done.setId(taskId);
            done.setChapterId((Long) persistResult.get("chapterId"));
            done.setChapterNo((String) persistResult.get("chapterNo"));
            done.setTitle((String) persistResult.get("title"));
            done.setTestId((Long) persistResult.get("testId"));
            done.setDurationSec(parsed.durationSec);
            done.setStatus("2");
            done.setStage("done");
            done.setProgress(100);
            done.setTranscriptCount(segments.size());
            done.setPointCount((Integer) persistResult.get("pointCount"));
            done.setQuestionCount((Integer) persistResult.get("questionCount"));
            done.setKbCount(kbCount);
            done.setMessage("导入完成");
            taskMapper.update(done);
        }
        catch (Throwable e)
        {
            log.error("视频课程导入失败，taskId=" + taskId, e);
            CourseVideoImport failed = new CourseVideoImport();
            failed.setId(taskId);
            failed.setStatus("3");
            failed.setStage("error");
            StringBuilder message = new StringBuilder(String.valueOf(e));
            StackTraceElement[] trace = e.getStackTrace();
            if (trace != null && trace.length > 0)
            {
                message.append(" @ ").append(trace[0].toString());
            }
            failed.setMessage(StringUtils.substring(message.toString(), 0, 900));
            try
            {
                taskMapper.update(failed);
            }
            catch (Throwable ignored)
            {
                log.error("更新导入任务失败状态时出错，taskId=" + taskId, ignored);
            }
            // 解析失败：删除刚建的空课时，避免课程页出现没有内容的课时
            try
            {
                if (task.getLessonId() != null && transcriptMapper.selectByLessonId(task.getLessonId()).isEmpty())
                {
                    lessonMapper.deleteById(task.getLessonId());
                }
            }
            catch (Throwable ignored)
            {
                log.warn("清理失败任务的空课时出错，lessonId=" + task.getLessonId());
            }
        }
    }

    private VideoParseResult parseVideo(String videoPath)
    {
        Map<String, Object> config = new LinkedHashMap<String, Object>();
        Map<String, Object> audio = new LinkedHashMap<String, Object>();
        audio.put("enabled", true);
        audio.put("format", "wav");
        audio.put("sample_rate", 16000);
        audio.put("channels", 1);
        config.put("audio", audio);
        Map<String, Object> frames = new LinkedHashMap<String, Object>();
        frames.put("enabled", false);
        config.put("frames", frames);
        Map<String, Object> transcode = new LinkedHashMap<String, Object>();
        transcode.put("enabled", false);
        config.put("transcode", transcode);

        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("path", videoPath);
        payload.put("config", config);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (StringUtils.isNotEmpty(mediaKitToken))
        {
            headers.set("X-Media-Kit-Token", mediaKitToken.trim());
        }
        try
        {
            @SuppressWarnings("unchecked")
            Map<String, Object> result = restTemplate.postForObject(mediaKitBaseUrl + "/api/video",
                    new HttpEntity<Map<String, Object>>(payload, headers), Map.class);
            if (result == null)
            {
                throw new IllegalStateException("media_kit 服务无响应");
            }
            if (!Boolean.TRUE.equals(result.get("ok")))
            {
                String message = "视频解析失败";
                Object error = result.get("error");
                if (error instanceof Map && ((Map<?, ?>) error).get("message") != null)
                {
                    message = String.valueOf(((Map<?, ?>) error).get("message"));
                }
                throw new IllegalStateException(message + "（请确认已启动 tools/start_media_kit.ps1）");
            }
            Map<String, Object> outputs = asMap(result.get("outputs"));
            String audioPath = outputs.get("audio_path") == null ? null : String.valueOf(outputs.get("audio_path"));
            if (StringUtils.isEmpty(audioPath))
            {
                throw new IllegalStateException("视频未提取到音频轨，请确认视频包含声音");
            }
            Map<String, Object> metadata = asMap(result.get("metadata"));
            VideoParseResult parsed = new VideoParseResult();
            parsed.audioPath = audioPath;
            parsed.durationSec = (int) Math.round(toDouble(metadata.get("duration_sec")));
            return parsed;
        }
        catch (HttpStatusCodeException e)
        {
            throw new IllegalStateException("media_kit 解析失败：HTTP " + e.getRawStatusCode());
        }
        catch (RestClientException e)
        {
            throw new IllegalStateException("media_kit 解析服务不可用，请先执行 tools/start_media_kit.ps1。详情：" + e.getMessage());
        }
    }

    private List<AsrResult.Segment> normalizeSegments(AsrResult asr, int durationSec)
    {
        List<AsrResult.Segment> segments = new ArrayList<AsrResult.Segment>();
        for (AsrResult.Segment segment : asr.getSegments())
        {
            if (segment.getText() == null || segment.getText().trim().isEmpty())
            {
                continue;
            }
            AsrResult.Segment copy = new AsrResult.Segment();
            copy.setStartMs(segment.getStartMs() == null ? 0 : segment.getStartMs());
            copy.setEndMs(segment.getEndMs() == null ? copy.getStartMs() : segment.getEndMs());
            copy.setText(segment.getText().trim());
            segments.add(copy);
        }
        if (segments.isEmpty() && StringUtils.isNotEmpty(asr.getText()))
        {
            AsrResult.Segment only = new AsrResult.Segment();
            only.setStartMs(0);
            only.setEndMs(durationSec * 1000);
            only.setText(asr.getText().trim());
            segments.add(only);
        }
        return segments;
    }

    private String buildTranscriptText(List<AsrResult.Segment> segments)
    {
        StringBuilder sb = new StringBuilder();
        for (AsrResult.Segment segment : segments)
        {
            int seconds = Math.max(0, (segment.getStartMs() == null ? 0 : segment.getStartMs()) / 1000);
            sb.append('[').append(String.format("%02d:%02d", seconds / 60, seconds % 60)).append("] ")
                    .append(segment.getText()).append('\n');
        }
        String text = sb.toString().trim();
        if (text.length() > 14000)
        {
            text = text.substring(0, 10000) + "\n……（中间内容省略）……\n"
                    + text.substring(text.length() - 3500);
        }
        return text;
    }

    private JsonNode analyzeStructure(String transcript, int durationSec)
    {
        // 把视频总时长告诉模型，便于按「每段 1.5~3 分钟」确定小节数量
        String answer = chatModelClient.chat(STRUCTURE_PROMPT,
                "【视频总时长】" + durationSec + " 秒\n【课程逐字稿】\n" + transcript);
        return parseJson(answer, "章节/知识点");
    }

    private JsonNode analyzeQuiz(String transcript)
    {
        String answer = chatModelClient.chat(QUIZ_PROMPT, "【课程逐字稿】\n" + transcript);
        return parseJson(answer, "随堂练习");
    }

    private JsonNode parseJson(String answer, String label)
    {
        if (StringUtils.isEmpty(answer))
        {
            throw new IllegalStateException("AI 未返回" + label + "结果，请检查模型配置");
        }
        String text = answer.trim();
        if (text.startsWith("```"))
        {
            int firstLine = text.indexOf('\n');
            text = firstLine >= 0 ? text.substring(firstLine + 1) : text;
            int fence = text.lastIndexOf("```");
            if (fence >= 0)
            {
                text = text.substring(0, fence);
            }
        }
        int start = text.indexOf('{');
        String json = extractJsonObject(text);
        if (json == null)
        {
            // 括号不配对时回退到粗截取，尽量兼容模型输出
            int end = text.lastIndexOf('}');
            if (start < 0 || end <= start)
            {
                throw new IllegalStateException("AI 返回的" + label + "不是有效 JSON："
                        + StringUtils.substring(text, 0, 200));
            }
            json = text.substring(start, end + 1);
        }
        try
        {
            return objectMapper.readTree(json);
        }
        catch (Exception e)
        {
            throw new IllegalStateException("解析 AI 返回的" + label + " JSON 失败：" + e.getMessage());
        }
    }

    /**
     * 从模型输出中截取第一个完整的 JSON 对象。
     *
     * <p>用括号配对而不是 {@code lastIndexOf('}')}：模型在 JSON 后追加解释、
     * 或返回多个对象时，后者会把尾部的说明文字一起截进来导致解析失败。</p>
     *
     * @param text 模型原始输出
     * @return 完整的 JSON 对象文本；括号不配对时返回 null
     */
    private String extractJsonObject(String text)
    {
        int start = text.indexOf('{');
        if (start < 0)
        {
            return null;
        }
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = start; i < text.length(); i++)
        {
            char current = text.charAt(i);
            if (inString)
            {
                if (escaped)
                {
                    escaped = false;
                }
                else if (current == '\\')
                {
                    escaped = true;
                }
                else if (current == '"')
                {
                    inString = false;
                }
                continue;
            }
            if (current == '"')
            {
                inString = true;
            }
            else if (current == '{')
            {
                depth++;
            }
            else if (current == '}')
            {
                depth--;
                if (depth == 0)
                {
                    return text.substring(start, i + 1);
                }
            }
        }
        return null;
    }

    /**
     * 在事务内写入课程数据；撞唯一键（跨实例并发）时整笔重试。
     *
     * <p>重试必须重跑整个事务：唯一键冲突后当前事务已被标记回滚，
     * 在原事务内继续写没有意义。每次重试都会重新读取章节列表并算出新的章号。</p>
     */
    private Map<String, Object> persistWithRetry(CourseVideoImport task, JsonNode structure, JsonNode quiz,
            List<AsrResult.Segment> segments)
    {
        DuplicateKeyException last = null;
        for (int attempt = 1; attempt <= 3; attempt++)
        {
            try
            {
                return transactionTemplate.execute(status -> persist(task, structure, quiz, segments));
            }
            catch (DuplicateKeyException e)
            {
                last = e;
                log.warn("写入课程数据撞唯一键，第 {} 次重试：{}", attempt, e.getMessage());
            }
        }
        throw last == null ? new IllegalStateException("写入课程数据失败") : last;
    }

    private Map<String, Object> persist(CourseVideoImport task, JsonNode structure, JsonNode quiz,
            List<AsrResult.Segment> segments)
    {
        String generatedTitle = structure.path("title").asText("视频课程");
        String title = StringUtils.isNotEmpty(task.getTitle()) ? task.getTitle() : generatedTitle;
        String description = StringUtils.substring(structure.path("description").asText(""), 0, 480);

        // 章与课时在任务创建阶段已落库，这里只补充内容（描述、时长、统计）
        CourseChapter chapter = task.getChapterId() == null ? null
                : chapterMapper.selectCourseChapterById(task.getChapterId());
        CourseLesson lesson = task.getLessonId() == null ? null : lessonMapper.selectById(task.getLessonId());
        if (chapter == null)
        {
            throw new IllegalStateException("课程章节不存在，无法写入导入结果");
        }
        String chapterNo = chapter.getChapterNo();
        if (chapter.getVideo() == null && lesson != null && lesson.getVideoPath() != null)
        {
            // 章视频字段保留首个课时视频，兼容旧版课程页
            chapter.setVideo(lesson.getVideoPath());
        }
        chapter.setDescription(description);
        chapter.setEndTime(task.getDurationSec());
        chapter.setUpdateBy(task.getCreateBy());
        chapterMapper.updateCourseChapter(chapter);

        // 题库：每个课时一套题，未指定时分配独立 testId，避免污染其它课程题目
        Long testId = task.getTestId() != null && task.getTestId() > 0 ? task.getTestId()
                : questionMapper.selectNextTestId();
        if (testId == null)
        {
            throw new IllegalStateException("无法分配题库ID，请检查 question 表数据");
        }

        // 逐字稿：按小节时间范围归类
        List<Map<String, Object>> sections = new ArrayList<Map<String, Object>>();
        JsonNode sectionNodes = structure.path("sections");
        if (sectionNodes.isArray())
        {
            for (JsonNode section : sectionNodes)
            {
                Map<String, Object> item = new LinkedHashMap<String, Object>();
                item.put("title", section.path("title").asText(""));
                item.put("start", section.path("start").asInt(0));
                item.put("end", section.path("end").asInt(0));
                item.put("points", section.path("points"));
                sections.add(item);
            }
        }
        // 模型可能漏掉 end：用下一节的 start 兜底，最后一节用视频时长，
        // 否则 end=0 会被当成「无上限」，第一节会把全片逐字稿都吞掉
        int maxSeconds = toInt(task.getDurationSec());
        for (AsrResult.Segment segment : segments)
        {
            if (segment.getEndMs() != null)
            {
                maxSeconds = Math.max(maxSeconds, segment.getEndMs() / 1000);
            }
        }
        for (int i = 0; i < sections.size(); i++)
        {
            if (toInt(sections.get(i).get("end")) > 0)
            {
                continue;
            }
            int fallback = maxSeconds;
            if (i + 1 < sections.size())
            {
                int nextStart = toInt(sections.get(i + 1).get("start"));
                if (nextStart > 0)
                {
                    fallback = nextStart;
                }
            }
            sections.get(i).put("end", fallback);
        }
        // 碎段合并兜底：模型偶尔不听话，按图示粒度（每段 ≥10 句且 ≥90 秒）合并
        mergeTinySections(sections, segments, maxSeconds);
        // 逐字稿：按课时覆盖写（同一课时重传才替换，不影响本章其它课时）
        Long lessonId = lesson == null ? null : lesson.getId();
        if (lessonId != null)
        {
            transcriptMapper.deleteByLessonId(lessonId);
        }
        else
        {
            transcriptMapper.deleteByChapterId(chapter.getId());
        }
        List<CourseTranscript> transcripts = new ArrayList<CourseTranscript>();
        int seq = 1;
        for (AsrResult.Segment segment : segments)
        {
            int startSec = (segment.getStartMs() == null ? 0 : segment.getStartMs()) / 1000;
            CourseTranscript row = new CourseTranscript();
            row.setChapterId(chapter.getId());
            row.setLessonId(lessonId);
            row.setSeq(seq++);
            row.setStartMs(segment.getStartMs());
            row.setEndMs(segment.getEndMs());
            row.setSection(sectionTitleAt(sections, startSec));
            row.setText(StringUtils.substring(segment.getText(), 0, 2000));
            transcripts.add(row);
        }
        if (!transcripts.isEmpty())
        {
            transcriptMapper.batchInsertInBatches(transcripts);
        }

        // 章节-知识点：一章多课时时只做增量合并，不能清空本章已有绑定
        Map<Long, ChapterPoint> existingLinks = new HashMap<Long, ChapterPoint>();
        for (ChapterPoint exist : chapterMapper.selectChapterPointList(chapter.getId()))
        {
            existingLinks.put(exist.getPointId(), exist);
        }
        List<ChapterPoint> links = new ArrayList<ChapterPoint>();
        int pointCount = 0;
        int linkOrder = 1;
        for (Map<String, Object> section : sections)
        {
            JsonNode points = (JsonNode) section.get("points");
            if (points == null || !points.isArray())
            {
                continue;
            }
            for (JsonNode pointNode : points)
            {
                String name = StringUtils.substring(pointNode.path("name").asText(""), 0, 190);
                if (StringUtils.isEmpty(name))
                {
                    continue;
                }
                KnowledgePoint point = findOrCreatePoint(task, pointNode, name);
                if (existingLinks.containsKey(point.getId()))
                {
                    // 已有绑定：保留原小节，只统计不重复插入
                    pointCount++;
                    continue;
                }
                ChapterPoint link = new ChapterPoint();
                link.setChapterId(chapter.getId());
                link.setPointId(point.getId());
                link.setSection(StringUtils.substring(String.valueOf(section.get("title")), 0, 190));
                link.setSortOrder((long) linkOrder++);
                links.add(link);
                existingLinks.put(point.getId(), link);
                pointCount++;
            }
        }
        if (!links.isEmpty())
        {
            chapterMapper.batchChapterPoint(links);
        }

        // 题目
        int questionCount = 0;
        JsonNode questions = quiz.path("questions");
        if (questions.isArray())
        {
            int number = questionMapper.selectMaxQuestionNumber(testId);
            for (JsonNode node : questions)
            {
                String content = node.path("question").asText("");
                if (StringUtils.isEmpty(content))
                {
                    continue;
                }
                CourseQuestion question = new CourseQuestion();
                question.setTestId(testId);
                question.setQuestionNumber(++number);
                question.setQuestionType("judge".equalsIgnoreCase(node.path("type").asText()) ? "judge" : "choice");
                question.setContent(content);
                question.setOptions(buildOptionsJson(node.path("options")));
                question.setAnswer(buildAnswerJson(node.path("answer")));
                question.setAnalysis(node.path("analysis").asText(""));
                question.setDifficulty(Math.max(1, Math.min(3, node.path("difficulty").asInt(2))));
                questionMapper.insertQuestion(question);
                questionCount++;
            }
        }

        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("chapterId", chapter.getId());
        result.put("chapterNo", chapterNo);
        result.put("lessonId", lessonId);
        result.put("lessonCode", lesson == null ? null : lesson.getLessonCode());
        result.put("title", title);
        result.put("testId", testId);
        result.put("pointCount", pointCount);
        result.put("questionCount", questionCount);
        result.put("transcriptCount", transcripts.size());

        // 回写课时统计
        if (lesson != null)
        {
            lesson.setTitle(StringUtils.isNotEmpty(lesson.getTitle())
                    ? lesson.getTitle() : StringUtils.substring(title, 0, 190));
            lesson.setDurationSec(task.getDurationSec());
            lesson.setTestId(testId);
            lesson.setTranscriptCount(transcripts.size());
            lesson.setPointCount(pointCount);
            lesson.setQuestionCount(questionCount);
            lesson.setUpdateBy(task.getCreateBy());
            lessonMapper.update(lesson);
        }
        return result;
    }

    /**
     * 合并碎段：不足 10 句且短于 90 秒的小节并入相邻小节。
     *
     * <p>粒度以课程页展示为准（每段 10~30 句、约 1.5~3 分钟）；4 分钟视频曾被切成
     * 7 段（每段 30 多秒），过碎。非碎段不处理，合并后时间边界保持连续。</p>
     */
    private void mergeTinySections(List<Map<String, Object>> sections,
            List<AsrResult.Segment> segments, int maxSeconds)
    {
        while (sections.size() > 1)
        {
            int tinyIndex = -1;
            for (int i = 0; i < sections.size(); i++)
            {
                int start = toInt(sections.get(i).get("start"));
                int end = toInt(sections.get(i).get("end"));
                if (end <= start)
                {
                    continue;
                }
                int sentences = 0;
                for (AsrResult.Segment segment : segments)
                {
                    int seconds = (segment.getStartMs() == null ? 0 : segment.getStartMs()) / 1000;
                    if (seconds >= start && seconds < end)
                    {
                        sentences++;
                    }
                }
                if (end - start < 90 && sentences < 10)
                {
                    tinyIndex = i;
                    break;
                }
            }
            if (tinyIndex < 0)
            {
                break;
            }
            if (tinyIndex < sections.size() - 1)
            {
                // 中间碎段：并入下一段（下一段起点前移到碎段起点）
                int segStart = toInt(sections.get(tinyIndex).get("start"));
                sections.remove(tinyIndex);
                sections.get(tinyIndex).put("start", segStart);
            }
            else
            {
                // 末尾碎段：并入上一段（上一段终点延到视频末尾）
                int segEnd = toInt(sections.get(tinyIndex).get("end"));
                if (segEnd <= 0)
                {
                    segEnd = maxSeconds;
                }
                sections.remove(tinyIndex);
                sections.get(tinyIndex - 1).put("end", segEnd);
            }
        }
    }

    /**
     * 收集章节下全部课时的逐字稿（知识库重建需要整章范围，避免其它课时切片被清理）。
     */
    private List<LessonTranscript> collectLessonTranscripts(Long chapterId)
    {
        List<LessonTranscript> result = new ArrayList<LessonTranscript>();
        if (chapterId == null)
        {
            return result;
        }
        for (CourseLesson lesson : lessonMapper.selectByChapterId(chapterId))
        {
            List<TranscriptLine> lines = new ArrayList<TranscriptLine>();
            for (CourseTranscript row : transcriptMapper.selectByLessonId(lesson.getId()))
            {
                TranscriptLine line = new TranscriptLine();
                line.setStart(row.getStartMs() == null ? null : row.getStartMs() / 1000);
                line.setText(row.getText());
                lines.add(line);
            }
            result.add(new LessonTranscript(lesson.getId(), lesson.getLessonCode(), lesson.getTestId(), lines));
        }
        return result;
    }

    private KnowledgePoint findOrCreatePoint(CourseVideoImport task, JsonNode node, String name)
    {
        KnowledgePoint query = new KnowledgePoint();
        query.setSubjectId(task.getSubjectId());
        query.setName(name);
        for (KnowledgePoint item : knowledgePointService.selectKnowledgePointList(query))
        {
            if (name.equals(item.getName() == null ? "" : item.getName().trim()))
            {
                return item;
            }
        }
        KnowledgePoint point = new KnowledgePoint();
        point.setSubjectId(task.getSubjectId());
        point.setName(name);
        point.setContent(StringUtils.substring(node.path("content").asText(""), 0, 4000));
        point.setType(node.path("type").asText("概念"));
        point.setDifficulty((long) Math.max(1, Math.min(5, node.path("difficulty").asInt(3))));
        point.setImportance((long) Math.max(1, Math.min(4, node.path("importance").asInt(2))));
        point.setStatus("0");
        point.setDelFlag("0");
        point.setCreateBy(task.getCreateBy());
        knowledgePointService.insertKnowledgePoint(point);
        return point;
    }

    private String sectionTitleAt(List<Map<String, Object>> sections, int seconds)
    {
        String title = null;
        for (Map<String, Object> section : sections)
        {
            int start = toInt(section.get("start"));
            int end = toInt(section.get("end"));
            if (seconds >= start && (end <= 0 || seconds < end))
            {
                title = String.valueOf(section.get("title"));
                break;
            }
        }
        if (title == null && !sections.isEmpty())
        {
            title = String.valueOf(sections.get(0).get("title"));
        }
        return StringUtils.substring(title, 0, 190);
    }

    private String buildOptionsJson(JsonNode options)
    {
        List<Map<String, String>> list = new ArrayList<Map<String, String>>();
        if (options.isArray())
        {
            for (JsonNode option : options)
            {
                Map<String, String> item = new LinkedHashMap<String, String>();
                item.put("label", option.path("label").asText(""));
                item.put("text", option.path("text").asText(""));
                if (StringUtils.isNotEmpty(item.get("text")))
                {
                    list.add(item);
                }
            }
        }
        try
        {
            return objectMapper.writeValueAsString(list);
        }
        catch (Exception e)
        {
            return "[]";
        }
    }

    private String buildAnswerJson(JsonNode answer)
    {
        List<String> labels = new ArrayList<String>();
        if (answer.isArray())
        {
            for (JsonNode item : answer)
            {
                labels.add(item.asText(""));
            }
        }
        else if (answer.isTextual() && !answer.asText("").isEmpty())
        {
            labels.add(answer.asText());
        }
        try
        {
            return objectMapper.writeValueAsString(labels);
        }
        catch (Exception e)
        {
            return "[]";
        }
    }

    private CourseChapter chapterQuery(Long subjectId)
    {
        CourseChapter query = new CourseChapter();
        query.setSubjectId(subjectId);
        return query;
    }

    private CourseChapter findChapter(List<CourseChapter> chapters, String chapterNo)
    {
        if (chapterNo == null)
        {
            return null;
        }
        Integer target = extractChapterNumber(chapterNo);
        for (CourseChapter chapter : chapters)
        {
            // 精确匹配（规范写法「第3章」等）
            if (chapterNo.equals(chapter.getChapterNo()))
            {
                return chapter;
            }
            // 按章号归一并匹配：让「3」「第三章」「3.21」等写法都命中已有的「第3章」，
            // 避免上传入口章号格式不一致导致同章被拆成两条记录、前端渲染成同名下拉项。
            if (target != null)
            {
                Integer existing = extractChapterNumber(chapter.getChapterNo());
                if (target.equals(existing))
                {
                    return chapter;
                }
            }
        }
        return null;
    }

    /** 章节是否已有逐字稿或知识点（已有内容的章节不再被上传分析覆盖） */
    private boolean hasCourseContent(Long chapterId)
    {
        if (chapterId == null)
        {
            return false;
        }
        if (!transcriptMapper.selectByChapterId(chapterId).isEmpty())
        {
            return true;
        }
        return !chapterMapper.selectChapterPointList(chapterId).isEmpty();
    }

    private String nextChapterNo(List<CourseChapter> chapters)
    {
        int max = 0;
        for (CourseChapter chapter : chapters)
        {
            if (chapter.getChapterNo() == null)
            {
                continue;
            }
            Matcher matcher = CHAPTER_NO_PATTERN.matcher(chapter.getChapterNo());
            if (matcher.find())
            {
                max = Math.max(max, Integer.parseInt(matcher.group(1)));
            }
        }
        return "第" + (max + 1) + "章";
    }

    private void update(Long taskId, String status, String stage, int progress, String message)
    {
        CourseVideoImport task = new CourseVideoImport();
        task.setId(taskId);
        task.setStatus(status);
        task.setStage(stage);
        task.setProgress(progress);
        task.setMessage(StringUtils.substring(message, 0, 900));
        taskMapper.update(task);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value)
    {
        if (value instanceof Map)
        {
            return (Map<String, Object>) value;
        }
        return new HashMap<String, Object>();
    }

    private static double toDouble(Object value)
    {
        if (value == null)
        {
            return 0D;
        }
        try
        {
            return Double.parseDouble(String.valueOf(value));
        }
        catch (NumberFormatException e)
        {
            return 0D;
        }
    }

    private static int toInt(Object value)
    {
        if (value == null)
        {
            return 0;
        }
        try
        {
            return (int) Double.parseDouble(String.valueOf(value));
        }
        catch (NumberFormatException e)
        {
            return 0;
        }
    }

    private static class VideoParseResult
    {
        private String audioPath;

        private int durationSec;
    }
}
