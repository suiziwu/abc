package com.ruoyi.project.domain;

import java.util.List;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * 课程章节对象 course_chapter
 *
 * @author ruoyi
 */
public class CourseChapter extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 章节ID */
    private Long id;

    /** 所属学科ID */
    private Long subjectId;

    /** 关联岗位ID */
    private Long positionId;

    /** 章号（如：第1章） */
    private String chapterNo;

    /** 章节名称 */
    private String title;

    /** 所属课程名称 */
    private String courseTitle;

    /** 章节描述 */
    private String description;

    /** 章节视频路径 */
    private String video;

    /** 视频起始时间（秒） */
    private Integer startTime;

    /** 视频结束时间（秒） */
    private Integer endTime;

    /** 显示排序 */
    private Long sortOrder;

    /** 状态（0正常 1停用） */
    private String status;

    /** 删除标志（0-正常 1-已删除） */
    private String delFlag;

    /** 章节包含的知识点数量（联表统计，只读） */
    private Long pointCount;

    /** 章节包含的课时数量（联表统计，只读） */
    private Long lessonCount;

    /** 提交时携带的章节知识点ID集合（用于保存章节-知识点关联，非表字段） */
    private List<Long> pointIds;

    public void setId(Long id)
    {
        this.id = id;
    }

    public Long getId()
    {
        return id;
    }

    public void setSubjectId(Long subjectId)
    {
        this.subjectId = subjectId;
    }

    public Long getSubjectId()
    {
        return subjectId;
    }

    public void setPositionId(Long positionId)
    {
        this.positionId = positionId;
    }

    public Long getPositionId()
    {
        return positionId;
    }

    public void setChapterNo(String chapterNo)
    {
        this.chapterNo = chapterNo;
    }

    public String getChapterNo()
    {
        return chapterNo;
    }

    public void setTitle(String title)
    {
        this.title = title;
    }

    public String getTitle()
    {
        return title;
    }

    public void setCourseTitle(String courseTitle)
    {
        this.courseTitle = courseTitle;
    }

    public String getCourseTitle()
    {
        return courseTitle;
    }

    public void setDescription(String description)
    {
        this.description = description;
    }

    public String getDescription()
    {
        return description;
    }

    public void setVideo(String video)
    {
        this.video = video;
    }

    public String getVideo()
    {
        return video;
    }

    public void setStartTime(Integer startTime)
    {
        this.startTime = startTime;
    }

    public Integer getStartTime()
    {
        return startTime;
    }

    public void setEndTime(Integer endTime)
    {
        this.endTime = endTime;
    }

    public Integer getEndTime()
    {
        return endTime;
    }

    public void setSortOrder(Long sortOrder)
    {
        this.sortOrder = sortOrder;
    }

    public Long getSortOrder()
    {
        return sortOrder;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public String getStatus()
    {
        return status;
    }

    public void setDelFlag(String delFlag)
    {
        this.delFlag = delFlag;
    }

    public String getDelFlag()
    {
        return delFlag;
    }

    public void setPointCount(Long pointCount)
    {
        this.pointCount = pointCount;
    }

    public Long getPointCount()
    {
        return pointCount;
    }

    public Long getLessonCount()
    {
        return lessonCount;
    }

    public void setLessonCount(Long lessonCount)
    {
        this.lessonCount = lessonCount;
    }

    public void setPointIds(List<Long> pointIds)
    {
        this.pointIds = pointIds;
    }

    public List<Long> getPointIds()
    {
        return pointIds;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("id", getId())
            .append("subjectId", getSubjectId())
            .append("chapterNo", getChapterNo())
            .append("title", getTitle())
            .append("courseTitle", getCourseTitle())
            .append("description", getDescription())
            .append("video", getVideo())
            .append("startTime", getStartTime())
            .append("endTime", getEndTime())
            .append("sortOrder", getSortOrder())
            .append("status", getStatus())
            .append("createBy", getCreateBy())
            .append("createTime", getCreateTime())
            .append("updateBy", getUpdateBy())
            .append("updateTime", getUpdateTime())
            .append("delFlag", getDelFlag())
            .append("pointCount", getPointCount())
            .toString();
    }
}