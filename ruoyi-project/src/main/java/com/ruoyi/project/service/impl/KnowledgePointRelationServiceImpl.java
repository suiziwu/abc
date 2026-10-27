package com.ruoyi.project.service.impl;

import java.util.List;
import com.ruoyi.common.utils.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.project.mapper.KnowledgePointRelationMapper;
import com.ruoyi.project.domain.KnowledgePointRelation;
import com.ruoyi.project.service.IKnowledgePointRelationService;

/**
 * 知识点关联（拓扑图边）Service业务层处理
 * 
 * @author ruoyi
 * @date 2026-06-23
 */
@Service
public class KnowledgePointRelationServiceImpl implements IKnowledgePointRelationService 
{
    @Autowired
    private KnowledgePointRelationMapper knowledgePointRelationMapper;

    /**
     * 查询知识点关联（拓扑图边）
     * 
     * @param id 知识点关联（拓扑图边）主键
     * @return 知识点关联（拓扑图边）
     */
    @Override
    public KnowledgePointRelation selectKnowledgePointRelationById(Long id)
    {
        return knowledgePointRelationMapper.selectKnowledgePointRelationById(id);
    }

    /**
     * 查询知识点关联（拓扑图边）列表
     * 
     * @param knowledgePointRelation 知识点关联（拓扑图边）
     * @return 知识点关联（拓扑图边）
     */
    @Override
    public List<KnowledgePointRelation> selectKnowledgePointRelationList(KnowledgePointRelation knowledgePointRelation)
    {
        return knowledgePointRelationMapper.selectKnowledgePointRelationList(knowledgePointRelation);
    }

    /**
     * 新增知识点关联（拓扑图边）
     * 
     * @param knowledgePointRelation 知识点关联（拓扑图边）
     * @return 结果
     */
    @Override
    public int insertKnowledgePointRelation(KnowledgePointRelation knowledgePointRelation)
    {
        knowledgePointRelation.setCreateTime(DateUtils.getNowDate());
        return knowledgePointRelationMapper.insertKnowledgePointRelation(knowledgePointRelation);
    }

    /**
     * 修改知识点关联（拓扑图边）
     * 
     * @param knowledgePointRelation 知识点关联（拓扑图边）
     * @return 结果
     */
    @Override
    public int updateKnowledgePointRelation(KnowledgePointRelation knowledgePointRelation)
    {
        return knowledgePointRelationMapper.updateKnowledgePointRelation(knowledgePointRelation);
    }

    /**
     * 批量删除知识点关联（拓扑图边）
     * 
     * @param ids 需要删除的知识点关联（拓扑图边）主键
     * @return 结果
     */
    @Override
    public int deleteKnowledgePointRelationByIds(Long[] ids)
    {
        return knowledgePointRelationMapper.deleteKnowledgePointRelationByIds(ids);
    }

    /**
     * 删除知识点关联（拓扑图边）信息
     * 
     * @param id 知识点关联（拓扑图边）主键
     * @return 结果
     */
    @Override
    public int deleteKnowledgePointRelationById(Long id)
    {
        return knowledgePointRelationMapper.deleteKnowledgePointRelationById(id);
    }
}
