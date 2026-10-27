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
import com.ruoyi.project.domain.UserKnowledgeAssessment;
import com.ruoyi.project.service.IUserKnowledgeAssessmentService;
import com.ruoyi.common.utils.poi.ExcelUtil;
import com.ruoyi.common.core.page.TableDataInfo;

/**
 * 用户知识点测评记录Controller
 * 
 * @author ruoyi
 * @date 2026-07-10
 */
@RestController
@RequestMapping("/project/assessment")
public class UserKnowledgeAssessmentController extends BaseController
{
    @Autowired
    private IUserKnowledgeAssessmentService userKnowledgeAssessmentService;

    /**
     * 查询用户知识点测评记录列表
     */
    @PreAuthorize("@ss.hasPermi('project:assessment:list')")
    @GetMapping("/list")
    public TableDataInfo list(UserKnowledgeAssessment userKnowledgeAssessment)
    {
        userKnowledgeAssessment.setUserId(getUserId());
        startPage();
        List<UserKnowledgeAssessment> list = userKnowledgeAssessmentService.selectUserKnowledgeAssessmentList(userKnowledgeAssessment);
        return getDataTable(list);
    }

    /**
     * 导出用户知识点测评记录列表
     */
    @PreAuthorize("@ss.hasPermi('project:assessment:export')")
    @Log(title = "用户知识点测评记录", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, UserKnowledgeAssessment userKnowledgeAssessment)
    {
        List<UserKnowledgeAssessment> list = userKnowledgeAssessmentService.selectUserKnowledgeAssessmentList(userKnowledgeAssessment);
        ExcelUtil<UserKnowledgeAssessment> util = new ExcelUtil<UserKnowledgeAssessment>(UserKnowledgeAssessment.class);
        util.exportExcel(response, list, "用户知识点测评记录数据");
    }

    /**
     * 获取用户知识点测评记录详细信息
     */
    @PreAuthorize("@ss.hasPermi('project:assessment:query')")
    @GetMapping(value = "/{id}")
    public AjaxResult getInfo(@PathVariable("id") Long id)
    {
        return success(userKnowledgeAssessmentService.selectUserKnowledgeAssessmentById(id));
    }

    /**
     * 新增用户知识点测评记录
     */
    @PreAuthorize("@ss.hasPermi('project:assessment:add')")
    @Log(title = "用户知识点测评记录", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody UserKnowledgeAssessment userKnowledgeAssessment)
    {
        userKnowledgeAssessment.setUserId(getUserId());
        return toAjax(userKnowledgeAssessmentService.insertUserKnowledgeAssessment(userKnowledgeAssessment));
    }

    /**
     * 修改用户知识点测评记录
     */
    @PreAuthorize("@ss.hasPermi('project:assessment:edit')")
    @Log(title = "用户知识点测评记录", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody UserKnowledgeAssessment userKnowledgeAssessment)
    {
        return toAjax(userKnowledgeAssessmentService.updateUserKnowledgeAssessment(userKnowledgeAssessment));
    }

    /**
     * 删除用户知识点测评记录
     */
    @PreAuthorize("@ss.hasPermi('project:assessment:remove')")
    @Log(title = "用户知识点测评记录", businessType = BusinessType.DELETE)
	@DeleteMapping("/{ids}")
    public AjaxResult remove(@PathVariable Long[] ids)
    {
        return toAjax(userKnowledgeAssessmentService.deleteUserKnowledgeAssessmentByIds(ids));
    }
}
