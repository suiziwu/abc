package com.ruoyi.web.controller.project;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import com.ruoyi.project.domain.KnowledgePoint;
import com.ruoyi.project.domain.KnowledgePointImport;
import com.ruoyi.project.domain.Subject;
import com.ruoyi.project.service.IKnowledgePointService;
import com.ruoyi.project.service.ISubjectService;
import com.ruoyi.common.utils.poi.ExcelUtil;
import com.ruoyi.common.core.page.TableDataInfo;

/**
 * 知识点Controller
 * 
 * @author ruoyi
 * @date 2026-06-24
 */
@RestController
@RequestMapping("/project/point")
public class KnowledgePointController extends BaseController
{
    @Autowired
    private IKnowledgePointService knowledgePointService;

    @Autowired
    private ISubjectService subjectService;

    /**
     * 查询知识点列表
     */
    @PreAuthorize("@ss.hasPermi('project:point:list')")
    @GetMapping("/list")
    public TableDataInfo list(KnowledgePoint knowledgePoint)
    {
        startPage();
        List<KnowledgePoint> list = knowledgePointService.selectKnowledgePointList(knowledgePoint);
        return getDataTable(list);
    }

    /**
     * 导出知识点列表
     */
    @PreAuthorize("@ss.hasPermi('project:point:export')")
    @Log(title = "知识点", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, KnowledgePoint knowledgePoint)
    {
        List<KnowledgePoint> list = knowledgePointService.selectKnowledgePointList(knowledgePoint);
        ExcelUtil<KnowledgePoint> util = new ExcelUtil<KnowledgePoint>(KnowledgePoint.class);
        util.exportExcel(response, list, "知识点数据");
    }

