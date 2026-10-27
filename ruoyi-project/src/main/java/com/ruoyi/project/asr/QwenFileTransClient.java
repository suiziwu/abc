package com.ruoyi.project.asr;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * 阿里云百炼 Qwen-Audio-3.1-ASR-Flash-Filetrans 录音文件识别客户端。
 *
 * <p>本地 WAV 转为 16k/16bit/mono 后，以 {@code data:audio/wav;base64,...} 作为
 * file_url 提交异步转写任务，轮询完成后下载 transcription_url 并提取文本。
 * 该方式无需把音频上传到公网或临时 OSS。</p>
 *
 * @author ruoyi
 */
@Component
public class QwenFileTransClient
{
    private final AsrProperties properties;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public QwenFileTransClient(AsrProperties properties)
    {
        this.properties = properties;
    }

    public AsrResult transcribe(byte[] wavBytes)
    {
        if (properties.getApiKey() == null || properties.getApiKey().trim().isEmpty())
        {
            throw new AsrException("未配置语音识别 API Key，请设置环境变量 DASHSCOPE_API_KEY");
        }
        long startedAt = System.nanoTime();
        String dataUrl = "data:audio/wav;base64,"
                + Base64.getEncoder().encodeToString(wavBytes);
        String taskId = createTask(dataUrl);
        TranscriptionResult transcription = poll(taskId);
        AsrResult result = new AsrResult();
        result.setText(transcription.text);
        result.setModel(properties.getModel());
        result.setAudioSeconds(transcription.audioSeconds);
        result.setDurationMs((System.nanoTime() - startedAt) / 1000000L);
        return result;
    }

    private String createTask(String fileUrl)
    {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", properties.getModel());
        ObjectNode input = body.putObject("input");
        input.putArray("file_urls").add(fileUrl);
        ObjectNode parameters = body.putObject("parameters");
        parameters.put("language", properties.getLanguage());

        Map<String, String> headers = authHeaders("application/json");
        headers.put("X-DashScope-Async", "enable");
        HttpResult result = request("POST",
                properties.getDashscopeBaseUrl() + "/services/audio/asr/transcription",
                headers, body.toString().getBytes(StandardCharsets.UTF_8), "application/json", 120);
        if (!result.isSuccess())
        {
            throw new AsrException("提交语音识别任务失败: " + result.body);
        }
        JsonNode root = readJson(result.body);
        String taskId = root.path("output").path("task_id").asText();
        if (taskId.isEmpty())
        {
            throw new AsrException("语音识别任务未返回 task_id: " + result.body);
        }
        return taskId;
    }

    private TranscriptionResult poll(String taskId)
    {
        long deadline = System.currentTimeMillis() + properties.getTimeoutSeconds() * 1000L;
        JsonNode last = null;
        String lastError = null;
        // 百炼任务创建后可能存在短暂的可查询延迟，先等待一个轮询周期
        sleep(properties.getPollIntervalMs());
        while (System.currentTimeMillis() < deadline)
        {
            HttpResult result = request("GET",
                    properties.getDashscopeBaseUrl() + "/tasks/" + taskId,
                    authHeaders(null), null, null, 30);
            if (!result.isSuccess())
            {
                // 任务刚创建时可能短暂返回 403/404，继续重试直到超时
                lastError = result.body;
                sleep(properties.getPollIntervalMs());
                continue;
            }
            last = readJson(result.body);
            JsonNode output = last.path("output");
            String status = output.path("task_status").asText();
            if ("SUCCEEDED".equalsIgnoreCase(status))
            {
                return readTranscription(output);
            }
            if ("FAILED".equalsIgnoreCase(status))
            {
                String code = output.path("code").asText();
                String message = output.path("message").asText();
                throw new AsrException("语音识别任务失败: " + code + " " + message);
            }
            sleep(properties.getPollIntervalMs());
        }
        throw new AsrException("语音识别任务超时（" + properties.getTimeoutSeconds() + "s）: "
                + (lastError != null ? lastError : (last == null ? taskId : last.toString())));
    }

