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
import com.ruoyi.project.domain.UserLearnRecord;
import com.ruoyi.project.service.IUserLearnRecordService;
import com.ruoyi.common.utils.poi.ExcelUtil;
import com.ruoyi.common.core.page.TableDataInfo;

/**
 * 用户学习记录Controller
 * 
 * @author ruoyi
 * @date 2026-07-01
 */
@RestController
@RequestMapping("/project/record")
public class UserLearnRecordController extends BaseController
{
    @Autowired
    private IUserLearnRecordService userLearnRecordService;

    /**
     * 查询用户学习记录列表
     */
    @PreAuthorize("@ss.hasPermi('project:record:list')")
    @GetMapping("/list")
    public TableDataInfo list(UserLearnRecord userLearnRecord)
    {
        userLearnRecord.setUserId(getUserId());
        startPage();
        List<UserLearnRecord> list = userLearnRecordService.selectUserLearnRecordList(userLearnRecord);
        return getDataTable(list);
    }

    /**
     * 导出用户学习记录列表
     */
    @PreAuthorize("@ss.hasPermi('project:record:export')")
    @Log(title = "用户学习记录", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, UserLearnRecord userLearnRecord)
    {
        userLearnRecord.setUserId(getUserId());
        List<UserLearnRecord> list = userLearnRecordService.selectUserLearnRecordList(userLearnRecord);
        ExcelUtil<UserLearnRecord> util = new ExcelUtil<UserLearnRecord>(UserLearnRecord.class);
        util.exportExcel(response, list, "用户学习记录数据");
    }

    /**
     * 获取用户学习记录详细信息
     */
    @PreAuthorize("@ss.hasPermi('project:record:query')")
    @GetMapping(value = "/{id}")
    public AjaxResult getInfo(@PathVariable("id") Long id)
    {
        UserLearnRecord row = userLearnRecordService.selectUserLearnRecordById(id);
        if (row == null || !getUserId().equals(row.getUserId()))
        {
            return error("无权访问该记录");
        }
        return success(row);
    }

    /**
     * 新增用户学习记录
     */
    @PreAuthorize("@ss.hasPermi('project:record:add')")
    @Log(title = "用户学习记录", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody UserLearnRecord userLearnRecord)
    {
        userLearnRecord.setUserId(getUserId());
        return toAjax(userLearnRecordService.insertUserLearnRecord(userLearnRecord));
    }

    /**
     * 修改用户学习记录
     */
    @PreAuthorize("@ss.hasPermi('project:record:edit')")
    @Log(title = "用户学习记录", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody UserLearnRecord userLearnRecord)
    {
        userLearnRecord.setUserId(getUserId());
        return toAjax(userLearnRecordService.updateUserLearnRecord(userLearnRecord));
    }

    /**
     * 删除用户学习记录
     */
    @PreAuthorize("@ss.hasPermi('project:record:remove')")
    @Log(title = "用户学习记录", businessType = BusinessType.DELETE)
	@DeleteMapping("/{ids}")
    public AjaxResult remove(@PathVariable Long[] ids)
    {
        return toAjax(userLearnRecordService.deleteUserLearnRecordByIds(ids, getUserId()));
    }
}
