package com.ruoyi.project.course.service;

import java.util.List;

import com.ruoyi.project.course.domain.CourseQuizItem;
import com.ruoyi.project.langchain.ChatModelOption;

/**
 * 课程学习服务：AI 助教问答 + 随堂练习。
 *
 * @author ruoyi
 */
public interface CourseStudyService
{
    /**
     * 基于课程视频文本回答学生问题（使用默认模型）。
     */
    default String chat(String question, String context)
    {
        return chat(question, context, null);
    }

    /**
     * 基于课程视频文本回答学生问题。
     *
     * @param model 模型标识，为空时使用默认模型
     */
    String chat(String question, String context, String model);

    /**
     * 返回 AI 助教可选的模型列表。
     */
    List<ChatModelOption> getModels();

    /**
     * 获取课程随堂练习题目。
     */
    List<CourseQuizItem> getQuiz(Long testId, int limit);
}
