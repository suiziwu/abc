package com.ruoyi.project.domain;

import java.util.Date;

import com.ruoyi.common.core.domain.BaseEntity;

/**
 * 岗位能力图谱对象 job_competency_graph
 *
 * @author ruoyi
 */
public class JobCompetencyGraph extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 岗位名称 */
    private String jobName;

    /** 职业编码 */
    private String jobCode;

    /** 职业技能等级 */
    private String level;

    /** 生成方式：llm / kb */
    private String sourceMode;

    /** 契约版本 */
    private String schemaVersion;

    /** 生成模式：kb / llm */
    private String mode;

    /** 审核状态：approved / pending / rejected */
    private String reviewStatus;

    /** 引擎版本 */
    private String engineVersion;

    /** 置信度 */
    private Double confidence;

    /** 覆盖率等统计（JSON） */
    private String coverageJson;

    /** 图谱全文（JSON） */
    private String graphJson;

    /** 删除标志（0-正常 1-已删除） */
    private String delFlag;

    /** 规范化岗位键（同岗位同模式共用，用于幂等与版本链） */
    private String jobKey;

    /** 版本号（同一 jobKey + mode 内从 1 递增） */
    private Integer version;

    /** 是否当前版本（1-当前 0-历史版本） */
    private Integer isCurrent;

    /** 规范化图谱 SHA-256（用于幂等去重） */
    private String graphHash;

    /** 被本版本取代的上一记录 id */
    private Long supersedesId;

    /** 审核人 */
    private String reviewer;

    /** 审核时间 */
    private Date reviewedAt;

    /** 审核备注 */
    private String reviewNotes;

    public Long getId()
    {
        return id;
    }

    public void setId(Long id)
    {
        this.id = id;
    }

    public String getJobName()
    {
        return jobName;
    }

    public void setJobName(String jobName)
    {
        this.jobName = jobName;
    }

    public String getJobCode()
    {
        return jobCode;
    }

    public void setJobCode(String jobCode)
    {
        this.jobCode = jobCode;
    }

    public String getLevel()
    {
        return level;
    }

    public void setLevel(String level)
    {
        this.level = level;
    }

    public String getSourceMode()
    {
        return sourceMode;
    }

    public void setSourceMode(String sourceMode)
    {
        this.sourceMode = sourceMode;
    }

    public String getSchemaVersion()
    {
        return schemaVersion;
    }

    public void setSchemaVersion(String schemaVersion)
    {
        this.schemaVersion = schemaVersion;
    }

    public String getMode()
    {
        return mode;
    }

    public void setMode(String mode)
    {
        this.mode = mode;
    }

    public String getReviewStatus()
    {
        return reviewStatus;
    }

    public void setReviewStatus(String reviewStatus)
    {
        this.reviewStatus = reviewStatus;
    }

    public String getEngineVersion()
    {
        return engineVersion;
    }

    public void setEngineVersion(String engineVersion)
    {
        this.engineVersion = engineVersion;
    }

    public Double getConfidence()
    {
        return confidence;
    }

    public void setConfidence(Double confidence)
    {
        this.confidence = confidence;
    }

    public String getCoverageJson()
    {
        return coverageJson;
    }

    public void setCoverageJson(String coverageJson)
    {
        this.coverageJson = coverageJson;
    }

    public String getGraphJson()
    {
        return graphJson;
    }

    public void setGraphJson(String graphJson)
    {
        this.graphJson = graphJson;
    }

    public String getDelFlag()
    {
        return delFlag;
    }

    public void setDelFlag(String delFlag)
    {
        this.delFlag = delFlag;
    }

    public String getJobKey()
    {
        return jobKey;
    }

    public void setJobKey(String jobKey)
    {
        this.jobKey = jobKey;
    }

    public Integer getVersion()
    {
        return version;
    }

    public void setVersion(Integer version)
    {
        this.version = version;
    }

    public Integer getIsCurrent()
    {
        return isCurrent;
    }

    public void setIsCurrent(Integer isCurrent)
    {
        this.isCurrent = isCurrent;
    }

    public String getGraphHash()
    {
        return graphHash;
    }

    public void setGraphHash(String graphHash)
    {
        this.graphHash = graphHash;
    }

    public Long getSupersedesId()
    {
        return supersedesId;
    }

    public void setSupersedesId(Long supersedesId)
    {
        this.supersedesId = supersedesId;
    }

    public String getReviewer()
    {
        return reviewer;
    }

    public void setReviewer(String reviewer)
    {
        this.reviewer = reviewer;
    }

    public Date getReviewedAt()
    {
        return reviewedAt;
    }

    public void setReviewedAt(Date reviewedAt)
    {
        this.reviewedAt = reviewedAt;
    }

    public String getReviewNotes()
    {
        return reviewNotes;
    }

    public void setReviewNotes(String reviewNotes)
    {
        this.reviewNotes = reviewNotes;
    }
}
