package com.ruoyi.project.asr;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * 极简 WAV 解析/重采样工具。
 *
 * <p>支持 PCM 8/16/24/32-bit、IEEE float 32-bit、WAVE_FORMAT_EXTENSIBLE，
 * 多声道会混为单声道；输出统一为 16-bit little-endian PCM/WAV。</p>
 *
 * @author ruoyi
 */
public final class WavAudio
{
    private final int sampleRate;

    private final float[] samples;

    private WavAudio(int sampleRate, float[] samples)
    {
        this.sampleRate = sampleRate;
        this.samples = samples;
    }

    public static WavAudio parse(byte[] data)
    {
        if (data == null || data.length < 44)
        {
            throw new AsrException("音频文件为空或不是有效的 WAV");
        }
        if (!"RIFF".equals(ascii(data, 0, 4)) || !"WAVE".equals(ascii(data, 8, 4)))
        {
            throw new AsrException("仅支持 WAV 音频，请上传 WAV 文件或转换后再上传");
        }

        int audioFormat = -1;
        int channels = 0;
        int sampleRate = 0;
        int bitsPerSample = 0;
        int dataOffset = -1;
        int dataSize = 0;

        int offset = 12;
        while (offset + 8 <= data.length)
        {
            String chunkId = ascii(data, offset, 4);
            long chunkSize = u32(data, offset + 4);
            int bodyOffset = offset + 8;
            if (chunkSize > Integer.MAX_VALUE)
            {
                break;
            }
            int bodySize = (int) chunkSize;
            if ("fmt ".equals(chunkId))
            {
                if (bodyOffset + 16 > data.length)
                {
                    break;
                }
                audioFormat = u16(data, bodyOffset);
                channels = u16(data, bodyOffset + 2);
                sampleRate = (int) u32(data, bodyOffset + 4);
                bitsPerSample = u16(data, bodyOffset + 14);
                if (audioFormat == 0xFFFE && bodySize >= 40 && bodyOffset + 26 <= data.length)
                {
                    audioFormat = u16(data, bodyOffset + 24);
                }
            }
            else if ("data".equals(chunkId))
            {
                dataOffset = bodyOffset;
                dataSize = Math.min(bodySize, data.length - bodyOffset);
                break;
            }
            long next = (long) bodyOffset + bodySize + (bodySize & 1);
            if (next <= offset || next > data.length)
            {
                break;
            }
            offset = (int) next;
        }

        if (audioFormat != 1 && audioFormat != 3)
        {
            throw new AsrException("不支持的 WAV 编码格式: " + audioFormat);
        }
        if (channels <= 0 || sampleRate <= 0 || dataOffset < 0 || dataSize <= 0)
        {
            throw new AsrException("WAV 缺少有效的 fmt/data 数据块");
        }
        if (bitsPerSample != 8 && bitsPerSample != 16 && bitsPerSample != 24 && bitsPerSample != 32)
        {
            throw new AsrException("不支持的位深: " + bitsPerSample);
        }
        if (audioFormat == 3 && bitsPerSample != 32)
        {
            throw new AsrException("IEEE float 仅支持 32-bit");
        }

        int bytesPerSample = bitsPerSample / 8;
        int blockAlign = channels * bytesPerSample;
        int frameCount = dataSize / blockAlign;
        if (frameCount <= 0)
        {
            throw new AsrException("WAV 没有有效的音频采样");
        }

        float[] samples = new float[frameCount];
        for (int frame = 0; frame < frameCount; frame++)
        {
            double sum = 0D;
            for (int channel = 0; channel < channels; channel++)
            {
                int position = dataOffset + frame * blockAlign + channel * bytesPerSample;
                sum += readSample(data, position, bitsPerSample, audioFormat == 3);
            }
            samples[frame] = (float) (sum / channels);
        }
        return new WavAudio(sampleRate, samples);
    }

    public WavAudio resample(int targetSampleRate)
    {
        if (targetSampleRate <= 0 || targetSampleRate == sampleRate)
        {
            return this;
        }
        double ratio = (double) sampleRate / targetSampleRate;
        int targetLength = (int) Math.round(samples.length / ratio);
        float[] result = new float[targetLength];
        for (int i = 0; i < targetLength; i++)
        {
            double sourcePosition = i * ratio;
            int index = (int) sourcePosition;
            int next = Math.min(index + 1, samples.length - 1);
            double fraction = sourcePosition - index;
            result[i] = (float) (samples[index] * (1D - fraction) + samples[next] * fraction);
        }
        return new WavAudio(targetSampleRate, result);
    }

