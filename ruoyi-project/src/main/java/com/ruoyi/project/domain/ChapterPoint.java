package com.ruoyi.project.domain;

import java.util.Date;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

/**
 * 章节-知识点关联对象 chapter_point
 *
 * 除关联关系外，附带联表查询出的知识点展示字段（只读），方便课程学习页直接按章节渲染知识点。
 *
 * @author ruoyi
 */
public class ChapterPoint
{
    /** 关联ID */
    private Long id;

    /** 章节ID */
    private Long chapterId;

    /** 知识点ID */
    private Long pointId;

    /** 所属小节（如：1.1 计算机网络的定义） */
    private String section;

    /** 显示排序 */
    private Long sortOrder;

    /** 创建时间 */
    private Date createTime;

    // ====== 以下为联表查询出的知识点展示字段（只读） ======

    /** 知识点名称 */
    private String pointName;

    /** 知识点内容/描述 */
    private String pointContent;

    /** 所属学科ID */
    private Long subjectId;

    /** 难度等级（1-简单 2-较易 3-中等 4-较难 5-困难） */
    private Long difficulty;

    /** 重要程度（1-了解 2-掌握 3-重点 4-核心） */
    private Long importance;

    /** 知识点类型（如：概念、方法等） */
    private String type;

    /** 知识点视频路径 */
    private String video;

    /** 所属章号（只读，联表带出） */
    private String chapterNo;

    /** 所属章节名称（只读，联表带出） */
    private String chapterTitle;

    /** 所属章节视频路径（只读，联表带出） */
    private String chapterVideo;

    public void setId(Long id)
    {
        this.id = id;
    }

    public Long getId()
    {
        return id;
    }

    public void setChapterId(Long chapterId)
    {
        this.chapterId = chapterId;
    }

    public Long getChapterId()
    {
        return chapterId;
    }

    public void setPointId(Long pointId)
    {
        this.pointId = pointId;
    }

    public Long getPointId()
    {
        return pointId;
    }

    public void setSection(String section)
    {
        this.section = section;
    }

    public String getSection()
    {
        return section;
    }

    public void setSortOrder(Long sortOrder)
    {
        this.sortOrder = sortOrder;
    }

    public Long getSortOrder()
    {
        return sortOrder;
    }

    public void setCreateTime(Date createTime)
    {
        this.createTime = createTime;
    }

    public Date getCreateTime()
    {
        return createTime;
    }

    public void setPointName(String pointName)
    {
        this.pointName = pointName;
    }

    public String getPointName()
    {
        return pointName;
    }

    public void setPointContent(String pointContent)
    {
        this.pointContent = pointContent;
    }

    public String getPointContent()
    {
        return pointContent;
    }

    public void setSubjectId(Long subjectId)
    {
        this.subjectId = subjectId;
    }

    public Long getSubjectId()
    {
        return subjectId;
    }

    public void setDifficulty(Long difficulty)
    {
        this.difficulty = difficulty;
    }

    public Long getDifficulty()
    {
        return difficulty;
    }

    public void setImportance(Long importance)
    {
        this.importance = importance;
    }

    public Long getImportance()
    {
        return importance;
    }

    public void setType(String type)
    {
        this.type = type;
    }

    public String getType()
    {
        return type;
    }

    public void setVideo(String video)
    {
        this.video = video;
    }

    public String getVideo()
    {
        return video;
    }

    public void setChapterNo(String chapterNo)
    {
        this.chapterNo = chapterNo;
    }

    public String getChapterNo()
    {
        return chapterNo;
    }

    public void setChapterTitle(String chapterTitle)
    {
        this.chapterTitle = chapterTitle;
    }

    public String getChapterTitle()
    {
        return chapterTitle;
    }

    public void setChapterVideo(String chapterVideo)
    {
        this.chapterVideo = chapterVideo;
    }

    public String getChapterVideo()
    {
        return chapterVideo;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("id", getId())
            .append("chapterId", getChapterId())
            .append("pointId", getPointId())
            .append("section", getSection())
            .append("sortOrder", getSortOrder())
            .append("createTime", getCreateTime())
            .append("pointName", getPointName())
            .append("pointContent", getPointContent())
            .append("subjectId", getSubjectId())
            .append("difficulty", getDifficulty())
            .append("importance", getImportance())
            .append("type", getType())
            .append("video", getVideo())
            .append("chapterNo", getChapterNo())
            .append("chapterTitle", getChapterTitle())
            .append("chapterVideo", getChapterVideo())
            .toString();
    }
}