package com.ruoyi.project.domain;

import java.util.Date;

/**
 * 岗位产业链与岗位群映射。
 *
 * @author ruoyi
 */
public class JobIndustryChain
{
    private Long id;

    private String jobCode;

    private String jobName;

    private String chainName;

    private String chainCode;

    private String chainDesc;

    private String stageName;

    private String stageDesc;

    private String clusterCode;

    private String clusterName;

    private String clusterDesc;

    private String source;

    private String sourceUrl;

    private Date createTime;

    public Long getId()
    {
        return id;
    }

    public void setId(Long id)
    {
        this.id = id;
    }

    public String getJobCode()
    {
        return jobCode;
    }

    public void setJobCode(String jobCode)
    {
        this.jobCode = jobCode;
    }

    public String getJobName()
    {
        return jobName;
    }

    public void setJobName(String jobName)
    {
        this.jobName = jobName;
    }

    public String getChainName()
    {
        return chainName;
    }

    public void setChainName(String chainName)
    {
        this.chainName = chainName;
    }

    public String getChainCode()
    {
        return chainCode;
    }

    public void setChainCode(String chainCode)
    {
        this.chainCode = chainCode;
    }

    public String getChainDesc()
    {
        return chainDesc;
    }

    public void setChainDesc(String chainDesc)
    {
        this.chainDesc = chainDesc;
    }

    public String getStageName()
    {
        return stageName;
    }

    public void setStageName(String stageName)
    {
        this.stageName = stageName;
    }

    public String getStageDesc()
    {
        return stageDesc;
    }

    public void setStageDesc(String stageDesc)
    {
        this.stageDesc = stageDesc;
    }

    public String getClusterCode()
    {
        return clusterCode;
    }

    public void setClusterCode(String clusterCode)
    {
        this.clusterCode = clusterCode;
    }

    public String getClusterName()
    {
        return clusterName;
    }

    public void setClusterName(String clusterName)
    {
        this.clusterName = clusterName;
    }

    public String getClusterDesc()
    {
        return clusterDesc;
    }

    public void setClusterDesc(String clusterDesc)
    {
        this.clusterDesc = clusterDesc;
    }

    public String getSource()
    {
        return source;
    }

    public void setSource(String source)
    {
        this.source = source;
    }

    public String getSourceUrl()
    {
        return sourceUrl;
    }

    public void setSourceUrl(String sourceUrl)
    {
        this.sourceUrl = sourceUrl;
    }

    public Date getCreateTime()
    {
        return createTime;
    }

    public void setCreateTime(Date createTime)
    {
        this.createTime = createTime;
    }
}
