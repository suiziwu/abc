package com.ruoyi.web.controller.project;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import org.springframework.web.multipart.MultipartFile;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.project.domain.Subject;
import com.ruoyi.project.service.ISubjectService;
import com.ruoyi.common.utils.poi.ExcelUtil;
import com.ruoyi.common.core.page.TableDataInfo;

/**
 * 学科Controller
 * 
 * @author ruoyi
 * @date 2026-06-23
 */
@RestController
@RequestMapping("/project/subject")
public class SubjectController extends BaseController
{
    @Autowired
    private ISubjectService subjectService;

    /**
     * 查询学科列表
     */
    @PreAuthorize("@ss.hasPermi('project:subject:list')")
    @GetMapping("/list")
    public TableDataInfo list(Subject subject)
    {
        startPage();
        List<Subject> list = subjectService.selectSubjectList(subject);
        return getDataTable(list);
    }

    /**
     * 导出学科列表
     */
    @PreAuthorize("@ss.hasPermi('project:subject:export')")
    @Log(title = "学科", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, Subject subject)
    {
        List<Subject> list = subjectService.selectSubjectList(subject);
        ExcelUtil<Subject> util = new ExcelUtil<Subject>(Subject.class);
        util.exportExcel(response, list, "学科数据");
    }

    /**
     * 导入学科数据（Excel）
     */
    @PreAuthorize("@ss.hasPermi('project:subject:add')")
    @Log(title = "学科", businessType = BusinessType.IMPORT)
    @PostMapping("/importData")
    public AjaxResult importData(MultipartFile file, boolean updateSupport) throws Exception
    {
        if (file == null || file.isEmpty())
        {
            return error("请选择需要导入的 Excel 文件");
        }
        ExcelUtil<Subject> util = new ExcelUtil<Subject>(Subject.class);
        List<Subject> list = util.importExcel(file.getInputStream());
        if (list == null || list.isEmpty())
        {
            return error("Excel 中没有可导入的数据");
        }
        String operName = getUsername();
        Map<String, Subject> existingMap = new HashMap<String, Subject>();
        for (Subject item : subjectService.selectSubjectList(new Subject()))
        {
            if (StringUtils.isNotEmpty(item.getName()))
            {
                existingMap.put(item.getName().trim(), item);
            }
        }
        int inserted = 0;
        int updated = 0;
        int skipped = 0;
        StringBuilder tips = new StringBuilder();
        for (int i = 0; i < list.size(); i++)
        {
            Subject item = list.get(i);
            String name = item.getName() == null ? "" : item.getName().trim();
            if (StringUtils.isEmpty(name))
            {
                skipped++;
                tips.append("第 ").append(i + 2).append(" 行：学科名称为空，已跳过<br/>");
                continue;
            }
            item.setName(name);
            Subject existing = existingMap.get(name);
            try
            {
                if (existing != null)
                {
                    if (updateSupport)
                    {
                        item.setId(existing.getId());
                        item.setUpdateBy(operName);
                        subjectService.updateSubject(item);
                        updated++;
                    }
                    else
                    {
                        skipped++;
                        tips.append("第 ").append(i + 2).append(" 行：学科「").append(name).append("」已存在，已跳过<br/>");
                    }
                }
                else
                {
                    if (StringUtils.isEmpty(item.getStatus()))
                    {
                        item.setStatus("0");
                    }
                    item.setDelFlag("0");
                    item.setCreateBy(operName);
                    subjectService.insertSubject(item);
                    existingMap.put(name, item);
                    inserted++;
                }
            }
            catch (Exception e)
            {
                skipped++;
                tips.append("第 ").append(i + 2).append(" 行：导入失败（").append(e.getMessage()).append("）<br/>");
            }
        }
        String message = "导入完成：新增 " + inserted + " 条，更新 " + updated + " 条，跳过 " + skipped + " 条";
        if (tips.length() > 0)
        {
            message = message + "<br/>" + tips;
        }
        return success(message);
    }

    /**
     * 下载学科导入模板
     */
    @PostMapping("/importTemplate")
    public void importTemplate(HttpServletResponse response)
    {
        ExcelUtil<Subject> util = new ExcelUtil<Subject>(Subject.class);
        util.importTemplateExcel(response, "学科数据");
    }

    /**
     * 获取学科详细信息
     */
    @PreAuthorize("@ss.hasPermi('project:subject:query')")
    @GetMapping(value = "/{id}")
    public AjaxResult getInfo(@PathVariable("id") Long id)
    {
        return success(subjectService.selectSubjectById(id));
    }

    /**
     * 新增学科
     */
    @PreAuthorize("@ss.hasPermi('project:subject:add')")
    @Log(title = "学科", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody Subject subject)
    {
        return toAjax(subjectService.insertSubject(subject));
    }

    /**
     * 修改学科
     */
    @PreAuthorize("@ss.hasPermi('project:subject:edit')")
    @Log(title = "学科", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody Subject subject)
    {
        return toAjax(subjectService.updateSubject(subject));
    }

    /**
     * 删除学科
     */
    @PreAuthorize("@ss.hasPermi('project:subject:remove')")
    @Log(title = "学科", businessType = BusinessType.DELETE)
	@DeleteMapping("/{ids}")
    public AjaxResult remove(@PathVariable Long[] ids)
    {
        return toAjax(subjectService.deleteSubjectByIds(ids));
    }
}
