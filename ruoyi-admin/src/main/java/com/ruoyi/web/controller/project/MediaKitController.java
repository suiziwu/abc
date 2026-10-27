package com.ruoyi.web.controller.project;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import javax.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;

/**
 * media_kit 视频/文档解析代理。
 *
 * 实际解析由同机 Python 服务 tools/media_kit 完成（默认 127.0.0.1:8130），
 * 本控制器只负责鉴权体系内的转发、上传落盘与标准错误码透传。
 *
 * 启动 Python 服务：在项目根目录执行 .\tools\start_media_kit.ps1
 *
 * @author ruoyi
 */
@RestController
@RequestMapping("/project/media")
public class MediaKitController extends BaseController
{
    private static final Logger log = LoggerFactory.getLogger(MediaKitController.class);

    /** media_kit 服务地址 */
    @Value("${media-kit.base-url:http://127.0.0.1:8130}")
    private String baseUrl;

    /** 可选鉴权令牌，与 Python 服务 MEDIA_KIT_TOKEN 一致 */
    @Value("${media-kit.token:}")
    private String token;

    /** 单次解析超时（秒） */
    @Value("${media-kit.timeout-seconds:600}")
    private int timeoutSeconds;

    /** 上传文件保存目录；为空时使用 <ruoyi.profile>/media_kit */
    @Value("${media-kit.upload-dir:}")
    private String uploadDir;

    /** 若依上传根目录 */
    @Value("${ruoyi.profile:./upload}")
    private String profile;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private RestTemplate restTemplate;

    @PostConstruct
    public void init()
    {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(Math.max(10, timeoutSeconds) * 1000);
        this.restTemplate = new RestTemplate(factory);
    }

    /**
     * 解析服务健康检查：返回版本与已注册的视频/文档格式。
     */
    @GetMapping("/health")
    public AjaxResult health()
    {
        try
        {
            ResponseEntity<Map> response = restTemplate.exchange(
                    baseUrl + "/health", HttpMethod.GET, null, Map.class);
            return AjaxResult.success(response.getBody());
        }
        catch (RestClientException e)
        {
            return unavailable(e);
        }
    }

    /**
     * 解析服务器本地视频（path 为绝对路径）。
     * body: {"path": "D:/data/a.ts", "config": {...}}
     */
    @PostMapping("/video")
    public AjaxResult parseVideo(@RequestBody Map<String, Object> body)
    {
        return forward("/api/video", body);
    }

    /**
     * 解析服务器本地文档（path 为绝对路径）。
     * body: {"path": "D:/data/book.pdf", "config": {...}}
     */
    @PostMapping("/document")
    public AjaxResult parseDocument(@RequestBody Map<String, Object> body)
    {
        return forward("/api/document", body);
    }

    /**
     * 上传并解析视频（multipart）。
     *
     * @param file 视频文件
     * @param config 可选 JSON 字符串，例如 {"audio":{"enabled":true}}
     */
    @PostMapping("/video/upload")
    public AjaxResult uploadVideo(@RequestParam("file") MultipartFile file,
                                  @RequestParam(value = "config", required = false) String config)
    {
        return uploadAndParse(file, config, "video", "/api/video");
    }

    /**
     * 上传并解析文档（multipart）。
     */
    @PostMapping("/document/upload")
    public AjaxResult uploadDocument(@RequestParam("file") MultipartFile file,
                                     @RequestParam(value = "config", required = false) String config)
    {
        return uploadAndParse(file, config, "document", "/api/document");
    }

    // ------------------------------------------------------------------

    private AjaxResult forward(String endpoint, Map<String, Object> body)
    {
        if (body == null || body.get("path") == null || String.valueOf(body.get("path")).trim().isEmpty())
        {
            return AjaxResult.error("缺少 path 参数");
        }
        try
        {
            Map<String, Object> result = call(endpoint, body);
            return wrap(result);
        }
        catch (HttpStatusCodeException e)
        {
            return AjaxResult.error(extractError(e));
        }
        catch (RestClientException e)
        {
            return unavailable(e);
        }
    }

