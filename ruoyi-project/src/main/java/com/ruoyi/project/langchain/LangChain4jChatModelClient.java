package com.ruoyi.project.langchain;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.output.Response;

/**
 * 基于 LangChain4j OpenAiChatModel 的多模型适配器。
 *
 * <p>支持 OpenAI 兼容接口的任意模型（百炼 qwen、DeepSeek 等）：
 * 模型清单来自 {@code ai.chat.models}，未配置清单时回退到 {@code langchain.*} 单模型配置。
 * API Key 未配置时仅在调用阶段报错，不影响应用启动。</p>
 *
 * @author ruoyi
 */
@Component
public class LangChain4jChatModelClient implements ChatModelClient
{
    private final LangChainProperties properties;

    private final AiChatProperties chatProperties;

    /** 按模型 id 缓存的 LangChain4j 模型实例 */
    private final Map<String, ChatLanguageModel> modelCache = new ConcurrentHashMap<String, ChatLanguageModel>();

    private volatile ChatLanguageModel defaultChatLanguageModel;

    public LangChain4jChatModelClient(LangChainProperties properties)
    {
        this(properties, new AiChatProperties());
    }

    @Autowired
    public LangChain4jChatModelClient(LangChainProperties properties, AiChatProperties chatProperties)
    {
        this.properties = properties;
        this.chatProperties = chatProperties;
    }

    @Override
    public String chat(String systemPrompt, String userPrompt)
    {
        return chat(null, systemPrompt, userPrompt);
    }

    @Override
    public String chat(String modelId, String systemPrompt, String userPrompt)
    {
        ChatLanguageModel model = getOrCreateModel(modelId);
        Response<AiMessage> response = model.generate(Arrays.asList(
                SystemMessage.from(systemPrompt),
                UserMessage.from(userPrompt)));
        if (response == null || response.content() == null)
        {
            return "";
        }
        String text = response.content().text();
        return text == null ? "" : text;
    }

    @Override
    public List<ChatModelOption> listModels()
    {
        List<ChatModelOption> options = new ArrayList<ChatModelOption>();
        List<AiChatProperties.ModelConfig> configs = configuredModels();
        if (configs.isEmpty())
        {
            ChatModelOption option = new ChatModelOption();
            option.setId(properties.getModelName());
            option.setLabel(properties.getModelName());
            option.setModel(properties.getModelName());
            option.setAvailable(notBlank(properties.getApiKey()));
            option.setDefault(true);
            options.add(option);
            return options;
        }

        String defaultId = resolveDefaultId();
        for (AiChatProperties.ModelConfig config : configs)
        {
            ChatModelOption option = new ChatModelOption();
            option.setId(config.getId());
            option.setLabel(displayLabel(config));
            option.setModel(config.getModel());
            option.setAvailable(notBlank(config.getApiKey()));
            option.setDefault(config.getId() != null && config.getId().equals(defaultId));
            options.add(option);
        }
        return options;
    }

    private ChatLanguageModel getOrCreateModel(String modelId)
    {
        List<AiChatProperties.ModelConfig> configs = configuredModels();
        if (configs.isEmpty())
        {
            if (notBlank(modelId) && !modelId.equalsIgnoreCase(properties.getModelName()))
            {
                throw new LangChainWorkflowException("不支持的模型: " + modelId
                        + "，当前未配置 ai.chat.models，仅支持 " + properties.getModelName());
            }
            return getOrCreateDefaultModel();
        }

        String id = notBlank(modelId) ? modelId.trim() : resolveDefaultId();
        AiChatProperties.ModelConfig config = findConfig(id);
        if (config == null)
        {
            throw new LangChainWorkflowException("不支持的模型: " + id + "，可选模型: " + ids(configs));
        }
        ChatLanguageModel cached = modelCache.get(config.getId());
        if (cached != null)
        {
            return cached;
        }
        ChatLanguageModel created = buildModel(config);
        ChatLanguageModel existing = modelCache.putIfAbsent(config.getId(), created);
        return existing == null ? created : existing;
    }

