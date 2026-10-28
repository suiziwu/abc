package com.ruoyi.project.domain;

import java.util.Date;

/**
 * 课程课时（course_lesson）。
 *
 * <p>章节对应课程目录（如「第一章 网络体系结构」），章下每个视频是一个课时；
 * 逐字稿、随堂练习、知识库切片都挂在课时上。</p>
 *
 * @author ruoyi
 */
public class CourseLesson
{
    private Long id;

    /** 所属章节ID */
    private Long chapterId;

    /** 所属学科ID */
    private Long subjectId;

    /** 课时序号（章内，从 1 开始） */
    private Integer lessonNo;

    /** 课时编号（如 1.1.1） */
    private String lessonCode;

    /** 课时标题 */
    private String title;

    /** 课时视频路径（/profile/...） */
    private String videoPath;

    /** 视频时长（秒） */
    private Integer durationSec;

    /** 显示排序 */
    private Integer sortOrder;

    /** 状态（0正常 1停用） */
    private String status;

    /** 本课时的随堂练习题库ID */
    private Long testId;

    private Integer transcriptCount;

    private Integer pointCount;

    private Integer questionCount;

    private Integer kbCount;

    private String createBy;

    private Date createTime;

    private String updateBy;

    private Date updateTime;

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

    public Long getSubjectId()
    {
        return subjectId;
    }

    public void setSubjectId(Long subjectId)
    {
        this.subjectId = subjectId;
    }

    public Integer getLessonNo()
    {
        return lessonNo;
    }

    public void setLessonNo(Integer lessonNo)
    {
        this.lessonNo = lessonNo;
    }

    public String getLessonCode()
    {
        return lessonCode;
    }

    public void setLessonCode(String lessonCode)
    {
        this.lessonCode = lessonCode;
    }

    public String getTitle()
    {
        return title;
    }

    public void setTitle(String title)
    {
        this.title = title;
    }

    public String getVideoPath()
    {
        return videoPath;
    }

    public void setVideoPath(String videoPath)
    {
        this.videoPath = videoPath;
    }

    public Integer getDurationSec()
    {
        return durationSec;
    }

    public void setDurationSec(Integer durationSec)
    {
        this.durationSec = durationSec;
    }

    public Integer getSortOrder()
    {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder)
    {
        this.sortOrder = sortOrder;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public Long getTestId()
    {
        return testId;
    }

    public void setTestId(Long testId)
    {
        this.testId = testId;
    }

    public Integer getTranscriptCount()
    {
        return transcriptCount;
    }

    public void setTranscriptCount(Integer transcriptCount)
    {
        this.transcriptCount = transcriptCount;
    }

    public Integer getPointCount()
    {
        return pointCount;
    }

    public void setPointCount(Integer pointCount)
    {
        this.pointCount = pointCount;
    }

    public Integer getQuestionCount()
    {
        return questionCount;
    }

    public void setQuestionCount(Integer questionCount)
    {
        this.questionCount = questionCount;
    }

    public Integer getKbCount()
    {
        return kbCount;
    }

    public void setKbCount(Integer kbCount)
    {
        this.kbCount = kbCount;
    }

    public String getCreateBy()
    {
        return createBy;
    }

    public void setCreateBy(String createBy)
    {
        this.createBy = createBy;
    }

    public Date getCreateTime()
    {
        return createTime;
    }

    public void setCreateTime(Date createTime)
    {
        this.createTime = createTime;
    }

    public String getUpdateBy()
    {
        return updateBy;
    }

    public void setUpdateBy(String updateBy)
    {
        this.updateBy = updateBy;
    }

    public Date getUpdateTime()
    {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime)
    {
        this.updateTime = updateTime;
    }
}
