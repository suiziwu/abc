package com.ruoyi.project.service;

import java.util.List;
import com.ruoyi.project.domain.UserKnowledgeAssessment;

/**
 * 用户知识点测评记录Service接口
 * 
 * @author ruoyi
 * @date 2026-07-10
 */
public interface IUserKnowledgeAssessmentService 
{
    /**
     * 查询用户知识点测评记录
     * 
     * @param id 用户知识点测评记录主键
     * @return 用户知识点测评记录
     */
    public UserKnowledgeAssessment selectUserKnowledgeAssessmentById(Long id);

    /**
     * 查询用户知识点测评记录列表
     * 
     * @param userKnowledgeAssessment 用户知识点测评记录
     * @return 用户知识点测评记录集合
     */
    public List<UserKnowledgeAssessment> selectUserKnowledgeAssessmentList(UserKnowledgeAssessment userKnowledgeAssessment);

    /**
     * 新增用户知识点测评记录
     * 
     * @param userKnowledgeAssessment 用户知识点测评记录
     * @return 结果
     */
    public int insertUserKnowledgeAssessment(UserKnowledgeAssessment userKnowledgeAssessment);

    /**
     * 修改用户知识点测评记录
     * 
     * @param userKnowledgeAssessment 用户知识点测评记录
     * @return 结果
     */
    public int updateUserKnowledgeAssessment(UserKnowledgeAssessment userKnowledgeAssessment);

    /**
     * 批量删除用户知识点测评记录
     * 
     * @param ids 需要删除的用户知识点测评记录主键集合
     * @return 结果
     */
    public int deleteUserKnowledgeAssessmentByIds(Long[] ids);

    /**
     * 删除用户知识点测评记录信息
     * 
     * @param id 用户知识点测评记录主键
     * @return 结果
     */
    public int deleteUserKnowledgeAssessmentById(Long id);
}