    private ChatLanguageModel getOrCreateDefaultModel()
    {
        if (defaultChatLanguageModel == null)
        {
            synchronized (this)
            {
                if (defaultChatLanguageModel == null)
                {
                    if (!notBlank(properties.getApiKey()))
                    {
                        throw new LangChainWorkflowException(
                                "未配置大模型 API Key，请在 config/application.yml 配置 ai.chat.models 或 langchain.api-key");
                    }
                    defaultChatLanguageModel = OpenAiChatModel.builder()
                            .baseUrl(properties.getBaseUrl())
                            .apiKey(properties.getApiKey())
                            .modelName(properties.getModelName())
                            .temperature(properties.getTemperature())
                            .timeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                            .maxRetries(1)
                            .logRequests(properties.getLogRequests())
                            .logResponses(properties.getLogResponses())
                            .build();
                }
            }
        }
        return defaultChatLanguageModel;
    }

    private ChatLanguageModel buildModel(AiChatProperties.ModelConfig config)
    {
        if (!notBlank(config.getApiKey()))
        {
            throw new LangChainWorkflowException("未配置模型「" + displayLabel(config)
                    + "」的 API Key，请在 config/application.yml 的 ai.chat.models 中填写");
        }
        if (!notBlank(config.getBaseUrl()) || !notBlank(config.getModel()))
        {
            throw new LangChainWorkflowException("模型「" + displayLabel(config)
                    + "」配置不完整，请检查 base-url 与 model");
        }
        return OpenAiChatModel.builder()
                .baseUrl(config.getBaseUrl())
                .apiKey(config.getApiKey())
                .modelName(config.getModel())
                .temperature(config.getTemperature() == null ? 1.0D : config.getTemperature())
                .timeout(Duration.ofSeconds(config.getTimeoutSeconds() == null ? 120 : config.getTimeoutSeconds()))
                .maxRetries(1)
                .logRequests(properties.getLogRequests())
                .logResponses(properties.getLogResponses())
                .build();
    }

    private String resolveDefaultId()
    {
        List<AiChatProperties.ModelConfig> configs = configuredModels();
        if (configs.isEmpty())
        {
            return properties.getModelName();
        }
        String defaultModel = chatProperties.getDefaultModel();
        if (notBlank(defaultModel))
        {
            for (AiChatProperties.ModelConfig config : configs)
            {
                if (defaultModel.trim().equals(config.getId()))
                {
                    return config.getId();
                }
            }
        }
        return configs.get(0).getId();
    }

    private AiChatProperties.ModelConfig findConfig(String id)
    {
        if (!notBlank(id))
        {
            return null;
        }
        for (AiChatProperties.ModelConfig config : configuredModels())
        {
            if (id.equals(config.getId()) || id.equalsIgnoreCase(config.getId()))
            {
                return config;
            }
        }
        return null;
    }

    private List<AiChatProperties.ModelConfig> configuredModels()
    {
        if (chatProperties == null || chatProperties.getModels() == null)
        {
            return new ArrayList<AiChatProperties.ModelConfig>();
        }
        List<AiChatProperties.ModelConfig> valid = new ArrayList<AiChatProperties.ModelConfig>();
        for (AiChatProperties.ModelConfig config : chatProperties.getModels())
        {
            if (config != null && notBlank(config.getId()) && notBlank(config.getModel()))
            {
                valid.add(config);
            }
        }
        return valid;
    }

    private String displayLabel(AiChatProperties.ModelConfig config)
    {
        if (notBlank(config.getLabel()))
        {
            return config.getLabel();
        }
        return notBlank(config.getModel()) ? config.getModel() : config.getId();
    }

    private String ids(List<AiChatProperties.ModelConfig> configs)
    {
        StringBuilder builder = new StringBuilder();
        for (AiChatProperties.ModelConfig config : configs)
        {
            if (builder.length() > 0)
            {
                builder.append('、');
            }
            builder.append(config.getId());
        }
        return builder.toString();
    }

    private static boolean notBlank(String value)
    {
        return value != null && !value.trim().isEmpty();
    }
}