    private AjaxResult uploadAndParse(MultipartFile file, String config, String kind, String endpoint)
    {
        try
        {
            File saved = saveUpload(file, kind);
            Map<String, Object> body = new HashMap<String, Object>();
            body.put("path", saved.getAbsolutePath());
            if (config != null && !config.trim().isEmpty())
            {
                body.put("config", objectMapper.readValue(config, Map.class));
            }
            Map<String, Object> result = call(endpoint, body);
            return wrap(result);
        }
        catch (IllegalArgumentException e)
        {
            return AjaxResult.error(e.getMessage());
        }
        catch (HttpStatusCodeException e)
        {
            return AjaxResult.error(extractError(e));
        }
        catch (RestClientException e)
        {
            return unavailable(e);
        }
        catch (Exception e)
        {
            log.error("media_kit 上传解析失败", e);
            return AjaxResult.error("上传解析失败：" + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> call(String endpoint, Map<String, Object> payload)
    {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null && !token.trim().isEmpty())
        {
            headers.set("X-Media-Kit-Token", token.trim());
        }
        HttpEntity<Map<String, Object>> entity = new HttpEntity<Map<String, Object>>(payload, headers);
        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl + endpoint, HttpMethod.POST, entity, Map.class);
        return response.getBody();
    }

    /**
     * 把 media_kit 的标准结果转换为若依响应：
     * ok=true 时 data 为完整解析结果；ok=false 时返回错误码与明细。
     */
    @SuppressWarnings("unchecked")
    private AjaxResult wrap(Map<String, Object> result)
    {
        if (result == null)
        {
            return AjaxResult.error("media_kit 服务无响应");
        }
        if (Boolean.TRUE.equals(result.get("ok")))
        {
            return AjaxResult.success(result);
        }
        String code = "";
        String message = "解析失败";
        Object error = result.get("error");
        if (error instanceof Map)
        {
            Map<String, Object> detail = (Map<String, Object>) error;
            code = detail.get("code") == null ? "" : String.valueOf(detail.get("code"));
            message = detail.get("message") == null ? message : String.valueOf(detail.get("message"));
        }
        AjaxResult ajax = AjaxResult.error(message);
        ajax.put("errorCode", code);
        ajax.put("detail", result);
        return ajax;
    }

    /** 解析 Python 服务返回的 4xx JSON 错误。 */
    @SuppressWarnings("unchecked")
    private String extractError(HttpStatusCodeException e)
    {
        try
        {
            Map<String, Object> body = objectMapper.readValue(e.getResponseBodyAsString(), Map.class);
            Object error = body.get("error");
            if (error instanceof Map)
            {
                return String.valueOf(((Map<String, Object>) error).get("message"));
            }
        }
        catch (Exception ignore)
        {
            // 忽略解析失败，回退到状态码
        }
        return "media_kit 请求失败：HTTP " + e.getRawStatusCode();
    }

    private AjaxResult unavailable(RestClientException e)
    {
        log.warn("media_kit 服务不可用：{}", e.getMessage());
        AjaxResult ajax = AjaxResult.error("media_kit 解析服务不可用，请先执行 tools/start_media_kit.ps1");
        ajax.put("detail", e.getMessage());
        return ajax;
    }

    /**
     * 保存上传文件到 media-kit.upload-dir（默认 <ruoyi.profile>/media_kit/<kind>）。
     */
    private File saveUpload(MultipartFile file, String kind) throws IOException
    {
        if (file == null || file.isEmpty())
        {
            throw new IllegalArgumentException("上传文件为空");
        }
        Path targetDir;
        if (uploadDir != null && !uploadDir.trim().isEmpty())
        {
            targetDir = Paths.get(uploadDir.trim(), kind);
        }
        else
        {
            targetDir = Paths.get(profile, "media_kit", kind);
        }
        targetDir = targetDir.toAbsolutePath().normalize();
        Files.createDirectories(targetDir);

        String original = file.getOriginalFilename() == null ? "upload.bin" : file.getOriginalFilename();
        original = Paths.get(original).getFileName().toString().replaceAll("[\\\\/:*?\"<>|]", "_");
        String name = UUID.randomUUID().toString().replace("-", "").substring(0, 8) + "_" + original;
        File dest = targetDir.resolve(name).toFile();
        file.transferTo(dest);
        return dest;
    }
}
