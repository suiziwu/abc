package com.ruoyi.project.kb;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.ruoyi.project.kb.store.LocalKbVectorStore;
import com.ruoyi.project.kb.store.MilvusKbVectorStore;

/**
 * 向量库工厂：按 {@code kb.provider} 选择实现，Milvus 不可用时按配置回退 local。
 *
 * @author ruoyi
 */
@Component
public class KbVectorStoreFactory
{
    private static final Logger log = LoggerFactory.getLogger(KbVectorStoreFactory.class);

    /** 可用性探测的最小间隔：Milvus 不可达时单次探测要等 3s 连接超时，不能每次请求都探 */
    private static final long PROBE_INTERVAL_MS = 30_000L;

    private final KbProperties kbProperties;

    private final Map<String, KbVectorStore> stores = new HashMap<>();

    private final Map<String, Boolean> availabilityCache = new HashMap<>();

    private volatile long lastAvailabilityCheck = 0L;

    private volatile KbVectorStore active;

    public KbVectorStoreFactory(KbProperties kbProperties, LocalKbVectorStore localStore,
            MilvusKbVectorStore milvusStore)
    {
        this.kbProperties = kbProperties;
        stores.put(localStore.provider(), localStore);
        stores.put(milvusStore.provider(), milvusStore);
    }

    /**
     * 当前生效的向量库实现。
     *
     * <p>若缓存的是回退实现、而配置的实现现在已可用（例如用户刚启动 Milvus），
     * 会自动重新探测并切回配置的实现。</p>
     */
    public KbVectorStore active()
    {
        if (active == null)
        {
            active = resolve();
            return active;
        }
        String configured = kbProperties.getProvider() == null ? "local" : kbProperties.getProvider().trim();
        if (!active.provider().equals(configured))
        {
            KbVectorStore configuredStore = stores.get(configured);
            if (configuredStore != null && cachedAvailable(configuredStore))
            {
                log.info("知识库实现 {} 已恢复，重新切换", configured);
                active = configuredStore;
            }
        }
        return active;
    }

    /**
     * 带缓存的可用性探测，避免每次请求都触发一次连接超时。
     */
    private boolean cachedAvailable(KbVectorStore store)
    {
        long now = System.currentTimeMillis();
        if (now - lastAvailabilityCheck > PROBE_INTERVAL_MS || !availabilityCache.containsKey(store.provider()))
        {
            synchronized (this)
            {
                if (now - lastAvailabilityCheck > PROBE_INTERVAL_MS
                        || !availabilityCache.containsKey(store.provider()))
                {
                    availabilityCache.put(store.provider(), store.available());
                    lastAvailabilityCheck = now;
                }
            }
        }
        return Boolean.TRUE.equals(availabilityCache.get(store.provider()));
    }

    /**
     * 强制重新探测（配置变更或 Milvus 恢复后调用）。
     */
    public KbVectorStore refresh()
    {
        lastAvailabilityCheck = 0L;
        availabilityCache.clear();
        active = resolve();
        return active;
    }

    /**
     * 当前实现名 + 是否可用，用于前端展示。
     */
    public Map<String, Object> describe()
    {
        KbVectorStore store = active();
        Map<String, Object> info = new java.util.LinkedHashMap<>();
        String configured = kbProperties.getProvider() == null ? "local" : kbProperties.getProvider().trim();
        info.put("configuredProvider", configured);
        info.put("activeProvider", store.provider());
        boolean storeAvailable = cachedAvailable(store);
        info.put("available", storeAvailable);
        info.put("reason", storeAvailable ? null : store.unavailableReason());
        // 配置的实现不可用时的具体原因（便于排查，如 Milvus 未启动/建表失败）
        KbVectorStore configuredStore = stores.get(configured);
        if (configuredStore != null && !configuredStore.provider().equals(store.provider()))
        {
            boolean configuredAvailable = cachedAvailable(configuredStore);
            info.put("configuredAvailable", configuredAvailable);
            info.put("configuredReason", configuredAvailable ? null : configuredStore.unavailableReason());
        }
        List<String> providers = new java.util.ArrayList<>(stores.keySet());
        java.util.Collections.sort(providers);
        info.put("providers", providers);
        return info;
    }

    private KbVectorStore resolve()
    {
        String configured = kbProperties.getProvider() == null ? "local" : kbProperties.getProvider().trim();
        KbVectorStore store = stores.get(configured);
        if (store == null)
        {
            log.warn("未知的知识库实现 kb.provider={}，回退 local", configured);
            return stores.get("local");
        }
        if ("local".equals(store.provider()))
        {
            return store;
        }
        if (store.available())
        {
            log.info("知识库向量库已启用: {}", store.provider());
            return store;
        }
        if (kbProperties.getMilvus().isFallbackToLocal())
        {
            log.warn("知识库实现 {} 不可用（{}），已回退 local", store.provider(), store.unavailableReason());
            return stores.get("local");
        }
        return store;
    }
}