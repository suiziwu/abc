package com.ruoyi.web.controller.project;

import java.util.List;
import javax.servlet.http.HttpServletResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.project.domain.AiChatHistory;
import com.ruoyi.project.service.IAiChatHistoryService;
import com.ruoyi.common.utils.poi.ExcelUtil;
import com.ruoyi.common.core.page.TableDataInfo;

/**
 * AI对话历史记录Controller
 * 
 * @author ruoyi
 * @date 2026-06-30
 */
@RestController
@RequestMapping("/project/history")
public class AiChatHistoryController extends BaseController
{
    @Autowired
    private IAiChatHistoryService aiChatHistoryService;

    /**
     * 查询AI对话历史记录列表
     */
    @PreAuthorize("@ss.hasPermi('project:history:list')")
    @GetMapping("/list")
    public TableDataInfo list(AiChatHistory aiChatHistory)
    {
        aiChatHistory.setUserId(getUserId());
        startPage();
        List<AiChatHistory> list = aiChatHistoryService.selectAiChatHistoryList(aiChatHistory);
        return getDataTable(list);
    }

    /**
     * 导出AI对话历史记录列表
     */
    @PreAuthorize("@ss.hasPermi('project:history:export')")
    @Log(title = "AI对话历史记录", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, AiChatHistory aiChatHistory)
    {
        List<AiChatHistory> list = aiChatHistoryService.selectAiChatHistoryList(aiChatHistory);
        ExcelUtil<AiChatHistory> util = new ExcelUtil<AiChatHistory>(AiChatHistory.class);
        util.exportExcel(response, list, "AI对话历史记录数据");
    }

    /**
     * 获取AI对话历史记录详细信息
     */
    @PreAuthorize("@ss.hasPermi('project:history:query')")
    @GetMapping(value = "/{id}")
    public AjaxResult getInfo(@PathVariable("id") Long id)
    {
        return success(aiChatHistoryService.selectAiChatHistoryById(id));
    }

    /**
     * 新增AI对话历史记录
     */
    @PreAuthorize("@ss.hasPermi('project:history:add')")
    @Log(title = "AI对话历史记录", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody AiChatHistory aiChatHistory)
    {
        aiChatHistory.setUserId(getUserId());
        return toAjax(aiChatHistoryService.insertAiChatHistory(aiChatHistory));
    }

    /**
     * 修改AI对话历史记录
     */
    @PreAuthorize("@ss.hasPermi('project:history:edit')")
    @Log(title = "AI对话历史记录", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody AiChatHistory aiChatHistory)
    {
        return toAjax(aiChatHistoryService.updateAiChatHistory(aiChatHistory));
    }

    /**
     * 删除AI对话历史记录
     */
    @PreAuthorize("@ss.hasPermi('project:history:remove')")
    @Log(title = "AI对话历史记录", businessType = BusinessType.DELETE)
	@DeleteMapping("/{ids}")
    public AjaxResult remove(@PathVariable Long[] ids)
    {
        return toAjax(aiChatHistoryService.deleteAiChatHistoryByIds(ids));
    }
}
