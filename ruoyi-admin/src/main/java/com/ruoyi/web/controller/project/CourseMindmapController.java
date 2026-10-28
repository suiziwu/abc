package com.ruoyi.web.controller.project;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.project.course.domain.MindmapAnalyzeRequest;
import com.ruoyi.project.course.domain.MindmapAnalyzeResult;
import com.ruoyi.project.course.domain.MindmapSyncResult;
import com.ruoyi.project.course.service.CourseMindmapService;

/**
 * 视频知识结构化梳理：AI 导图生成 + 知识图谱同步。
 *
 * @author ruoyi
 */
@RestController
@RequestMapping("/project/course/mindmap")
public class CourseMindmapController extends BaseController
{
    @Autowired
    private CourseMindmapService courseMindmapService;

    /**
     * AI 分析视频逐字稿，生成结构化知识导图（不写库，仅保存分析记录）。
     */
    @PreAuthorize("@ss.hasPermi('project:point:edit')")
    @PostMapping("/analyze")
    public AjaxResult analyze(@RequestBody MindmapAnalyzeRequest request)
    {
        try
        {
            MindmapAnalyzeResult result = courseMindmapService.analyze(request);
            return success(result);
        }
        catch (Exception e)
        {
            logger.error("AI 知识结构化梳理失败", e);
            return error(friendlyError(e));
        }
    }

    /**
     * 将分析结果标准化后同步到知识图谱（新增/映射知识点、写入关系、绑定章节）。
     *
     * @param body extractionId
     */
    @PreAuthorize("@ss.hasPermi('project:point:edit')")
    @PostMapping("/sync")
    public AjaxResult sync(@RequestBody Map<String, Object> body)
    {
        Object value = body == null ? null : body.get("extractionId");
        Long extractionId = null;
        if (value != null && !String.valueOf(value).trim().isEmpty())
        {
            try
            {
                extractionId = Long.valueOf(String.valueOf(value).trim());
            }
            catch (NumberFormatException e)
            {
                return error("extractionId 格式不正确");
            }
        }
        if (extractionId == null)
        {
            return error("extractionId 不能为空");
        }
        try
        {
            MindmapSyncResult result = courseMindmapService.sync(extractionId);
            return success(result);
        }
        catch (Exception e)
        {
            logger.error("知识图谱同步失败", e);
            return error("知识图谱同步失败: " + e.getMessage());
        }
    }

    /**
     * 查询某学科最近的结构化梳理记录（可追溯）。
     */
    @PreAuthorize("@ss.hasPermi('project:point:list')")
    @GetMapping("/extractions")
    public AjaxResult extractions(@RequestParam("subjectId") Long subjectId)
    {
        try
        {
            return success(courseMindmapService.recent(subjectId));
        }
        catch (Exception e)
        {
            logger.error("查询梳理记录失败", e);
            return error("查询梳理记录失败: " + e.getMessage());
        }
    }

    private String friendlyError(Exception e)
    {
        String message = e.getMessage() == null ? "" : e.getMessage();
        if (message.contains("AccessDenied") || message.contains("Unpurchased")
                || message.contains("Arrearage") || message.contains("arrears"))
        {
            return "模型未开通或账户欠费，请在百炼控制台确认余额。详情：" + message;
        }
        String lower = message.toLowerCase();
        if (lower.contains("invalid api key") || lower.contains("authentication")
                || lower.contains("insufficient balance") || lower.contains("unauthorized"))
        {
            return "所选模型的 API Key 无效或余额不足，请检查 config/application.yml。详情：" + message;
        }
        return message;
    }
}
