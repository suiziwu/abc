package com.ruoyi.project.asr;

/**
 * 语音识别服务。
 *
 * @author ruoyi
 */
public interface AsrService
{
    /**
     * 识别 WAV 音频。
     *
     * @param wavBytes WAV 文件内容（支持 8/16/24/32-bit PCM、单/多声道，内部重采样为 16k mono）
     */
    AsrResult transcribe(byte[] wavBytes);

    /**
     * 识别流式音源（不把整段音频载入内存）。
     *
     * <p>默认实现会把整段音频读成字节后委托 {@link #transcribe(byte[])}，
     * 仅适用于时长可控的场景；长音频请由实现类覆盖。</p>
     *
     * @param source WAV 音源
     */
    default AsrResult transcribe(PcmAudioSource source)
    {
        return transcribe(source.readWav(0, (int) Math.min(source.frameCount(), Integer.MAX_VALUE),
                source.sampleRate()));
    }
}
