package com.ruoyi.web.controller.project;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.project.langchain.LangChainProperties;
import com.ruoyi.project.langchain.LangChainWorkflow;
import com.ruoyi.project.langchain.LangChainWorkflowResult;
import com.ruoyi.project.langchain.LangChainWorkflowService;

/**
 * LangChain 工作流接口。
 *
 * @author ruoyi
 */
@RestController
@RequestMapping("/project/langchain")
public class LangChainWorkflowController extends BaseController
{
    @Autowired
    private LangChainWorkflowService langChainWorkflowService;

    @Autowired
    private LangChainProperties langChainProperties;

    /**
     * 获取已加载的 LangGraph 清单全文。
     */
    @GetMapping("/workflow")
    public AjaxResult workflow()
    {
        return success(langChainWorkflowService.getWorkflow());
    }

    /**
     * 工作流健康信息。
     */
    @GetMapping("/health")
    public AjaxResult health()
    {
        LangChainWorkflow workflow = langChainWorkflowService.getWorkflow();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("name", workflow.getFlow().getName());
        data.put("framework", workflow.getFramework());
        data.put("format", workflow.getFormat());
        data.put("entrypoint", workflow.getExecution().getEntrypoint());
        data.put("finishPoint", workflow.getExecution().getFinishPoint());
        data.put("nodeCount", workflow.getGraph().getNodes().size());
        data.put("edgeCount", workflow.getGraph().getEdges().size());
        int llmCount = 0;
        for (LangChainWorkflow.Node node : workflow.getGraph().getNodes())
        {
            if ("llm".equals(node.getType()))
            {
                llmCount++;
            }
        }
        data.put("llmCount", llmCount);
        data.put("model", langChainProperties.getModelName());
        data.put("apiKeyConfigured", langChainProperties.getApiKey() != null
                && !langChainProperties.getApiKey().trim().isEmpty());
        return success(data);
    }

    /**
     * 执行工作流。
     *
     * @param body 请求体，字段 input，例如 {"input":"[0]后端开发工程师"}
     */
    @PostMapping("/invoke")
    public AjaxResult invoke(@RequestBody Map<String, String> body)
    {
        String input = body == null ? null : body.get("input");
        if (input == null || input.trim().isEmpty())
        {
            return error("参数 input 不能为空");
        }
        LangChainWorkflowResult result = langChainWorkflowService.invoke(input);
        return success(result);
    }
}
