package com.ruoyi.project.domain;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.ruoyi.common.annotation.Excel;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * 知识点关联（拓扑图边）对象 knowledge_point_relation
 * 
 * @author ruoyi
 * @date 2026-06-23
 */
public class KnowledgePointRelation extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 关联ID */
    private Long id;

    /** 源知识点ID */
    @Excel(name = "源知识点ID")
    private Long sourceId;

    /** 目标知识点ID */
    @Excel(name = "目标知识点ID")
    private Long targetId;

    /** 关联类型（prerequisite-前置依赖 related-相关参考 extension-扩展延伸） */
    @Excel(name = "关联类型", readConverterExp = "p=rerequisite-前置依赖,r=elated-相关参考,e=xtension-扩展延伸")
    private String relationType;

    /** 关联描述 */
    @Excel(name = "关联描述")
    private String description;

    /** 关联权重/强度（用于拓扑图连线粗细） */
    @Excel(name = "关联权重/强度", readConverterExp = "用=于拓扑图连线粗细")
    private Long weight;

    public void setId(Long id) 
    {
        this.id = id;
    }

    public Long getId() 
    {
        return id;
    }

    public void setSourceId(Long sourceId) 
    {
        this.sourceId = sourceId;
    }

    public Long getSourceId() 
    {
        return sourceId;
    }

    public void setTargetId(Long targetId) 
    {
        this.targetId = targetId;
    }

    public Long getTargetId() 
    {
        return targetId;
    }

    public void setRelationType(String relationType) 
    {
        this.relationType = relationType;
    }

    public String getRelationType() 
    {
        return relationType;
    }

    public void setDescription(String description) 
    {
        this.description = description;
    }

    public String getDescription() 
    {
        return description;
    }

    public void setWeight(Long weight) 
    {
        this.weight = weight;
    }

    public Long getWeight() 
    {
        return weight;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this,ToStringStyle.MULTI_LINE_STYLE)
            .append("id", getId())
            .append("sourceId", getSourceId())
            .append("targetId", getTargetId())
            .append("relationType", getRelationType())
            .append("description", getDescription())
            .append("weight", getWeight())
            .append("createTime", getCreateTime())
            .toString();
    }
}
