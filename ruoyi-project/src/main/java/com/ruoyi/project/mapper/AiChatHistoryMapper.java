package com.ruoyi.project.mapper;

import java.util.List;
import com.ruoyi.project.domain.AiChatHistory;

/**
 * AI对话历史记录Mapper接口
 * 
 * @author ruoyi
 * @date 2026-06-30
 */
public interface AiChatHistoryMapper 
{
    /**
     * 查询AI对话历史记录
     * 
     * @param id AI对话历史记录主键
     * @return AI对话历史记录
     */
    public AiChatHistory selectAiChatHistoryById(Long id);

    /**
     * 查询AI对话历史记录列表
     * 
     * @param aiChatHistory AI对话历史记录
     * @return AI对话历史记录集合
     */
    public List<AiChatHistory> selectAiChatHistoryList(AiChatHistory aiChatHistory);

    /**
     * 新增AI对话历史记录
     * 
     * @param aiChatHistory AI对话历史记录
     * @return 结果
     */
    public int insertAiChatHistory(AiChatHistory aiChatHistory);

    /**
     * 修改AI对话历史记录
     * 
     * @param aiChatHistory AI对话历史记录
     * @return 结果
     */
    public int updateAiChatHistory(AiChatHistory aiChatHistory);

    /**
     * 删除AI对话历史记录
     * 
     * @param id AI对话历史记录主键
     * @return 结果
     */
    public int deleteAiChatHistoryById(Long id);

    /**
     * 批量删除AI对话历史记录
     * 
     * @param ids 需要删除的数据主键集合
     * @return 结果
     */
    public int deleteAiChatHistoryByIds(Long[] ids);
}
