package com.ruoyi.project.langchain;

/**
 * LangChain 工作流加载、校验、执行异常。
 *
 * @author ruoyi
 */
public class LangChainWorkflowException extends RuntimeException
{
    private static final long serialVersionUID = 1L;

    public LangChainWorkflowException(String message)
    {
        super(message);
    }

    public LangChainWorkflowException(String message, Throwable cause)
    {
        super(message, cause);
    }
}
