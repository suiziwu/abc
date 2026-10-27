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
import com.ruoyi.project.domain.KnowledgePointRelation;
import com.ruoyi.project.service.IKnowledgePointRelationService;
import com.ruoyi.common.utils.poi.ExcelUtil;
import com.ruoyi.common.core.page.TableDataInfo;

/**
 * 知识点关联（拓扑图边）Controller
 * 
 * @author ruoyi
 * @date 2026-06-23
 */
@RestController
@RequestMapping("/project/relation")
public class KnowledgePointRelationController extends BaseController
{
    @Autowired
    private IKnowledgePointRelationService knowledgePointRelationService;

    /**
     * 查询知识点关联（拓扑图边）列表
     */
    @PreAuthorize("@ss.hasPermi('project:relation:list')")
    @GetMapping("/list")
    public TableDataInfo list(KnowledgePointRelation knowledgePointRelation)
    {
        startPage();
        List<KnowledgePointRelation> list = knowledgePointRelationService.selectKnowledgePointRelationList(knowledgePointRelation);
        return getDataTable(list);
    }

    /**
     * 导出知识点关联（拓扑图边）列表
     */
    @PreAuthorize("@ss.hasPermi('project:relation:export')")
    @Log(title = "知识点关联（拓扑图边）", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, KnowledgePointRelation knowledgePointRelation)
    {
        List<KnowledgePointRelation> list = knowledgePointRelationService.selectKnowledgePointRelationList(knowledgePointRelation);
        ExcelUtil<KnowledgePointRelation> util = new ExcelUtil<KnowledgePointRelation>(KnowledgePointRelation.class);
        util.exportExcel(response, list, "知识点关联（拓扑图边）数据");
    }

    /**
     * 获取知识点关联（拓扑图边）详细信息
     */
    @PreAuthorize("@ss.hasPermi('project:relation:query')")
    @GetMapping(value = "/{id}")
    public AjaxResult getInfo(@PathVariable("id") Long id)
    {
        return success(knowledgePointRelationService.selectKnowledgePointRelationById(id));
    }

    /**
     * 新增知识点关联（拓扑图边）
     */
    @PreAuthorize("@ss.hasPermi('project:relation:add')")
    @Log(title = "知识点关联（拓扑图边）", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody KnowledgePointRelation knowledgePointRelation)
    {
        return toAjax(knowledgePointRelationService.insertKnowledgePointRelation(knowledgePointRelation));
    }

    /**
     * 修改知识点关联（拓扑图边）
     */
    @PreAuthorize("@ss.hasPermi('project:relation:edit')")
    @Log(title = "知识点关联（拓扑图边）", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody KnowledgePointRelation knowledgePointRelation)
    {
        return toAjax(knowledgePointRelationService.updateKnowledgePointRelation(knowledgePointRelation));
    }

    /**
     * 删除知识点关联（拓扑图边）
     */
    @PreAuthorize("@ss.hasPermi('project:relation:remove')")
    @Log(title = "知识点关联（拓扑图边）", businessType = BusinessType.DELETE)
	@DeleteMapping("/{ids}")
    public AjaxResult remove(@PathVariable Long[] ids)
    {
        return toAjax(knowledgePointRelationService.deleteKnowledgePointRelationByIds(ids));
    }
}
