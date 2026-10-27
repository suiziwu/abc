package com.ruoyi.project.course.mapper;

import java.util.List;

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
}
