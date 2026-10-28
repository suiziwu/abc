package com.ruoyi.project.langchain;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * LangChain 工作流执行器测试（使用测试桩 ChatModelClient，不依赖真实 API）。
 *
 * @author ruoyi
 */
public class LangChainWorkflowExecutorTest
{
    private static final String WORKFLOW =
            "langchain/rencai-huaxiang-20260914150500.langgraph.json";

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

    private ChatModelClient stubClient()
    {
        return (systemPrompt, userPrompt) ->
        {
            if (systemPrompt.contains("课程架构师"))
            {
                return "SUBJECT_SELECTOR_RESULT";
            }
            if (systemPrompt.contains("技术面试出题专家"))
            {
                return "QUESTION_GENERATOR_RESULT";
            }
            if (systemPrompt.contains("个性化学习路径规划专家")
                    || systemPrompt.contains("错题知识点诊断专家"))
            {
                return "WRONG_ANSWER_DIAGNOSER_RESULT";
            }
            throw new AssertionError("未知 system prompt: " + systemPrompt);
        };
    }

    @Test
    public void testSubjectSelectorBranch()
    {
        LangChainWorkflowResult result =
                executor.execute(workflow, stubClient(), "[0]后端开发工程师");
        assertEquals("SUBJECT_SELECTOR_RESULT", result.getOutput());
        assertTrue(result.getPath().contains("subject_selector"));
        assertTrue(result.getModelOutputs().containsKey("output"));
    }

    @Test
    public void testQuestionGeneratorBranch()
    {
        LangChainWorkflowResult result =
                executor.execute(workflow, stubClient(), "[1]Java开发工程师");
        assertEquals("QUESTION_GENERATOR_RESULT", result.getOutput());
        assertTrue(result.getPath().contains("question_generator"));
    }

    @Test
    public void testWrongAnswerDiagnoserBranch()
    {
        LangChainWorkflowResult result =
                executor.execute(workflow, stubClient(), "[2]错题诊断");
        assertEquals("WRONG_ANSWER_DIAGNOSER_RESULT", result.getOutput());
        assertTrue(result.getPath().contains("wrong_answer_diagnoser"));
    }

    @Test
    public void testDefaultBranch()
    {
        LangChainWorkflowResult result = executor.execute(workflow, stubClient(), "无前缀");
        assertEquals("", result.getOutput());
        List<String> path = result.getPath();
        assertTrue(path.contains("route"));
        assertTrue(path.contains("end"));
    }
}