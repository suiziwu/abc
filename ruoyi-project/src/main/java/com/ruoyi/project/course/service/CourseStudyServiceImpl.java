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
import com.ruoyi.project.langchain.ChatModelClient;
import com.ruoyi.project.langchain.ChatModelOption;

/**
 * 课程学习服务实现。
 *
 * @author ruoyi
 */
@Service
public class CourseStudyServiceImpl implements CourseStudyService
{
    private static final Pattern OPTION_LINE = Pattern.compile("^\\s*([A-Za-z])[\\.、:：]\\s*(.+)$");

    private static final String SYSTEM_PROMPT = "你是“智程学途”的课程助教，负责基于课程视频文本回答学生问题。\n"
            + "要求：\n"
            + "1. 只依据提供的课程文本回答，不要编造课程中没有的知识；\n"
            + "2. 回答使用中文，条理清晰，必要时分点说明；\n"
            + "3. 如果知识点在文本中有时间戳，请在回答中引用，例如 [01:20]；\n"
            + "4. 如果课程文本不足以回答，请明确说明并给出建议的思考方向。";

    private final CourseQuestionMapper questionMapper;

    private final ChatModelClient chatModelClient;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public CourseStudyServiceImpl(CourseQuestionMapper questionMapper, ChatModelClient chatModelClient)
    {
        this.questionMapper = questionMapper;
        this.chatModelClient = chatModelClient;
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
        String userPrompt = "【课程文本】\n" + safeContext + "\n\n【学生问题】\n" + question.trim();
        return chatModelClient.chat(model, SYSTEM_PROMPT, userPrompt);
    }

    @Override
    public List<ChatModelOption> getModels()
    {
        return chatModelClient.listModels();
    }

    @Override
    public List<CourseQuizItem> getQuiz(Long testId, int limit)
    {
        Long actualTestId = testId == null ? 101L : testId;
        int actualLimit = limit <= 0 ? 10 : limit;
        List<CourseQuestion> questions = questionMapper.selectByTestId(actualTestId);
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
