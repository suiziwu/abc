package com.ruoyi.project.course.service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.project.course.domain.CourseQuestion;
import com.ruoyi.project.course.domain.CourseQuizItem;
import com.ruoyi.project.course.domain.CourseQuizItem.QuizOption;
import com.ruoyi.project.course.mapper.CourseQuestionMapper;
import com.ruoyi.project.domain.KbChunk;
import com.ruoyi.project.kb.KbIngestService;
import com.ruoyi.project.kb.KbProperties;
import com.ruoyi.project.kb.KbVectorStore;
import com.ruoyi.project.kb.KbVectorStoreFactory;
import com.ruoyi.project.langchain.ChatModelClient;
import com.ruoyi.project.langchain.ChatModelOption;

/**
 * 课程学习服务实现。
 *
 * <p>AI 助教采用「知识库优先」策略：先用问题做向量检索（RAG），
 * 命中则用检索片段拼上下文；知识库不可用或无命中时回退到前端传入的课程文本。</p>
 *
 * @author ruoyi
 */
@Service
public class CourseStudyServiceImpl implements CourseStudyService
{
    private static final Pattern OPTION_LINE = Pattern.compile("^\\s*([A-Za-z])[\\.、:：]\\s*(.+)$");

    private static final String SYSTEM_PROMPT = "你是“智程学途”的课程助教，负责基于课程知识库回答学生问题。\n"
            + "要求：\n"
            + "1. 只依据提供的知识库片段回答，不要编造片段中没有的知识；\n"
            + "2. 回答使用中文，条理清晰，必要时分点说明；\n"
            + "3. 逐字稿片段带有时间戳，引用时请原样标注，例如 [01:20]；\n"
            + "4. 如果片段不足以回答，请明确说明并给出建议的思考方向。";

    private final CourseQuestionMapper questionMapper;

    private final ChatModelClient chatModelClient;

    private final KbVectorStoreFactory kbVectorStoreFactory;

    private final KbIngestService kbIngestService;

    private final KbProperties kbProperties;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public CourseStudyServiceImpl(CourseQuestionMapper questionMapper, ChatModelClient chatModelClient,
            KbVectorStoreFactory kbVectorStoreFactory, KbIngestService kbIngestService,
            KbProperties kbProperties)
    {
        this.questionMapper = questionMapper;
        this.chatModelClient = chatModelClient;
        this.kbVectorStoreFactory = kbVectorStoreFactory;
        this.kbIngestService = kbIngestService;
        this.kbProperties = kbProperties;
    }

    @Override
    public String chat(String question, String context, String model)
    {
        if (question == null || question.trim().isEmpty())
        {
            throw new IllegalArgumentException("问题不能为空");
        }
        String safeContext = context == null ? "" : context.trim();
        if (safeContext.length() > 8000)
        {
            safeContext = safeContext.substring(0, 8000);
        }
        String userPrompt = "【课程文本】\n" + (safeContext.isEmpty() ? "（无相关课程文本）" : safeContext)
                + "\n\n【学生问题】\n" + question.trim();
        return chatModelClient.chat(model, SYSTEM_PROMPT, userPrompt);
    }

    @Override
    public CourseAnswer ask(String question, String fallbackContext, String model,
            Long subjectId, Long chapterId)
    {
        if (question == null || question.trim().isEmpty())
        {
            throw new IllegalArgumentException("问题不能为空");
        }
        String safeQuestion = question.trim();
        CourseAnswer result = new CourseAnswer();
        result.setModel(model == null || model.trim().isEmpty() ? null : model.trim());

        // 1. 知识库检索
        List<KbChunk> hits = retrieve(safeQuestion, subjectId, chapterId);
        String context;
        if (!hits.isEmpty())
        {
            context = buildRagContext(hits);
            result.setStrategy("rag");
            result.setTopScore(hits.get(0).getScore());
            for (KbChunk hit : hits)
            {
                result.getSources().add(toSource(hit));
            }
        }
        else if (fallbackContext != null && !fallbackContext.trim().isEmpty())
        {
            // 2. 回退：前端传入的课程文本
            context = fallbackContext.trim();
            result.setStrategy("context");
        }
        else
        {
            context = "";
            result.setStrategy("none");
        }
        if (context.length() > 8000)
        {
            context = context.substring(0, 8000);
        }

        String userPrompt = "【知识库片段】\n" + (context.isEmpty() ? "（无相关片段）" : context)
                + "\n\n【学生问题】\n" + safeQuestion;
        result.setAnswer(chatModelClient.chat(result.getModel(), SYSTEM_PROMPT, userPrompt));
        return result;
    }

