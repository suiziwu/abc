package com.ruoyi.project.kb;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 知识库（RAG）配置。
 *
 * <pre>
 * kb:
 *   enabled: true
 *   provider: local              # local（MySQL 向量，Windows 开箱可用）| milvus
 *   chunk-size: 320
 *   chunk-overlap: 60
 *   top-k: 5
 *   min-score: 0.2
 *   embedding:
 *     base-url: https://.../compatible-mode/v1
 *     api-key: ${DASHSCOPE_API_KEY:}
 *     model: text-embedding-v3
 *     dimensions: 1024
 *     batch-size: 10
 *     timeout-seconds: 60
 *   milvus:
 *     base-url: http://127.0.0.1:19530
 *     collection: course_kb
 * </pre>
 *
 * <p>embedding.api-key 为空时会回退到 langchain.api-key；
 * embedding.base-url 为空时会回退到 ai.chat.models 中第一个模型的 base-url。</p>
 *
 * @author ruoyi
 */
@Component
@ConfigurationProperties(prefix = "kb")
public class KbProperties
{
    /** 是否启用知识库检索 */
    private boolean enabled = true;

    /** 向量库实现：local / milvus */
    private String provider = "local";

    /** 切块目标长度（字符） */
    private int chunkSize = 320;

    /** 切块重叠长度（字符） */
    private int chunkOverlap = 60;

    /** 检索返回条数 */
    private int topK = 5;

    /** 相似度下限（余弦，-1~1） */
    private double minScore = 0.2D;

    /** 向量化配置 */
    private Embedding embedding = new Embedding();

    /** Milvus 配置 */
    private Milvus milvus = new Milvus();

    /** 检索策略配置（对标百炼：双路召回 + RRF 融合） */
    private Retrieval retrieval = new Retrieval();

    /** 重排序配置（对标百炼 rerank，默认关闭） */
    private Rerank rerank = new Rerank();

    public boolean isEnabled()
    {
        return enabled;
    }

    public void setEnabled(boolean enabled)
    {
        this.enabled = enabled;
    }

    public String getProvider()
    {
        return provider;
    }

    public void setProvider(String provider)
    {
        this.provider = provider;
    }

    public int getChunkSize()
    {
        return chunkSize;
    }

    public void setChunkSize(int chunkSize)
    {
        this.chunkSize = chunkSize;
    }

    public int getChunkOverlap()
    {
        return chunkOverlap;
    }

    public void setChunkOverlap(int chunkOverlap)
    {
        this.chunkOverlap = chunkOverlap;
    }

    public int getTopK()
    {
        return topK;
    }

    public void setTopK(int topK)
    {
        this.topK = topK;
    }

    public double getMinScore()
    {
        return minScore;
    }

    public void setMinScore(double minScore)
    {
        this.minScore = minScore;
    }

    public Embedding getEmbedding()
    {
        return embedding;
    }

    public void setEmbedding(Embedding embedding)
    {
        this.embedding = embedding;
    }

    public Milvus getMilvus()
    {
        return milvus;
    }

    public void setMilvus(Milvus milvus)
    {
        this.milvus = milvus;
    }

    public Retrieval getRetrieval()
    {
        return retrieval;
    }

    public void setRetrieval(Retrieval retrieval)
    {
        this.retrieval = retrieval;
    }

    public Rerank getRerank()
    {
        return rerank;
    }

    public void setRerank(Rerank rerank)
    {
        this.rerank = rerank;
    }

    /**
     * 向量化接口配置（OpenAI 兼容 /embeddings）。
     */
    public static class Embedding
    {
        private String baseUrl;

        private String apiKey;

        private String model = "text-embedding-v3";

        private Integer dimensions = 1024;

        private Integer batchSize = 10;

        private Integer timeoutSeconds = 60;

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

        public Integer getDimensions()
        {
            return dimensions;
        }

        public void setDimensions(Integer dimensions)
        {
            this.dimensions = dimensions;
        }

        public Integer getBatchSize()
        {
            return batchSize;
        }

