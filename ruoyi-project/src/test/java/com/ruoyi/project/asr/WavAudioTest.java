package com.ruoyi.project.asr;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WAV 解析与重采样测试。
 *
 * @author ruoyi
 */
public class WavAudioTest
{
    @Test
    public void testParse16kMonoWav()
    {
        byte[] wav = buildWav(16000, 1, 1);
        WavAudio audio = WavAudio.parse(wav);
        assertEquals(16000, audio.getSampleRate());
        assertEquals(1.0D, audio.durationSeconds(), 0.001D);
        assertEquals(32000, audio.toPcm16Le().length);
        byte[] normalized = audio.toWavBytes();
        assertEquals(44 + 32000, normalized.length);
        assertEquals(1.0D, WavAudio.parse(normalized).durationSeconds(), 0.001D);
    }

    @Test
    public void testResample48kTo16k()
    {
        byte[] wav = buildWav(48000, 1, 1);
        WavAudio audio = WavAudio.parse(wav).resample(16000);
        assertEquals(16000, audio.getSampleRate());
        assertEquals(1.0D, audio.durationSeconds(), 0.01D);
        assertEquals(32000, audio.toPcm16Le().length);
    }

    @Test
    public void testInvalidWav()
    {
        assertThrows(AsrException.class, () -> WavAudio.parse(new byte[] {1, 2, 3, 4}));
        assertThrows(AsrException.class,
                () -> WavAudio.parse("not a wav file at all, just some text padding....".getBytes()));
    }

    private byte[] buildWav(int sampleRate, int seconds, int channels)
    {
        int frames = sampleRate * seconds;
        int dataSize = frames * channels * 2;
        ByteBuffer buffer = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put("RIFF".getBytes());
        buffer.putInt(36 + dataSize);
        buffer.put("WAVE".getBytes());
        buffer.put("fmt ".getBytes());
        buffer.putInt(16);
        buffer.putShort((short) 1);
        buffer.putShort((short) channels);
        buffer.putInt(sampleRate);
        buffer.putInt(sampleRate * channels * 2);
        buffer.putShort((short) (channels * 2));
        buffer.putShort((short) 16);
        buffer.put("data".getBytes());
        buffer.putInt(dataSize);
        for (int i = 0; i < frames; i++)
        {
            short sample = (short) (Math.sin(2 * Math.PI * 440 * i / sampleRate) * 8000);
            for (int channel = 0; channel < channels; channel++)
            {
                buffer.putShort(sample);
            }
        }
        return buffer.array();
    }
}
