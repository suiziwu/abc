package com.ruoyi.project.asr;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Qwen-Audio-3.1-ASR-Flash-Filetrans 客户端测试（本地模拟 DashScope HTTP 接口）。
 *
 * @author ruoyi
 */
public class QwenFileTransClientTest
{
    @Test
    public void testDataUrlTaskPollAndTranscription() throws Exception
    {
        final AtomicInteger pollCount = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        final String base = "http://127.0.0.1:" + server.getAddress().getPort();

        server.createContext("/services/audio/asr/transcription", jsonHandler(exchange ->
        {
            String body = new String(readAll(exchange.getRequestBody()), StandardCharsets.UTF_8);
            assertTrue(body.contains("data:audio/wav;base64,"));
            return "{\"output\":{\"task_id\":\"task1\",\"task_status\":\"PENDING\"}}";
        }));
        server.createContext("/tasks/task1", jsonHandler(exchange ->
        {
            if (pollCount.getAndIncrement() == 0)
            {
                return "{\"output\":{\"task_id\":\"task1\",\"task_status\":\"RUNNING\"}}";
            }
            return "{\"output\":{\"task_id\":\"task1\",\"task_status\":\"SUCCEEDED\","
                    + "\"results\":[{\"transcription_url\":\"" + base + "/result.json\"}]}}";
        }));
        server.createContext("/result.json", jsonHandler(exchange ->
                "{\"transcripts\":[{\"text\":\"测试语音识别成功\"}]}"));
        server.start();

        try
        {
            AsrProperties properties = new AsrProperties();
            properties.setDashscopeBaseUrl(base);
            properties.setApiKey("test-key");
            properties.setModel("qwen-audio-3.1-asr-flash-filetrans");
            properties.setPollIntervalMs(10);
            properties.setTimeoutSeconds(5);

            QwenFileTransClient client = new QwenFileTransClient(properties);
            AsrResult result = client.transcribe(buildWav(16000, 1));
            assertEquals("测试语音识别成功", result.getText());
            assertEquals("qwen-audio-3.1-asr-flash-filetrans", result.getModel());
        }
        finally
        {
            server.stop(0);
        }
    }

    private HttpHandler jsonHandler(BodySupplier supplier)
    {
        return exchange ->
        {
            try
            {
                String body = supplier.get(exchange);
                send(exchange, 200, body);
            }
            catch (Exception e)
            {
                send(exchange, 500, "{\"message\":\"" + e.getMessage() + "\"}");
            }
        };
    }

    private void send(HttpExchange exchange, int status, String body) throws IOException
    {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody())
        {
            out.write(bytes);
        }
    }

    private byte[] readAll(InputStream inputStream) throws IOException
    {
        try (InputStream in = inputStream; ByteArrayOutputStream out = new ByteArrayOutputStream())
        {
            byte[] buffer = new byte[4096];
            int length;
            while ((length = in.read(buffer)) != -1)
            {
                out.write(buffer, 0, length);
            }
            return out.toByteArray();
        }
    }

    private byte[] buildWav(int sampleRate, int seconds)
    {
        int frames = sampleRate * seconds;
        int dataSize = frames * 2;
        ByteBuffer buffer = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put("RIFF".getBytes());
        buffer.putInt(36 + dataSize);
        buffer.put("WAVE".getBytes());
        buffer.put("fmt ".getBytes());
        buffer.putInt(16);
        buffer.putShort((short) 1);
        buffer.putShort((short) 1);
        buffer.putInt(sampleRate);
        buffer.putInt(sampleRate * 2);
        buffer.putShort((short) 2);
        buffer.putShort((short) 16);
        buffer.put("data".getBytes());
        buffer.putInt(dataSize);
        for (int i = 0; i < frames; i++)
        {
            buffer.putShort((short) (Math.sin(2 * Math.PI * 440 * i / sampleRate) * 8000));
        }
        return buffer.array();
    }

    private interface BodySupplier
    {
        String get(HttpExchange exchange) throws Exception;
    }
}
