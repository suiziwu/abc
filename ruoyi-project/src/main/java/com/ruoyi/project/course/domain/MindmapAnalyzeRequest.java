package com.ruoyi.project.course.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * 视频知识结构化梳理请求。
 *
 * @author ruoyi
 */
public class MindmapAnalyzeRequest
{
    /** 学科ID */
    private Long subjectId;

    /** 章号（用于同步时绑定章节） */
    private String chapterNo;

    /** 模型标识（可为空，使用默认模型） */
    private String model;

    /** 视频章节与逐字稿 */
    private List<Chapter> chapters = new ArrayList<Chapter>();

    public Long getSubjectId()
    {
        return subjectId;
    }

    public void setSubjectId(Long subjectId)
    {
        this.subjectId = subjectId;
    }

    public String getChapterNo()
    {
        return chapterNo;
    }

    public void setChapterNo(String chapterNo)
    {
        this.chapterNo = chapterNo;
    }

    public String getModel()
    {
        return model;
    }

    public void setModel(String model)
    {
        this.model = model;
    }

    public List<Chapter> getChapters()
    {
        return chapters;
    }

    public void setChapters(List<Chapter> chapters)
    {
        this.chapters = chapters == null ? new ArrayList<Chapter>() : chapters;
    }

    /**
     * 一个视频章节。
     */
    public static class Chapter
    {
        private String title;

        private List<Segment> segments = new ArrayList<Segment>();

        public String getTitle()
        {
            return title;
        }

        public void setTitle(String title)
        {
            this.title = title;
        }

        public List<Segment> getSegments()
        {
            return segments;
        }

        public void setSegments(List<Segment> segments)
        {
            this.segments = segments == null ? new ArrayList<Segment>() : segments;
        }
    }

    /**
     * 一句话级字幕片段。
     */
    public static class Segment
    {
        /** 起始秒数 */
        private Integer start;

        private String text;

        public Integer getStart()
        {
            return start;
        }

        public void setStart(Integer start)
        {
            this.start = start;
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
