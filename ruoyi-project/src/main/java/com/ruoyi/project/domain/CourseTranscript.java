package com.ruoyi.project.domain;

/**
 * 课程视频逐字稿分句（course_transcript）。
 *
 * @author ruoyi
 */
public class CourseTranscript
{
    private Long id;

    private Long chapterId;

    /** 所属课时ID（一章多课时后，逐字稿归属到课时） */
    private Long lessonId;

    private Integer seq;

    private Integer startMs;

    private Integer endMs;

    private String section;

    private String text;

    public Long getId()
    {
        return id;
    }

    public void setId(Long id)
    {
        this.id = id;
    }

    public Long getChapterId()
    {
        return chapterId;
    }

    public void setChapterId(Long chapterId)
    {
        this.chapterId = chapterId;
    }

    public Long getLessonId()
    {
        return lessonId;
    }

    public void setLessonId(Long lessonId)
    {
        this.lessonId = lessonId;
    }

    public Integer getSeq()
    {
        return seq;
    }

    public void setSeq(Integer seq)
    {
        this.seq = seq;
    }

    public Integer getStartMs()
    {
        return startMs;
    }

    public void setStartMs(Integer startMs)
    {
        this.startMs = startMs;
    }

    public Integer getEndMs()
    {
        return endMs;
    }

    public void setEndMs(Integer endMs)
    {
        this.endMs = endMs;
    }

    public String getSection()
    {
        return section;
    }

    public void setSection(String section)
    {
        this.section = section;
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
