package com.ruoyi.project.service.impl;

import java.util.List;
import com.ruoyi.common.utils.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.project.mapper.AiChatHistoryMapper;
import com.ruoyi.project.domain.AiChatHistory;
import com.ruoyi.project.service.IAiChatHistoryService;

/**
 * AI对话历史记录Service业务层处理
 * 
 * @author ruoyi
 * @date 2026-06-30
 */
@Service
public class AiChatHistoryServiceImpl implements IAiChatHistoryService 
{
    @Autowired
    private AiChatHistoryMapper aiChatHistoryMapper;

    /**
     * 查询AI对话历史记录
     * 
     * @param id AI对话历史记录主键
     * @return AI对话历史记录
     */
    @Override
    public AiChatHistory selectAiChatHistoryById(Long id)
    {
        return aiChatHistoryMapper.selectAiChatHistoryById(id);
    }

    /**
     * 查询AI对话历史记录列表
     * 
     * @param aiChatHistory AI对话历史记录
     * @return AI对话历史记录
     */
    @Override
    public List<AiChatHistory> selectAiChatHistoryList(AiChatHistory aiChatHistory)
    {
        return aiChatHistoryMapper.selectAiChatHistoryList(aiChatHistory);
    }

    /**
     * 新增AI对话历史记录
     * 
     * @param aiChatHistory AI对话历史记录
     * @return 结果
     */
    @Override
    public int insertAiChatHistory(AiChatHistory aiChatHistory)
    {
        aiChatHistory.setCreateTime(DateUtils.getNowDate());
        return aiChatHistoryMapper.insertAiChatHistory(aiChatHistory);
    }

    /**
     * 修改AI对话历史记录
     * 
     * @param aiChatHistory AI对话历史记录
     * @return 结果
     */
    @Override
    public int updateAiChatHistory(AiChatHistory aiChatHistory)
    {
        aiChatHistory.setUpdateTime(DateUtils.getNowDate());
        return aiChatHistoryMapper.updateAiChatHistory(aiChatHistory);
    }

    /**
     * 批量删除AI对话历史记录
     * 
     * @param ids 需要删除的AI对话历史记录主键
     * @return 结果
     */
    @Override
    public int deleteAiChatHistoryByIds(Long[] ids)
    {
        return aiChatHistoryMapper.deleteAiChatHistoryByIds(ids);
    }

    /**
     * 删除AI对话历史记录信息
     * 
     * @param id AI对话历史记录主键
     * @return 结果
     */
    @Override
    public int deleteAiChatHistoryById(Long id)
    {
        return aiChatHistoryMapper.deleteAiChatHistoryById(id);
    }
}
