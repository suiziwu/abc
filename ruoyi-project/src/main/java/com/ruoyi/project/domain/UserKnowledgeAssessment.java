package com.ruoyi.project.domain;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.ruoyi.common.annotation.Excel;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * 用户知识点测评记录对象 user_knowledge_assessment
 * 
 * @author ruoyi
 * @date 2026-07-10
 */
public class UserKnowledgeAssessment extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 测评记录ID */
    private Long id;

    /** 用户ID */
    @Excel(name = "用户ID")
    private Long userId;

    /** 关联的AI查询记录ID（目标岗位） */
    @Excel(name = "关联的AI查询记录ID", readConverterExp = "目=标岗位")
    private Long historyId;

    /** 测评标题/描述 */
    @Excel(name = "测评标题/描述")
    private String assessTitle;

    /** AI生成的题目列表（JSON数组） */
    @Excel(name = "AI生成的题目列表", readConverterExp = "J=SON数组")
    private String questions;

    /** 用户提交的答案（JSON对象） */
    @Excel(name = "用户提交的答案", readConverterExp = "J=SON对象")
    private String answers;

    /** 分析出的薄弱知识点（JSON数组，含point_id/name/weak_score） */
    @Excel(name = "分析出的薄弱知识点", readConverterExp = "J=SON数组，含point_id/name/weak_score")
    private String weakPoints;

    /** 分析出的薄弱学科列表（JSON数组，含id/name/weak_score） */
    @Excel(name = "分析出的薄弱学科列表", readConverterExp = "J=SON数组，含id/name/weak_score")
    private String weakSubjects;

    /** AI总体分析建议 */
    @Excel(name = "AI总体分析建议")
    private String overallAnalysis;

    /** AI生成的针对性学习路径（JSON） */
    @Excel(name = "AI生成的针对性学习路径")
    private String aiResponse;

    /** 生成学习路径时的候选知识点池大小 */
    @Excel(name = "候选知识点池大小")
    private Long pointCount;

    /** 总体得分（0-100） */
    @Excel(name = "总体得分", readConverterExp = "0=-100")
    private Long totalScore;

    /** 状态（0-待答题 1-已完成答题 2-已分析出结果） */
    @Excel(name = "状态", readConverterExp = "0=-待答题,1=-已完成答题,2=-已分析出结果")
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

    public void setHistoryId(Long historyId) 
    {
        this.historyId = historyId;
    }

    public Long getHistoryId() 
    {
        return historyId;
    }

    public void setAssessTitle(String assessTitle) 
    {
        this.assessTitle = assessTitle;
    }

    public String getAssessTitle() 
    {
        return assessTitle;
    }

    public void setQuestions(String questions) 
    {
        this.questions = questions;
    }

    public String getQuestions() 
    {
        return questions;
    }

    public void setAnswers(String answers) 
    {
        this.answers = answers;
    }

    public String getAnswers() 
    {
        return answers;
    }

    public void setWeakPoints(String weakPoints) 
    {
        this.weakPoints = weakPoints;
    }

    public String getWeakPoints() 
    {
        return weakPoints;
    }

    public void setWeakSubjects(String weakSubjects) 
    {
        this.weakSubjects = weakSubjects;
    }

    public String getWeakSubjects() 
    {
        return weakSubjects;
    }

    public void setOverallAnalysis(String overallAnalysis) 
    {
        this.overallAnalysis = overallAnalysis;
    }

    public String getOverallAnalysis() 
    {
        return overallAnalysis;
    }

    public void setAiResponse(String aiResponse) 
    {
        this.aiResponse = aiResponse;
    }

    public String getAiResponse() 
    {
        return aiResponse;
    }

    public void setPointCount(Long pointCount) 
    {
        this.pointCount = pointCount;
    }

    public Long getPointCount() 
    {
        return pointCount;
    }

    public void setTotalScore(Long totalScore) 
    {
        this.totalScore = totalScore;
    }

    public Long getTotalScore() 
    {
        return totalScore;
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
            .append("historyId", getHistoryId())
            .append("assessTitle", getAssessTitle())
            .append("questions", getQuestions())
            .append("answers", getAnswers())
            .append("weakPoints", getWeakPoints())
            .append("weakSubjects", getWeakSubjects())
            .append("overallAnalysis", getOverallAnalysis())
            .append("aiResponse", getAiResponse())
            .append("pointCount", getPointCount())
            .append("totalScore", getTotalScore())
            .append("status", getStatus())
            .append("createBy", getCreateBy())
            .append("createTime", getCreateTime())
            .append("updateBy", getUpdateBy())
            .append("updateTime", getUpdateTime())
            .append("delFlag", getDelFlag())
            .toString();
    }
}
