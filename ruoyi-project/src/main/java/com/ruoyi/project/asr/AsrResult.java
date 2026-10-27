package com.ruoyi.project.asr;

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
}
