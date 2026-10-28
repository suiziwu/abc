package com.ruoyi.project.kb;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.project.langchain.AiChatProperties;

/**
 * 向量化客户端（OpenAI 兼容 /embeddings）。
 *
 * <p>默认使用百炼 text-embedding-v3（1024 维），支持批量；未单独配置 Key 时
 * 复用 langchain.api-key 与 ai.chat.models 的 base-url。</p>
 *
 * @author ruoyi
 */
@Component
public class EmbeddingClient
{
    private final KbProperties kbProperties;

    private final AiChatProperties aiChatProperties;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private RestTemplate restTemplate;

    @Autowired
    public EmbeddingClient(KbProperties kbProperties, AiChatProperties aiChatProperties)
    {
        this.kbProperties = kbProperties;
        this.aiChatProperties = aiChatProperties;
    }

    /** langchain.api-key（由 Spring 从外部 config/application.yml 注入） */
    private String langchainApiKey;

    @Autowired(required = false)
    public void setLangchainApiKey(
            @org.springframework.beans.factory.annotation.Value("${langchain.api-key:}") String langchainApiKey)
    {
        this.langchainApiKey = langchainApiKey;
    }

    /**
     * 解析实际的 base-url：优先 kb.embedding.base-url，其次 ai.chat.models 第一个模型的 base-url。
     */
    public String resolvedBaseUrl()
    {
        KbProperties.Embedding emb = kbProperties.getEmbedding();
        if (notBlank(emb.getBaseUrl()))
        {
            return trimEndSlash(emb.getBaseUrl());
        }
        if (aiChatProperties != null && aiChatProperties.getModels() != null)
        {
            for (AiChatProperties.ModelConfig model : aiChatProperties.getModels())
            {
                if (notBlank(model.getBaseUrl()))
                {
                    return trimEndSlash(model.getBaseUrl());
                }
            }
        }
        return "";
    }

    /**
     * 解析实际的 api-key：优先 kb.embedding.api-key，其次 langchain.api-key，
     * 最后取 ai.chat.models 中任一模型的 api-key。
     */
    public String resolvedApiKey()
    {
        KbProperties.Embedding emb = kbProperties.getEmbedding();
        if (notBlank(emb.getApiKey()))
        {
            return emb.getApiKey().trim();
        }
        if (notBlank(langchainApiKey))
        {
            return langchainApiKey.trim();
        }
        if (aiChatProperties != null && aiChatProperties.getModels() != null)
        {
            for (AiChatProperties.ModelConfig model : aiChatProperties.getModels())
            {
                if (notBlank(model.getApiKey()))
                {
                    return model.getApiKey().trim();
                }
            }
        }
        return "";
    }

    /**
     * 是否已完成向量化配置（base-url + api-key + 模型名齐全）。
     */
    public boolean isConfigured()
    {
        return notBlank(resolvedBaseUrl()) && notBlank(resolvedApiKey())
                && notBlank(kbProperties.getEmbedding().getModel());
    }

    /**
     * 单条文本向量化。
     */
    public float[] embed(String text)
    {
        List<float[]> result = embedBatch(java.util.Collections.singletonList(text));
        return result.isEmpty() ? new float[0] : result.get(0);
    }

    /**
     * 批量向量化，自动按 batchSize 分批并归一化。
     *
     * @param texts 待向量化文本
     * @return 与输入等长的向量列表
     */
    public List<float[]> embedBatch(List<String> texts)
    {
        List<float[]> vectors = new ArrayList<>();
        if (texts == null || texts.isEmpty())
        {
            return vectors;
        }
        if (!isConfigured())
        {
            throw new IllegalStateException("向量化未配置：请在 config/application.yml 中配置 kb.embedding 或 langchain.api-key");
        }
        int batchSize = kbProperties.getEmbedding().getBatchSize() == null
                ? 10 : Math.max(1, kbProperties.getEmbedding().getBatchSize());
        for (int start = 0; start < texts.size(); start += batchSize)
        {
            int end = Math.min(start + batchSize, texts.size());
            vectors.addAll(requestBatch(texts.subList(start, end)));
        }
        return vectors;
    }

    private List<float[]> requestBatch(List<String> batch)
    {
        String url = resolvedBaseUrl() + "/embeddings";
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", kbProperties.getEmbedding().getModel());
        body.put("input", batch);
        if (kbProperties.getEmbedding().getDimensions() != null)
        {
            body.put("dimensions", kbProperties.getEmbedding().getDimensions());
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(resolvedApiKey());

        RuntimeException lastError = null;
        for (int attempt = 0; attempt < 3; attempt++)
        {
            try
            {
                String json = objectMapper.writeValueAsString(body);
                ResponseEntity<String> response = template().exchange(url, HttpMethod.POST,
                        new HttpEntity<>(json, headers), String.class);
                return parseVectors(response.getBody(), batch.size());
            }
            catch (Exception e)
            {
                lastError = e instanceof RuntimeException ? (RuntimeException) e : new RuntimeException(e);
                sleepQuietly(600L * (attempt + 1));
            }
        }
        throw new IllegalStateException("向量化请求失败：" + (lastError == null ? "未知错误" : lastError.getMessage()),
                lastError);
    }

    private List<float[]> parseVectors(String json, int expected) throws Exception
    {
        List<float[]> vectors = new ArrayList<>();
        if (json == null || json.trim().isEmpty())
        {
            throw new IllegalStateException("向量化返回为空");
        }
        JsonNode root = objectMapper.readTree(json);
        JsonNode data = root.path("data");
        if (!data.isArray() || data.size() == 0)
        {
            String message = root.path("error").path("message").asText("");
            throw new IllegalStateException("向量化返回异常" + (message.isEmpty() ? "：" + json : "：" + message));
        }
        for (JsonNode item : data)
        {
            JsonNode arr = item.path("embedding");
            float[] vector = new float[arr.size()];
            for (int i = 0; i < arr.size(); i++)
            {
                vector[i] = (float) arr.get(i).asDouble();
            }
            vectors.add(normalize(vector));
        }
        if (vectors.size() != expected)
        {
            throw new IllegalStateException("向量化条数不一致：期望 " + expected + "，实际 " + vectors.size());
        }
        return vectors;
    }

    /**
     * L2 归一化，便于用点积直接等价余弦相似度。
     */
    public static float[] normalize(float[] vector)
    {
        if (vector == null || vector.length == 0)
        {
            return vector;
        }
        double sum = 0D;
        for (float value : vector)
        {
            sum += (double) value * value;
        }
        double norm = Math.sqrt(sum);
        if (norm < 1e-8)
        {
            return vector;
        }
        float[] result = new float[vector.length];
        for (int i = 0; i < vector.length; i++)
        {
            result[i] = (float) (vector[i] / norm);
        }
        return result;
    }

    private RestTemplate template()
    {
        if (restTemplate == null)
        {
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            int timeout = kbProperties.getEmbedding().getTimeoutSeconds() == null
                    ? 60 : kbProperties.getEmbedding().getTimeoutSeconds();
            factory.setConnectTimeout(10_000);
            factory.setReadTimeout(timeout * 1000);
            restTemplate = new RestTemplate(factory);
        }
        return restTemplate;
    }

    private static void sleepQuietly(long millis)
    {
        try
        {
            Thread.sleep(millis);
        }
        catch (InterruptedException ignored)
        {
            Thread.currentThread().interrupt();
        }
    }

    private static boolean notBlank(String value)
    {
        return value != null && !value.trim().isEmpty();
    }

    private static String trimEndSlash(String value)
    {
        String trimmed = value.trim();
        while (trimmed.endsWith("/"))
        {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }
}