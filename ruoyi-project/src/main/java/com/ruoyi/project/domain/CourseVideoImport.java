package com.ruoyi.project.domain;

import java.util.Date;

/**
 * 视频课程导入任务（course_video_import）。
 *
 * @author ruoyi
 */
public class CourseVideoImport
{
    private Long id;

    private Long subjectId;

    private Long chapterId;

    /** 所属课时ID（一章多课时后，导入任务归属到课时） */
    private Long lessonId;

    /** 课时编号（如 1.1.1） */
    private String lessonCode;

    private String chapterNo;

    private String title;

    private String fileName;

    private String videoPath;

    private Integer durationSec;

    /** 0待处理 1处理中 2成功 3失败 */
    private String status;

    private String stage;

    private Integer progress;

    private String message;

    private Integer transcriptCount;

    private Integer pointCount;

    private Integer questionCount;

    private Integer kbCount;

    private Long testId;

    private String createBy;

    private Date createTime;

    private Date updateTime;

    public Long getId()
    {
        return id;
    }

    public void setId(Long id)
    {
        this.id = id;
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

    public Long getLessonId()
    {
        return lessonId;
    }

    public void setLessonId(Long lessonId)
    {
        this.lessonId = lessonId;
    }

    public String getLessonCode()
    {
        return lessonCode;
    }

    public void setLessonCode(String lessonCode)
    {
        this.lessonCode = lessonCode;
    }

    public String getChapterNo()
    {
        return chapterNo;
    }

    public void setChapterNo(String chapterNo)
    {
        this.chapterNo = chapterNo;
    }

    public String getTitle()
    {
        return title;
    }

    public void setTitle(String title)
    {
        this.title = title;
    }

    public String getFileName()
    {
        return fileName;
    }

    public void setFileName(String fileName)
    {
        this.fileName = fileName;
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

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public String getStage()
    {
        return stage;
    }

    public void setStage(String stage)
    {
        this.stage = stage;
    }

    public Integer getProgress()
    {
        return progress;
    }

    public void setProgress(Integer progress)
    {
        this.progress = progress;
    }

    public String getMessage()
    {
        return message;
    }

    public void setMessage(String message)
    {
        this.message = message;
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

    public Long getTestId()
    {
        return testId;
    }

    public void setTestId(Long testId)
    {
        this.testId = testId;
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

    public Date getUpdateTime()
    {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime)
    {
        this.updateTime = updateTime;
    }
}
