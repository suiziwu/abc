package com.ruoyi.project.service;

import java.util.List;
import com.ruoyi.project.domain.Subject;

/**
 * 学科Service接口
 * 
 * @author ruoyi
 * @date 2026-06-23
 */
public interface ISubjectService 
{
    /**
     * 查询学科
     * 
     * @param id 学科主键
     * @return 学科
     */
    public Subject selectSubjectById(Long id);

    /**
     * 查询学科列表
     * 
     * @param subject 学科
     * @return 学科集合
     */
    public List<Subject> selectSubjectList(Subject subject);

    /**
     * 新增学科
     * 
     * @param subject 学科
     * @return 结果
     */
    public int insertSubject(Subject subject);

    /**
     * 修改学科
     * 
     * @param subject 学科
     * @return 结果
     */
    public int updateSubject(Subject subject);

    /**
     * 批量删除学科
     * 
     * @param ids 需要删除的学科主键集合
     * @return 结果
     */
    public int deleteSubjectByIds(Long[] ids);

    /**
     * 删除学科信息
     * 
     * @param id 学科主键
     * @return 结果
     */
    public int deleteSubjectById(Long id);
}
