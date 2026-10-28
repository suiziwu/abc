package com.ruoyi.project.competency;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.project.langchain.LangChainWorkflowException;

/**
 * 岗位候选目录：大典职业 + 国家职业标准职业名，用于输入联想。
 *
 * @author ruoyi
 */
@Service
public class JobCatalogService
{
    private static final String LOCATION = "classpath:langchain/job-catalog.json";

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ResourceLoader resourceLoader;

    private List<Map<String, Object>> jobs = new ArrayList<>();

    @PostConstruct
    public void init()
    {
        try (InputStream inputStream = resourceLoader.getResource(LOCATION).getInputStream())
        {
            Map<String, Object> data = objectMapper.readValue(inputStream,
                    new TypeReference<Map<String, Object>>() {});
            List<?> items = data.get("jobs") instanceof List ? (List<?>) data.get("jobs") : Collections.emptyList();
            for (Object item : items)
            {
                if (item instanceof Map)
                {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> job = (Map<String, Object>) item;
                    jobs.add(job);
                }
            }
        }
        catch (IOException e)
        {
            throw new LangChainWorkflowException("岗位目录加载失败: " + LOCATION, e);
        }
    }

    /**
     * 按关键词做模糊匹配：完全匹配、前缀、包含、按序字符匹配。
     */
    public List<Map<String, Object>> suggest(String keyword, int limit)
    {
        String query = keyword == null ? "" : keyword.trim();
        if (query.isEmpty())
        {
            return Collections.emptyList();
        }
        List<ScoredJob> scored = new ArrayList<>();
        for (Map<String, Object> job : jobs)
        {
            String name = text(job.get("name"));
            if (name.isEmpty())
            {
                continue;
            }
            int score = score(name, query);
            if (score > 0)
            {
                scored.add(new ScoredJob(job, score, name.length()));
            }
        }
        scored.sort((left, right) -> {
            if (left.score != right.score)
            {
                return right.score - left.score;
            }
            return left.length - right.length;
        });
        List<Map<String, Object>> result = new ArrayList<>();
        for (ScoredJob item : scored)
        {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("name", text(item.job.get("name")));
            entry.put("code", text(item.job.get("code")));
            entry.put("source", "catalog");
            result.add(entry);
            if (result.size() >= limit)
            {
                break;
            }
        }
        return result;
    }

    private int score(String name, String query)
    {
        if (name.equals(query))
        {
            return 100;
        }
        if (name.startsWith(query))
        {
            return 90;
        }
        if (name.contains(query))
        {
            return 80;
        }
        int index = 0;
        for (int i = 0; i < name.length() && index < query.length(); i++)
        {
            if (name.charAt(i) == query.charAt(index))
            {
                index++;
            }
        }
        if (index == query.length())
        {
            return 40;
        }
        return 0;
    }

    private String text(Object value)
    {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static class ScoredJob
    {
        private final Map<String, Object> job;

        private final int score;

        private final int length;

        ScoredJob(Map<String, Object> job, int score, int length)
        {
            this.job = job;
            this.score = score;
            this.length = length;
        }
    }
}
