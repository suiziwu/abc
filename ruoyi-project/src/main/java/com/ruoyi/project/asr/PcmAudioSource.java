package com.ruoyi.project.asr;

import java.io.Closeable;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/**
 * 流式 WAV 音源：只解析文件头，按帧窗口读取 PCM。
 *
 * <p>与 {@link WavAudio} 的区别：{@code WavAudio.parse} 会把整段音频展开成 float 数组，
 * 1 小时视频约占用 600MB 以上；本类始终只保留「一个窗口」的采样，内存与音频总长无关。</p>
 *
 * <p>输出统一为 16-bit little-endian、单声道 WAV，便于直接送录音文件识别接口。</p>
 *
 * @author ruoyi
 */
public final class PcmAudioSource implements Closeable
{
    /** 单次读取的帧数，控制临时缓冲大小 */
    private static final int READ_BATCH_FRAMES = 8192;

    private final RandomAccessFile file;

    private final byte[] bytes;

    private final int audioFormat;

    private final int channels;

    private final int sampleRate;

    private final int bitsPerSample;

    private final int bytesPerSample;

    private final int blockAlign;

    private final long dataOffset;

    private final long frameCount;

    private PcmAudioSource(RandomAccessFile file, byte[] bytes, int audioFormat, int channels,
            int sampleRate, int bitsPerSample, long dataOffset, long frameCount)
    {
        this.file = file;
        this.bytes = bytes;
        this.audioFormat = audioFormat;
        this.channels = channels;
        this.sampleRate = sampleRate;
        this.bitsPerSample = bitsPerSample;
        this.bytesPerSample = bitsPerSample / 8;
        this.blockAlign = channels * this.bytesPerSample;
        this.dataOffset = dataOffset;
        this.frameCount = frameCount;
    }

    /**
     * 打开磁盘上的 WAV 文件（不载入 PCM 数据）。
     */
    public static PcmAudioSource open(Path wavFile) throws IOException
    {
        if (wavFile == null)
        {
            throw new AsrException("音频文件不存在");
        }
        RandomAccessFile file = new RandomAccessFile(wavFile.toFile(), "r");
        try
        {
            return parse(file, null);
        }
        catch (RuntimeException | IOException e)
        {
            closeQuietly(file);
            throw e;
        }
    }

    /**
     * 包装内存中的 WAV 字节（兼容旧调用路径与单元测试）。
     */
    public static PcmAudioSource ofBytes(byte[] wavBytes)
    {
        if (wavBytes == null || wavBytes.length < 44)
        {
            throw new AsrException("音频文件为空或不是有效的 WAV");
        }
        try
        {
            return parse(null, wavBytes);
        }
        catch (IOException e)
        {
            throw new AsrException("解析 WAV 失败: " + e.getMessage(), e);
        }
    }

