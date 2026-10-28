package com.ruoyi.project.langchain;

import java.util.Map;
import java.util.Set;

/**
 * LangChain 工作流服务。
 *
 * @author ruoyi
 */
public interface LangChainWorkflowService
{
    /** 默认工作流标识 */
    String DEFAULT_WORKFLOW_ID = "default";

    /**
     * 获取已加载的默认 LangGraph 清单。
     */
    LangChainWorkflow getWorkflow();

    /**
     * 按标识获取 LangGraph 清单。
     *
     * @param workflowId 工作流标识，{@code default} 表示默认工作流
     */
    LangChainWorkflow getWorkflow(String workflowId);

    /**
     * 已加载的工作流标识集合（含 default）。
     */
    Set<String> listWorkflowIds();

    /**
     * 执行默认工作流。
     *
     * @param input 用户输入（前三位为分支标记，如 [0]/[1]/[2]）
     * @return 执行结果
     */
    LangChainWorkflowResult invoke(String input);

    /**
     * 执行指定工作流。
     *
     * @param workflowId 工作流标识，{@code default} 表示默认工作流
     * @param input 用户输入
     * @return 执行结果
     */
    LangChainWorkflowResult invoke(String workflowId, String input);

    /**
     * 执行指定工作流，并附带额外状态（如官方标准上下文）。
     *
     * @param workflowId 工作流标识
     * @param input 用户输入
     * @param extraState 额外状态，可为 null
     * @return 执行结果
     */
    LangChainWorkflowResult invoke(String workflowId, String input, Map<String, Object> extraState);
}
