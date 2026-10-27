package com.ruoyi.web.controller.project;

import java.util.List;
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
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.project.domain.CourseChapter;
import com.ruoyi.project.service.ICourseChapterService;

/**
 * 课程章节Controller
 *
 * @author ruoyi
 */
@RestController
@RequestMapping("/project/chapter")
public class CourseChapterController extends BaseController
{
    @Autowired
    private ICourseChapterService courseChapterService;

    /**
     * 查询课程章节列表（可按 subjectId / chapterNo 过滤）
     */
    @PreAuthorize("@ss.hasPermi('project:point:list')")
    @GetMapping("/list")
    public AjaxResult list(CourseChapter courseChapter)
    {
        return success(courseChapterService.selectCourseChapterList(courseChapter));
    }

    /**
     * 获取课程章节详细信息
     */
    @PreAuthorize("@ss.hasPermi('project:point:list')")
    @GetMapping(value = "/{id}")
    public AjaxResult getInfo(@PathVariable("id") Long id)
    {
        return success(courseChapterService.selectCourseChapterById(id));
    }

    /**
     * 查询章节下的知识点列表
     */
    @PreAuthorize("@ss.hasPermi('project:point:list')")
    @GetMapping(value = "/{id}/points")
    public AjaxResult points(@PathVariable("id") Long id)
    {
        return success(courseChapterService.selectChapterPointList(id));
    }

    /**
     * 批量查询知识点所属章节（用于把扁平知识点列表还原为“章节 → 知识点”学习路径）
     *
     * 用法：GET /project/chapter/pointChapters?pointIds=1,2,3
     */
    @PreAuthorize("@ss.hasPermi('project:point:list')")
    @GetMapping("/pointChapters")
    public AjaxResult pointChapters(@RequestParam(value = "pointIds", required = false) List<Long> pointIds)
    {
        return success(courseChapterService.selectChapterPointByPointIds(pointIds));
    }

    /**
     * 新增课程章节
     */
    @PreAuthorize("@ss.hasPermi('project:point:add')")
    @Log(title = "课程章节", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody CourseChapter courseChapter)
    {
        if (!courseChapterService.checkChapterNoUnique(courseChapter))
        {
            return error("新增章节失败，该学科下章号「" + courseChapter.getChapterNo() + "」已存在");
        }
        courseChapter.setCreateBy(getUsername());
        return toAjax(courseChapterService.insertCourseChapter(courseChapter));
    }

    /**
     * 修改课程章节
     */
    @PreAuthorize("@ss.hasPermi('project:point:edit')")
    @Log(title = "课程章节", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody CourseChapter courseChapter)
    {
        if (!courseChapterService.checkChapterNoUnique(courseChapter))
        {
            return error("修改章节失败，该学科下章号「" + courseChapter.getChapterNo() + "」已存在");
        }
        courseChapter.setUpdateBy(getUsername());
        return toAjax(courseChapterService.updateCourseChapter(courseChapter));
    }

    /**
     * 删除课程章节
     */
    @PreAuthorize("@ss.hasPermi('project:point:remove')")
    @Log(title = "课程章节", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public AjaxResult remove(@PathVariable Long[] ids)
    {
        return toAjax(courseChapterService.deleteCourseChapterByIds(ids));
    }
}