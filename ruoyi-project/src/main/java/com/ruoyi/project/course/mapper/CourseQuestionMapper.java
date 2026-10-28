package com.ruoyi.project.course.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.ruoyi.project.course.domain.CourseQuestion;

/**
 * 课程题库 Mapper。
 *
 * @author ruoyi
 */
@Mapper
public interface CourseQuestionMapper
{
    @Select("select id, test_id as testId, question_number as questionNumber, "
            + "question_type as questionType, content, options, answer, analysis, difficulty "
            + "from question where test_id = #{testId} order by question_number, id")
    List<CourseQuestion> selectByTestId(@Param("testId") Long testId);

    /** 按题库取题（带数量上限，避免把整个题库读进内存后再截断） */
    @Select("select id, test_id as testId, question_number as questionNumber, "
            + "question_type as questionType, content, options, answer, analysis, difficulty "
            + "from question where test_id = #{testId} order by question_number, id limit #{limit}")
    List<CourseQuestion> selectByTestIdLimit(@Param("testId") Long testId, @Param("limit") int limit);

    /** 当前题库最大题号（用于追加） */
    @Select("select coalesce(max(question_number), 0) from question where test_id = #{testId}")
    int selectMaxQuestionNumber(@Param("testId") Long testId);

    /** 分配新的题库ID：从 1000 起递增，避免与既有题库撞号 */
    @Select("select coalesce(max(test_id), 1000) + 1 from question where test_id >= 1000")
    Long selectNextTestId();

    /** 新增题目 */
    @Insert("insert into question (test_id, question_number, question_type, content, options, answer, "
            + "analysis, score, difficulty, sort_order, create_time) "
            + "values (#{testId}, #{questionNumber}, #{questionType}, #{content}, #{options}, #{answer}, "
            + "#{analysis}, 5, #{difficulty}, #{questionNumber}, now())")
    int insertQuestion(CourseQuestion question);
}
