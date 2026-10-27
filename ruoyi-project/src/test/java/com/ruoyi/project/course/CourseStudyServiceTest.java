package com.ruoyi.project.course;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.ruoyi.project.course.domain.CourseQuestion;
import com.ruoyi.project.course.domain.CourseQuizItem;
import com.ruoyi.project.course.mapper.CourseQuestionMapper;
import com.ruoyi.project.course.service.CourseStudyServiceImpl;
import com.ruoyi.project.langchain.ChatModelClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 课程题库解析与 AI 助教服务测试。
 *
 * @author ruoyi
 */
public class CourseStudyServiceTest
{
    @Test
    public void testQuizParsing()
    {
        CourseStudyServiceImpl service = new CourseStudyServiceImpl(stubMapper(), stubChat());
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

    @Test
    public void testChat()
    {
        CourseStudyServiceImpl service = new CourseStudyServiceImpl(stubMapper(), stubChat());
        assertEquals("stub-answer", service.chat("集合框架怎么选？", "[00:30] 集合框架"));
    }

    private CourseQuestionMapper stubMapper()
    {
        return testId -> buildQuestions();
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
