package com.ruoyi.project.course;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.ruoyi.project.course.domain.CourseQuestion;
import com.ruoyi.project.course.domain.CourseQuizItem;
import com.ruoyi.project.course.mapper.CourseQuestionMapper;
import com.ruoyi.project.course.service.CourseStudyServiceImpl;
import com.ruoyi.project.kb.EmbeddingClient;
import com.ruoyi.project.kb.KbIngestService;
import com.ruoyi.project.kb.KbProperties;
import com.ruoyi.project.kb.KbVectorStoreFactory;
import com.ruoyi.project.kb.store.LocalKbVectorStore;
import com.ruoyi.project.kb.store.MilvusKbVectorStore;
import com.ruoyi.project.langchain.ChatModelClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 课程题库解析、AI 助教服务与知识库检索测试。
 *
 * @author ruoyi
 */
public class CourseStudyServiceTest
{
    @Test
    public void testQuizParsing()
    {
        CourseStudyServiceImpl service = newService();
        List<CourseQuizItem> quiz = service.getQuiz(101L, 10);
        assertEquals(3, quiz.size());

        CourseQuizItem first = quiz.get(0);
        assertEquals("Java 程序的入口方法是哪个？", first.getQuestion());
        assertEquals(4, first.getOptions().size());
        assertEquals("B", first.getOptions().get(1).getLabel());
        assertEquals("main()", first.getOptions().get(1).getText());
        assertEquals(Arrays.asList("B"), first.getAnswer());
        assertFalse(first.isMultiple());

        CourseQuizItem second = quiz.get(1);
        assertEquals(Arrays.asList("B", "C"), second.getAnswer());
        assertTrue(second.isMultiple());

        CourseQuizItem third = quiz.get(2);
        assertEquals("A", third.getOptions().get(0).getLabel());
        assertEquals("正确", third.getOptions().get(0).getText());
        assertEquals(Arrays.asList("B"), third.getAnswer());
    }

    /**
     * 知识库关闭（或未配置）时应回退到前端传入的课程文本，行为与改造前一致。
     */
    @Test
    public void testChatFallsBackToContext()
    {
        CourseStudyServiceImpl service = newService();
        assertEquals("stub-answer", service.chat("集合框架怎么选？", "[00:30] 集合框架"));
    }

    /**
     * L2 归一化：归一化后模长为 1，点积即余弦相似度。
     */
    @Test
    public void testEmbeddingNormalize()
    {
        float[] vector = EmbeddingClient.normalize(new float[] { 3F, 4F });
        assertEquals(0.6D, vector[0], 1e-6);
        assertEquals(0.8D, vector[1], 1e-6);
        assertEquals(1.0D, Math.hypot(vector[0], vector[1]), 1e-6);
    }

    private CourseStudyServiceImpl newService()
    {
        return new CourseStudyServiceImpl(stubMapper(), stubChat(), stubStoreFactory(),
                stubIngestService(), stubProperties());
    }

    /** 知识库关闭的配置：确保问答走 context 兜底 */
    private KbProperties stubProperties()
    {
        KbProperties properties = new KbProperties();
        properties.setEnabled(false);
        return properties;
    }

    private KbVectorStoreFactory stubStoreFactory()
    {
        KbProperties properties = stubProperties();
        LocalKbVectorStore local = new LocalKbVectorStore(null, null);
        MilvusKbVectorStore milvus = new MilvusKbVectorStore(properties, null, null);
        return new KbVectorStoreFactory(properties, local, milvus);
    }

    private KbIngestService stubIngestService()
    {
        return new KbIngestService(stubProperties(), null, stubStoreFactory(), null, null, null, null, null, null);
    }

    private CourseQuestionMapper stubMapper()
    {
        return new CourseQuestionMapper()
        {
            @Override
            public List<CourseQuestion> selectByTestId(Long testId)
            {
                return buildQuestions();
            }

            @Override
            public List<CourseQuestion> selectByTestIdLimit(Long testId, int limit)
            {
                List<CourseQuestion> all = buildQuestions();
                return all.size() <= limit ? all : new ArrayList<>(all.subList(0, limit));
            }

            @Override
            public Long selectNextTestId()
            {
                return 1001L;
            }

            @Override
            public int selectMaxQuestionNumber(Long testId)
            {
                return buildQuestions().size();
            }

            @Override
            public int insertQuestion(CourseQuestion question)
            {
                return 1;
            }
        };
    }

    private ChatModelClient stubChat()
    {
        return (system, user) -> "stub-answer";
    }

    private List<CourseQuestion> buildQuestions()
    {
        List<CourseQuestion> questions = new ArrayList<>();

        CourseQuestion first = new CourseQuestion();
        first.setId(1L);
        first.setQuestionType("choice");
        first.setContent("Java 程序的入口方法是哪个？\nA. start()\nB. main()\nC. run()\nD. init()");
        first.setAnswer("B");
        first.setAnalysis("Java 程序入口是 main 方法");
        questions.add(first);

        CourseQuestion second = new CourseQuestion();
        second.setId(2L);
        second.setQuestionType("choice");
        second.setContent("以下哪些属于 Java 集合框架？\nA. ArrayList\nB. List\nC. Map\nD. Socket");
        second.setAnswer("BC");
        second.setAnalysis("List 与 Map 是集合框架接口");
        questions.add(second);

        CourseQuestion third = new CourseQuestion();
        third.setId(3L);
        third.setQuestionType("judge");
        third.setContent("Java 支持多继承。");
        third.setAnswer("false");
        third.setAnalysis("Java 类只支持单继承，接口可多实现");
        questions.add(third);

        return questions;
    }
}
