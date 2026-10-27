package com.ruoyi.project.langchain;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import dev.langchain4j.model.openai.OpenAiChatModel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * LangChain4j 适配测试：验证 Jar 在 JDK 8 下可加载、Builder 可用、缺少 Key 时报错清晰。
 *
 * @author ruoyi
 */
public class LangChain4jChatModelClientTest
{
    @Test
    public void testOpenAiCompatibleModelBuilder()
    {
        OpenAiChatModel model = OpenAiChatModel.builder()
                .baseUrl("https://ws-uxxtz5szu7puw1so.cn-beijing.maas.aliyuncs.com/compatible-mode/v1")
                .apiKey("test-key")
                .modelName("qwen3.8-flash")
                .temperature(1.0D)
                .timeout(Duration.ofSeconds(5))
                .build();
        assertEquals("qwen3.8-flash", model.modelName());
    }

    @Test
    public void testMissingApiKeyFailsFast()
    {
        LangChainProperties properties = new LangChainProperties();
        properties.setApiKey("");
        LangChain4jChatModelClient client = new LangChain4jChatModelClient(properties);
        LangChainWorkflowException exception = assertThrows(LangChainWorkflowException.class,
                () -> client.chat("system", "user"));
        assertTrue(exception.getMessage().contains("API Key"));
    }

    @Test
    public void testModelOptionsAndUnsupportedModel()
    {
        LangChainProperties properties = new LangChainProperties();
        AiChatProperties chatProperties = new AiChatProperties();
        chatProperties.setDefaultModel("qwen3.8-flash");

        AiChatProperties.ModelConfig qwen = new AiChatProperties.ModelConfig();
        qwen.setId("qwen3.8-flash");
        qwen.setLabel("通义千问");
        qwen.setBaseUrl("https://example.com/v1");
        qwen.setApiKey("key-a");
        qwen.setModel("qwen3.8-flash");

        AiChatProperties.ModelConfig deepseek = new AiChatProperties.ModelConfig();
        deepseek.setId("deepseek-chat");
        deepseek.setLabel("DeepSeek Chat");
        deepseek.setBaseUrl("https://api.deepseek.com/v1");
        deepseek.setApiKey("");
        deepseek.setModel("deepseek-chat");

        chatProperties.setModels(Arrays.asList(qwen, deepseek));
        LangChain4jChatModelClient client = new LangChain4jChatModelClient(properties, chatProperties);

        List<ChatModelOption> options = client.listModels();
        assertEquals(2, options.size());
        assertEquals("qwen3.8-flash", options.get(0).getId());
        assertTrue(options.get(0).isDefault());
        assertTrue(options.get(0).isAvailable());
        assertEquals("deepseek-chat", options.get(1).getId());
        assertFalse(options.get(1).isDefault());
        assertFalse(options.get(1).isAvailable());

        LangChainWorkflowException exception = assertThrows(LangChainWorkflowException.class,
                () -> client.chat("not-exist", "system", "user"));
        assertTrue(exception.getMessage().contains("不支持的模型"));
        assertTrue(exception.getMessage().contains("qwen3.8-flash"));
    }
}