    private TranscriptionResult readTranscription(JsonNode output)
    {
        TranscriptionResult result = new TranscriptionResult();
        result.audioSeconds = output.path("usage").path("duration").asDouble(0D);
        List<String> urls = new ArrayList<>();
        String directUrl = output.path("transcription_url").asText();
        if (!directUrl.isEmpty())
        {
            urls.add(directUrl);
        }
        JsonNode items = output.path("results");
        if (items.isArray())
        {
            for (JsonNode item : items)
            {
                String url = item.path("transcription_url").asText();
                if (!url.isEmpty() && !urls.contains(url))
                {
                    urls.add(url);
                }
            }
        }
        StringBuilder text = new StringBuilder();
        for (String url : urls)
        {
            HttpResult transcription = request("GET", url, new HashMap<String, String>(),
                    null, null, 60);
            if (!transcription.isSuccess())
            {
                throw new AsrException("下载语音识别结果失败: " + transcription.body);
            }
            JsonNode root = readJson(transcription.body);
            JsonNode transcripts = root.path("transcripts");
            if (transcripts.isArray())
            {
                for (JsonNode transcript : transcripts)
                {
                    text.append(transcript.path("text").asText(""));
                }
            }
        }
        if (text.length() == 0)
        {
            String fallback = output.path("text").asText();
            if (!fallback.isEmpty())
            {
                text.append(fallback);
            }
        }
        result.text = text.toString();
        return result;
    }

    private HttpResult request(String method, String url, Map<String, String> headers, byte[] body,
            String contentType, int timeoutSeconds)
    {
        HttpURLConnection connection = null;
        try
        {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestMethod(method);
            connection.setConnectTimeout(30000);
            connection.setReadTimeout(timeoutSeconds * 1000);
            for (Map.Entry<String, String> header : headers.entrySet())
            {
                connection.setRequestProperty(header.getKey(), header.getValue());
            }
            if (contentType != null)
            {
                connection.setRequestProperty("Content-Type", contentType);
            }
            if (body != null)
            {
                connection.setDoOutput(true);
                try (OutputStream out = connection.getOutputStream())
                {
                    out.write(body);
                }
            }
            int status = connection.getResponseCode();
            String responseBody = readBody(status >= 400 ? connection.getErrorStream()
                    : connection.getInputStream());
            return new HttpResult(status, responseBody);
        }
        catch (IOException e)
        {
            throw new AsrException("请求 DashScope 失败: " + e.getMessage(), e);
        }
        finally
        {
            if (connection != null)
            {
                connection.disconnect();
            }
        }
    }

    private Map<String, String> authHeaders(String contentType)
    {
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer " + properties.getApiKey().trim());
        if (contentType != null)
        {
            headers.put("Content-Type", contentType);
        }
        return headers;
    }

    private JsonNode readJson(String body)
    {
        try
        {
            return objectMapper.readTree(body);
        }
        catch (Exception e)
        {
            throw new AsrException("解析 DashScope 响应失败: " + body, e);
        }
    }

    private String readBody(InputStream inputStream)
    {
        if (inputStream == null)
        {
            return "";
        }
        try (InputStream in = inputStream; ByteArrayOutputStream out = new ByteArrayOutputStream())
        {
            byte[] buffer = new byte[4096];
            int length;
            while ((length = in.read(buffer)) != -1)
            {
                out.write(buffer, 0, length);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
        catch (IOException e)
        {
            return "";
        }
    }

    private void sleep(long millis)
    {
        try
        {
            Thread.sleep(millis);
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
            throw new AsrException("等待语音识别任务被中断", e);
        }
    }

    private static class HttpResult
    {
        private final int status;

        private final String body;

        private HttpResult(int status, String body)
        {
            this.status = status;
            this.body = body;
        }

        private boolean isSuccess()
        {
            return status >= 200 && status < 300;
        }
    }

    private static class TranscriptionResult
    {
        private String text = "";

        private double audioSeconds;
    }
}
