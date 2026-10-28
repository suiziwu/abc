package com.ruoyi.web.controller.project;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.project.domain.JobCompetencyWeight;
import com.ruoyi.project.service.IJobCompetencyWeightService;

/**
 * 岗位能力权重管理。
 *
 * @author ruoyi
 */
@RestController
@RequestMapping("/project/weight")
public class JobCompetencyWeightController extends BaseController
{
    @Autowired
    private IJobCompetencyWeightService weightService;

    @PreAuthorize("@ss.hasPermi('project:weight:list')")
    @GetMapping("/list")
    public TableDataInfo list(JobCompetencyWeight query)
    {
        startPage();
        return getDataTable(weightService.selectWeightList(query));
    }

    @PreAuthorize("@ss.hasPermi('project:weight:query')")
    @GetMapping("/{id}")
    public AjaxResult getInfo(@PathVariable Long id)
    {
        return success(weightService.selectWeightById(id));
    }

    @PreAuthorize("@ss.hasPermi('project:weight:add')")
    @Log(title = "岗位能力权重", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody JobCompetencyWeight record)
    {
        if (record.getSource() == null || record.getSource().isEmpty())
        {
            record.setSource("manual");
        }
        int rows = weightService.insertWeight(record);
        return rows > 0 ? success(record) : error("新增失败");
    }

    @PreAuthorize("@ss.hasPermi('project:weight:edit')")
    @Log(title = "岗位能力权重", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody JobCompetencyWeight record)
    {
        return toAjax(weightService.updateWeight(record));
    }

    @PreAuthorize("@ss.hasPermi('project:weight:remove')")
    @Log(title = "岗位能力权重", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public AjaxResult remove(@PathVariable Long[] ids)
    {
        return toAjax(weightService.deleteWeightByIds(ids));
    }
}
