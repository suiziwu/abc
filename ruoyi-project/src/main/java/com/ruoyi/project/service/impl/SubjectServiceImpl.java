package com.ruoyi.project.service.impl;

import java.util.List;
import com.ruoyi.common.utils.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.project.mapper.SubjectMapper;
import com.ruoyi.project.domain.Subject;
import com.ruoyi.project.service.ISubjectService;

/**
 * 学科Service业务层处理
 * 
 * @author ruoyi
 * @date 2026-06-23
 */
@Service
public class SubjectServiceImpl implements ISubjectService 
{
    @Autowired
    private SubjectMapper subjectMapper;

    /**
     * 查询学科
     * 
     * @param id 学科主键
     * @return 学科
     */
    @Override
    public Subject selectSubjectById(Long id)
    {
        return subjectMapper.selectSubjectById(id);
    }

    /**
     * 查询学科列表
     * 
     * @param subject 学科
     * @return 学科
     */
    @Override
    public List<Subject> selectSubjectList(Subject subject)
    {
        return subjectMapper.selectSubjectList(subject);
    }

    /**
     * 新增学科
     * 
     * @param subject 学科
     * @return 结果
     */
    @Override
    public int insertSubject(Subject subject)
    {
        subject.setCreateTime(DateUtils.getNowDate());
        return subjectMapper.insertSubject(subject);
    }

    /**
     * 修改学科
     * 
     * @param subject 学科
     * @return 结果
     */
    @Override
    public int updateSubject(Subject subject)
    {
        subject.setUpdateTime(DateUtils.getNowDate());
        return subjectMapper.updateSubject(subject);
    }

    /**
     * 批量删除学科
     * 
     * @param ids 需要删除的学科主键
     * @return 结果
     */
    @Override
    public int deleteSubjectByIds(Long[] ids)
    {
        return subjectMapper.deleteSubjectByIds(ids);
    }

    /**
     * 删除学科信息
     * 
     * @param id 学科主键
     * @return 结果
     */
    @Override
    public int deleteSubjectById(Long id)
    {
        return subjectMapper.deleteSubjectById(id);
    }
}