        public void setBatchSize(Integer batchSize)
        {
            this.batchSize = batchSize;
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

    /**
     * Milvus 连接配置（RESTful v2）。
     */
    public static class Milvus
    {
        private String baseUrl = "http://127.0.0.1:19530";

        private String collection = "course_kb";

        /** 首次启动失败时是否自动回退到 local，避免影响主流程 */
        private boolean fallbackToLocal = true;

        public String getBaseUrl()
        {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl)
        {
            this.baseUrl = baseUrl;
        }

        public String getCollection()
        {
            return collection;
        }

        public void setCollection(String collection)
        {
            this.collection = collection;
        }

        public boolean isFallbackToLocal()
        {
            return fallbackToLocal;
        }

        public void setFallbackToLocal(boolean fallbackToLocal)
        {
            this.fallbackToLocal = fallbackToLocal;
        }
    }

    /** 允许外部批量设置语料类型（预留） */
    private List<String> defaultSourceTypes = new ArrayList<>();

    public List<String> getDefaultSourceTypes()
    {
        return defaultSourceTypes;
    }

    public void setDefaultSourceTypes(List<String> defaultSourceTypes)
    {
        this.defaultSourceTypes = defaultSourceTypes;
    }

    /**
     * 检索策略：向量召回 + 关键词召回双路并行，再用 RRF 融合。
     */
    public static class Retrieval
    {
        /** 是否开启关键词召回（关闭后退化为纯向量检索） */
        private boolean hybrid = true;

        /** 单路召回候选数（百炼建议 50~100，再由 rerank/topK 收敛） */
        private int candidateK = 50;

        /** RRF 平滑常数，论文默认 60 */
        private int rrfK = 60;

        /** 关键词召回的相似度下限（MySQL 全文相关度，0 表示不过滤） */
        private double keywordMinScore = 0D;

        /** 是否按 content_hash 去重（同一段内容被多个来源收录时只留一条） */
        private boolean dedupeByHash = true;

        public boolean isHybrid()
        {
            return hybrid;
        }

        public void setHybrid(boolean hybrid)
        {
            this.hybrid = hybrid;
        }

        public int getCandidateK()
        {
            return candidateK;
        }

        public void setCandidateK(int candidateK)
        {
            this.candidateK = candidateK;
        }

        public int getRrfK()
        {
            return rrfK;
        }

        public void setRrfK(int rrfK)
        {
            this.rrfK = rrfK;
        }

        public double getKeywordMinScore()
        {
            return keywordMinScore;
        }

        public void setKeywordMinScore(double keywordMinScore)
        {
            this.keywordMinScore = keywordMinScore;
        }

        public boolean isDedupeByHash()
        {
            return dedupeByHash;
        }

        public void setDedupeByHash(boolean dedupeByHash)
        {
            this.dedupeByHash = dedupeByHash;
        }
    }

    /**
     * 重排序配置：默认关闭，配置 api-key 后开启即可提升排序精度。
     */
    public static class Rerank
    {
        private boolean enabled = false;

        /** 兼容 DashScope 原生接口与百炼业务空间地址 */
        private String baseUrl = "https://dashscope.aliyuncs.com/api/v1/services/rerank/text-rerank/text-rerank";

        /** 为空时回退到 kb.embedding / langchain 的 api-key */
        private String apiKey;

        private String model = "qwen3-rerank";

        /** 参与重排的候选数上限（控制时延） */
        private int topN = 20;

        private int timeoutSeconds = 10;

        public boolean isEnabled()
        {
            return enabled;
        }

        public void setEnabled(boolean enabled)
        {
            this.enabled = enabled;
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

        public int getTopN()
        {
            return topN;
        }

        public void setTopN(int topN)
        {
            this.topN = topN;
        }

        public int getTimeoutSeconds()
        {
            return timeoutSeconds;
        }

        public void setTimeoutSeconds(int timeoutSeconds)
        {
            this.timeoutSeconds = timeoutSeconds;
        }
    }
}