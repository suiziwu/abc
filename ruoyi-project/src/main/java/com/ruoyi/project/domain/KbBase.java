package com.ruoyi.project.domain;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * 知识库对象 kb_base（与学科一对一）。
 *
 * @author ruoyi
 */
public class KbBase extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 知识库ID */
    private Long id;

    /** 所属学科ID */
    private Long subjectId;

    /** 知识库名称（默认与学科名称一致） */
    private String name;

    /** 状态（0正常 1停用） */
    private String status;

    /** 删除标志（0-正常 1-已删除） */
    private String delFlag;

    /** 分块数量（非数据库字段，列表查询时统计） */
    private Long chunkCount;

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

    public String getName()
    {
        return name;
    }

    public void setName(String name)
    {
        this.name = name;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public String getDelFlag()
    {
        return delFlag;
    }

    public void setDelFlag(String delFlag)
    {
        this.delFlag = delFlag;
    }

    public Long getChunkCount()
    {
        return chunkCount;
    }

    public void setChunkCount(Long chunkCount)
    {
        this.chunkCount = chunkCount;
    }

    @Override
    public String toString()
    {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("id", getId())
            .append("subjectId", getSubjectId())
            .append("name", getName())
            .append("status", getStatus())
            .append("createBy", getCreateBy())
            .append("createTime", getCreateTime())
            .append("updateBy", getUpdateBy())
            .append("updateTime", getUpdateTime())
            .append("delFlag", getDelFlag())
            .append("chunkCount", getChunkCount())
            .toString();
    }
}
