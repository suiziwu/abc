package com.ruoyi.project.langchain;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 可选大模型列表配置（OpenAI 兼容接口）。
 *
 * <p>示例：</p>
 * <pre>
 * ai:
 *   chat:
 *     default-model: qwen3.8-flash
 *     models:
 *       - id: qwen3.8-flash
 *         label: 通义千问 3.8 Flash
 *         base-url: https://.../compatible-mode/v1
 *         api-key: ${DASHSCOPE_API_KEY:}
 *         model: qwen3.8-flash
 * </pre>
 *
 * @author ruoyi
 */
@Component
@ConfigurationProperties(prefix = "ai.chat")
public class AiChatProperties
{
    /** 默认模型 id；为空时取列表第一个 */
    private String defaultModel;

    /** 可选模型列表 */
    private List<ModelConfig> models = new ArrayList<>();

    public String getDefaultModel()
    {
        return defaultModel;
    }

    public void setDefaultModel(String defaultModel)
    {
        this.defaultModel = defaultModel;
    }

    public List<ModelConfig> getModels()
    {
        return models;
    }

    public void setModels(List<ModelConfig> models)
    {
        this.models = models;
    }

    /**
     * 单个 OpenAI 兼容模型配置。
     */
    public static class ModelConfig
    {
        /** 前端选择的模型标识 */
        private String id;

        /** 展示名称 */
        private String label;

        /** OpenAI 兼容接口地址 */
        private String baseUrl;

        /** API Key */
        private String apiKey;

        /** 真实模型名 */
        private String model;

        /** 温度 */
        private Double temperature = 1.0D;

        /** 超时时间（秒） */
        private Integer timeoutSeconds = 120;

        public String getId()
        {
            return id;
        }

        public void setId(String id)
        {
            this.id = id;
        }

        public String getLabel()
        {
            return label;
        }

        public void setLabel(String label)
        {
            this.label = label;
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

        public String getModel()
        {
            return model;
        }

        public void setModel(String model)
        {
            this.model = model;
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
    }
}
