package com.ruoyi.project.langchain;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

/**
 * LangChain 工作流服务实现：启动时加载并校验 JSON 清单，调用时执行 StateGraph。
 *
 * @author ruoyi
 */
@Service
public class LangChainWorkflowServiceImpl implements LangChainWorkflowService
{
    private static final Logger logger = LoggerFactory.getLogger(LangChainWorkflowServiceImpl.class);

    private final LangChainWorkflowLoader workflowLoader;

    private final LangChainWorkflowExecutor workflowExecutor;

    private final ChatModelClient chatModelClient;

    private final LangChainProperties properties;

    private final ResourceLoader resourceLoader;

    private final Map<String, LangChainWorkflow> workflows = new LinkedHashMap<>();

    public LangChainWorkflowServiceImpl(LangChainWorkflowLoader workflowLoader,
            LangChainWorkflowExecutor workflowExecutor, ChatModelClient chatModelClient,
            LangChainProperties properties, ResourceLoader resourceLoader)
    {
        this.workflowLoader = workflowLoader;
        this.workflowExecutor = workflowExecutor;
        this.chatModelClient = chatModelClient;
        this.properties = properties;
        this.resourceLoader = resourceLoader;
    }

    @PostConstruct
    public void init()
    {
        LangChainWorkflow defaultWorkflow = loadWorkflow(properties.getWorkflowLocation());
        workflows.put(DEFAULT_WORKFLOW_ID, defaultWorkflow);
        logger.info("LangChain 默认工作流加载成功: id={}, name={}, nodes={}, edges={}",
                DEFAULT_WORKFLOW_ID, defaultWorkflow.getFlow().getName(),
                defaultWorkflow.getGraph().getNodes().size(),
                defaultWorkflow.getGraph().getEdges().size());

        for (Map.Entry<String, String> entry : properties.getWorkflows().entrySet())
        {
            String workflowId = entry.getKey() == null ? "" : entry.getKey().trim();
            String location = entry.getValue();
            if (workflowId.isEmpty() || location == null || location.trim().isEmpty())
            {
                throw new LangChainWorkflowException("附加工作流配置不完整: " + entry);
            }
            if (DEFAULT_WORKFLOW_ID.equals(workflowId) || workflows.containsKey(workflowId))
            {
                throw new LangChainWorkflowException("工作流标识重复: " + workflowId);
            }
            LangChainWorkflow workflow = loadWorkflow(location.trim());
            workflows.put(workflowId, workflow);
            logger.info("LangChain 工作流加载成功: id={}, name={}, nodes={}, edges={}",
                    workflowId, workflow.getFlow().getName(),
                    workflow.getGraph().getNodes().size(),
                    workflow.getGraph().getEdges().size());
        }
    }

    @Override
    public LangChainWorkflow getWorkflow()
    {
        return getWorkflow(DEFAULT_WORKFLOW_ID);
    }

    @Override
    public LangChainWorkflow getWorkflow(String workflowId)
    {
        LangChainWorkflow workflow = workflows.get(normalizeId(workflowId));
        if (workflow == null)
        {
            throw new LangChainWorkflowException("未知工作流: " + workflowId);
        }
        return workflow;
    }

    @Override
    public Set<String> listWorkflowIds()
    {
        return Collections.unmodifiableSet(workflows.keySet());
    }

    @Override
    public LangChainWorkflowResult invoke(String input)
    {
        return invoke(DEFAULT_WORKFLOW_ID, input);
    }

    @Override
    public LangChainWorkflowResult invoke(String workflowId, String input)
    {
        return invoke(workflowId, input, null);
    }

    @Override
    public LangChainWorkflowResult invoke(String workflowId, String input, Map<String, Object> extraState)
    {
        if (input == null || input.trim().isEmpty())
        {
            throw new LangChainWorkflowException("输入不能为空");
        }
        return workflowExecutor.execute(getWorkflow(workflowId), chatModelClient, input, extraState);
    }

    private String normalizeId(String workflowId)
    {
        return workflowId == null || workflowId.trim().isEmpty()
                ? DEFAULT_WORKFLOW_ID : workflowId.trim();
    }

    private LangChainWorkflow loadWorkflow(String location)
    {
        Resource resource = resourceLoader.getResource(location);
        if (!resource.exists())
        {
            throw new LangChainWorkflowException("LangChain 工作流文件不存在: " + location);
        }
        try (InputStream inputStream = resource.getInputStream())
        {
            return workflowLoader.load(inputStream);
        }
        catch (IOException e)
        {
            throw new LangChainWorkflowException("LangChain 工作流文件读取失败: " + location, e);
        }
    }
}
