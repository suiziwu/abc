package com.ruoyi.project.service;

import java.util.List;
import com.ruoyi.project.domain.CourseChapter;
import com.ruoyi.project.domain.ChapterPoint;

/**
 * 课程章节Service接口
 *
 * @author ruoyi
 */
public interface ICourseChapterService
{
    /**
     * 查询课程章节列表
     *
     * @param courseChapter 课程章节
     * @return 课程章节集合
     */
    public List<CourseChapter> selectCourseChapterList(CourseChapter courseChapter);

    /**
     * 查询课程章节
     *
     * @param id 课程章节主键
     * @return 课程章节
     */
    public CourseChapter selectCourseChapterById(Long id);

    /**
     * 校验同一学科下章号是否唯一
     *
     * @param courseChapter 课程章节
     * @return 结果（true 唯一）
     */
    public boolean checkChapterNoUnique(CourseChapter courseChapter);

    /**
     * 新增课程章节（可选同时保存章节-知识点关联）
     *
     * @param courseChapter 课程章节
     * @return 结果
     */
    public int insertCourseChapter(CourseChapter courseChapter);

    /**
     * 修改课程章节（可选同时重置章节-知识点关联）
     *
     * @param courseChapter 课程章节
     * @return 结果
     */
    public int updateCourseChapter(CourseChapter courseChapter);

    /**
     * 批量删除课程章节
     *
     * @param ids 课程章节主键集合
     * @return 结果
     */
    public int deleteCourseChapterByIds(Long[] ids);

    /**
     * 查询章节下的知识点列表
     *
     * @param chapterId 章节主键
     * @return 章节知识点集合
     */
    public List<ChapterPoint> selectChapterPointList(Long chapterId);

    /**
     * 根据知识点ID集合批量查询其所属章节
     *
     * @param pointIds 知识点ID集合
     * @return 章节知识点集合
     */
    public List<ChapterPoint> selectChapterPointByPointIds(List<Long> pointIds);
}