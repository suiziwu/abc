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
}
