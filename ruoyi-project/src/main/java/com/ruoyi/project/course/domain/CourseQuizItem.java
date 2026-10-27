package com.ruoyi.project.course.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * 前端随堂练习题目。
 *
 * @author ruoyi
 */
public class CourseQuizItem
{
    private Long id;
    private String type;
    private String question;
    private List<QuizOption> options = new ArrayList<>();
    private List<String> answer = new ArrayList<>();
    private String analysis;
    private Integer difficulty;
    private boolean multiple;

    public Long getId()
    {
        return id;
    }

    public void setId(Long id)
    {
        this.id = id;
    }

    public String getType()
    {
        return type;
    }

    public void setType(String type)
    {
        this.type = type;
    }

    public String getQuestion()
    {
        return question;
    }

    public void setQuestion(String question)
    {
        this.question = question;
    }

    public List<QuizOption> getOptions()
    {
        return options;
    }

    public void setOptions(List<QuizOption> options)
    {
        this.options = options;
    }

    public List<String> getAnswer()
    {
        return answer;
    }

    public void setAnswer(List<String> answer)
    {
        this.answer = answer;
    }

    public String getAnalysis()
    {
        return analysis;
    }

    public void setAnalysis(String analysis)
    {
        this.analysis = analysis;
    }

    public Integer getDifficulty()
    {
        return difficulty;
    }

    public void setDifficulty(Integer difficulty)
    {
        this.difficulty = difficulty;
    }

    public boolean isMultiple()
    {
        return multiple;
    }

    public void setMultiple(boolean multiple)
    {
        this.multiple = multiple;
    }

    public static class QuizOption
    {
        private String label;
        private String text;

        public QuizOption()
        {
        }

        public QuizOption(String label, String text)
        {
            this.label = label;
            this.text = text;
        }

        public String getLabel()
        {
            return label;
        }

        public void setLabel(String label)
        {
            this.label = label;
        }

        public String getText()
        {
            return text;
        }

        public void setText(String text)
        {
            this.text = text;
        }
    }
}
