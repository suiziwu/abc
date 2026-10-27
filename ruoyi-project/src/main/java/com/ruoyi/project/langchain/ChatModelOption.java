package com.ruoyi.project.langchain;

/**
 * 前端可选的模型项。
 *
 * @author ruoyi
 */
public class ChatModelOption
{
    /** 模型标识（前端提交的 model 值） */
    private String id;

    /** 展示名称 */
    private String label;

    /** 真实模型名 */
    private String model;

    /** Key 是否已配置，未配置时前端置灰 */
    private boolean available;

    /** 是否为默认模型 */
    private boolean isDefault;

    public String getId()
    {
        return id;
    }

    public void setId(String id)
    {
        this.id = id;
    }

    public String getLabel()
    {
        return label;
    }

    public void setLabel(String label)
    {
        this.label = label;
    }

    public String getModel()
    {
        return model;
    }

    public void setModel(String model)
    {
        this.model = model;
    }

    public boolean isAvailable()
    {
        return available;
    }

    public void setAvailable(boolean available)
    {
        this.available = available;
    }

    public boolean isDefault()
    {
        return isDefault;
    }

    public void setDefault(boolean isDefault)
    {
        this.isDefault = isDefault;
    }
}
