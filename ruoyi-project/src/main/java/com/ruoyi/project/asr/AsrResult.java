package com.ruoyi.project.asr;

import java.util.ArrayList;
import java.util.List;

/**
 * 语音识别结果。
 *
 * @author ruoyi
 */
public class AsrResult
{
    private String text;

    private String model;

    private double audioSeconds;

    private long durationMs;

    /** 分句结果（带时间戳，毫秒） */
    private List<Segment> segments = new ArrayList<Segment>();

    public String getText()
    {
        return text;
    }

    public void setText(String text)
    {
        this.text = text;
    }

    public String getModel()
    {
        return model;
    }

    public void setModel(String model)
    {
        this.model = model;
    }

    public double getAudioSeconds()
    {
        return audioSeconds;
    }

    public void setAudioSeconds(double audioSeconds)
    {
        this.audioSeconds = audioSeconds;
    }

    public long getDurationMs()
    {
        return durationMs;
    }

    public void setDurationMs(long durationMs)
    {
        this.durationMs = durationMs;
    }

    public List<Segment> getSegments()
    {
        return segments;
    }

    public void setSegments(List<Segment> segments)
    {
        this.segments = segments == null ? new ArrayList<Segment>() : segments;
    }

    /**
     * 识别出的单句。
     */
    public static class Segment
    {
        private Integer startMs;

        private Integer endMs;

        private String text;

        public Segment()
        {
        }

        public Segment(Integer startMs, Integer endMs, String text)
        {
            this.startMs = startMs;
            this.endMs = endMs;
            this.text = text;
        }

        public Integer getStartMs()
        {
            return startMs;
        }

        public void setStartMs(Integer startMs)
        {
            this.startMs = startMs;
        }

        public Integer getEndMs()
        {
            return endMs;
        }

        public void setEndMs(Integer endMs)
        {
            this.endMs = endMs;
        }

        public String getText()
        {
            return text;
        }

        public void setText(String text)
        {
            this.text = text;
        }
    }
}
