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
     * 将分析结果标准化后映射/写入知识图谱，并绑定章节。
     */
    MindmapSyncResult sync(Long extractionId);

    /**
     * 查询某学科最近的结构化梳理记录。
     */
    List<KnowledgeExtraction> recent(Long subjectId);
}
