package com.ruoyi.project.course.service;

import java.util.ArrayList;
import java.util.List;

/**
 * 知识库问答结果：回答正文 + 引用来源 + 使用的生成策略。
 *
 * @author ruoyi
 */
public class CourseAnswer
{
    /** 回答正文 */
    private String answer;

    /** 实际使用的模型 */
    private String model;

    /** 引用来源 */
    private List<Source> sources = new ArrayList<>();

    /** 生成方式：rag-知识库检索 / context-前端上下文兜底 / none-无上下文 */
    private String strategy;

    /** 知识库命中的最高相似度（无命中为 null） */
    private Double topScore;

    public String getAnswer()
    {
        return answer;
    }

    public void setAnswer(String answer)
    {
        this.answer = answer;
    }

    public String getModel()
    {
        return model;
    }

    public void setModel(String model)
    {
        this.model = model;
    }

    public List<Source> getSources()
    {
        return sources;
    }

    public void setSources(List<Source> sources)
    {
        this.sources = sources;
    }

    public String getStrategy()
    {
        return strategy;
    }

    public void setStrategy(String strategy)
    {
        this.strategy = strategy;
    }

    public Double getTopScore()
    {
        return topScore;
    }

    public void setTopScore(Double topScore)
    {
        this.topScore = topScore;
    }

    /**
     * 引用来源（章节 / 知识点 / 视频时间戳）。
     */
    public static class Source
    {
        private Long chunkId;

        private String docId;

        private String sourceType;

        private String title;

        private Long subjectId;

        private Long chapterId;

        private Long pointId;

        private Integer startTime;

        private Double score;

        /** 引用片段（截断后用于前端展示） */
        private String snippet;

        public Long getChunkId()
        {
            return chunkId;
        }

        public void setChunkId(Long chunkId)
        {
            this.chunkId = chunkId;
        }

        public String getDocId()
        {
            return docId;
        }

        public void setDocId(String docId)
        {
            this.docId = docId;
        }

        public String getSourceType()
        {
            return sourceType;
        }

        public void setSourceType(String sourceType)
        {
            this.sourceType = sourceType;
        }

        public String getTitle()
        {
            return title;
        }

        public void setTitle(String title)
        {
            this.title = title;
        }

        public Long getSubjectId()
        {
            return subjectId;
        }

        public void setSubjectId(Long subjectId)
        {
            this.subjectId = subjectId;
        }

        public Long getChapterId()
        {
            return chapterId;
        }

        public void setChapterId(Long chapterId)
        {
            this.chapterId = chapterId;
        }

        public Long getPointId()
        {
            return pointId;
        }

        public void setPointId(Long pointId)
        {
            this.pointId = pointId;
        }

        public Integer getStartTime()
        {
            return startTime;
        }

        public void setStartTime(Integer startTime)
        {
            this.startTime = startTime;
        }

        public Double getScore()
        {
            return score;
        }

        public void setScore(Double score)
        {
            this.score = score;
        }

        public String getSnippet()
        {
            return snippet;
        }

        public void setSnippet(String snippet)
        {
            this.snippet = snippet;
        }
    }
}