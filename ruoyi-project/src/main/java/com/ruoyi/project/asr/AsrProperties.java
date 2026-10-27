package com.ruoyi.project.asr;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Qwen-Audio-3.1-ASR-Flash-Filetrans 语音识别配置。
 *
 * @author ruoyi
 */
@Component
@ConfigurationProperties(prefix = "ai.asr")
public class AsrProperties
{
    /** DashScope API 根地址（录音文件识别任务） */
    private String dashscopeBaseUrl = "https://ws-uxxtz5szu7puw1so.cn-beijing.maas.aliyuncs.com/api/v1";

    /** API Key，建议通过环境变量 DASHSCOPE_API_KEY 注入 */
    private String apiKey;

    /** 录音文件识别模型 */
    private String model = "qwen-audio-3.1-asr-flash-filetrans";

    /** 目标采样率（上传前统一转为 16k 单声道 WAV） */
    private Integer sampleRate = 16000;

    /** 识别语言 */
    private String language = "zh";

    /** 单次识别最大音频时长（秒） */
    private Integer maxAudioSeconds = 120;

    /** 轮询间隔（毫秒） */
    private Integer pollIntervalMs = 2000;

    /** 任务总超时（秒） */
    private Integer timeoutSeconds = 180;

    public String getDashscopeBaseUrl()
    {
        return dashscopeBaseUrl;
    }

    public void setDashscopeBaseUrl(String dashscopeBaseUrl)
    {
        this.dashscopeBaseUrl = dashscopeBaseUrl;
    }

    public String getApiKey()
    {
        return apiKey;
    }

    public void setApiKey(String apiKey)
    {
        this.apiKey = apiKey;
    }

    public String getModel()
    {
        return model;
    }

    public void setModel(String model)
    {
        this.model = model;
    }

    public Integer getSampleRate()
    {
        return sampleRate;
    }

    public void setSampleRate(Integer sampleRate)
    {
        this.sampleRate = sampleRate;
    }

    public String getLanguage()
    {
        return language;
    }

    public void setLanguage(String language)
    {
        this.language = language;
    }

    public Integer getMaxAudioSeconds()
    {
        return maxAudioSeconds;
    }

    public void setMaxAudioSeconds(Integer maxAudioSeconds)
    {
        this.maxAudioSeconds = maxAudioSeconds;
    }

    public Integer getPollIntervalMs()
    {
        return pollIntervalMs;
    }

    public void setPollIntervalMs(Integer pollIntervalMs)
    {
        this.pollIntervalMs = pollIntervalMs;
    }

    public Integer getTimeoutSeconds()
    {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(Integer timeoutSeconds)
    {
        this.timeoutSeconds = timeoutSeconds;
    }
}
