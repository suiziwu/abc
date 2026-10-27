package com.ruoyi.project.course.domain;

/**
 * 题库题目（question 表）。
 *
 * @author ruoyi
 */
public class CourseQuestion
{
    private Long id;
    private Long testId;
    private Integer questionNumber;
    private String questionType;
    private String content;
    private String options;
    private String answer;
    private String analysis;
    private Integer difficulty;

    public Long getId()
    {
        return id;
    }

    public void setId(Long id)
    {
        this.id = id;
    }

    public Long getTestId()
    {
        return testId;
    }

    public void setTestId(Long testId)
    {
        this.testId = testId;
    }

    public Integer getQuestionNumber()
    {
        return questionNumber;
    }

    public void setQuestionNumber(Integer questionNumber)
    {
        this.questionNumber = questionNumber;
    }

    public String getQuestionType()
    {
        return questionType;
    }

    public void setQuestionType(String questionType)
    {
        this.questionType = questionType;
    }

    public String getContent()
    {
        return content;
    }

    public void setContent(String content)
    {
        this.content = content;
    }

    public String getOptions()
    {
        return options;
    }

    public void setOptions(String options)
    {
        this.options = options;
    }

    public String getAnswer()
    {
        return answer;
    }

    public void setAnswer(String answer)
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
}
