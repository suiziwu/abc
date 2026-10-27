package com.ruoyi.project.langchain;

import java.io.IOException;
import java.io.InputStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * LangChain 工作流加载与校验测试。
 *
 * @author ruoyi
 */
public class LangChainWorkflowLoaderTest
{
    private static final String WORKFLOW =
            "langchain/rencai-huaxiang-20260914150500.langgraph.json";

    private LangChainWorkflowLoader loader;

    @BeforeEach
    public void setUp()
    {
        loader = new LangChainWorkflowLoader();
    }

    @Test
    public void testLoadAndValidate()
    {
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(WORKFLOW))
        {
            assertNotNull(inputStream, "工作流资源不存在: " + WORKFLOW);
            LangChainWorkflow workflow = loader.load(inputStream);

            assertEquals("langchain/langgraph", workflow.getFramework());
            assertEquals("langgraph-state-graph", workflow.getFormat());
            assertEquals("人才画像20260914150500", workflow.getFlow().getName());
            assertEquals(7, workflow.getGraph().getNodes().size());
            assertEquals(9, workflow.getGraph().getEdges().size());
            assertEquals(1, workflow.getGraph().getConditionalEdges().size());

            int llmCount = 0;
            for (LangChainWorkflow.Node node : workflow.getGraph().getNodes())
            {
                if ("llm".equals(node.getType()))
                {
                    llmCount++;
                }
            }
            assertEquals(3, llmCount);

            assertEquals("start", workflow.getExecution().getEntrypoint());
            assertEquals("end", workflow.getExecution().getFinishPoint());
            assertEquals("{{output}}{{output2}}{{output3}}",
                    workflow.getExecution().getOutputTemplate());
            assertTrue(workflow.getExtensions().containsKey("source"));
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }
}