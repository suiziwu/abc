package com.ruoyi.project.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.ruoyi.project.domain.JobCompetencyGraph;

/**
 * 岗位能力图谱 Mapper。
 *
 * @author ruoyi
 */
public interface JobCompetencyGraphMapper
{
    /**
     * 查询图谱列表（不含全文）。
     */
    List<JobCompetencyGraph> selectJobCompetencyGraphList(JobCompetencyGraph query);

    /**
     * 按主键查询图谱（含全文）。
     */
    JobCompetencyGraph selectJobCompetencyGraphById(Long id);

    /**
     * 按岗位名称查询最新一条图谱（含全文），用于生成前判重。
     */
    JobCompetencyGraph selectLatestJobCompetencyGraphByName(@Param("jobName") String jobName);

    /**
     * 模糊查询已入库的岗位名称（去重），用于输入联想。
     */
    List<String> selectDistinctJobNames(@Param("keyword") String keyword);

    /**
     * 更新审核状态。
     */
    int updateReviewStatus(@Param("id") Long id, @Param("reviewStatus") String reviewStatus);

    /**
     * 新增图谱。
     */
    int insertJobCompetencyGraph(JobCompetencyGraph record);

    /**
     * 批量删除。
     */
    int deleteJobCompetencyGraphByIds(Long[] ids);
}
