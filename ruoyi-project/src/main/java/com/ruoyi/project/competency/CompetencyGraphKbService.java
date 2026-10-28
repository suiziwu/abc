package com.ruoyi.project.competency;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.project.langchain.LangChainWorkflowException;

/**
 * KB 模式图谱服务：读取 Python 预生成的 v3 图谱，不在 Java 侧重写规则引擎。
 *
 * @author ruoyi
 */
@Service
public class CompetencyGraphKbService
{
    private static final String INDEX_LOCATION = "classpath:langchain/competency-graphs/index.json";

    private static final String BASE_LOCATION = "classpath:langchain/competency-graphs/";

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ResourceLoader resourceLoader;

    private final Map<String, Map<String, Object>> cache = new ConcurrentHashMap<>();

    private List<Map<String, Object>> jobs = new ArrayList<>();

    private String kbVersion = "";

    @PostConstruct
    public void init()
    {
        try (InputStream inputStream = resourceLoader.getResource(INDEX_LOCATION).getInputStream())
        {
            Map<String, Object> index = objectMapper.readValue(inputStream,
                    new TypeReference<Map<String, Object>>() {});
            jobs = asList(index.get("jobs"));
            kbVersion = text(index.get("kb_version"), "");
        }
        catch (IOException e)
        {
            throw new LangChainWorkflowException("KB 图谱索引加载失败: " + INDEX_LOCATION, e);
        }
    }

    /**
     * 是否收录该岗位。
     */
    public boolean supports(String jobName)
    {
        return resolve(jobName) != null;
    }

    /**
     * 读取 v3 图谱；未收录返回 null。
     */
    public Map<String, Object> getGraph(String jobName)
    {
        Map<String, Object> entry = resolve(jobName);
        if (entry == null)
        {
            return null;
        }
        String file = text(entry.get("file"), "");
        if (file.isEmpty())
        {
            return null;
        }
        try
        {
            return cache.computeIfAbsent(file, key -> load(file));
        }
        catch (RuntimeException e)
        {
            throw e;
        }
    }

    public List<Map<String, Object>> listJobs()
    {
        return jobs;
    }

    public String getKbVersion()
    {
        return kbVersion;
    }

    private Map<String, Object> load(String file)
    {
        Resource resource = resourceLoader.getResource(BASE_LOCATION + file);
        if (!resource.exists())
        {
            throw new LangChainWorkflowException("KB 图谱文件不存在: " + BASE_LOCATION + file);
        }
        try (InputStream inputStream = resource.getInputStream())
        {
            Map<String, Object> graph = objectMapper.readValue(inputStream,
                    new TypeReference<Map<String, Object>>() {});
            Map<String, Object> meta = asMap(graph.get("meta"));
            meta.put("mode", "kb");
            meta.put("kb_version", kbVersion);
            return graph;
        }
        catch (IOException e)
        {
            throw new LangChainWorkflowException("KB 图谱文件读取失败: " + file, e);
        }
    }

    private Map<String, Object> resolve(String query)
    {
        if (query == null || query.trim().isEmpty())
        {
            return null;
        }
        String target = query.trim();
        for (Map<String, Object> entry : jobs)
        {
            if (target.equals(text(entry.get("name"), "")))
            {
                return entry;
            }
        }
        for (Map<String, Object> entry : jobs)
        {
            for (Object alias : asList(entry.get("aliases")))
            {
                if (target.equals(text(alias, "")))
                {
                    return entry;
                }
            }
        }
        String lower = target.toLowerCase();
        for (Map<String, Object> entry : jobs)
        {
            String name = text(entry.get("name"), "");
            if (!lower.isEmpty() && (name.toLowerCase().contains(lower) || lower.contains(name.toLowerCase())))
            {
                return entry;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value)
    {
        return value instanceof Map ? (Map<String, Object>) value : new LinkedHashMap<>();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> asList(Object value)
    {
        return value instanceof List ? (List<Map<String, Object>>) value : new ArrayList<>();
    }

    private static String text(Object value, String fallback)
    {
        return value == null || String.valueOf(value).trim().isEmpty() ? fallback : String.valueOf(value);
    }
}
