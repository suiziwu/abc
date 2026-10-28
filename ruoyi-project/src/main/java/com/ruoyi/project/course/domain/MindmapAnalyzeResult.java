package com.ruoyi.project.course.domain;

/**
 * 结构化梳理结果（含可追溯的记录ID）。
 *
 * @author ruoyi
 */
public class MindmapAnalyzeResult
{
    /** 梳理记录ID（同步时使用） */
    private Long extractionId;

    /** 使用的模型 */
    private String model;

    /** 分析耗时（毫秒） */
    private Long durationMs;

    /** 输入逐字稿字符数 */
    private Integer transcriptChars;

    /** 结构化结果 */
    private MindmapAnalysis analysis;

    public Long getExtractionId()
    {
        return extractionId;
    }

    public void setExtractionId(Long extractionId)
    {
        this.extractionId = extractionId;
    }

    public String getModel()
    {
        return model;
    }

    public void setModel(String model)
    {
        this.model = model;
    }

    public Long getDurationMs()
    {
        return durationMs;
    }

    public void setDurationMs(Long durationMs)
    {
        this.durationMs = durationMs;
    }

    public Integer getTranscriptChars()
    {
        return transcriptChars;
    }

    public void setTranscriptChars(Integer transcriptChars)
    {
        this.transcriptChars = transcriptChars;
    }

    public MindmapAnalysis getAnalysis()
    {
        return analysis;
    }

    public void setAnalysis(MindmapAnalysis analysis)
    {
        this.analysis = analysis;
    }
}
