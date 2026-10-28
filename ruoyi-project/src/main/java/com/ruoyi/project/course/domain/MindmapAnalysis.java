package com.ruoyi.project.course.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * AI 从视频逐字稿中梳理出的结构化知识导图。
 *
 * @author ruoyi
 */
public class MindmapAnalysis
{
    /** 导图标题 */
    private String title;

    /** 内容摘要 */
    private String summary;

    /** 知识点节点 */
    private List<Node> nodes = new ArrayList<Node>();

    /** 节点关系 */
    private List<Relation> relations = new ArrayList<Relation>();

    /** 关键结论 */
    private List<String> conclusions = new ArrayList<String>();

    public String getTitle()
    {
        return title;
    }

    public void setTitle(String title)
    {
        this.title = title;
    }

    public String getSummary()
    {
        return summary;
    }

    public void setSummary(String summary)
    {
        this.summary = summary;
    }

    public List<Node> getNodes()
    {
        return nodes;
    }

    public void setNodes(List<Node> nodes)
    {
        this.nodes = nodes == null ? new ArrayList<Node>() : nodes;
    }

    public List<Relation> getRelations()
    {
        return relations;
    }

    public void setRelations(List<Relation> relations)
    {
        this.relations = relations == null ? new ArrayList<Relation>() : relations;
    }

    public List<String> getConclusions()
    {
        return conclusions;
    }

    public void setConclusions(List<String> conclusions)
    {
        this.conclusions = conclusions == null ? new ArrayList<String>() : conclusions;
    }

    /**
     * 知识点节点。
     */
    public static class Node
    {
        /** 节点标识（关系引用用） */
        private String key;

        /** 知识点名称 */
        private String name;

        /** 父节点标识（用于层级结构） */
        private String parentKey;

        /** 所属小节 */
        private String section;

        /** 知识点定义 */
        private String definition;

        /** 知识点类型（概念/技术/协议/应用/设备/标准/组织/指标/算法） */
        private String type;

        /** 难度 1-5 */
        private Integer difficulty;

        /** 重要程度 1-4 */
        private Integer importance;

        /** 视频证据（带时间戳的原文片段） */
        private List<String> evidence = new ArrayList<String>();

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

        public String getParentKey()
        {
            return parentKey;
        }

        public void setParentKey(String parentKey)
        {
            this.parentKey = parentKey;
        }

        public String getSection()
        {
            return section;
        }

        public void setSection(String section)
        {
            this.section = section;
        }

        public String getDefinition()
        {
            return definition;
        }

        public void setDefinition(String definition)
        {
            this.definition = definition;
        }

        public String getType()
        {
            return type;
        }

        public void setType(String type)
        {
            this.type = type;
        }

        public Integer getDifficulty()
        {
            return difficulty;
        }

        public void setDifficulty(Integer difficulty)
        {
            this.difficulty = difficulty;
        }

        public Integer getImportance()
        {
            return importance;
        }

        public void setImportance(Integer importance)
        {
            this.importance = importance;
        }

        public List<String> getEvidence()
        {
            return evidence;
        }

        public void setEvidence(List<String> evidence)
        {
            this.evidence = evidence == null ? new ArrayList<String>() : evidence;
        }
    }

    /**
     * 知识点关系。
     */
    public static class Relation
    {
        /** 源节点 key */
        private String sourceKey;

        /** 目标节点 key */
        private String targetKey;

        /** 关系类型（prerequisite/related/extension） */
        private String relationType;

        /** 关系说明 */
        private String description;

        /** 关系强度 1-5 */
        private Integer weight;

        public String getSourceKey()
        {
            return sourceKey;
        }

        public void setSourceKey(String sourceKey)
        {
            this.sourceKey = sourceKey;
        }

        public String getTargetKey()
        {
            return targetKey;
        }

        public void setTargetKey(String targetKey)
        {
            this.targetKey = targetKey;
        }

        public String getRelationType()
        {
            return relationType;
        }

        public void setRelationType(String relationType)
        {
            this.relationType = relationType;
        }

        public String getDescription()
        {
            return description;
        }

        public void setDescription(String description)
        {
            this.description = description;
        }

        public Integer getWeight()
        {
            return weight;
        }

        public void setWeight(Integer weight)
        {
            this.weight = weight;
        }
    }
}
