package com.ruoyi.web.controller.project;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.project.asr.AsrResult;
import com.ruoyi.project.asr.AsrService;

/**
 * Fun-ASR 语音识别接口。
 *
 * @author ruoyi
 */
@RestController
@RequestMapping("/project/asr")
public class AsrController extends BaseController
{
    private static final Logger logger = LoggerFactory.getLogger(AsrController.class);

    private static final long MAX_FILE_SIZE = 25L * 1024L * 1024L;

    @Autowired
    private AsrService asrService;

    /**
     * 上传 WAV 音频并提取文本（仅文本提取，不含实时采集/流处理）。
     *
     * @param file 上传的 WAV 音频文件
     */
    @PostMapping("/transcribe")
    public AjaxResult transcribe(@RequestParam(value = "file", required = false) MultipartFile file)
    {
        if (file == null || file.isEmpty())
        {
            return error("音频文件不能为空");
        }
        if (file.getSize() > MAX_FILE_SIZE)
        {
            return error("音频文件过大，最大支持 25MB");
        }
        try
        {
            AsrResult result = asrService.transcribe(file.getBytes());
            return success(result);
        }
        catch (IOException e)
        {
            logger.error("读取上传音频失败", e);
            return error("读取音频文件失败");
        }
        catch (Exception e)
        {
            logger.error("Qwen-Audio 语音识别失败", e);
            return error(friendlyError(e));
        }
    }

    private String friendlyError(Exception e)
    {
        String message = e.getMessage() == null ? "" : e.getMessage();
        if (message.contains("AccessDenied") || message.contains("Unpurchased")
                || message.contains("Arrearage") || message.contains("arrears"))
        {
            return "百炼语音识别模型未开通或账户欠费，请在百炼控制台开通 qwen-audio-3.1-asr-flash-filetrans 并确认账户余额。详情："
                    + message;
        }
        return message;
    }
}
