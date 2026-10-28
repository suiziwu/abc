package com.ruoyi.project.course.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * 结构化梳理结果同步到知识图谱后的统计与映射明细。
 *
 * @author ruoyi
 */
public class MindmapSyncResult
{
    /** 梳理记录ID */
    private Long extractionId;

    /** 状态 */
    private String status;

    /** 知识点总数 */
    private Integer nodeCount = 0;

    /** 关系总数 */
    private Integer relationCount = 0;

    /** 新建知识点数 */
    private Integer createdPoints = 0;

    /** 匹配已有知识点数 */
    private Integer matchedPoints = 0;

    /** 新建关系数 */
    private Integer createdRelations = 0;

    /** 跳过关系数（重复/无效） */
    private Integer skippedRelations = 0;

    /** 绑定到章节的知识点数 */
    private Integer chapterBound = 0;

    /** 节点映射明细 */
    private List<Mapping> mappings = new ArrayList<Mapping>();

    /** 结果说明 */
    private String message;

    public Long getExtractionId()
    {
        return extractionId;
    }

    public void setExtractionId(Long extractionId)
    {
        this.extractionId = extractionId;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
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

    public Integer getSkippedRelations()
    {
        return skippedRelations;
    }

    public void setSkippedRelations(Integer skippedRelations)
    {
        this.skippedRelations = skippedRelations;
    }

    public Integer getChapterBound()
    {
        return chapterBound;
    }

    public void setChapterBound(Integer chapterBound)
    {
        this.chapterBound = chapterBound;
    }

    public List<Mapping> getMappings()
    {
        return mappings;
    }

    public void setMappings(List<Mapping> mappings)
    {
        this.mappings = mappings == null ? new ArrayList<Mapping>() : mappings;
    }

    public String getMessage()
    {
        return message;
    }

    public void setMessage(String message)
    {
        this.message = message;
    }

    /**
     * 单个节点的入库映射。
     */
    public static class Mapping
    {
        private String key;

        private String name;

        private Long pointId;

        /** exact-同名匹配 fuzzy-近似匹配 new-新建 */
        private String matchType;

        public String getKey()
        {
            return key;
        }

        public void setKey(String key)
        {
            this.key = key;
        }

        public String getName()
        {
            return name;
        }

        public void setName(String name)
        {
            this.name = name;
        }

        public Long getPointId()
        {
            return pointId;
        }

        public void setPointId(Long pointId)
        {
            this.pointId = pointId;
        }

        public String getMatchType()
        {
            return matchType;
        }

        public void setMatchType(String matchType)
        {
            this.matchType = matchType;
        }
    }
}
