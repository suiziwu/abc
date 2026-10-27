package com.ruoyi.project.asr;

import org.springframework.stereotype.Service;

/**
 * Qwen-Audio-3.1-ASR-Flash-Filetrans 语音识别服务实现。
 *
 * @author ruoyi
 */
@Service
public class AsrServiceImpl implements AsrService
{
    private final AsrProperties properties;

    private final QwenFileTransClient qwenFileTransClient;

    public AsrServiceImpl(AsrProperties properties, QwenFileTransClient qwenFileTransClient)
    {
        this.properties = properties;
        this.qwenFileTransClient = qwenFileTransClient;
    }

    @Override
    public AsrResult transcribe(byte[] wavBytes)
    {
        WavAudio audio = WavAudio.parse(wavBytes);
        double duration = audio.durationSeconds();
        if (duration < 0.2D)
        {
            throw new AsrException("音频时长太短，请至少 0.2 秒");
        }
        if (properties.getMaxAudioSeconds() != null && duration > properties.getMaxAudioSeconds())
        {
            throw new AsrException("音频超过 " + properties.getMaxAudioSeconds() + " 秒，请分段识别");
        }
        WavAudio target = audio.resample(properties.getSampleRate());
        AsrResult result = qwenFileTransClient.transcribe(target.toWavBytes());
        if (result.getAudioSeconds() <= 0D)
        {
            result.setAudioSeconds(target.durationSeconds());
        }
        return result;
    }
}
