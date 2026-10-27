package com.ruoyi.project.domain;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.ruoyi.common.annotation.Excel;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * AI对话历史记录对象 ai_chat_history
 * 
 * @author ruoyi
 * @date 2026-06-30
 */
public class AiChatHistory extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 记录ID */
    private Long id;

    /** 用户ID */
    @Excel(name = "用户ID")
    private Long userId;

    /** 用户输入的目标岗位及描述 */
    @Excel(name = "用户输入的目标岗位及描述")
    private String userInput;

    /** 查询时的学科池上下文（JSON数组） */
    @Excel(name = "查询时的学科池上下文", readConverterExp = "J=SON数组")
    private String subjectContext;

    /** AI选择的学科列表（JSON数组，含id/name/relevance_score/reason） */
    @Excel(name = "AI选择的学科列表", readConverterExp = "J=SON数组，含id/name/relevance_score/reason")
    private String selectedSubjects;

    /** AI总体分析意见 */
    @Excel(name = "AI总体分析意见")
    private String overallAnalysis;

    /** AI原始响应全文 */
    @Excel(name = "AI原始响应全文")
    private String rawResponse;

    /** 状态（0正常 1失败） */
    @Excel(name = "状态", readConverterExp = "0=正常,1=失败")
    private String status;

    /** 删除标志（0-正常 1-已删除） */
    private String delFlag;

    public void setId(Long id) 
    {
        this.id = id;
    }

    public Long getId() 
    {
        return id;
    }

    public void setUserId(Long userId) 
    {
        this.userId = userId;
    }

    public Long getUserId() 
    {
        return userId;
    }

    public void setUserInput(String userInput) 
    {
        this.userInput = userInput;
    }

    public String getUserInput() 
    {
        return userInput;
    }

    public void setSubjectContext(String subjectContext) 
    {
        this.subjectContext = subjectContext;
    }

    public String getSubjectContext() 
    {
        return subjectContext;
    }

    public void setSelectedSubjects(String selectedSubjects) 
    {
        this.selectedSubjects = selectedSubjects;
    }

    public String getSelectedSubjects() 
    {
        return selectedSubjects;
    }

    public void setOverallAnalysis(String overallAnalysis) 
    {
        this.overallAnalysis = overallAnalysis;
    }

    public String getOverallAnalysis() 
    {
        return overallAnalysis;
    }

    public void setRawResponse(String rawResponse) 
    {
        this.rawResponse = rawResponse;
    }

    public String getRawResponse() 
    {
        return rawResponse;
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

    @Override
    public String toString() {
        return new ToStringBuilder(this,ToStringStyle.MULTI_LINE_STYLE)
            .append("id", getId())
            .append("userId", getUserId())
            .append("userInput", getUserInput())
            .append("subjectContext", getSubjectContext())
            .append("selectedSubjects", getSelectedSubjects())
            .append("overallAnalysis", getOverallAnalysis())
            .append("rawResponse", getRawResponse())
            .append("status", getStatus())
            .append("createBy", getCreateBy())
            .append("createTime", getCreateTime())
            .append("updateBy", getUpdateBy())
            .append("updateTime", getUpdateTime())
            .append("delFlag", getDelFlag())
            .toString();
    }
}
