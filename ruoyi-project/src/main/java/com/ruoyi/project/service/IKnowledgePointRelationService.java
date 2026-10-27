package com.ruoyi.project.service;

import java.util.List;
import com.ruoyi.project.domain.KnowledgePointRelation;

/**
 * 知识点关联（拓扑图边）Service接口
 * 
 * @author ruoyi
 * @date 2026-06-23
 */
public interface IKnowledgePointRelationService 
{
    /**
     * 查询知识点关联（拓扑图边）
     * 
     * @param id 知识点关联（拓扑图边）主键
     * @return 知识点关联（拓扑图边）
     */
    public KnowledgePointRelation selectKnowledgePointRelationById(Long id);

    /**
     * 查询知识点关联（拓扑图边）列表
     * 
     * @param knowledgePointRelation 知识点关联（拓扑图边）
     * @return 知识点关联（拓扑图边）集合
     */
    public List<KnowledgePointRelation> selectKnowledgePointRelationList(KnowledgePointRelation knowledgePointRelation);

    /**
     * 新增知识点关联（拓扑图边）
     * 
     * @param knowledgePointRelation 知识点关联（拓扑图边）
     * @return 结果
     */
    public int insertKnowledgePointRelation(KnowledgePointRelation knowledgePointRelation);

    /**
     * 修改知识点关联（拓扑图边）
     * 
     * @param knowledgePointRelation 知识点关联（拓扑图边）
     * @return 结果
     */
    public int updateKnowledgePointRelation(KnowledgePointRelation knowledgePointRelation);

    /**
     * 批量删除知识点关联（拓扑图边）
     * 
     * @param ids 需要删除的知识点关联（拓扑图边）主键集合
     * @return 结果
     */
    public int deleteKnowledgePointRelationByIds(Long[] ids);

    /**
     * 删除知识点关联（拓扑图边）信息
     * 
     * @param id 知识点关联（拓扑图边）主键
     * @return 结果
     */
    public int deleteKnowledgePointRelationById(Long id);
}
