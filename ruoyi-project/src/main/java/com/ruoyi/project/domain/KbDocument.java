package com.ruoyi.project.domain;

import java.util.Date;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

/**
 * 知识库文档对象 kb_document
 *
 * <p>对应「知识库 → 文档 → 切片」的中间层：一次入库的源文档
 * （一个章节、一个知识点、一节视频、一套题库各算一篇文档）。</p>
 *
 * @author ruoyi
 */
public class KbDocument
{
    /** 文档ID */
    private Long id;

    /** 所属知识库ID */
    private Long kbId;

    /** 源文档标识（chapter:10 / point:11001 / transcript:94:10 / quiz:999） */
    private String docKey;

    /** 学科ID */
    private Long subjectId;

    /** 章节ID */
    private Long chapterId;

    /** 语料类型 */
    private String sourceType;

    /** 文档标题 */
    private String title;

    /** 章节路径 */
    private String sectionPath;

    /** 来源地址 */
    private String sourceUri;

    /** 起始页 */
    private Integer pageStart;

    /** 结束页 */
    private Integer pageEnd;

    /** 切片数 */
    private Integer chunkCount;

    /** 估算 token 数 */
    private Integer tokenCount;

    /** 内容指纹 */
    private String contentHash;

    /** 状态（1就绪 0处理中 2失败 3停用） */
    private String status;

    /** 版本号 */
    private Integer version;

    /** 标签（JSON 数组） */
    private String tags;

    /** 创建时间 */
    private Date createTime;

    /** 更新时间 */
    private Date updateTime;

    public Long getId()
    {
        return id;
    }

    public void setId(Long id)
    {
        this.id = id;
    }

    public Long getKbId()
    {
        return kbId;
    }

    public void setKbId(Long kbId)
    {
        this.kbId = kbId;
    }

    public String getDocKey()
    {
        return docKey;
    }

    public void setDocKey(String docKey)
    {
        this.docKey = docKey;
    }

    public Long getSubjectId()
    {
        return subjectId;
    }

    public void setSubjectId(Long subjectId)
    {
        this.subjectId = subjectId;
    }

    public Long getChapterId()
    {
        return chapterId;
    }

    public void setChapterId(Long chapterId)
    {
        this.chapterId = chapterId;
    }

    public String getSourceType()
    {
        return sourceType;
    }

    public void setSourceType(String sourceType)
    {
        this.sourceType = sourceType;
    }

    public String getTitle()
    {
        return title;
    }

    public void setTitle(String title)
    {
        this.title = title;
    }

    public String getSectionPath()
    {
        return sectionPath;
    }

    public void setSectionPath(String sectionPath)
    {
        this.sectionPath = sectionPath;
    }

    public String getSourceUri()
    {
        return sourceUri;
    }

    public void setSourceUri(String sourceUri)
    {
        this.sourceUri = sourceUri;
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

    public Integer getChunkCount()
    {
        return chunkCount;
    }

    public void setChunkCount(Integer chunkCount)
    {
        this.chunkCount = chunkCount;
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

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public Integer getVersion()
    {
        return version;
    }

    public void setVersion(Integer version)
    {
        this.version = version;
    }

    public String getTags()
    {
        return tags;
    }

    public void setTags(String tags)
    {
        this.tags = tags;
    }

    public Date getCreateTime()
    {
        return createTime;
    }

    public void setCreateTime(Date createTime)
    {
        this.createTime = createTime;
    }

    public Date getUpdateTime()
    {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime)
    {
        this.updateTime = updateTime;
    }

    @Override
    public String toString()
    {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("id", getId())
            .append("kbId", getKbId())
            .append("docKey", getDocKey())
            .append("subjectId", getSubjectId())
            .append("chapterId", getChapterId())
            .append("sourceType", getSourceType())
            .append("title", getTitle())
            .append("chunkCount", getChunkCount())
            .append("tokenCount", getTokenCount())
            .append("contentHash", getContentHash())
            .append("status", getStatus())
            .append("version", getVersion())
            .append("updateTime", getUpdateTime())
            .toString();
    }
}