    /**
     * 语义检索；知识库未启用、未配置或向量库不可用时返回空列表（走兜底）。
     */
    private List<KbChunk> retrieve(String question, Long subjectId, Long chapterId)
    {
        if (!kbProperties.isEnabled())
        {
            return new ArrayList<>();
        }
        try
        {
            KbVectorStore store = kbVectorStoreFactory.active();
            if (!store.available() || store.count(subjectId) == 0)
            {
                return new ArrayList<>();
            }
            // 相似度阈值已在 KbIngestService.search 内部按「向量召回」阶段过滤，
            // 这里不能再按 score 过滤，否则关键词召回（score 为空）的命中会被误杀
            return kbIngestService.search(question, subjectId, chapterId, kbProperties.getTopK());
        }
        catch (Exception e)
        {
            // 知识库异常不应阻断问答，交由调用方回退上下文
            org.slf4j.LoggerFactory.getLogger(CourseStudyServiceImpl.class)
                    .warn("知识库检索失败，回退课程上下文：{}", e.getMessage());
            return new ArrayList<>();
        }
    }

    private String buildRagContext(List<KbChunk> hits)
    {
        StringBuilder sb = new StringBuilder();
        int index = 1;
        for (KbChunk hit : hits)
        {
            sb.append("【片段").append(index++).append("｜").append(sourceLabel(hit));
            if (hit.getSectionPath() != null && !hit.getSectionPath().isEmpty())
            {
                sb.append("｜").append(hit.getSectionPath());
            }
            sb.append("】\n");
            if (hit.getStartTime() != null)
            {
                sb.append("[").append(formatTime(hit.getStartTime())).append("] ");
            }
            sb.append(hit.getContent() == null ? "" : hit.getContent()).append("\n\n");
        }
        return sb.toString().trim();
    }

    private CourseAnswer.Source toSource(KbChunk hit)
    {
        CourseAnswer.Source source = new CourseAnswer.Source();
        source.setChunkId(hit.getId());
        source.setDocId(hit.getDocId());
        source.setSourceType(hit.getSourceType());
        source.setTitle(hit.getTitle());
        source.setSubjectId(hit.getSubjectId());
        source.setChapterId(hit.getChapterId());
        source.setPointId(hit.getPointId());
        source.setStartTime(hit.getStartTime());
        source.setScore(hit.getScore());
        String content = hit.getContent() == null ? "" : hit.getContent();
        source.setSnippet(content.length() > 120 ? content.substring(0, 120) + "…" : content);
        return source;
    }

    private String sourceLabel(KbChunk hit)
    {
        String type = hit.getSourceType() == null ? "" : hit.getSourceType();
        String typeLabel;
        switch (type)
        {
            case KbIngestService.TYPE_TRANSCRIPT:
                typeLabel = "视频逐字稿";
                break;
            case KbIngestService.TYPE_POINT:
                typeLabel = "知识点";
                break;
            case KbIngestService.TYPE_CHAPTER:
                typeLabel = "章节";
                break;
            case KbIngestService.TYPE_QUIZ:
                typeLabel = "随堂练习";
                break;
            default:
                typeLabel = "知识库";
                break;
        }
        String title = hit.getTitle() == null ? "" : hit.getTitle();
        return title.isEmpty() ? typeLabel : typeLabel + "：" + title;
    }

    private static String formatTime(Integer seconds)
    {
        int value = seconds == null ? 0 : Math.max(0, seconds);
        return String.format("%02d:%02d", value / 60, value % 60);
    }

    @Override
    public List<ChatModelOption> getModels()
    {
        return chatModelClient.listModels();
    }

    @Override
    public List<CourseQuizItem> getQuiz(Long testId, int limit)
    {
        if (testId == null)
        {
            throw new IllegalArgumentException("题库ID不能为空");
        }
        int actualLimit = limit <= 0 ? 10 : limit;
        // SQL 侧直接 limit，避免整个题库读进内存再截断
        List<CourseQuestion> questions = questionMapper.selectByTestIdLimit(testId, actualLimit);
        List<CourseQuizItem> result = new ArrayList<>();
        for (CourseQuestion question : questions)
        {
            CourseQuizItem item = convert(question);
            if (item != null && !item.getOptions().isEmpty())
            {
                result.add(item);
            }
            if (result.size() >= actualLimit)
            {
                break;
            }
        }
        return result;
    }

