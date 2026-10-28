package com.ruoyi.web.controller.project;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.project.langchain.LangChainWorkflowResult;
import com.ruoyi.project.langchain.LangChainWorkflowService;

/**
 * AI 学习路径对话 Controller。
 *
 * <p>底层使用百炼 qwen3.7-flash（OpenAI 兼容接口）+ LangGraph 工作流，
 * 根据输入前缀 [0]/[1]/[2] 选择学科筛选、面试出题或错题诊断 Prompt。</p>
 *
 * @author ruoyi
 */
@RestController
@RequestMapping("/project/learn")
public class LearnController extends BaseController
{
    @Autowired
    private LangChainWorkflowService langChainWorkflowService;

    /**
     * AI 对话接口。
     */
    @PreAuthorize("@ss.hasPermi('project:learn:query')")
    @PostMapping("/chat")
    public AjaxResult chat(@RequestBody Map<String, String> params)
    {
        String userInput = params == null ? null : params.get("message");
        if (userInput == null || userInput.trim().isEmpty())
        {
            return AjaxResult.error("消息不能为空");
        }
        // 限制长度：该输入会直接进入大模型上下文，过长既贵又容易被塞入无关指令
        if (userInput.length() > 4000)
        {
            userInput = userInput.substring(0, 4000);
        }

        try
        {
            LangChainWorkflowResult result = langChainWorkflowService.invoke(userInput);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("reply", result.getOutput());
            data.put("raw", result.getOutput());
            data.put("path", result.getPath());
            data.put("steps", result.getSteps());
            data.put("durationMs", result.getDurationMs());
            return AjaxResult.success(data);
        }
        catch (Exception e)
        {
            logger.error("调用百炼 qwen3.8-flash 失败", e);
            return AjaxResult.error(friendlyError(e));
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
        return "AI服务调用失败: " + message;
    }
}
