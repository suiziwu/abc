package com.ruoyi.project.domain;

import com.ruoyi.common.annotation.Excel;

/**
 * 知识点 Excel 导入对象。
 *
 * <p>与 {@link KnowledgePoint} 分离，导入列使用“学科名称 + 文本枚举（简单/中等、了解/重点 等）”的
 * 友好格式，由 Controller 统一转换为数据库字段值，避免导入时依赖数字 ID 与字典值。</p>
 *
 * @author ruoyi
 */
public class KnowledgePointImport
{
    /** 知识点名称 */
    @Excel(name = "知识点名称")
    private String name;

    /** 所属学科（填写学科名称） */
    @Excel(name = "所属学科")
    private String subjectName;

    /** 知识点内容/描述 */
    @Excel(name = "知识点内容")
    private String content;

    /** 难度等级（简单/较易/中等/较难/困难，或 1-5） */
    @Excel(name = "难度等级")
    private String difficulty;

    /** 重要程度（了解/掌握/重点/核心，或 1-4） */
    @Excel(name = "重要程度")
    private String importance;

    /** 知识点类型（概念/语法/方法/工具/框架/管理等） */
    @Excel(name = "知识点类型")
    private String type;

    /** 显示排序 */
    @Excel(name = "显示排序")
    private Long sortOrder;

    /** 状态（正常/停用，或 0/1） */
    @Excel(name = "状态")
    private String status;

    /** 附件路径 */
    @Excel(name = "附件路径")
    private String attachment;

    /** 视频路径 */
    @Excel(name = "视频路径")
    private String video;

    public String getName()
    {
        return name;
    }

    public void setName(String name)
    {
        this.name = name;
    }

    public String getSubjectName()
    {
        return subjectName;
    }

    public void setSubjectName(String subjectName)
    {
        this.subjectName = subjectName;
    }

    public String getContent()
    {
        return content;
    }

    public void setContent(String content)
    {
        this.content = content;
    }

    public String getDifficulty()
    {
        return difficulty;
    }

    public void setDifficulty(String difficulty)
    {
        this.difficulty = difficulty;
    }

    public String getImportance()
    {
        return importance;
    }

    public void setImportance(String importance)
    {
        this.importance = importance;
    }

    public String getType()
    {
        return type;
    }

    public void setType(String type)
    {
        this.type = type;
    }

    public Long getSortOrder()
    {
        return sortOrder;
    }

    public void setSortOrder(Long sortOrder)
    {
        this.sortOrder = sortOrder;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public String getAttachment()
    {
        return attachment;
    }

    public void setAttachment(String attachment)
    {
        this.attachment = attachment;
    }

    public String getVideo()
    {
        return video;
    }

    public void setVideo(String video)
    {
        this.video = video;
    }
}
