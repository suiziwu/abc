package com.ruoyi.project.domain;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.ruoyi.common.annotation.Excel;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * 用户视频观看记录对象 user_video_watch
 * 
 * @author ruoyi
 * @date 2026-07-01
 */
public class UserVideoWatch extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 记录ID */
    private Long id;

    /** 用户ID */
    @Excel(name = "用户ID")
    private Long userId;

    /** 知识点ID（冗余，方便统计） */
    @Excel(name = "知识点ID", readConverterExp = "冗=余，方便统计")
    private Long pointId;

    /** 已观看时长（秒） */
    @Excel(name = "已观看时长", readConverterExp = "秒=")
    private Long watchDuration;

    /** 观看进度百分比 */
    @Excel(name = "观看进度百分比")
    private Long watchProgress;

    /** 最后观看时间 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    @Excel(name = "最后观看时间", width = 30, dateFormat = "yyyy-MM-dd")
    private Date lastWatchTime;

    /** 是否看完（0-否 1-是） */
    @Excel(name = "是否看完", readConverterExp = "0=-否,1=-是")
    private String completed;

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

    public void setWatchDuration(Long watchDuration) 
    {
        this.watchDuration = watchDuration;
    }

    public Long getWatchDuration() 
    {
        return watchDuration;
    }

    public void setWatchProgress(Long watchProgress) 
    {
        this.watchProgress = watchProgress;
    }

    public Long getWatchProgress() 
    {
        return watchProgress;
    }

    public void setLastWatchTime(Date lastWatchTime) 
    {
        this.lastWatchTime = lastWatchTime;
    }

    public Date getLastWatchTime() 
    {
        return lastWatchTime;
    }

    public void setCompleted(String completed) 
    {
        this.completed = completed;
    }

    public String getCompleted() 
    {
        return completed;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this,ToStringStyle.MULTI_LINE_STYLE)
            .append("id", getId())
            .append("userId", getUserId())
            .append("pointId", getPointId())
            .append("watchDuration", getWatchDuration())
            .append("watchProgress", getWatchProgress())
            .append("lastWatchTime", getLastWatchTime())
            .append("completed", getCompleted())
            .append("createTime", getCreateTime())
            .append("updateTime", getUpdateTime())
            .toString();
    }
}
