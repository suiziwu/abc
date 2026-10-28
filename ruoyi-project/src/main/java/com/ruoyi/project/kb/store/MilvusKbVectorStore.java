package com.ruoyi.project.kb.store;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.project.domain.KbChunk;
import com.ruoyi.project.kb.KbChunkTxService;
import com.ruoyi.project.kb.KbProperties;
import com.ruoyi.project.kb.KbVectorStore;
import com.ruoyi.project.mapper.KbChunkMapper;

/**
 * Milvus 向量库实现（Milvus 2.4+ RESTful v2）。
 *
 * <p>设计要点：
 * <ul>
 *   <li>Milvus 只存向量与过滤字段，原文仍写 kb_chunk 表，避免大字段进向量库；</li>
 *   <li>collection 使用 COSINE 度量，字段：id(VarChar 主键) / subject_id / chapter_id /
 *       source_type / vector(FLT 1024)；</li>
 *   <li>健康检查失败时按 {@code kb.milvus.fallback-to-local} 自动回退 local，保证主流程可用。</li>
 * </ul></p>
 *
 * @author ruoyi
 */
@Component
public class MilvusKbVectorStore implements KbVectorStore
{
    private static final Logger log = LoggerFactory.getLogger(MilvusKbVectorStore.class);

    /** 合法的分块来源类型，用于阻断 filter 表达式注入。 */
    private static final java.util.Set<String> ALLOWED_SOURCE_TYPES = new java.util.HashSet<>(
            java.util.Arrays.asList("chapter", "point", "transcript", "quiz"));

    private final KbProperties kbProperties;

    private final KbChunkMapper kbChunkMapper;

    private final KbChunkTxService kbChunkTxService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private RestTemplate restTemplate;

    private volatile Boolean collectionReady = null;

    private volatile String lastError = null;

    public MilvusKbVectorStore(KbProperties kbProperties, KbChunkMapper kbChunkMapper,
            KbChunkTxService kbChunkTxService)
    {
        this.kbProperties = kbProperties;
        this.kbChunkMapper = kbChunkMapper;
        this.kbChunkTxService = kbChunkTxService;
    }

    @Override
    public String provider()
    {
        return "milvus";
    }

    @Override
    public boolean available()
    {
        try
        {
            ensureCollection();
            return true;
        }
        catch (Exception e)
        {
            lastError = e.getMessage();
            return false;
        }
    }

    @Override
    public String unavailableReason()
    {
        return lastError == null ? "Milvus 不可用（请确认 docker 已启动 Milvus 服务）" : lastError;
    }

