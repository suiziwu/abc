package com.ruoyi.project.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.project.domain.CourseChapter;
import com.ruoyi.project.domain.ChapterPoint;

/**
 * 课程章节Mapper接口
 *
 * @author ruoyi
 */
public interface CourseChapterMapper
{
    /**
     * 查询课程章节列表（含章节知识点数量）
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
     * @param subjectId 学科ID
     * @param chapterNo 章号
     * @return 课程章节
     */
    public CourseChapter checkChapterNoUnique(@Param("subjectId") Long subjectId, @Param("chapterNo") String chapterNo);

    /**
     * 新增课程章节
     *
     * @param courseChapter 课程章节
     * @return 结果
     */
    public int insertCourseChapter(CourseChapter courseChapter);

    /**
     * 修改课程章节
     *
     * @param courseChapter 课程章节
     * @return 结果
     */
    public int updateCourseChapter(CourseChapter courseChapter);

    /**
     * 删除课程章节（章节-知识点关联由外键级联删除）
     *
     * @param id 课程章节主键
     * @return 结果
     */
    public int deleteCourseChapterById(Long id);

    /**
     * 查询章节下的知识点列表（联表带出知识点展示字段）
     *
     * @param chapterId 章节主键
     * @return 章节知识点集合
     */
    public List<ChapterPoint> selectChapterPointList(Long chapterId);

    /**
     * 根据知识点ID集合批量查询其所属章节（用于把扁平知识点列表还原成“章节 → 知识点”路径）
     *
     * @param pointIds 知识点ID集合
     * @return 章节-知识点关联集合（含 chapterNo / chapterTitle / chapterVideo）
     */
    public List<ChapterPoint> selectChapterPointByPointIds(@Param("pointIds") List<Long> pointIds);

    /**
     * 删除章节下的全部知识点关联
     *
     * @param chapterId 章节主键
     * @return 结果
     */
    public int deleteChapterPointByChapterId(Long chapterId);

    /**
     * 批量新增章节-知识点关联
     *
     * @param list 章节-知识点关联集合（含 section / sortOrder）
     * @return 结果
     */
    public int batchChapterPoint(@Param("list") List<ChapterPoint> list);
}