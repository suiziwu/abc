package com.ruoyi.project.asr;

import java.io.IOException;
import java.nio.file.Path;

import org.springframework.stereotype.Component;

/**
 * 长音频分片识别：按最大时长切片后逐片调用 ASR，并还原全局时间戳。
 *
 * <p>切片直接从磁盘按帧窗口读取，整段音频不会展开成 float 数组，
 * 因此 1 小时视频的峰值内存只与「单个分片」有关。</p>
 *
 * @author ruoyi
 */
@Component
public class ChunkedAsrTranscriber
{
    /** 单个分片的最大重试次数（网络抖动/上游限流时避免整单失败） */
    private static final int MAX_RETRY = 3;

    private final AsrService asrService;

    private final AsrProperties properties;

    public ChunkedAsrTranscriber(AsrService asrService, AsrProperties properties)
    {
        this.asrService = asrService;
        this.properties = properties;
    }

    /**
     * 识别磁盘上的 WAV 文件（推荐路径：流式分片）。
     *
     * @param wavFile WAV 文件
     * @return 合并后的识别结果（句子时间戳为毫秒）
     */
    public AsrResult transcribe(Path wavFile)
    {
        try (PcmAudioSource source = PcmAudioSource.open(wavFile))
        {
            return transcribe(source);
        }
        catch (IOException e)
        {
            throw new AsrException("读取音频文件失败: " + e.getMessage(), e);
        }
    }

    /**
     * 识别内存中的整段 WAV 音频（兼容旧调用路径）。
     *
     * @param wavBytes WAV 音频
     * @return 合并后的识别结果（句子时间戳为毫秒）
     */
    public AsrResult transcribe(byte[] wavBytes)
    {
        return transcribe(PcmAudioSource.ofBytes(wavBytes));
    }

    private AsrResult transcribe(PcmAudioSource source)
    {
        int maxSeconds = properties.getMaxAudioSeconds() == null ? 120 : properties.getMaxAudioSeconds();
        int chunkSeconds = Math.max(20, maxSeconds - 10);
        int sourceRate = source.sampleRate();
        int targetRate = properties.getSampleRate();

        AsrResult merged = new AsrResult();
        StringBuilder text = new StringBuilder();
        double totalSeconds = 0D;

        int totalChunks = (int) Math.ceil(source.durationSeconds() / chunkSeconds);
        for (int index = 0; index < totalChunks; index++)
        {
            long startFrame = (long) index * chunkSeconds * sourceRate;
            long remaining = source.frameCount() - startFrame;
            if (remaining <= 0L)
            {
                break;
            }
            int frames = (int) Math.min((long) chunkSeconds * sourceRate, remaining);
            double chunkDuration = frames / (double) sourceRate;
            if (chunkDuration < 0.2D)
            {
                continue;
            }
            // 只读取当前分片并重采样一次（原实现先整段重采样、再逐片重复解析）
            byte[] chunkWav = source.readWav(startFrame, frames, targetRate);
            AsrResult part = transcribeWithRetry(chunkWav);
            totalSeconds += chunkDuration;

            int offsetMs = (int) Math.round((double) index * chunkSeconds * 1000D);
            for (AsrResult.Segment segment : part.getSegments())
            {
                int start = segment.getStartMs() == null ? 0 : segment.getStartMs();
                int end = segment.getEndMs() == null ? start : segment.getEndMs();
                merged.getSegments().add(new AsrResult.Segment(start + offsetMs, end + offsetMs, segment.getText()));
            }
            if (part.getText() != null && !part.getText().trim().isEmpty())
            {
                if (text.length() > 0)
                {
                    text.append(' ');
                }
                text.append(part.getText().trim());
            }
        }
        merged.setText(text.toString().trim());
        merged.setAudioSeconds(totalSeconds > 0D ? totalSeconds : source.durationSeconds());
        merged.setModel(properties.getModel());
        return merged;
    }

    private AsrResult transcribeWithRetry(byte[] chunkWav)
    {
        AsrException last = null;
        for (int attempt = 1; attempt <= MAX_RETRY; attempt++)
        {
            try
            {
                return asrService.transcribe(chunkWav);
            }
            catch (AsrException e)
            {
                last = e;
                if (attempt < MAX_RETRY)
                {
                    try
                    {
                        Thread.sleep(1000L * attempt);
                    }
                    catch (InterruptedException interrupted)
                    {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        throw last == null ? new AsrException("语音识别失败") : last;
    }
}
