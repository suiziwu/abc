package com.ruoyi.project.course.service;

import java.util.List;

import com.ruoyi.project.course.domain.KnowledgeExtraction;
import com.ruoyi.project.course.domain.MindmapAnalyzeRequest;
import com.ruoyi.project.course.domain.MindmapAnalyzeResult;
import com.ruoyi.project.course.domain.MindmapSyncResult;

/**
 * 视频知识结构化梳理：AI 生成导图 → 标准化 → 映射/入库到知识图谱。
 *
 * @author ruoyi
 */
public interface CourseMindmapService
{
    /**
     * 分析视频逐字稿，生成结构化知识导图，并保存分析记录。
     */
    MindmapAnalyzeResult analyze(MindmapAnalyzeRequest request);

    /**
     * 分析课本正文，生成结构化知识拓扑，并保存分析记录。
     *
     * @param subjectId 归属学科
     * @param chapterNo 章号（可空）
     * @param model     模型ID（可空，用系统默认）
     * @param text      课本正文
     */
    MindmapAnalyzeResult analyzeTextbook(Long subjectId, String chapterNo, String model, String text);

    /**
     * 将分析结果标准化后映射/写入知识图谱，并绑定章节。
     */
    MindmapSyncResult sync(Long extractionId);

    /**
     * 查询某学科最近的结构化梳理记录。
     */
    List<KnowledgeExtraction> recent(Long subjectId);
}
