package com.ruoyi.project.domain;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.ruoyi.common.annotation.Excel;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * 用户学习记录对象 user_learn_record
 * 
 * @author ruoyi
 * @date 2026-07-01
 */
public class UserLearnRecord extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 记录ID */
    private Long id;

    /** 用户ID */
    @Excel(name = "用户ID")
    private Long userId;

    /** 知识点ID */
    @Excel(name = "知识点ID")
    private Long pointId;

    /** 关联的AI查询记录ID */
    @Excel(name = "关联的AI查询记录ID")
    private Long historyId;

    /** 学习状态（0-未学 1-学习中 2-已完成） */
    @Excel(name = "学习状态", readConverterExp = "0=-未学,1=-学习中,2=-已完成")
    private String status;

    /** 学习进度百分比（0-100） */
    @Excel(name = "学习进度百分比", readConverterExp = "0=-100")
    private Long progress;

    /** 知识点测评分数（0-100） */
    @Excel(name = "知识点测评分数", readConverterExp = "0=-100")
    private Long pointQuizScore;

    /** 开始学习时间 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    @Excel(name = "开始学习时间", width = 30, dateFormat = "yyyy-MM-dd")
    private Date startTime;

    /** 完成时间 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    @Excel(name = "完成时间", width = 30, dateFormat = "yyyy-MM-dd")
    private Date completeTime;

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

    public void setPointId(Long pointId) 
    {
        this.pointId = pointId;
    }

    public Long getPointId() 
    {
        return pointId;
    }

    public void setHistoryId(Long historyId) 
    {
        this.historyId = historyId;
    }

    public Long getHistoryId() 
    {
        return historyId;
    }

    public void setStatus(String status) 
    {
        this.status = status;
    }

    public String getStatus() 
    {
        return status;
    }

    public void setProgress(Long progress) 
    {
        this.progress = progress;
    }

    public Long getProgress() 
    {
        return progress;
    }

    public void setPointQuizScore(Long pointQuizScore) 
    {
        this.pointQuizScore = pointQuizScore;
    }

    public Long getPointQuizScore() 
    {
        return pointQuizScore;
    }

    public void setStartTime(Date startTime) 
    {
        this.startTime = startTime;
    }

    public Date getStartTime() 
    {
        return startTime;
    }

    public void setCompleteTime(Date completeTime) 
    {
        this.completeTime = completeTime;
    }

    public Date getCompleteTime() 
    {
        return completeTime;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this,ToStringStyle.MULTI_LINE_STYLE)
            .append("id", getId())
            .append("userId", getUserId())
            .append("pointId", getPointId())
            .append("historyId", getHistoryId())
            .append("status", getStatus())
            .append("progress", getProgress())
            .append("pointQuizScore", getPointQuizScore())
            .append("startTime", getStartTime())
            .append("completeTime", getCompleteTime())
            .append("createTime", getCreateTime())
            .append("updateTime", getUpdateTime())
            .toString();
    }
}
