package com.ruoyi.project.service.impl;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.ruoyi.project.domain.JobCompetencyGraph;
import com.ruoyi.project.mapper.JobCompetencyGraphMapper;
import com.ruoyi.project.service.IJobCompetencyGraphService;

/**
 * 岗位能力图谱服务实现。
 *
 * @author ruoyi
 */
@Service
public class JobCompetencyGraphServiceImpl implements IJobCompetencyGraphService
{
    @Autowired
    private JobCompetencyGraphMapper jobCompetencyGraphMapper;

    @Override
    public List<JobCompetencyGraph> selectJobCompetencyGraphList(JobCompetencyGraph query)
    {
        return jobCompetencyGraphMapper.selectJobCompetencyGraphList(query);
    }

    @Override
    public JobCompetencyGraph selectJobCompetencyGraphById(Long id)
    {
        return jobCompetencyGraphMapper.selectJobCompetencyGraphById(id);
    }

    @Override
    public JobCompetencyGraph selectLatestJobCompetencyGraphByName(String jobName)
    {
        return jobCompetencyGraphMapper.selectLatestJobCompetencyGraphByName(jobName);
    }

    @Override
    public List<String> selectDistinctJobNames(String keyword)
    {
        return jobCompetencyGraphMapper.selectDistinctJobNames(keyword);
    }

    @Override
    public int updateReviewStatus(Long id, String reviewStatus)
    {
        return jobCompetencyGraphMapper.updateReviewStatus(id, reviewStatus);
    }

    @Override
    public int insertJobCompetencyGraph(JobCompetencyGraph record)
    {
        return jobCompetencyGraphMapper.insertJobCompetencyGraph(record);
    }

    @Override
    public int deleteJobCompetencyGraphByIds(Long[] ids)
    {
        return jobCompetencyGraphMapper.deleteJobCompetencyGraphByIds(ids);
    }
}
