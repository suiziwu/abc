package com.ruoyi.project.domain;

/**
 * 国家职业标准技能要求权重。
 *
 * @author ruoyi
 */
public class JobCompetencyWeight
{
    private Long id;

    private String jobCode;

    private String jobName;

    private String level;

    private String domainName;

    private Integer weight;

    private String source;

    private String sourceFile;

    private String sourcePage;

    private String issueNumber;

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

    public String getLevel()
    {
        return level;
    }

    public void setLevel(String level)
    {
        this.level = level;
    }

    public String getDomainName()
    {
        return domainName;
    }

    public void setDomainName(String domainName)
    {
        this.domainName = domainName;
    }

    public Integer getWeight()
    {
        return weight;
    }

    public void setWeight(Integer weight)
    {
        this.weight = weight;
    }

    public String getSource()
    {
        return source;
    }

    public void setSource(String source)
    {
        this.source = source;
    }

    public String getSourceFile()
    {
        return sourceFile;
    }

    public void setSourceFile(String sourceFile)
    {
        this.sourceFile = sourceFile;
    }

    public String getSourcePage()
    {
        return sourcePage;
    }

    public void setSourcePage(String sourcePage)
    {
        this.sourcePage = sourcePage;
    }

    public String getIssueNumber()
    {
        return issueNumber;
    }

    public void setIssueNumber(String issueNumber)
    {
        this.issueNumber = issueNumber;
    }
}
