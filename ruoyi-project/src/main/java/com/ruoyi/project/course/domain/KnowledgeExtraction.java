package com.ruoyi.project.course.domain;

import java.util.Date;

/**
 * 视频知识结构化梳理记录（可追溯）。
 *
 * @author ruoyi
 */
public class KnowledgeExtraction
{
    private Long id;

    private Long subjectId;

    private String chapterNo;

    private String sourceType;

    private String model;

    private Integer transcriptChars;

    private String rawResponse;

    private String analysisJson;

    private String mappingJson;

    private Integer nodeCount;

    private Integer relationCount;

    private Integer createdPoints;

    private Integer matchedPoints;

    private Integer createdRelations;

    private String status;

    private String errorMsg;

    private String createBy;

    private Date createTime;

    private String syncBy;

    private Date syncTime;

    public Long getId()
    {
        return id;
    }

    public void setId(Long id)
    {
        this.id = id;
    }

    public Long getSubjectId()
    {
        return subjectId;
    }

    public void setSubjectId(Long subjectId)
    {
        this.subjectId = subjectId;
    }

    public String getChapterNo()
    {
        return chapterNo;
    }

    public void setChapterNo(String chapterNo)
    {
        this.chapterNo = chapterNo;
    }

    public String getSourceType()
    {
        return sourceType;
    }

    public void setSourceType(String sourceType)
    {
        this.sourceType = sourceType;
    }

    public String getModel()
    {
        return model;
    }

    public void setModel(String model)
    {
        this.model = model;
    }

    public Integer getTranscriptChars()
    {
        return transcriptChars;
    }

    public void setTranscriptChars(Integer transcriptChars)
    {
        this.transcriptChars = transcriptChars;
    }

    public String getRawResponse()
    {
        return rawResponse;
    }

    public void setRawResponse(String rawResponse)
    {
        this.rawResponse = rawResponse;
    }

    public String getAnalysisJson()
    {
        return analysisJson;
    }

    public void setAnalysisJson(String analysisJson)
    {
        this.analysisJson = analysisJson;
    }

    public String getMappingJson()
    {
        return mappingJson;
    }

    public void setMappingJson(String mappingJson)
    {
        this.mappingJson = mappingJson;
    }

    public Integer getNodeCount()
    {
        return nodeCount;
    }

    public void setNodeCount(Integer nodeCount)
    {
        this.nodeCount = nodeCount;
    }

    public Integer getRelationCount()
    {
        return relationCount;
    }

    public void setRelationCount(Integer relationCount)
    {
        this.relationCount = relationCount;
    }

    public Integer getCreatedPoints()
    {
        return createdPoints;
    }

    public void setCreatedPoints(Integer createdPoints)
    {
        this.createdPoints = createdPoints;
    }

    public Integer getMatchedPoints()
    {
        return matchedPoints;
    }

    public void setMatchedPoints(Integer matchedPoints)
    {
        this.matchedPoints = matchedPoints;
    }

    public Integer getCreatedRelations()
    {
        return createdRelations;
    }

    public void setCreatedRelations(Integer createdRelations)
    {
        this.createdRelations = createdRelations;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public String getErrorMsg()
    {
        return errorMsg;
    }

    public void setErrorMsg(String errorMsg)
    {
        this.errorMsg = errorMsg;
    }

    public String getCreateBy()
    {
        return createBy;
    }

    public void setCreateBy(String createBy)
    {
        this.createBy = createBy;
    }

    public Date getCreateTime()
    {
        return createTime;
    }

    public void setCreateTime(Date createTime)
    {
        this.createTime = createTime;
    }

    public String getSyncBy()
    {
        return syncBy;
    }

    public void setSyncBy(String syncBy)
    {
        this.syncBy = syncBy;
    }

    public Date getSyncTime()
    {
        return syncTime;
    }

    public void setSyncTime(Date syncTime)
    {
        this.syncTime = syncTime;
    }
}
