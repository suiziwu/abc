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
import com.ruoyi.project.domain.UserVideoWatch;
import com.ruoyi.project.service.IUserVideoWatchService;
import com.ruoyi.common.utils.poi.ExcelUtil;
import com.ruoyi.common.core.page.TableDataInfo;

/**
 * 用户视频观看记录Controller
 * 
 * @author ruoyi
 * @date 2026-07-01
 */
@RestController
@RequestMapping("/project/watch")
public class UserVideoWatchController extends BaseController
{
    @Autowired
    private IUserVideoWatchService userVideoWatchService;

    /**
     * 查询用户视频观看记录列表
     */
    @PreAuthorize("@ss.hasPermi('project:watch:list')")
    @GetMapping("/list")
    public TableDataInfo list(UserVideoWatch userVideoWatch)
    {
        startPage();
        List<UserVideoWatch> list = userVideoWatchService.selectUserVideoWatchList(userVideoWatch);
        return getDataTable(list);
    }

    /**
     * 导出用户视频观看记录列表
     */
    @PreAuthorize("@ss.hasPermi('project:watch:export')")
    @Log(title = "用户视频观看记录", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, UserVideoWatch userVideoWatch)
    {
        List<UserVideoWatch> list = userVideoWatchService.selectUserVideoWatchList(userVideoWatch);
        ExcelUtil<UserVideoWatch> util = new ExcelUtil<UserVideoWatch>(UserVideoWatch.class);
        util.exportExcel(response, list, "用户视频观看记录数据");
    }

    /**
     * 获取用户视频观看记录详细信息
     */
    @PreAuthorize("@ss.hasPermi('project:watch:query')")
    @GetMapping(value = "/{id}")
    public AjaxResult getInfo(@PathVariable("id") Long id)
    {
        return success(userVideoWatchService.selectUserVideoWatchById(id));
    }

    /**
     * 新增用户视频观看记录
     */
    @PreAuthorize("@ss.hasPermi('project:watch:add')")
    @Log(title = "用户视频观看记录", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody UserVideoWatch userVideoWatch)
    {
        return toAjax(userVideoWatchService.insertUserVideoWatch(userVideoWatch));
    }

    /**
     * 修改用户视频观看记录
     */
    @PreAuthorize("@ss.hasPermi('project:watch:edit')")
    @Log(title = "用户视频观看记录", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody UserVideoWatch userVideoWatch)
    {
        return toAjax(userVideoWatchService.updateUserVideoWatch(userVideoWatch));
    }

    /**
     * 删除用户视频观看记录
     */
    @PreAuthorize("@ss.hasPermi('project:watch:remove')")
    @Log(title = "用户视频观看记录", businessType = BusinessType.DELETE)
	@DeleteMapping("/{ids}")
    public AjaxResult remove(@PathVariable Long[] ids)
    {
        return toAjax(userVideoWatchService.deleteUserVideoWatchByIds(ids));
    }
}
