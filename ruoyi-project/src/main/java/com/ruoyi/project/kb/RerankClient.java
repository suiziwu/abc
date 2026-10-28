package com.ruoyi.project.kb;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.project.domain.KbChunk;

/**
 * 重排序客户端（百炼 qwen3-rerank / gte-rerank 兼容）。
 *
 * <p>百炼检索链路是「召回 → 精排」两段式：向量与关键词各召回一批候选，
 * 再由 rerank 模型按「问题-片段」相关性重新排序。默认关闭，
 * 配置 {@code kb.rerank.enabled=true} 且提供 api-key 后生效。</p>
 *
 * <p>任何异常都退化为原顺序，绝不因为精排失败而影响问答主链路。</p>
 *
 * @author ruoyi
 */
@Component
public class RerankClient
{
    private static final Logger log = LoggerFactory.getLogger(RerankClient.class);

    /** 单条文档送入精排的最大字符数，避免请求体过大 */
    private static final int MAX_DOC_CHARS = 2000;

    private final KbProperties kbProperties;

    private final EmbeddingClient embeddingClient;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private RestTemplate restTemplate;

    public RerankClient(KbProperties kbProperties, EmbeddingClient embeddingClient)
    {
        this.kbProperties = kbProperties;
        this.embeddingClient = embeddingClient;
    }

    /**
     * 是否可用（开关打开且 base-url / api-key 齐全）。
     */
    public boolean isEnabled()
    {
        KbProperties.Rerank rerank = kbProperties.getRerank();
        return rerank.isEnabled() && notBlank(rerank.getBaseUrl()) && notBlank(resolveApiKey());
    }

    /**
     * 对候选切片重排。
     *
     * @param query 用户问题
     * @param hits 融合后的候选（按 RRF 降序）
     * @param limit 最终返回条数
     * @return 重排后的切片；失败时返回原顺序的前 limit 条
     */
    public List<KbChunk> rerank(String query, List<KbChunk> hits, int limit)
    {
        if (hits == null || hits.size() <= 1 || limit <= 0)
        {
            return hits == null ? new ArrayList<>() : hits;
        }
        if (!isEnabled())
        {
            return head(hits, limit);
        }
        KbProperties.Rerank rerank = kbProperties.getRerank();
        int candidateSize = Math.min(hits.size(), Math.max(limit, rerank.getTopN()));
        List<KbChunk> candidates = head(hits, candidateSize);
        try
        {
            List<String> documents = new ArrayList<>(candidates.size());
            for (KbChunk chunk : candidates)
            {
                String content = chunk.getContent() == null ? "" : chunk.getContent();
                documents.add(content.length() > MAX_DOC_CHARS ? content.substring(0, MAX_DOC_CHARS) : content);
            }

            Map<String, Object> input = new LinkedHashMap<>();
            input.put("query", query);
            input.put("documents", documents);

            Map<String, Object> parameters = new LinkedHashMap<>();
            parameters.put("return_documents", false);
            parameters.put("top_n", Math.min(limit, candidates.size()));

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", rerank.getModel());
            body.put("input", input);
            body.put("parameters", parameters);

            JsonNode root = post(body);
            JsonNode results = root.path("output").path("results");
            if (!results.isArray() || results.size() == 0)
            {
                return head(hits, limit);
            }
            List<KbChunk> ranked = new ArrayList<>(results.size());
            for (JsonNode item : results)
            {
                int index = item.path("index").asInt(-1);
                if (index < 0 || index >= candidates.size())
                {
                    continue;
                }
                KbChunk chunk = candidates.get(index);
                // 精排分数写回 keywordScore，保留原始 score 供前端展示相似度
                chunk.setKeywordScore(item.path("relevance_score").asDouble(chunk.getKeywordScore() == null
                        ? 0D : chunk.getKeywordScore()));
                ranked.add(chunk);
            }
            if (ranked.isEmpty())
            {
                return head(hits, limit);
            }
            // 未进入 top_n 的候选按原顺序补在后面，保证召回不丢
            for (KbChunk chunk : candidates)
            {
                if (!ranked.contains(chunk))
                {
                    ranked.add(chunk);
                }
            }
            return head(ranked, limit);
        }
        catch (Exception e)
        {
            log.warn("重排序失败，按融合顺序返回：{}", e.getMessage());
            return head(hits, limit);
        }
    }

    private JsonNode post(Map<String, Object> body) throws Exception
    {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(resolveApiKey());
        String json = objectMapper.writeValueAsString(body);
        ResponseEntity<String> response = template().exchange(kbProperties.getRerank().getBaseUrl(),
                HttpMethod.POST, new HttpEntity<>(json, headers), String.class);
        return objectMapper.readTree(response.getBody() == null ? "{}" : response.getBody());
    }

    private String resolveApiKey()
    {
        String key = kbProperties.getRerank().getApiKey();
        if (notBlank(key))
        {
            return key.trim();
        }
        return embeddingClient.resolvedApiKey();
    }

    private RestTemplate template()
    {
        if (restTemplate == null)
        {
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(5_000);
            factory.setReadTimeout(Math.max(1, kbProperties.getRerank().getTimeoutSeconds()) * 1000);
            restTemplate = new RestTemplate(factory);
        }
        return restTemplate;
    }

    private static List<KbChunk> head(List<KbChunk> hits, int limit)
    {
        if (hits.size() <= limit)
        {
            return new ArrayList<>(hits);
        }
        return new ArrayList<>(hits.subList(0, limit));
    }

    private static boolean notBlank(String value)
    {
        return value != null && !value.trim().isEmpty();
    }
}
