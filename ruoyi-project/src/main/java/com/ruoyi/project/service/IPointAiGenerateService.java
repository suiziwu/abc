package com.ruoyi.project.service;

import java.util.List;
import java.util.Map;

/**
 * 知识点 AI 生成 Service 接口。
 *
 * <p>对应 abc 版前端「知识点管理」里的两个入口：</p>
 * <ul>
 *   <li>AI 按章生成：根据岗位 + 学科生成章节与知识点并落库；</li>
 *   <li>课程逐字稿提取：根据课程视频文本生成知识点草稿（不落库）。</li>
 * </ul>
 *
 * @author ruoyi
 */
public interface IPointAiGenerateService
{
    /**
     * 调用 AI 按章生成知识点，并保存章节与知识点。
     *
     * @param positionId 岗位ID
     * @param subjectId 学科ID
     * @param chapterCount 章节数量
     * @param createBy 创建人
     * @return 生成结果（chapterCount、pointCount、chapters）
     */
    public Map<String, Object> generateByAi(Long positionId, Long subjectId, Integer chapterCount, String createBy);

    /**
     * 根据课程逐字稿生成知识点草稿（不落库）。
     *
     * @param subjectId 学科ID
     * @param courseTitle 课程标题
     * @param transcript 课程逐字稿
     * @return 知识点草稿列表
     */
    public List<Map<String, Object>> generateDraftsFromCourse(Long subjectId, String courseTitle, String transcript);
}
