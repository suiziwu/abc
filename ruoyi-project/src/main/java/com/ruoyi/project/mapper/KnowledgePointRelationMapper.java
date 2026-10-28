package com.ruoyi.project.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.project.domain.KnowledgePointRelation;

/**
 * 知识点关联（拓扑图边）Mapper接口
 * 
 * @author ruoyi
 * @date 2026-06-23
 */
public interface KnowledgePointRelationMapper 
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
     * 查询指定知识点集合内部的关联（用于导图同步去重，避免全表扫描）
     *
     * @param pointIds 知识点主键集合
     * @return 关联集合
     */
    public List<KnowledgePointRelation> selectRelationsByPointIds(@Param("pointIds") List<Long> pointIds);

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
     * 删除知识点关联（拓扑图边）
     * 
     * @param id 知识点关联（拓扑图边）主键
     * @return 结果
     */
    public int deleteKnowledgePointRelationById(Long id);

    /**
     * 批量删除知识点关联（拓扑图边）
     * 
     * @param ids 需要删除的数据主键集合
     * @return 结果
     */
    public int deleteKnowledgePointRelationByIds(Long[] ids);
}