    private CourseQuizItem convert(CourseQuestion source)
    {
        if (source == null || source.getContent() == null)
        {
            return null;
        }
        CourseQuizItem item = new CourseQuizItem();
        item.setId(source.getId());
        item.setType(source.getQuestionType());
        item.setDifficulty(source.getDifficulty());
        item.setAnalysis(source.getAnalysis());

        String content = source.getContent().replace("\r\n", "\n").replace("\r", "\n").trim();
        List<QuizOption> options = parseJsonOptions(source.getOptions());
        String questionText;
        if (options.isEmpty())
        {
            String[] lines = content.split("\n");
            StringBuilder question = new StringBuilder();
            for (String line : lines)
            {
                Matcher matcher = OPTION_LINE.matcher(line);
                if (matcher.matches())
                {
                    options.add(new QuizOption(matcher.group(1).toUpperCase(), matcher.group(2).trim()));
                }
                else if (options.isEmpty())
                {
                    if (question.length() > 0)
                    {
                        question.append('\n');
                    }
                    question.append(line.trim());
                }
                else if (!line.trim().isEmpty() && !options.isEmpty())
                {
                    QuizOption last = options.get(options.size() - 1);
                    last.setText(last.getText() + "\n" + line.trim());
                }
            }
            questionText = question.toString().trim();
        }
        else
        {
            questionText = content;
        }

        if (options.isEmpty() && isJudge(source.getQuestionType()))
        {
            options.add(new QuizOption("A", "正确"));
            options.add(new QuizOption("B", "错误"));
        }
        if (questionText.isEmpty())
        {
            questionText = content;
        }

        item.setQuestion(questionText);
        item.setOptions(options);
        List<String> answer = normalizeAnswer(source.getAnswer(), options, source.getQuestionType());
        item.setAnswer(answer);
        item.setMultiple(answer.size() > 1);
        return item;
    }

    private List<QuizOption> parseJsonOptions(String optionsJson)
    {
        List<QuizOption> options = new ArrayList<>();
        if (optionsJson == null || optionsJson.trim().isEmpty())
        {
            return options;
        }
        String text = optionsJson.trim();
        if (!text.startsWith("[") && !text.startsWith("{"))
        {
            return options;
        }
        try
        {
            JsonNode root = objectMapper.readTree(text);
            if (root.isArray())
            {
                int index = 0;
                for (JsonNode node : root)
                {
                    String label = String.valueOf((char) ('A' + index));
                    String value;
                    if (node.isObject())
                    {
                        label = node.path("label").asText(label);
                        value = node.path("text").asText(node.path("value").asText(""));
                    }
                    else
                    {
                        value = node.asText("");
                    }
                    if (!value.isEmpty())
                    {
                        options.add(new QuizOption(label.toUpperCase(), value));
                    }
                    index++;
                }
            }
            else if (root.isObject())
            {
                java.util.Iterator<String> fields = root.fieldNames();
                while (fields.hasNext())
                {
                    String label = fields.next();
                    options.add(new QuizOption(label.toUpperCase(), root.path(label).asText("")));
                }
            }
        }
        catch (Exception ignored)
        {
            // options 不是标准 JSON 时回退为 content 解析
        }
        return options;
    }

    private List<String> normalizeAnswer(String answerText, List<QuizOption> options, String questionType)
    {
        List<String> rawTokens = new ArrayList<>();
        if (answerText != null && !answerText.trim().isEmpty())
        {
            String text = answerText.trim();
            if (text.startsWith("["))
            {
                try
                {
                    JsonNode root = objectMapper.readTree(text);
                    if (root.isArray())
                    {
                        for (JsonNode node : root)
                        {
                            rawTokens.add(node.asText());
                        }
                    }
                }
                catch (Exception ignored)
                {
                    rawTokens.add(text);
                }
            }
            else if (isJudge(questionType))
            {
                // 判断题答案是 true/false/正确/错误 等完整单词，不能按字母拆开
                rawTokens.add(text);
            }
            else if (text.matches("^[A-Za-z]{2,}$"))
            {
                for (char c : text.toCharArray())
                {
                    rawTokens.add(String.valueOf(c));
                }
            }
            else if (text.contains(",") || text.contains(";") || text.contains("|") || text.contains("，"))
            {
                for (String token : text.split("[,;|，]"))
                {
                    if (!token.trim().isEmpty())
                    {
                        rawTokens.add(token.trim());
                    }
                }
            }
            else
            {
                rawTokens.add(text);
            }
        }

        List<String> labels = new ArrayList<>();
        for (String token : rawTokens)
        {
            String normalized = token.trim();
            String label = null;
            if (isJudge(questionType))
            {
                if ("true".equalsIgnoreCase(normalized) || "1".equals(normalized)
                        || "正确".equals(normalized) || "对".equals(normalized))
                {
                    label = "A";
                }
                else if ("false".equalsIgnoreCase(normalized) || "0".equals(normalized)
                        || "错误".equals(normalized) || "错".equals(normalized))
                {
                    label = "B";
                }
            }
            if (label == null)
            {
                for (QuizOption option : options)
                {
                    if (option.getLabel().equalsIgnoreCase(normalized)
                            || option.getText().equals(normalized))
                    {
                        label = option.getLabel();
                        break;
                    }
                }
            }
            if (label != null && !labels.contains(label))
            {
                labels.add(label);
            }
        }
        return labels;
    }

    private boolean isJudge(String questionType)
    {
        return questionType != null && questionType.toLowerCase().contains("judge");
    }
}
