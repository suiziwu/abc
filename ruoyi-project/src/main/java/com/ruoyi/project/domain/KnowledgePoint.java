package com.ruoyi.project.domain;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.ruoyi.common.annotation.Excel;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * 知识点对象 knowledge_point
 * 
 * @author ruoyi
 * @date 2026-06-24
 */
public class KnowledgePoint extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 知识点ID */
    private Long id;

    /** 所属学科ID */
    @Excel(name = "所属学科ID")
    private Long subjectId;

    /** 所属章节ID（对应 course_chapter.id） */
    private Long chapterId;

    /** 知识点名称 */
    @Excel(name = "知识点名称")
    private String name;

    /** 知识点内容/描述 */
    @Excel(name = "知识点内容/描述")
    private String content;

    /** 难度等级（1-简单 2-较易 3-中等 4-较难 5-困难） */
    @Excel(name = "难度等级", readConverterExp = "1=简单,2=较易,3=中等,4=较难,5=困难")
    private Long difficulty;

    /** 重要程度（1-了解 2-掌握 3-重点 4-核心） */
    @Excel(name = "重要程度", readConverterExp = "1=了解,2=掌握,3=重点,4=核心")
    private Long importance;

    /** 知识点类型（如：概念、公式、方法、题型等） */
    @Excel(name = "知识点类型")
    private String type;

    /** 显示排序 */
    @Excel(name = "显示排序")
    private Long sortOrder;

    /** 状态（0正常 1停用） */
    @Excel(name = "状态", readConverterExp = "0=正常,1=停用")
    private String status;

    /** 删除标志（0-正常 1-已删除） */
    private String delFlag;

    /** 附件路径 */
    @Excel(name = "附件路径")
    private String attachment;

    /** 视频路径 */
    @Excel(name = "视频路径")
    private String video;

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

    public void setChapterId(Long chapterId) 
    {
        this.chapterId = chapterId;
    }

    public Long getChapterId() 
    {
        return chapterId;
    }

    public void setName(String name) 
    {
        this.name = name;
    }

    public String getName() 
    {
        return name;
    }

    public void setContent(String content) 
    {
        this.content = content;
    }

    public String getContent() 
    {
        return content;
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

    public void setAttachment(String attachment) 
    {
        this.attachment = attachment;
    }

    public String getAttachment() 
    {
        return attachment;
    }

    public void setVideo(String video) 
    {
        this.video = video;
    }

    public String getVideo() 
    {
        return video;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this,ToStringStyle.MULTI_LINE_STYLE)
            .append("id", getId())
            .append("subjectId", getSubjectId())
            .append("name", getName())
            .append("content", getContent())
            .append("difficulty", getDifficulty())
            .append("importance", getImportance())
            .append("type", getType())
            .append("sortOrder", getSortOrder())
            .append("status", getStatus())
            .append("createBy", getCreateBy())
            .append("createTime", getCreateTime())
            .append("updateBy", getUpdateBy())
            .append("updateTime", getUpdateTime())
            .append("delFlag", getDelFlag())
            .append("attachment", getAttachment())
            .append("video", getVideo())
            .toString();
    }
}