    /**
     * 导入知识点数据（Excel）
     */
    @PreAuthorize("@ss.hasPermi('project:point:add')")
    @Log(title = "知识点", businessType = BusinessType.IMPORT)
    @PostMapping("/importData")
    public AjaxResult importData(MultipartFile file, boolean updateSupport) throws Exception
    {
        if (file == null || file.isEmpty())
        {
            return error("请选择需要导入的 Excel 文件");
        }
        ExcelUtil<KnowledgePointImport> util = new ExcelUtil<KnowledgePointImport>(KnowledgePointImport.class);
        List<KnowledgePointImport> list = util.importExcel(file.getInputStream());
        if (list == null || list.isEmpty())
        {
            return error("Excel 中没有可导入的数据");
        }

        Map<String, Long> subjectIdByName = new HashMap<String, Long>();
        Set<Long> subjectIds = new HashSet<Long>();
        for (Subject subject : subjectService.selectSubjectList(new Subject()))
        {
            if (StringUtils.isNotEmpty(subject.getName()))
            {
                subjectIdByName.put(subject.getName().trim(), subject.getId());
            }
            subjectIds.add(subject.getId());
        }

        Map<String, KnowledgePoint> existingMap = new HashMap<String, KnowledgePoint>();
        for (KnowledgePoint item : knowledgePointService.selectKnowledgePointList(new KnowledgePoint()))
        {
            existingMap.put(item.getSubjectId() + "|" + (item.getName() == null ? "" : item.getName().trim()), item);
        }

        String operName = getUsername();
        int inserted = 0;
        int updated = 0;
        int skipped = 0;
        StringBuilder tips = new StringBuilder();
        for (int i = 0; i < list.size(); i++)
        {
            KnowledgePointImport row = list.get(i);
            int rowNo = i + 2;
            String name = row.getName() == null ? "" : row.getName().trim();
            String subjectName = row.getSubjectName() == null ? "" : row.getSubjectName().trim();
            if (StringUtils.isEmpty(name))
            {
                skipped++;
                tips.append("第 ").append(rowNo).append(" 行：知识点名称为空，已跳过<br/>");
                continue;
            }
            Long subjectId = subjectIdByName.get(subjectName);
            if (subjectId == null || !subjectIds.contains(subjectId))
            {
                skipped++;
                tips.append("第 ").append(rowNo).append(" 行：未找到所属学科「").append(subjectName)
                        .append("」，请先在学科管理中创建<br/>");
                continue;
            }
            String key = subjectId + "|" + name;
            KnowledgePoint entity = new KnowledgePoint();
            entity.setSubjectId(subjectId);
            entity.setName(name);
            entity.setContent(row.getContent());
            entity.setDifficulty(parseDifficulty(row.getDifficulty()));
            entity.setImportance(parseImportance(row.getImportance()));
            entity.setType(StringUtils.isNotEmpty(row.getType()) ? row.getType().trim() : null);
            entity.setSortOrder(row.getSortOrder());
            entity.setAttachment(row.getAttachment());
            entity.setVideo(row.getVideo());
            String status = parseStatus(row.getStatus());
            try
            {
                KnowledgePoint existing = existingMap.get(key);
                if (existing != null)
                {
                    if (updateSupport)
                    {
                        entity.setId(existing.getId());
                        entity.setStatus(status);
                        entity.setUpdateBy(operName);
                        knowledgePointService.updateKnowledgePoint(entity);
                        updated++;
                    }
                    else
                    {
                        skipped++;
                        tips.append("第 ").append(rowNo).append(" 行：学科「").append(subjectName)
                                .append("」下已存在知识点「").append(name).append("」，已跳过<br/>");
                    }
                }
                else
                {
                    if (entity.getDifficulty() == null)
                    {
                        entity.setDifficulty(1L);
                    }
                    if (entity.getImportance() == null)
                    {
                        entity.setImportance(1L);
                    }
                    entity.setStatus(status == null ? "0" : status);
                    entity.setDelFlag("0");
                    entity.setCreateBy(operName);
                    knowledgePointService.insertKnowledgePoint(entity);
                    existingMap.put(key, entity);
                    inserted++;
                }
            }
            catch (Exception e)
            {
                skipped++;
                tips.append("第 ").append(rowNo).append(" 行：导入失败（").append(e.getMessage()).append("）<br/>");
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
     * 下载知识点导入模板
     */
    @PostMapping("/importTemplate")
    public void importTemplate(HttpServletResponse response)
    {
        ExcelUtil<KnowledgePointImport> util = new ExcelUtil<KnowledgePointImport>(KnowledgePointImport.class);
        util.importTemplateExcel(response, "知识点数据");
    }

    /**
     * 解析难度等级：支持「简单/较易/中等/较难/困难」或 1-5
     */
    private Long parseDifficulty(String value)
    {
        if (StringUtils.isEmpty(value))
        {
            return null;
        }
        String text = value.trim();
        if (text.matches("\\d+"))
        {
            return Long.valueOf(text);
        }
        if (text.contains("简单"))
        {
            return 1L;
        }
        if (text.contains("较易"))
        {
            return 2L;
        }
        if (text.contains("中等"))
        {
            return 3L;
        }
        if (text.contains("较难"))
        {
            return 4L;
        }
        if (text.contains("困难"))
        {
            return 5L;
        }
        return null;
    }

    /**
     * 解析重要程度：支持「了解/掌握/重点/核心」或 1-4
     */
    private Long parseImportance(String value)
    {
        if (StringUtils.isEmpty(value))
        {
            return null;
        }
        String text = value.trim();
        if (text.matches("\\d+"))
        {
            return Long.valueOf(text);
        }
        if (text.contains("了解"))
        {
            return 1L;
        }
        if (text.contains("掌握"))
        {
            return 2L;
        }
        if (text.contains("重点"))
        {
            return 3L;
        }
        if (text.contains("核心"))
        {
            return 4L;
        }
        return null;
    }

    /**
     * 解析状态：正常/启用/0 → 0，停用/禁用/1 → 1
     */
    private String parseStatus(String value)
    {
        if (StringUtils.isEmpty(value))
        {
            return null;
        }
        String text = value.trim();
        if ("0".equals(text) || text.contains("正常") || text.contains("启用"))
        {
            return "0";
        }
        if ("1".equals(text) || text.contains("停用") || text.contains("禁用"))
        {
            return "1";
        }
        return null;
    }

    /**
     * 获取知识点详细信息
     */
    @PreAuthorize("@ss.hasPermi('project:point:query')")
    @GetMapping(value = "/{id}")
    public AjaxResult getInfo(@PathVariable("id") Long id)
    {
        return success(knowledgePointService.selectKnowledgePointById(id));
    }

    /**
     * 新增知识点
     */
    @PreAuthorize("@ss.hasPermi('project:point:add')")
    @Log(title = "知识点", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody KnowledgePoint knowledgePoint)
    {
        return toAjax(knowledgePointService.insertKnowledgePoint(knowledgePoint));
    }

    /**
     * 修改知识点
     */
    @PreAuthorize("@ss.hasPermi('project:point:edit')")
    @Log(title = "知识点", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody KnowledgePoint knowledgePoint)
    {
        return toAjax(knowledgePointService.updateKnowledgePoint(knowledgePoint));
    }

    /**
     * 删除知识点
     */
    @PreAuthorize("@ss.hasPermi('project:point:remove')")
    @Log(title = "知识点", businessType = BusinessType.DELETE)
	@DeleteMapping("/{ids}")
    public AjaxResult remove(@PathVariable Long[] ids)
    {
        return toAjax(knowledgePointService.deleteKnowledgePointByIds(ids));
    }
}
