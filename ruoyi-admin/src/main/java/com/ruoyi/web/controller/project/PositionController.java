package com.ruoyi.web.controller.project;

import java.util.List;
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
import com.ruoyi.project.domain.Position;
import com.ruoyi.project.service.IPositionService;
import com.ruoyi.common.core.page.TableDataInfo;

/**
 * 岗位Controller
 * 
 * @author ruoyi
 * @date 2026-10-27
 */
@RestController
@RequestMapping("/project/position")
public class PositionController extends BaseController
{
    @Autowired
    private IPositionService positionService;

    /**
     * 查询岗位列表
     */
    @PreAuthorize("@ss.hasPermi('project:position:list')")
    @GetMapping("/list")
    public TableDataInfo list(Position position)
    {
        startPage();
        List<Position> list = positionService.selectPositionList(position);
        return getDataTable(list);
    }

    /**
     * 获取岗位详细信息
     */
    @PreAuthorize("@ss.hasPermi('project:position:query')")
    @GetMapping(value = "/{id}")
    public AjaxResult getInfo(@PathVariable("id") Long id)
    {
        return success(positionService.selectPositionById(id));
    }

    /**
     * 新增岗位
     */
    @PreAuthorize("@ss.hasPermi('project:position:add')")
    @Log(title = "岗位", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody Position position)
    {
        return toAjax(positionService.insertPosition(position));
    }

    /**
     * 修改岗位
     */
    @PreAuthorize("@ss.hasPermi('project:position:edit')")
    @Log(title = "岗位", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody Position position)
    {
        return toAjax(positionService.updatePosition(position));
    }

    /**
     * 删除岗位
     */
    @PreAuthorize("@ss.hasPermi('project:position:remove')")
    @Log(title = "岗位", businessType = BusinessType.DELETE)
	@DeleteMapping("/{ids}")
    public AjaxResult remove(@PathVariable Long[] ids)
    {
        return toAjax(positionService.deletePositionByIds(ids));
    }
}
