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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.project.domain.JobIndustryChain;
import com.ruoyi.project.service.IJobIndustryChainService;

/**
 * 岗位产业链与岗位群映射管理。
 *
 * @author ruoyi
 */
@RestController
@RequestMapping("/project/chain")
public class JobIndustryChainController extends BaseController
{
    @Autowired
    private IJobIndustryChainService chainService;

    @PreAuthorize("@ss.hasPermi('project:chain:list')")
    @GetMapping("/list")
    public TableDataInfo list(JobIndustryChain query)
    {
        startPage();
        return getDataTable(chainService.selectChainList(query));
    }

    @PreAuthorize("@ss.hasPermi('project:chain:query')")
    @GetMapping("/{id}")
    public AjaxResult getInfo(@PathVariable Long id)
    {
        return success(chainService.selectChainById(id));
    }

    /**
     * 按职业编码或名称查询产业链/岗位群映射。
     */
    @GetMapping("/byJob")
    public AjaxResult byJob(@RequestParam(required = false) String jobCode,
            @RequestParam(required = false) String jobName)
    {
        JobIndustryChain record = null;
        if (jobCode != null && !jobCode.trim().isEmpty())
        {
            record = chainService.selectChainByJobCode(jobCode.trim());
        }
        if (record == null && jobName != null && !jobName.trim().isEmpty())
        {
            record = chainService.selectChainByJobName(jobName.trim());
        }
        return success(record);
    }

    @PreAuthorize("@ss.hasPermi('project:chain:add')")
    @Log(title = "岗位产业链", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody JobIndustryChain record)
    {
        if (record.getJobName() == null || record.getJobName().trim().isEmpty())
        {
            return error("职业名称不能为空");
        }
        if (record.getChainName() == null || record.getChainName().trim().isEmpty())
        {
            return error("产业链/行业不能为空");
        }
        if (record.getSource() == null || record.getSource().trim().isEmpty())
        {
            record.setSource("manual");
        }
        int rows = chainService.insertChain(record);
        return rows > 0 ? success(record) : error("新增失败");
    }

    @PreAuthorize("@ss.hasPermi('project:chain:edit')")
    @Log(title = "岗位产业链", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody JobIndustryChain record)
    {
        return toAjax(chainService.updateChain(record));
    }

    @PreAuthorize("@ss.hasPermi('project:chain:remove')")
    @Log(title = "岗位产业链", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public AjaxResult remove(@PathVariable Long[] ids)
    {
        return toAjax(chainService.deleteChainByIds(ids));
    }
}
