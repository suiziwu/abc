package com.ruoyi.project.domain;

import java.util.Date;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

/**
 * 知识库分块对象 kb_chunk
 *
 * <p>存放向量化后的课程语料，供「视频 AI 助教」做 RAG 检索。
 * embedding 字段为归一化后的浮点数组 JSON，检索时按余弦相似度计算。</p>
 *
 * <p>v2 起补齐元数据：kbId/docPk 构成「知识库 → 文档 → 切片」三级归属，
 * contentHash 支撑增量入库，tokenCount 用于上下文预算，status 用于软停用。</p>
 *
 * @author ruoyi
 */
public class KbChunk
{
    /** 分块ID */
    private Long id;

    /** 所属知识库ID（kb_base.id） */
    private Long kbId;

    /** 所属文档ID（kb_document.id） */
    private Long docPk;

    /** 源文档标识（如 point:11001） */
    private String docId;

    /** 语料类型（transcript-逐字稿 point-知识点 chapter-章节 quiz-题库） */
    private String sourceType;

    /** 所属学科ID */
    private Long subjectId;

    /** 所属章节ID */
    private Long chapterId;

    /** 关联知识点ID */
    private Long pointId;

    /** 分块标题 */
    private String title;

    /** 章节路径（第4章 树 > 4.4 二叉树） */
    private String sectionPath;

    /** 分块正文 */
    private String content;

    /** 起始页 */
    private Integer pageStart;

    /** 结束页 */
    private Integer pageEnd;

    /** 同一文档内的分块序号（从 0 开始，文档内唯一） */
    private Integer chunkIndex;

    /** 视频时间戳（秒，仅逐字稿） */
    private Integer startTime;

    /** 估算 token 数 */
    private Integer tokenCount;

    /** 内容指纹（增量入库/去重） */
    private String contentHash;

    /** 状态（1启用 0停用） */
    private Integer status;

    /** 向量化模型 */
    private String model;

    /** 向量维度 */
    private Integer dimensions;

    /** 向量（归一化后的浮点数组 JSON） */
    private String embedding;

    /** 创建时间 */
    private Date createTime;

    /** 更新时间 */
    private Date updateTime;

    /** 相似度得分（检索结果回填，非表字段） */
    private Double score;

    /** 关键词相关度（全文检索回填，非表字段） */
    private Double keywordScore;

    public void setId(Long id)
    {
        this.id = id;
    }

    public Long getId()
    {
        return id;
    }

    public Long getKbId()
    {
        return kbId;
    }

    public void setKbId(Long kbId)
    {
        this.kbId = kbId;
    }

    public Long getDocPk()
    {
        return docPk;
    }

    public void setDocPk(Long docPk)
    {
        this.docPk = docPk;
    }

    public void setDocId(String docId)
    {
        this.docId = docId;
    }

    public String getDocId()
    {
        return docId;
    }

    public void setSourceType(String sourceType)
    {
        this.sourceType = sourceType;
    }

    public String getSourceType()
    {
        return sourceType;
    }

    public void setSubjectId(Long subjectId)
    {
        this.subjectId = subjectId;
    }

    public Long getSubjectId()
    {
        return subjectId;
    }

    public void setChapterId(Long chapterId)
    {
        this.chapterId = chapterId;
    }

    public Long getChapterId()
    {
        return chapterId;
    }

    public void setPointId(Long pointId)
    {
        this.pointId = pointId;
    }

    public Long getPointId()
    {
        return pointId;
    }

    public void setTitle(String title)
    {
        this.title = title;
    }

    public String getTitle()
    {
        return title;
    }

    public String getSectionPath()
    {
        return sectionPath;
    }

    public void setSectionPath(String sectionPath)
    {
        this.sectionPath = sectionPath;
    }

    public void setContent(String content)
    {
        this.content = content;
    }

    public String getContent()
    {
        return content;
    }

    public Integer getPageStart()
    {
        return pageStart;
    }

    public void setPageStart(Integer pageStart)
    {
        this.pageStart = pageStart;
    }

    public Integer getPageEnd()
    {
        return pageEnd;
    }

    public void setPageEnd(Integer pageEnd)
    {
        this.pageEnd = pageEnd;
    }

    public void setChunkIndex(Integer chunkIndex)
    {
        this.chunkIndex = chunkIndex;
    }

    public Integer getChunkIndex()
    {
        return chunkIndex;
    }

    public void setStartTime(Integer startTime)
    {
        this.startTime = startTime;
    }

    public Integer getStartTime()
    {
        return startTime;
    }

    public Integer getTokenCount()
    {
        return tokenCount;
    }

    public void setTokenCount(Integer tokenCount)
    {
        this.tokenCount = tokenCount;
    }

    public String getContentHash()
    {
        return contentHash;
    }

    public void setContentHash(String contentHash)
    {
        this.contentHash = contentHash;
    }

    public Integer getStatus()
    {
        return status;
    }

    public void setStatus(Integer status)
    {
        this.status = status;
    }

    public void setModel(String model)
    {
        this.model = model;
    }

    public String getModel()
    {
        return model;
    }

    public void setDimensions(Integer dimensions)
    {
        this.dimensions = dimensions;
    }

    public Integer getDimensions()
    {
        return dimensions;
    }

    public void setEmbedding(String embedding)
    {
        this.embedding = embedding;
    }

    public String getEmbedding()
    {
        return embedding;
    }

    public void setCreateTime(Date createTime)
    {
        this.createTime = createTime;
    }

    public Date getCreateTime()
    {
        return createTime;
    }

    public Date getUpdateTime()
    {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime)
    {
        this.updateTime = updateTime;
    }

    public void setScore(Double score)
    {
        this.score = score;
    }

    public Double getScore()
    {
        return score;
    }

    public Double getKeywordScore()
    {
        return keywordScore;
    }

    public void setKeywordScore(Double keywordScore)
    {
        this.keywordScore = keywordScore;
    }

    /**
     * 切片在向量库中的唯一键：docId#chunkIndex。
     */
    public String vectorKey()
    {
        return docId + "#" + (chunkIndex == null ? 0 : chunkIndex);
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("id", getId())
            .append("kbId", getKbId())
            .append("docPk", getDocPk())
            .append("docId", getDocId())
            .append("sourceType", getSourceType())
            .append("subjectId", getSubjectId())
            .append("chapterId", getChapterId())
            .append("pointId", getPointId())
            .append("title", getTitle())
            .append("sectionPath", getSectionPath())
            .append("chunkIndex", getChunkIndex())
            .append("startTime", getStartTime())
            .append("tokenCount", getTokenCount())
            .append("contentHash", getContentHash())
            .append("status", getStatus())
            .append("model", getModel())
            .append("dimensions", getDimensions())
            .append("createTime", getCreateTime())
            .append("updateTime", getUpdateTime())
            .toString();
    }
}
