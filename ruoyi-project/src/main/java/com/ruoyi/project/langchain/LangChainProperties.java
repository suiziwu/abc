package com.ruoyi.project.langchain;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * LangChain / LangChain4j 运行参数。
 *
 * @author ruoyi
 */
@Component
@ConfigurationProperties(prefix = "langchain")
public class LangChainProperties
{
    /** 工作流清单位置，支持 classpath:/file: */
    private String workflowLocation = "classpath:langchain/rencai-huaxiang-20260914150500.langgraph.json";

    /** OpenAI 兼容接口地址（DeepSeek） */
    private String baseUrl = "https://api.deepseek.com/v1";

    /** API Key，建议通过环境变量 DEEPSEEK_API_KEY 注入 */
    private String apiKey;

    /** 模型名 */
    private String modelName = "deepseek-chat";

    /** 温度 */
    private Double temperature = 1.0D;

    /** 超时时间（秒） */
    private Integer timeoutSeconds = 120;

    /** 是否打印请求日志 */
    private Boolean logRequests = Boolean.FALSE;

    /** 是否打印响应日志 */
    private Boolean logResponses = Boolean.FALSE;

    public String getWorkflowLocation()
    {
        return workflowLocation;
    }

    public void setWorkflowLocation(String workflowLocation)
    {
        this.workflowLocation = workflowLocation;
    }

    public String getBaseUrl()
    {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl)
    {
        this.baseUrl = baseUrl;
    }

    public String getApiKey()
    {
        return apiKey;
    }

    public void setApiKey(String apiKey)
    {
        this.apiKey = apiKey;
    }

    public String getModelName()
    {
        return modelName;
    }

    public void setModelName(String modelName)
    {
        this.modelName = modelName;
    }

    public Double getTemperature()
    {
        return temperature;
    }

    public void setTemperature(Double temperature)
    {
        this.temperature = temperature;
    }

    public Integer getTimeoutSeconds()
    {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(Integer timeoutSeconds)
    {
        this.timeoutSeconds = timeoutSeconds;
    }

    public Boolean getLogRequests()
    {
        return logRequests;
    }

    public void setLogRequests(Boolean logRequests)
    {
        this.logRequests = logRequests;
    }

    public Boolean getLogResponses()
    {
        return logResponses;
    }

    public void setLogResponses(Boolean logResponses)
    {
        this.logResponses = logResponses;
    }
}