    public byte[] toPcm16Le()
    {
        byte[] result = new byte[samples.length * 2];
        for (int i = 0; i < samples.length; i++)
        {
            short sample = toShort(samples[i]);
            result[i * 2] = (byte) (sample & 0xFF);
            result[i * 2 + 1] = (byte) ((sample >> 8) & 0xFF);
        }
        return result;
    }

    /**
     * 输出标准 16-bit PCM WAV（单声道），用于上传给录音文件识别接口。
     */
    public byte[] toWavBytes()
    {
        byte[] pcm = toPcm16Le();
        ByteArrayOutputStream out = new ByteArrayOutputStream(44 + pcm.length);
        writeAscii(out, "RIFF");
        writeIntLe(out, 36 + pcm.length);
        writeAscii(out, "WAVE");
        writeAscii(out, "fmt ");
        writeIntLe(out, 16);
        writeShortLe(out, 1);
        writeShortLe(out, 1);
        writeIntLe(out, sampleRate);
        writeIntLe(out, sampleRate * 2);
        writeShortLe(out, 2);
        writeShortLe(out, 16);
        writeAscii(out, "data");
        writeIntLe(out, pcm.length);
        out.write(pcm, 0, pcm.length);
        return out.toByteArray();
    }

    public double durationSeconds()
    {
        return samples.length / (double) sampleRate;
    }

    /**
     * 截取一段音频（用于长音频分片识别）。
     *
     * @param startSeconds 起始秒
     * @param lengthSeconds 长度秒
     * @return 截取后的音频
     */
    public WavAudio slice(double startSeconds, double lengthSeconds)
    {
        int from = Math.max(0, (int) Math.floor(startSeconds * sampleRate));
        int to = Math.min(samples.length, from + (int) Math.max(0D, lengthSeconds * sampleRate));
        if (to <= from)
        {
            return new WavAudio(sampleRate, new float[0]);
        }
        float[] part = new float[to - from];
        System.arraycopy(samples, from, part, 0, part.length);
        return new WavAudio(sampleRate, part);
    }

    public int getSampleRate()
    {
        return sampleRate;
    }

    private static short toShort(float value)
    {
        if (value > 1F)
        {
            value = 1F;
        }
        else if (value < -1F)
        {
            value = -1F;
        }
        return (short) Math.round(value * 32767F);
    }

    private static void writeAscii(ByteArrayOutputStream out, String text)
    {
        byte[] bytes = text.getBytes(StandardCharsets.US_ASCII);
        out.write(bytes, 0, bytes.length);
    }

    private static void writeIntLe(ByteArrayOutputStream out, int value)
    {
        out.write(value & 0xFF);
        out.write((value >> 8) & 0xFF);
        out.write((value >> 16) & 0xFF);
        out.write((value >> 24) & 0xFF);
    }

    private static void writeShortLe(ByteArrayOutputStream out, int value)
    {
        out.write(value & 0xFF);
        out.write((value >> 8) & 0xFF);
    }

    private static float readSample(byte[] data, int position, int bits, boolean floatFormat)
    {
        if (bits == 8)
        {
            return ((data[position] & 0xFF) - 128) / 128F;
        }
        if (bits == 16)
        {
            int value = (data[position] & 0xFF) | ((data[position + 1] & 0xFF) << 8);
            short sample = (short) value;
            return sample / 32768F;
        }
        if (bits == 24)
        {
            int value = (data[position] & 0xFF)
                    | ((data[position + 1] & 0xFF) << 8)
                    | ((data[position + 2] & 0xFF) << 16);
            if ((value & 0x800000) != 0)
            {
                value |= 0xFF000000;
            }
            return value / 8388608F;
        }
        int value = (data[position] & 0xFF)
                | ((data[position + 1] & 0xFF) << 8)
                | ((data[position + 2] & 0xFF) << 16)
                | ((data[position + 3] & 0xFF) << 24);
        if (floatFormat)
        {
            return Float.intBitsToFloat(value);
        }
        return (float) (value / 2147483648D);
    }

    private static String ascii(byte[] data, int offset, int length)
    {
        return new String(data, offset, length, StandardCharsets.US_ASCII);
    }

    private static int u16(byte[] data, int offset)
    {
        return (data[offset] & 0xFF) | ((data[offset + 1] & 0xFF) << 8);
    }

    private static long u32(byte[] data, int offset)
    {
        return (data[offset] & 0xFFL)
                | ((data[offset + 1] & 0xFFL) << 8)
                | ((data[offset + 2] & 0xFFL) << 16)
                | ((data[offset + 3] & 0xFFL) << 24);
    }
}
