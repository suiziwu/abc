package com.ruoyi.web.controller.project;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.project.langchain.LangChainProperties;
import com.ruoyi.project.langchain.LangChainWorkflow;
import com.ruoyi.project.langchain.LangChainWorkflowException;
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
        data.put("id", LangChainWorkflowService.DEFAULT_WORKFLOW_ID);
        data.put("name", workflow.getFlow().getName());
        data.put("framework", workflow.getFramework());
        data.put("format", workflow.getFormat());
        data.put("entrypoint", workflow.getExecution().getEntrypoint());
        data.put("finishPoint", workflow.getExecution().getFinishPoint());
        data.put("nodeCount", workflow.getGraph().getNodes().size());
        data.put("edgeCount", workflow.getGraph().getEdges().size());
        data.put("llmCount", countLlm(workflow));
        data.put("model", langChainProperties.getModelName());
        data.put("apiKeyConfigured", langChainProperties.getApiKey() != null
                && !langChainProperties.getApiKey().trim().isEmpty());
        return success(data);
    }

    /**
     * 已加载的工作流列表。
     */
    @GetMapping("/workflows")
    public AjaxResult workflows()
    {
        List<Map<String, Object>> items = new ArrayList<>();
        for (String workflowId : langChainWorkflowService.listWorkflowIds())
        {
            LangChainWorkflow workflow = langChainWorkflowService.getWorkflow(workflowId);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("id", workflowId);
            data.put("name", workflow.getFlow().getName());
            data.put("description", workflow.getFlow().getDescription());
            data.put("nodeCount", workflow.getGraph().getNodes().size());
            data.put("edgeCount", workflow.getGraph().getEdges().size());
            data.put("llmCount", countLlm(workflow));
            items.add(data);
        }
        return success(items);
    }

    /**
     * 获取指定工作流的清单全文。
     */
    @GetMapping("/workflows/{workflowId}")
    public AjaxResult workflow(@PathVariable String workflowId)
    {
        try
        {
            return success(langChainWorkflowService.getWorkflow(workflowId));
        }
        catch (LangChainWorkflowException e)
        {
            return error(e.getMessage());
        }
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

    /**
     * 按工作流标识执行。
     *
     * @param workflowId 工作流标识，例如 competency-graph
     * @param body 请求体，字段 input
     */
    @PostMapping("/invoke/{workflowId}")
    public AjaxResult invoke(@PathVariable String workflowId, @RequestBody Map<String, String> body)
    {
        String input = body == null ? null : body.get("input");
        if (input == null || input.trim().isEmpty())
        {
            return error("参数 input 不能为空");
        }
        try
        {
            LangChainWorkflowResult result = langChainWorkflowService.invoke(workflowId, input);
            return success(result);
        }
        catch (LangChainWorkflowException e)
        {
            return error(e.getMessage());
        }
    }

    private int countLlm(LangChainWorkflow workflow)
    {
        int llmCount = 0;
        for (LangChainWorkflow.Node node : workflow.getGraph().getNodes())
        {
            if ("llm".equals(node.getType()))
            {
                llmCount++;
            }
        }
        return llmCount;
    }
}