    private static PcmAudioSource parse(RandomAccessFile file, byte[] bytes) throws IOException
    {
        PcmAudioSource reader = new PcmAudioSource(file, bytes, -1, 0, 0, 0, -1, 0);

        byte[] riff = new byte[12];
        if (reader.readAt(0, riff, 12) < 12
                || !"RIFF".equals(ascii(riff, 0, 4)) || !"WAVE".equals(ascii(riff, 8, 4)))
        {
            throw new AsrException("仅支持 WAV 音频，请上传 WAV 文件或转换后再上传");
        }

        int audioFormat = -1;
        int channels = 0;
        int sampleRate = 0;
        int bitsPerSample = 0;
        long dataOffset = -1;
        long dataSize = 0;

        byte[] chunk = new byte[8];
        long offset = 12;
        while (reader.readAt(offset, chunk, 8) == 8)
        {
            String chunkId = ascii(chunk, 0, 4);
            long chunkSize = u32(chunk, 4);
            long bodyOffset = offset + 8;
            if ("fmt ".equals(chunkId))
            {
                byte[] fmt = new byte[40];
                int read = reader.readAt(bodyOffset, fmt, fmt.length);
                if (read < 16)
                {
                    break;
                }
                audioFormat = u16(fmt, 0);
                channels = u16(fmt, 2);
                sampleRate = (int) u32(fmt, 4);
                bitsPerSample = u16(fmt, 14);
                if (audioFormat == 0xFFFE && chunkSize >= 40 && read >= 26)
                {
                    audioFormat = u16(fmt, 24);
                }
            }
            else if ("data".equals(chunkId))
            {
                dataOffset = bodyOffset;
                dataSize = chunkSize;
                break;
            }
            long next = bodyOffset + chunkSize + (chunkSize & 1);
            if (next <= offset)
            {
                break;
            }
            offset = next;
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

        int blockAlign = channels * (bitsPerSample / 8);
        long frameCount = dataSize / blockAlign;
        if (frameCount <= 0)
        {
            throw new AsrException("WAV 没有有效的音频采样");
        }
        return new PcmAudioSource(file, bytes, audioFormat, channels, sampleRate, bitsPerSample,
                dataOffset, frameCount);
    }

    public int sampleRate()
    {
        return sampleRate;
    }

    public long frameCount()
    {
        return frameCount;
    }

    public double durationSeconds()
    {
        return frameCount / (double) sampleRate;
    }

    /**
     * 读取一个窗口并转成 16-bit 单声道 WAV 字节。
     *
     * @param startFrame 起始帧（按源采样率计）
     * @param frames 期望帧数（按源采样率计）
     * @param targetRate 目标采样率
     * @return WAV 字节（16-bit PCM、单声道）
     */
    public byte[] readWav(long startFrame, int frames, int targetRate)
    {
        int rate = targetRate > 0 ? targetRate : sampleRate;
        long available = Math.min(frames, frameCount - startFrame);
        if (available <= 0L)
        {
            return wav(new byte[0], rate);
        }
        double ratio = (double) sampleRate / rate;
        int targetLength = (int) Math.round(available / ratio);
        // 多读一帧，供线性插值的右端点使用
        int sourceLength = (int) Math.min(available + 1, frameCount - startFrame);
        float[] source = readMono(startFrame, sourceLength);
        if (source.length == 0)
        {
            return wav(new byte[0], rate);
        }
        byte[] pcm = new byte[targetLength * 2];
        for (int i = 0; i < targetLength; i++)
        {
            double position = i * ratio;
            int index = (int) position;
            if (index >= source.length)
            {
                index = source.length - 1;
            }
            int next = Math.min(index + 1, source.length - 1);
            double fraction = position - index;
            float value = (float) (source[index] * (1D - fraction) + source[next] * fraction);
            short sample = toShort(value);
            pcm[i * 2] = (byte) (sample & 0xFF);
            pcm[i * 2 + 1] = (byte) ((sample >> 8) & 0xFF);
        }
        return wav(pcm, rate);
    }

    /**
     * 按帧读取并混为单声道。
     */
    private float[] readMono(long startFrame, int frames)
    {
        float[] out = new float[frames];
        int batchFrames = Math.min(frames, READ_BATCH_FRAMES);
        byte[] buffer = new byte[batchFrames * blockAlign];
        int done = 0;
        long frame = startFrame;
        while (done < frames)
        {
            int batch = Math.min(frames - done, READ_BATCH_FRAMES);
            int wanted = batch * blockAlign;
            int read;
            try
            {
                read = readAt(dataOffset + frame * blockAlign, buffer, wanted);
            }
            catch (IOException e)
            {
                throw new AsrException("读取音频数据失败: " + e.getMessage(), e);
            }
            if (read <= 0)
            {
                break;
            }
            int got = read / blockAlign;
            for (int i = 0; i < got; i++)
            {
                double sum = 0D;
                int base = i * blockAlign;
                for (int channel = 0; channel < channels; channel++)
                {
                    sum += readSample(buffer, base + channel * bytesPerSample, bitsPerSample,
                            audioFormat == 3);
                }
                out[done + i] = (float) (sum / channels);
            }
            done += got;
            frame += got;
            if (got < batch)
            {
                break;
            }
        }
        if (done == frames)
        {
            return out;
        }
        float[] trimmed = new float[done];
        System.arraycopy(out, 0, trimmed, 0, done);
        return trimmed;
    }

    private int readAt(long position, byte[] buffer, int length) throws IOException
    {
        if (position < 0L)
        {
            return -1;
        }
        if (bytes != null)
        {
            if (position >= bytes.length)
            {
                return -1;
            }
            int count = (int) Math.min(length, bytes.length - position);
            System.arraycopy(bytes, (int) position, buffer, 0, count);
            return count;
        }
        file.seek(position);
        int total = 0;
        while (total < length)
        {
            int read = file.read(buffer, total, length - total);
            if (read < 0)
            {
                break;
            }
            total += read;
        }
        return total == 0 ? -1 : total;
    }

    private static byte[] wav(byte[] pcm, int sampleRate)
    {
        byte[] result = new byte[44 + pcm.length];
        writeAscii(result, 0, "RIFF");
        writeIntLe(result, 4, 36 + pcm.length);
        writeAscii(result, 8, "WAVE");
        writeAscii(result, 12, "fmt ");
        writeIntLe(result, 16, 16);
        writeShortLe(result, 20, 1);
        writeShortLe(result, 22, 1);
        writeIntLe(result, 24, sampleRate);
        writeIntLe(result, 28, sampleRate * 2);
        writeShortLe(result, 32, 2);
        writeShortLe(result, 34, 16);
        writeAscii(result, 36, "data");
        writeIntLe(result, 40, pcm.length);
        System.arraycopy(pcm, 0, result, 44, pcm.length);
        return result;
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

    private static float readSample(byte[] data, int position, int bits, boolean floatFormat)
    {
        if (bits == 8)
        {
            return ((data[position] & 0xFF) - 128) / 128F;
        }
        if (bits == 16)
        {
            int value = (data[position] & 0xFF) | ((data[position + 1] & 0xFF) << 8);
            return (short) value / 32768F;
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

    private static void writeAscii(byte[] target, int offset, String text)
    {
        byte[] value = text.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(value, 0, target, offset, value.length);
    }

    private static void writeIntLe(byte[] target, int offset, int value)
    {
        target[offset] = (byte) (value & 0xFF);
        target[offset + 1] = (byte) ((value >> 8) & 0xFF);
        target[offset + 2] = (byte) ((value >> 16) & 0xFF);
        target[offset + 3] = (byte) ((value >> 24) & 0xFF);
    }

    private static void writeShortLe(byte[] target, int offset, int value)
    {
        target[offset] = (byte) (value & 0xFF);
        target[offset + 1] = (byte) ((value >> 8) & 0xFF);
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

    private static void closeQuietly(RandomAccessFile file)
    {
        try
        {
            if (file != null)
            {
                file.close();
            }
        }
        catch (IOException ignored)
        {
            // 关闭失败无需处理
        }
    }

    @Override
    public void close() throws IOException
    {
        if (file != null)
        {
            file.close();
        }
    }
}
