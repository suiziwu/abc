package com.ruoyi.project.talent.domain;

import java.util.Date;

/**
 * 人才培养模块：岗位采集记录（CSV 导入用）
 *
 * @author ruoyi
 */
public class TalentJobPosting
{
    private Long id;

    /** 采集批次，空字符串表示纳入当前快照 */
    private String batchDate;

    /** 采集关键词（行业对照表取二级行业名称） */
    private String keyword;

    private String city;

    private String jobTitle;

    private String description;

    /** 数据来源（CSV 导入时按文件名生成，便于重复导入覆盖） */
    private String source;

    private String skillNames;

    private Date createTime;

    private String jobFamily;

    private String industry;

    public Long getId()
    {
        return id;
    }

    public void setId(Long id)
    {
        this.id = id;
    }

    public String getBatchDate()
    {
        return batchDate;
    }

    public void setBatchDate(String batchDate)
    {
        this.batchDate = batchDate;
    }

    public String getKeyword()
    {
        return keyword;
    }

    public void setKeyword(String keyword)
    {
        this.keyword = keyword;
    }

    public String getCity()
    {
        return city;
    }

    public void setCity(String city)
    {
        this.city = city;
    }

    public String getJobTitle()
    {
        return jobTitle;
    }

    public void setJobTitle(String jobTitle)
    {
        this.jobTitle = jobTitle;
    }

    public String getDescription()
    {
        return description;
    }

    public void setDescription(String description)
    {
        this.description = description;
    }

    public String getSource()
    {
        return source;
    }

    public void setSource(String source)
    {
        this.source = source;
    }

    public String getSkillNames()
    {
        return skillNames;
    }

    public void setSkillNames(String skillNames)
    {
        this.skillNames = skillNames;
    }

    public Date getCreateTime()
    {
        return createTime;
    }

    public void setCreateTime(Date createTime)
    {
        this.createTime = createTime;
    }

    public String getJobFamily()
    {
        return jobFamily;
    }

    public void setJobFamily(String jobFamily)
    {
        this.jobFamily = jobFamily;
    }

    public String getIndustry()
    {
        return industry;
    }

    public void setIndustry(String industry)
    {
        this.industry = industry;
    }
}
