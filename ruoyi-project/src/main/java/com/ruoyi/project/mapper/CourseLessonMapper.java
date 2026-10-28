package com.ruoyi.project.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.project.domain.CourseLesson;

/**
 * 课程课时 Mapper。
 *
 * @author ruoyi
 */
public interface CourseLessonMapper
{
    /** 按章节查询课时（按课时序号排序） */
    public List<CourseLesson> selectByChapterId(@Param("chapterId") Long chapterId);

    /** 按学科查询全部课时（课程学习页用） */
    public List<CourseLesson> selectBySubjectId(@Param("subjectId") Long subjectId);

    /** 查询单个课时 */
    public CourseLesson selectById(@Param("id") Long id);

    /** 章内下一个课时序号 */
    public Integer selectNextLessonNo(@Param("chapterId") Long chapterId);

    /** 新增课时 */
    public int insert(CourseLesson lesson);

    /** 修改课时 */
    public int update(CourseLesson lesson);

    /** 删除课时 */
    public int deleteById(@Param("id") Long id);

    /** 按章节删除课时 */
    public int deleteByChapterId(@Param("chapterId") Long chapterId);
}