    @Override
    public int upsert(List<KbChunk> chunks)
    {
        if (chunks == null || chunks.isEmpty())
        {
            return 0;
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        List<String> docIds = new ArrayList<>();
        for (KbChunk chunk : chunks)
        {
            if (chunk.getDocId() != null && !docIds.contains(chunk.getDocId()))
            {
                docIds.add(chunk.getDocId());
            }
            if (chunk.getEmbedding() == null || chunk.getEmbedding().isEmpty())
            {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", chunk.getDocId() + "#" + chunk.getChunkIndex());
            row.put("subject_id", chunk.getSubjectId() == null ? 0L : chunk.getSubjectId());
            row.put("chapter_id", chunk.getChapterId() == null ? 0L : chunk.getChapterId());
            row.put("source_type", chunk.getSourceType());
            row.put("point_id", chunk.getPointId() == null ? 0L : chunk.getPointId());
            row.put("vector", parse(chunk.getEmbedding()));
            rows.add(row);
        }
        // 先写向量：失败时 MySQL 未动，旧原文与旧向量仍完整可用
        if (!rows.isEmpty())
        {
            post("/v2/vectordb/entities/upsert", payload("data", rows));
        }
        try
        {
            return kbChunkTxService.insertChunks(chunks);
        }
        catch (RuntimeException e)
        {
            // MySQL 写入失败：清掉刚写入的向量，避免出现「有向量无原文」
            try
            {
                deleteVectorsByDocIds(docIds);
            }
            catch (Exception cleanup)
            {
                log.warn("补偿删除 Milvus 向量失败，需人工对账: {}", cleanup.getMessage());
            }
            throw e;
        }
    }

    @Override
    public List<KbChunk> search(float[] queryVector, Long subjectId, Long chapterId,
            List<String> sourceTypes, int topK)
    {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("collectionName", collection());
        // Milvus REST v2 检索：data 必须是一维向量的二维数组（[[0.1, 0.2, ...]]），
        // 不能写成 [{"vector":[...]}]，否则报 Mismatch type []float32 with value object
        payload.put("data", java.util.Collections.singletonList(toDoubleList(queryVector)));
        payload.put("annsField", "vector");
        payload.put("limit", topK);
        payload.put("outputFields", java.util.Arrays.asList("id", "subject_id", "chapter_id", "source_type", "point_id"));
        String filter = buildFilter(subjectId, chapterId, sourceTypes);
        if (!filter.isEmpty())
        {
            payload.put("filter", filter);
        }

        JsonNode root = post("/v2/vectordb/entities/search", payload);
        JsonNode hits = root.path("data");
        List<KbChunk> result = new ArrayList<>();
        List<String> docIds = new ArrayList<>();
        for (JsonNode hit : hits)
        {
            KbChunk chunk = new KbChunk();
            String compositeId = hit.path("id").asText("");
            int split = compositeId.lastIndexOf('#');
            String docId = split > 0 ? compositeId.substring(0, split) : compositeId;
            chunk.setDocId(docId);
            try
            {
                chunk.setChunkIndex(split > 0 ? Integer.parseInt(compositeId.substring(split + 1)) : 0);
            }
            catch (NumberFormatException ignored)
            {
                chunk.setChunkIndex(0);
            }
            chunk.setSourceType(hit.path("source_type").asText(""));
            chunk.setSubjectId(hit.path("subject_id").asLong(0L));
            chunk.setChapterId(hit.path("chapter_id").asLong(0L));
            chunk.setPointId(hit.path("point_id").asLong(0L));
            chunk.setScore(hit.path("distance").asDouble(0D));
            result.add(chunk);
            if (!docIds.contains(docId))
            {
                docIds.add(docId);
            }
        }

        // 一次性回填原文（按 docId 批量查，避免全表扫描）
        if (!docIds.isEmpty())
        {
            Map<String, KbChunk> rowMap = new LinkedHashMap<>();
            for (KbChunk row : kbChunkMapper.selectKbChunkByDocIds(docIds))
            {
                rowMap.put(row.getDocId() + "#" + row.getChunkIndex(), row);
            }
            List<KbChunk> filled = new ArrayList<>(result.size());
            for (KbChunk chunk : result)
            {
                KbChunk row = rowMap.get(chunk.getDocId() + "#" + chunk.getChunkIndex());
                if (row == null)
                {
                    // 原文缺失（双写漂移或已清理）：丢弃该命中，避免把空上下文喂给模型
                    continue;
                }
                // 直接回传数据库整行：section_path / token_count / content_hash 等元数据
                // 都要参与上下文拼装与去重，只挑几个字段复制会丢元数据
                row.setScore(chunk.getScore());
                filled.add(row);
            }
            return filled;
        }
        return result;
    }

    @Override
    public int clear(Long subjectId, Long chapterId, String sourceType)
    {
        String filter = buildFilter(subjectId, chapterId,
                sourceType == null ? null : java.util.Collections.singletonList(sourceType));
        // 过滤条件为空会删除整个 collection，必须直接拒绝
        if (filter.isEmpty())
        {
            throw new IllegalStateException("清空条件不能为空");
        }
        // 先删向量：Milvus 删除不可回滚，放在 MySQL 之后失败会留下无正文的空向量
        Map<String, Object> deletePayload = new LinkedHashMap<>();
        deletePayload.put("collectionName", collection());
        deletePayload.put("filter", filter);
        post("/v2/vectordb/entities/delete", deletePayload);
        return kbChunkMapper.deleteKbChunkByFilter(subjectId, chapterId, sourceType, null);
    }

    @Override
    public int deleteByDocIds(List<String> docIds)
    {
        deleteVectorsByDocIds(docIds);
        return kbChunkTxService.deleteByDocIds(docIds);
    }

    /**
     * 只删向量（Milvus 主键是 docId#chunkIndex，用前缀 like 匹配整个文档）。
     */
    private void deleteVectorsByDocIds(List<String> docIds)
    {
        if (docIds == null || docIds.isEmpty())
        {
            return;
        }
        List<String> parts = new ArrayList<>();
        for (String docId : docIds)
        {
            if (docId != null && !docId.isEmpty())
            {
                parts.add("id like '" + docId.replace("'", "") + "#%'");
            }
        }
        if (parts.isEmpty())
        {
            return;
        }
        Map<String, Object> deletePayload = new LinkedHashMap<>();
        deletePayload.put("collectionName", collection());
        deletePayload.put("filter", String.join(" or ", parts));
        post("/v2/vectordb/entities/delete", deletePayload);
    }

    @Override
    public int count(Long subjectId)
    {
        return kbChunkMapper.countKbChunk(subjectId);
    }

    private String collection()
    {
        return kbProperties.getMilvus().getCollection();
    }

    /**
     * 确保 collection 存在（首次调用时创建）。
     */
    private synchronized void ensureCollection() throws Exception
    {
        if (Boolean.TRUE.equals(collectionReady))
        {
            return;
        }
        int dimensions = kbProperties.getEmbedding().getDimensions() == null
                ? 1024 : kbProperties.getEmbedding().getDimensions();

        Map<String, Object> schema = new LinkedHashMap<>();
        List<Map<String, Object>> fields = new ArrayList<>();
        fields.add(field("id", "VarChar", true, 256, null));
        fields.add(field("vector", "FloatVector", false, null, dimensions));
        fields.add(field("subject_id", "Int64", false, null, null));
        fields.add(field("chapter_id", "Int64", false, null, null));
        fields.add(field("point_id", "Int64", false, null, null));
        fields.add(field("source_type", "VarChar", false, 32, null));
        schema.put("autoId", false);
        schema.put("enableDynamicField", false);
        schema.put("fields", fields);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("collectionName", collection());
        payload.put("schema", schema);
        payload.put("indexParams", java.util.Collections.singletonList(
                index("vector", "AUTOINDEX", "COSINE")));

        try
        {
            post("/v2/vectordb/collections/create", payload);
        }
        catch (RuntimeException e)
        {
            String message = String.valueOf(e.getMessage());
            if (!message.contains("duplicate collection") && !message.contains("already exist"))
            {
                throw e;
            }
            // 已存在且 schema 不兼容（如早期版本建错维度/字段）时，删除后重建
            if (!isSchemaCompatible(dimensions))
            {
                log.warn("Milvus 集合 {} 已存在但 schema 不兼容，删除后重建", collection());
                Map<String, Object> drop = new LinkedHashMap<>();
                drop.put("collectionName", collection());
                post("/v2/vectordb/collections/drop", drop);
                post("/v2/vectordb/collections/create", payload);
            }
        }
        collectionReady = Boolean.TRUE;
    }

    /**
     * 校验已有集合是否与当前配置兼容（向量维度 + 关键标量字段）。
     */
    private boolean isSchemaCompatible(int dimensions)
    {
        try
        {
            Map<String, Object> query = new LinkedHashMap<>();
            query.put("collectionName", collection());
            JsonNode root = post("/v2/vectordb/collections/describe", query);
            JsonNode fields = root.path("data").path("fields");
            boolean vectorOk = false;
            boolean dimOk = false;
            boolean sourceTypeOk = false;
            for (JsonNode field : fields)
            {
                String name = field.path("name").asText(field.path("fieldName").asText(""));
                if ("vector".equals(name))
                {
                    String type = field.path("type").asText(field.path("dataType").asText(""));
                    vectorOk = type.isEmpty() || "FloatVector".equalsIgnoreCase(type);
                    // describe 返回的 params 是 [{key:"dim", value:"1024"}] 数组，不能直接 path("dim")
                    dimOk = paramInt(field, "dim", -1) == dimensions;
                }
                if ("source_type".equals(name))
                {
                    sourceTypeOk = true;
                }
            }
            return vectorOk && dimOk && sourceTypeOk;
        }
        catch (Exception e)
        {
            // 读取失败时不能贸然删除集合（会丢数据），按兼容处理，后续写入失败时再暴露问题
            log.warn("读取 Milvus 集合 schema 失败，跳过重建：{}", e.getMessage());
            return true;
        }
    }

    /**
     * 读取字段参数值，兼容 describe 的两种返回形态：
     * params 数组 [{key,value}] 或对象 {dim:1024} / elementTypeParams。
     */
    private int paramInt(JsonNode field, String key, int defaultValue)
    {
        JsonNode params = field.path("params");
        if (params.isArray())
        {
            for (JsonNode item : params)
            {
                if (key.equals(item.path("key").asText()))
                {
                    try
                    {
                        return Integer.parseInt(item.path("value").asText().trim());
                    }
                    catch (NumberFormatException ignored)
                    {
                        return defaultValue;
                    }
                }
            }
            return defaultValue;
        }
        JsonNode value = params.path(key);
        if (value.isMissingNode() || value.isNull())
        {
            value = field.path("elementTypeParams").path(key);
        }
        if (value.isMissingNode() || value.isNull())
        {
            value = field.path(key);
        }
        return value.isMissingNode() || value.isNull() ? defaultValue : value.asInt(defaultValue);
    }

    /**
     * 构造 REST v2 字段定义。
     *
     * <p>注意：VarChar 的 max_length 与 FloatVector 的 dim 必须放在
     * {@code elementTypeParams} 中，否则 Milvus 会报
     * “type param(max_length) should be specified for varChar field”。</p>
     */
    private Map<String, Object> field(String name, String type, boolean primary,
            Integer maxLength, Integer dim)
    {
        Map<String, Object> field = new LinkedHashMap<>();
        field.put("fieldName", name);
        field.put("dataType", type);
        if (primary)
        {
            field.put("isPrimary", true);
        }
        if (maxLength != null || dim != null)
        {
            Map<String, Object> params = new LinkedHashMap<>();
            if (maxLength != null)
            {
                params.put("max_length", maxLength);
            }
            if (dim != null)
            {
                params.put("dim", dim);
            }
            field.put("elementTypeParams", params);
        }
        return field;
    }

    private Map<String, Object> index(String fieldName, String indexType, String metricType)
    {
        Map<String, Object> index = new LinkedHashMap<>();
        index.put("fieldName", fieldName);
        index.put("indexType", indexType);
        index.put("metricType", metricType);
        return index;
    }

    private Map<String, Object> payload(String key, List<Map<String, Object>> rows)
    {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("collectionName", collection());
        payload.put(key, rows);
        return payload;
    }

    private Map<String, Object> payload(String key, List<Map<String, Object>> rows, String filter)
    {
        Map<String, Object> payload = payload(key, rows);
        payload.put("filter", filter);
        return payload;
    }

    private String buildFilter(Long subjectId, Long chapterId, List<String> sourceTypes)
    {
        List<String> parts = new ArrayList<>();
        if (subjectId != null)
        {
            parts.add("subject_id == " + subjectId);
        }
        if (chapterId != null)
        {
            parts.add("chapter_id == " + chapterId);
        }
        if (sourceTypes != null && !sourceTypes.isEmpty())
        {
            List<String> quoted = new ArrayList<>();
            for (String type : sourceTypes)
            {
                // sourceType 来自请求参数，必须白名单校验后再拼进表达式，否则可注入扩大删除范围
                if (type == null || !ALLOWED_SOURCE_TYPES.contains(type))
                {
                    throw new IllegalArgumentException("非法的知识库来源类型：" + type);
                }
                quoted.add("'" + type.replace("'", "") + "'");
            }
            parts.add("source_type in [" + String.join(", ", quoted) + "]");
        }
        return String.join(" and ", parts);
    }

    private JsonNode post(String path, Map<String, Object> payload)
    {
        try
        {
            String url = trimEndSlash(kbProperties.getMilvus().getBaseUrl()) + path;
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            String json = objectMapper.writeValueAsString(payload);
            ResponseEntity<String> response = template().exchange(url, HttpMethod.POST,
                    new HttpEntity<>(json, headers), String.class);
            JsonNode root = objectMapper.readTree(response.getBody() == null ? "{}" : response.getBody());
            if (root.path("code").asInt(0) != 0 && root.has("code"))
            {
                throw new IllegalStateException("Milvus 返回错误：" + root.path("message").asText(root.toString()));
            }
            return root;
        }
        catch (Exception e)
        {
            lastError = e.getMessage();
            throw e instanceof RuntimeException ? (RuntimeException) e : new RuntimeException(e);
        }
    }

    private RestTemplate template()
    {
        if (restTemplate == null)
        {
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(3_000);
            factory.setReadTimeout(30_000);
            restTemplate = new RestTemplate(factory);
        }
        return restTemplate;
    }

    private List<Double> toDoubleList(float[] vector)
    {
        List<Double> list = new ArrayList<>(vector.length);
        for (float value : vector)
        {
            list.add((double) value);
        }
        return list;
    }

    private float[] parse(String json)
    {
        try
        {
            double[] values = objectMapper.readValue(json, double[].class);
            float[] vector = new float[values.length];
            for (int i = 0; i < values.length; i++)
            {
                vector[i] = (float) values[i];
            }
            return vector;
        }
        catch (Exception e)
        {
            return new float[0];
        }
    }

    private static String trimEndSlash(String value)
    {
        String trimmed = value == null ? "" : value.trim();
        while (trimmed.endsWith("/"))
        {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }
}