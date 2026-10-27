package com.ruoyi.project.langchain;

/**
 * LangChain 工作流服务。
 *
 * @author ruoyi
 */
public interface LangChainWorkflowService
{
    /**
     * 获取已加载的 LangGraph 清单。
     */
    LangChainWorkflow getWorkflow();

    /**
     * 执行工作流。
     *
     * @param input 用户输入（前三位为分支标记，如 [0]/[1]/[2]）
     * @return 执行结果
     */
    LangChainWorkflowResult invoke(String input);
}
