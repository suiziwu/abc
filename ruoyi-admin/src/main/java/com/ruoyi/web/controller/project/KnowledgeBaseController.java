package com.ruoyi.web.controller.project;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.project.domain.KbChunk;
import com.ruoyi.project.kb.KbDocumentImportService;
import com.ruoyi.project.kb.KbIngestService;
import com.ruoyi.project.kb.KbIngestService.TranscriptLine;

/**
 * 知识库管理：状态、重建、检索、清空。
 *
 * @author ruoyi
 */
@RestController
@RequestMapping("/project/kb")
public class KnowledgeBaseController extends BaseController
{
    @Autowired
    private KbIngestService kbIngestService;

    @Autowired
    private KbDocumentImportService kbDocumentImportService;

    /**
     * 知识库状态：分块总数、各类型数量、向量库实现与可用性、向量化配置。
     */
    @PreAuthorize("@ss.hasPermi('project:point:list')")
    @GetMapping("/status")
    public AjaxResult status(@RequestParam(value = "subjectId", required = false) Long subjectId)
    {
        try
        {
            return success(kbIngestService.stats(subjectId));
        }
        catch (Exception e)
        {
            logger.error("查询知识库状态失败", e);
            return error("查询知识库状态失败: " + e.getMessage());
        }
    }

    /**
     * 可切换的知识库列表（全局 / 学科 / 章节，含分块数）。
     *
     * <p>只返回名称与分块数量，不返回正文；登录用户即可访问，供课程学习页的
     * 「知识库」下拉一键切换检索范围。前端用返回的 subjectId / chapterId 调用
     * {@code /project/course/chat} 即可切换。</p>
     */
    @GetMapping("/list")
    public AjaxResult list()
    {
        try
        {
            Map<String, Object> data = new java.util.LinkedHashMap<String, Object>();
            data.put("enabled", kbIngestService.isEnabled());
            data.put("bases", kbIngestService.listBases());
            return success(data);
        }
        catch (Exception e)
        {
            logger.error("查询知识库列表失败", e);
            return error("查询知识库列表失败: " + e.getMessage());
        }
    }

    /**
     * 重建知识库（按学科/章节，可选附带逐字稿）。
     *
     * <p>请求体示例：
     * <pre>
     * {
     *   "subjectId": 94,
     *   "chapterId": 10,
     *   "testId": 102,
     *   "transcript": [{"start": 1, "text": "计算机网络是..."}]
     * }
     * </pre></p>
     */
    @PreAuthorize("@ss.hasPermi('project:point:edit')")
    @Log(title = "知识库", businessType = BusinessType.INSERT)
    @PostMapping("/rebuild")
    public AjaxResult rebuild(@RequestBody Map<String, Object> body)
    {
        if (body == null || body.get("subjectId") == null)
        {
            return error("subjectId 不能为空");
        }
        try
        {
            Long subjectId = toLong(body.get("subjectId"));
            Long chapterId = toLong(body.get("chapterId"));
            Long testId = toLong(body.get("testId"));
            List<TranscriptLine> transcript = null;
            Object rawTranscript = body.get("transcript");
            if (rawTranscript instanceof List)
            {
                transcript = new java.util.ArrayList<>();
                for (Object item : (List<?>) rawTranscript)
                {
                    if (item instanceof Map)
                    {
                        Map<?, ?> map = (Map<?, ?>) item;
                        TranscriptLine line = new TranscriptLine();
                        line.setStart(toInt(map.get("start")));
                        line.setText(map.get("text") == null ? "" : String.valueOf(map.get("text")));
                        transcript.add(line);
                    }
                }
            }
            return success(kbIngestService.rebuild(subjectId, chapterId, testId, transcript));
        }
        catch (Exception e)
        {
            logger.error("重建知识库失败", e);
            return error("重建知识库失败: " + e.getMessage());
        }
    }

    /**
     * 语义检索（调试用）。
     */
    @PreAuthorize("@ss.hasPermi('project:point:list')")
    @GetMapping("/search")
    public AjaxResult search(@RequestParam("q") String query,
            @RequestParam(value = "subjectId", required = false) Long subjectId,
            @RequestParam(value = "chapterId", required = false) Long chapterId,
            @RequestParam(value = "topK", required = false) Integer topK)
    {
        try
        {
            List<KbChunk> hits = kbIngestService.search(query, subjectId, chapterId, topK);
            return success(hits);
        }
        catch (Exception e)
        {
            logger.error("知识库检索失败", e);
            return error("知识库检索失败: " + e.getMessage());
        }
    }

