package com.ruoyi.project.service;

import java.util.List;

import com.ruoyi.project.domain.JobCompetencyGraph;

/**
 * 岗位能力图谱服务。
 *
 * @author ruoyi
 */
public interface IJobCompetencyGraphService
{
    List<JobCompetencyGraph> selectJobCompetencyGraphList(JobCompetencyGraph query);

    JobCompetencyGraph selectJobCompetencyGraphById(Long id);

    JobCompetencyGraph selectLatestJobCompetencyGraphByName(String jobName);

    List<String> selectDistinctJobNames(String keyword);

    int updateReviewStatus(Long id, String reviewStatus);

    int insertJobCompetencyGraph(JobCompetencyGraph record);

    int deleteJobCompetencyGraphByIds(Long[] ids);
}
