package com.ruoyi.project.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.project.domain.CourseVideoImport;

/**
 * 视频课程导入任务 Mapper。
 *
 * @author ruoyi
 */
public interface CourseVideoImportMapper
{
    public int insert(CourseVideoImport task);

    public int update(CourseVideoImport task);

    public CourseVideoImport selectById(@Param("id") Long id);

    public List<CourseVideoImport> selectList(@Param("subjectId") Long subjectId);

    /** 章节下的全部导入任务 */
    public List<CourseVideoImport> selectByChapterId(@Param("chapterId") Long chapterId);

    /** 章节最近一次成功的导入任务（用于课程页恢复配套题库） */
    public CourseVideoImport selectLatestSuccessByChapterId(@Param("chapterId") Long chapterId);

    /** 课时最近一次成功的导入任务 */
    public CourseVideoImport selectLatestSuccessByLessonId(@Param("lessonId") Long lessonId);
}
