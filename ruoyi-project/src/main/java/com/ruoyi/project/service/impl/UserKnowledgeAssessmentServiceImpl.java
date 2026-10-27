package com.ruoyi.project.service.impl;

import java.util.List;
import com.ruoyi.common.utils.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.project.mapper.UserKnowledgeAssessmentMapper;
import com.ruoyi.project.domain.UserKnowledgeAssessment;
import com.ruoyi.project.service.IUserKnowledgeAssessmentService;

/**
 * 用户知识点测评记录Service业务层处理
 * 
 * @author ruoyi
 * @date 2026-07-10
 */
@Service
public class UserKnowledgeAssessmentServiceImpl implements IUserKnowledgeAssessmentService 
{
    @Autowired
    private UserKnowledgeAssessmentMapper userKnowledgeAssessmentMapper;

    /**
     * 查询用户知识点测评记录
     * 
     * @param id 用户知识点测评记录主键
     * @return 用户知识点测评记录
     */
    @Override
    public UserKnowledgeAssessment selectUserKnowledgeAssessmentById(Long id)
    {
        return userKnowledgeAssessmentMapper.selectUserKnowledgeAssessmentById(id);
    }

    /**
     * 查询用户知识点测评记录列表
     * 
     * @param userKnowledgeAssessment 用户知识点测评记录
     * @return 用户知识点测评记录
     */
    @Override
    public List<UserKnowledgeAssessment> selectUserKnowledgeAssessmentList(UserKnowledgeAssessment userKnowledgeAssessment)
    {
        return userKnowledgeAssessmentMapper.selectUserKnowledgeAssessmentList(userKnowledgeAssessment);
    }

    /**
     * 新增用户知识点测评记录
     * 
     * @param userKnowledgeAssessment 用户知识点测评记录
     * @return 结果
     */
    @Override
    public int insertUserKnowledgeAssessment(UserKnowledgeAssessment userKnowledgeAssessment)
    {
        userKnowledgeAssessment.setCreateTime(DateUtils.getNowDate());
        return userKnowledgeAssessmentMapper.insertUserKnowledgeAssessment(userKnowledgeAssessment);
    }

    /**
     * 修改用户知识点测评记录
     * 
     * @param userKnowledgeAssessment 用户知识点测评记录
     * @return 结果
     */
    @Override
    public int updateUserKnowledgeAssessment(UserKnowledgeAssessment userKnowledgeAssessment)
    {
        userKnowledgeAssessment.setUpdateTime(DateUtils.getNowDate());
        return userKnowledgeAssessmentMapper.updateUserKnowledgeAssessment(userKnowledgeAssessment);
    }

    /**
     * 批量删除用户知识点测评记录
     * 
     * @param ids 需要删除的用户知识点测评记录主键
     * @return 结果
     */
    @Override
    public int deleteUserKnowledgeAssessmentByIds(Long[] ids)
    {
        return userKnowledgeAssessmentMapper.deleteUserKnowledgeAssessmentByIds(ids);
    }

    /**
     * 删除用户知识点测评记录信息
     * 
     * @param id 用户知识点测评记录主键
     * @return 结果
     */
    @Override
    public int deleteUserKnowledgeAssessmentById(Long id)
    {
        return userKnowledgeAssessmentMapper.deleteUserKnowledgeAssessmentById(id);
    }
}
