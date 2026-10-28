package com.ruoyi.project.langchain;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 岗位能力图谱工作流测试（使用测试桩 ChatModelClient，不依赖真实 API）。
 *
 * <p>校验点：资源清单可加载、五段线性链路可执行、状态在节点间正确传递、
 * 结束节点输出装配节点产出的图谱 JSON。</p>
 *
 * @author ruoyi
 */
public class CompetencyGraphWorkflowTest
{
    private static final String WORKFLOW = "langchain/gangwei-nengli-tupu-20261027.langgraph.json";

    private static final String GRAPH_JSON =
            "{\"job\":{\"name\":\"计算机程序设计员\"},\"tasks\":[],\"competencies\":[]}";

    private LangChainWorkflow workflow;

    private LangChainWorkflowExecutor executor;

    @BeforeEach
    public void setUp() throws IOException
    {
        LangChainWorkflowLoader loader = new LangChainWorkflowLoader();
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(WORKFLOW))
        {
            workflow = loader.load(inputStream);
        }
        executor = new LangChainWorkflowExecutor();
    }

    private ChatModelClient stubClient(List<String> userPrompts)
    {
        return (systemPrompt, userPrompt) ->
        {
            userPrompts.add(userPrompt);
            if (systemPrompt.contains("产业链与岗位群分析师"))
            {
                return "{\"job\":{\"name\":\"计算机程序设计员\"}}";
            }
            if (systemPrompt.contains("典型工作任务拆解专家"))
            {
                return "{\"tasks\":[]}";
            }
            if (systemPrompt.contains("岗位能力结构拆解专家"))
            {
                return "{\"competencies\":[]}";
            }
            if (systemPrompt.contains("知识点与技能点映射专家"))
            {
                return "{\"elements\":{\"competencies\":[]}}";
            }
            if (systemPrompt.contains("岗位能力图谱装配专家"))
            {
                return GRAPH_JSON;
            }
            throw new AssertionError("未知 system prompt: " + systemPrompt);
        };
    }

    @Test
    public void testLinearChain()
    {
        List<String> userPrompts = new ArrayList<>();
        LangChainWorkflowResult result =
                executor.execute(workflow, stubClient(userPrompts), "计算机程序设计员");

        assertEquals(GRAPH_JSON, result.getOutput());
        assertEquals(7, result.getSteps());
        assertEquals(Arrays.asList("start", "industry_chain_mapper", "task_analyzer",
                "competency_decomposer", "element_mapper", "graph_assembler", "end"),
                result.getPath());
        assertEquals(GRAPH_JSON, result.getState().get("competency_graph"));
        assertEquals(5, result.getModelOutputs().size());
        assertEquals(5, userPrompts.size());
    }

    @Test
    public void testStatePassedBetweenNodes()
    {
        List<String> userPrompts = new ArrayList<>();
        executor.execute(workflow, stubClient(userPrompts), "计算机程序设计员");

        // 第二个节点的用户模板应带上岗位名与第一步产出的岗位定位
        assertTrue(userPrompts.get(0).contains("计算机程序设计员"));
        assertTrue(userPrompts.get(1).contains("计算机程序设计员"));
        assertTrue(userPrompts.get(1).contains("\"name\":\"计算机程序设计员\""));
        // 装配节点应拿到前四步的全部产出
        assertTrue(userPrompts.get(4).contains("{\"tasks\":[]}"));
        assertTrue(userPrompts.get(4).contains("{\"elements\":{\"competencies\":[]}}"));
    }
}