    /**
     * 清空知识库（必须指定学科，可选按章节/类型收窄）。
     */
    @PreAuthorize("@ss.hasPermi('project:point:remove')")
    @Log(title = "知识库", businessType = BusinessType.DELETE)
    @DeleteMapping("/clear")
    public AjaxResult clear(@RequestParam(value = "subjectId", required = false) Long subjectId,
            @RequestParam(value = "chapterId", required = false) Long chapterId,
            @RequestParam(value = "sourceType", required = false) String sourceType)
    {
        // 学科是必填边界：缺少学科会退化为跨学科删除，必须拒绝
        if (subjectId == null)
        {
            return error("请指定学科后再清空知识库");
        }
        try
        {
            Map<String, Object> result = new java.util.LinkedHashMap<>();
            result.put("removed", kbIngestService.clear(subjectId, chapterId, sourceType));
            return success(result);
        }
        catch (Exception e)
        {
            logger.error("清空知识库失败", e);
            return error("清空知识库失败: " + e.getMessage());
        }
    }

    /**
     * 导入课本文档：上传文件 → 解析 → LLM 语义切片 → 向量化 → 写入指定学科知识库。
     */
    @PreAuthorize("@ss.hasPermi('project:point:edit')")
    @Log(title = "知识库", businessType = BusinessType.INSERT)
    @PostMapping("/import-document")
    public AjaxResult importDocument(@RequestParam("file") MultipartFile file,
            @RequestParam("subjectId") Long subjectId,
            @RequestParam(value = "chapterId", required = false) Long chapterId,
            @RequestParam(value = "model", required = false) String model,
            @RequestParam(value = "subjectName", required = false) String subjectName)
    {
        if (file == null || file.isEmpty())
        {
            return error("请上传课本文件");
        }
        try
        {
            byte[] bytes = file.getBytes();
            Map<String, Object> data = kbDocumentImportService.importDocument(bytes,
                    file.getOriginalFilename(), subjectId, subjectName, chapterId, model,
                    SecurityUtils.getUsername());
            if (Boolean.FALSE.equals(data.get("success")))
            {
                return error(String.valueOf(data.get("error")));
            }
            return success(data);
        }
        catch (Exception e)
        {
            logger.error("导入课本失败", e);
            return error("导入课本失败: " + e.getMessage());
        }
    }

    /**
     * 浏览知识库切片（按学科/章节/类型过滤，可分页）。
     */
    @PreAuthorize("@ss.hasPermi('project:point:list')")
    @GetMapping("/chunks")
    public AjaxResult chunks(@RequestParam(value = "subjectId", required = false) Long subjectId,
            @RequestParam(value = "chapterId", required = false) Long chapterId,
            @RequestParam(value = "sourceType", required = false) String sourceType,
            @RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
            @RequestParam(value = "pageSize", defaultValue = "50") Integer pageSize)
    {
        try
        {
            java.util.List<String> types = null;
            if (sourceType != null && !sourceType.trim().isEmpty())
            {
                types = new java.util.ArrayList<String>();
                for (String t : sourceType.split(","))
                {
                    if (!t.trim().isEmpty())
                    {
                        types.add(t.trim());
                    }
                }
            }
            java.util.List<KbChunk> all = kbDocumentImportService.listChunks(subjectId, chapterId, types);
            int total = all.size();
            int from = Math.max(0, (pageNum - 1) * pageSize);
            int to = Math.min(total, from + pageSize);
            java.util.Map<String, Object> data = new java.util.LinkedHashMap<String, Object>();
            data.put("total", total);
            data.put("rows", from >= total ? new java.util.ArrayList<KbChunk>() : all.subList(from, to));
            return success(data);
        }
        catch (Exception e)
        {
            logger.error("查询知识库切片失败", e);
            return error("查询知识库切片失败: " + e.getMessage());
        }
    }

    private static Long toLong(Object value)
    {
        if (value == null || String.valueOf(value).trim().isEmpty())
        {
            return null;
        }
        return Long.valueOf(String.valueOf(value).trim());
    }

    private static Integer toInt(Object value)
    {
        if (value == null || String.valueOf(value).trim().isEmpty())
        {
            return null;
        }
        try
        {
            return (int) Double.parseDouble(String.valueOf(value).trim());
        }
        catch (NumberFormatException e)
        {
            return null;
        }
    }
}