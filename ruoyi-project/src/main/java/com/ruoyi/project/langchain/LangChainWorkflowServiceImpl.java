package com.ruoyi.project.langchain;

import java.io.IOException;
import java.io.InputStream;

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

    private volatile LangChainWorkflow workflow;

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
        this.workflow = loadWorkflow();
        logger.info("LangChain 工作流加载成功: name={}, nodes={}, edges={}",
                workflow.getFlow().getName(),
                workflow.getGraph().getNodes().size(),
                workflow.getGraph().getEdges().size());
    }

    @Override
    public LangChainWorkflow getWorkflow()
    {
        return workflow;
    }

    @Override
    public LangChainWorkflowResult invoke(String input)
    {
        if (input == null || input.trim().isEmpty())
        {
            throw new LangChainWorkflowException("输入不能为空");
        }
        return workflowExecutor.execute(workflow, chatModelClient, input);
    }

    private LangChainWorkflow loadWorkflow()
    {
        String location = properties.getWorkflowLocation();
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
