package com.ruoyi.web.controller.project;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.project.course.domain.CourseQuizItem;
import com.ruoyi.project.course.service.CourseAnswer;
import com.ruoyi.project.course.service.CourseStudyService;

/**
 * 课程学习：AI 助教问答与随堂练习。
 *
 * @author ruoyi
 */
@RestController
@RequestMapping("/project/course")
public class CourseStudyController extends BaseController
{
    @Autowired
    private CourseStudyService courseStudyService;

    /**
     * 基于课程视频文本回答学生问题。
     *
     * @param body question、context、model（可选，为空时使用默认模型）
     */
    @PreAuthorize("@ss.hasPermi('project:point:list')")
    @PostMapping("/chat")
    public AjaxResult chat(@RequestBody Map<String, String> body)
    {
        String question = body == null ? null : body.get("question");
        String context = body == null ? null : body.get("context");
        String model = body == null ? null : body.get("model");
        if (question == null || question.trim().isEmpty())
        {
            return error("问题不能为空");
        }
        try
        {
            String answer = courseStudyService.chat(question, context, model);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("answer", answer);
            data.put("model", model == null || model.trim().isEmpty() ? null : model.trim());
            return success(data);
        }
        catch (Exception e)
        {
            logger.error("课程 AI 助教回答失败", e);
            return error(friendlyError(e));
        }
    }

    /**
     * 知识库增强问答（RAG）：基于整门课程（或指定章节）的知识库检索后作答。
     *
     * <p>与 {@code /chat} 的区别：本接口先用问题向量检索知识库（kb_base/kb_chunk + Milvus），
     * 命中片段拼成上下文交给大模型，并在 {@code sources} 中返回引用来源；
     * 知识库无命中时才回退到前端传入的 {@code context}。</p>
     *
     * <p>请求体示例：
     * <pre>
     * {
     *   "question": "计算机网络由哪些部分组成？",
     *   "model": "qwen3.8-flash",
     *   "subjectId": 94,
     *   "chapterId": null
     * }
     * </pre></p>
     */
    @PreAuthorize("@ss.hasPermi('project:point:list')")
    @PostMapping("/ask")
    public AjaxResult ask(@RequestBody Map<String, Object> body)
    {
        String question = body == null ? null : toText(body.get("question"));
        if (question == null || question.trim().isEmpty())
        {
            return error("问题不能为空");
        }
        try
        {
            CourseAnswer answer = courseStudyService.ask(
                    question.trim(),
                    toText(body.get("context")),
                    toText(body.get("model")),
                    toLong(body.get("subjectId")),
                    toLong(body.get("chapterId")));
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("answer", answer.getAnswer());
            data.put("model", answer.getModel());
            data.put("strategy", answer.getStrategy());
            data.put("topScore", answer.getTopScore());
            data.put("sources", answer.getSources());
            return success(data);
        }
        catch (Exception e)
        {
            logger.error("课程知识库问答失败", e);
            return error(friendlyError(e));
        }
    }

    /**
     * 获取 AI 助教可选的模型列表。
     */
    @PreAuthorize("@ss.hasPermi('project:point:list')")
    @GetMapping("/models")
    public AjaxResult models()
    {
        try
        {
            return success(courseStudyService.getModels());
        }
        catch (Exception e)
        {
            logger.error("加载模型列表失败", e);
            return error("加载模型列表失败: " + e.getMessage());
        }
    }

    /**
     * 获取随堂练习题目。
     *
     * @param testId 题库测试 ID，默认 101（Java 题库）
     * @param limit 返回题目数量
     */
    @PreAuthorize("@ss.hasPermi('project:point:list')")
    @GetMapping("/quiz")
    public AjaxResult quiz(@RequestParam(value = "testId", defaultValue = "101") Long testId,
            @RequestParam(value = "limit", defaultValue = "10") Integer limit)
    {
        try
        {
            List<CourseQuizItem> questions = courseStudyService.getQuiz(testId, limit);
            return success(questions);
        }
        catch (Exception e)
        {
            logger.error("加载随堂练习失败", e);
            return error("加载随堂练习失败: " + e.getMessage());
        }
    }

    private String friendlyError(Exception e)
    {
        String message = e.getMessage() == null ? "" : e.getMessage();
        if (message.contains("AccessDenied") || message.contains("Unpurchased")
                || message.contains("Arrearage") || message.contains("arrears"))
        {
            return "百炼模型未开通或账户欠费，请在百炼控制台开通 qwen3.8-flash 并确认账户余额。详情："
                    + message;
        }
        String lower = message.toLowerCase();
        if (lower.contains("invalid api key") || lower.contains("authentication")
                || lower.contains("insufficient balance") || lower.contains("unauthorized"))
        {
            return "所选模型的 API Key 无效或账户余额不足，请检查 config/application.yml 中 ai.chat.models 的配置。详情："
                    + message;
        }
        return "AI 助教调用失败: " + message;
    }

    /**
     * 请求体取值转字符串，空值统一返回 null。
     */
    private static String toText(Object value)
    {
        if (value == null)
        {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    /**
     * 请求体取值转 Long，空值统一返回 null。
     */
    private static Long toLong(Object value)
    {
        if (value == null)
        {
            return null;
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty())
        {
            return null;
        }
        try
        {
            return Long.valueOf(text);
        }
        catch (NumberFormatException e)
        {
            return null;
        }
    }
}
