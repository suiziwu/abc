package com.ruoyi.project.service.impl;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.ruoyi.project.domain.JobCompetencyWeight;
import com.ruoyi.project.mapper.JobCompetencyWeightMapper;
import com.ruoyi.project.service.IJobCompetencyWeightService;

/**
 * 国家职业标准技能要求权重服务实现。
 *
 * @author ruoyi
 */
@Service
public class JobCompetencyWeightServiceImpl implements IJobCompetencyWeightService
{
    @Autowired
    private JobCompetencyWeightMapper jobCompetencyWeightMapper;

    @Override
    public List<Map<String, Object>> selectByJobCode(String jobCode)
    {
        return jobCompetencyWeightMapper.selectWeightsByJobCode(jobCode);
    }

    @Override
    public List<Map<String, Object>> selectByJobName(String jobName)
    {
        return jobCompetencyWeightMapper.selectWeightsByJobName(jobName);
    }

    @Override
    public List<JobCompetencyWeight> selectWeightList(JobCompetencyWeight query)
    {
        return jobCompetencyWeightMapper.selectWeightList(query);
    }

    @Override
    public JobCompetencyWeight selectWeightById(Long id)
    {
        return jobCompetencyWeightMapper.selectWeightById(id);
    }

    @Override
    public int insertWeight(JobCompetencyWeight record)
    {
        return jobCompetencyWeightMapper.insertWeight(record);
    }

    @Override
    public int updateWeight(JobCompetencyWeight record)
    {
        return jobCompetencyWeightMapper.updateWeight(record);
    }

    @Override
    public int deleteWeightByIds(Long[] ids)
    {
        return jobCompetencyWeightMapper.deleteWeightByIds(ids);
    }
}
