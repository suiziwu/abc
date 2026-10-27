package com.ruoyi.project.service;

import java.util.List;
import com.ruoyi.project.domain.KnowledgePoint;

/**
 * 知识点Service接口
 * 
 * @author ruoyi
 * @date 2026-06-24
 */
public interface IKnowledgePointService 
{
    /**
     * 查询知识点
     * 
     * @param id 知识点主键
     * @return 知识点
     */
    public KnowledgePoint selectKnowledgePointById(Long id);

    /**
     * 查询知识点列表
     * 
     * @param knowledgePoint 知识点
     * @return 知识点集合
     */
    public List<KnowledgePoint> selectKnowledgePointList(KnowledgePoint knowledgePoint);

    /**
     * 新增知识点
     * 
     * @param knowledgePoint 知识点
     * @return 结果
     */
    public int insertKnowledgePoint(KnowledgePoint knowledgePoint);

    /**
     * 修改知识点
     * 
     * @param knowledgePoint 知识点
     * @return 结果
     */
    public int updateKnowledgePoint(KnowledgePoint knowledgePoint);

    /**
     * 批量删除知识点
     * 
     * @param ids 需要删除的知识点主键集合
     * @return 结果
     */
    public int deleteKnowledgePointByIds(Long[] ids);

    /**
     * 删除知识点信息
     * 
     * @param id 知识点主键
     * @return 结果
     */
    public int deleteKnowledgePointById(Long id);
}
