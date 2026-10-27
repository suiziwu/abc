package com.ruoyi.project.service.impl;

import java.util.List;
import com.ruoyi.common.utils.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.project.mapper.KnowledgePointMapper;
import com.ruoyi.project.domain.KnowledgePoint;
import com.ruoyi.project.service.IKnowledgePointService;

/**
 * 知识点Service业务层处理
 * 
 * @author ruoyi
 * @date 2026-06-24
 */
@Service
public class KnowledgePointServiceImpl implements IKnowledgePointService 
{
    @Autowired
    private KnowledgePointMapper knowledgePointMapper;

    /**
     * 查询知识点
     * 
     * @param id 知识点主键
     * @return 知识点
     */
    @Override
    public KnowledgePoint selectKnowledgePointById(Long id)
    {
        return knowledgePointMapper.selectKnowledgePointById(id);
    }

    /**
     * 查询知识点列表
     * 
     * @param knowledgePoint 知识点
     * @return 知识点
     */
    @Override
    public List<KnowledgePoint> selectKnowledgePointList(KnowledgePoint knowledgePoint)
    {
        return knowledgePointMapper.selectKnowledgePointList(knowledgePoint);
    }

    /**
     * 新增知识点
     * 
     * @param knowledgePoint 知识点
     * @return 结果
     */
    @Override
    public int insertKnowledgePoint(KnowledgePoint knowledgePoint)
    {
        knowledgePoint.setCreateTime(DateUtils.getNowDate());
        return knowledgePointMapper.insertKnowledgePoint(knowledgePoint);
    }

    /**
     * 修改知识点
     * 
     * @param knowledgePoint 知识点
     * @return 结果
     */
    @Override
    public int updateKnowledgePoint(KnowledgePoint knowledgePoint)
    {
        knowledgePoint.setUpdateTime(DateUtils.getNowDate());
        return knowledgePointMapper.updateKnowledgePoint(knowledgePoint);
    }

    /**
     * 批量删除知识点
     * 
     * @param ids 需要删除的知识点主键
     * @return 结果
     */
    @Override
    public int deleteKnowledgePointByIds(Long[] ids)
    {
        return knowledgePointMapper.deleteKnowledgePointByIds(ids);
    }

    /**
     * 删除知识点信息
     * 
     * @param id 知识点主键
     * @return 结果
     */
    @Override
    public int deleteKnowledgePointById(Long id)
    {
        return knowledgePointMapper.deleteKnowledgePointById(id);
    }
}
