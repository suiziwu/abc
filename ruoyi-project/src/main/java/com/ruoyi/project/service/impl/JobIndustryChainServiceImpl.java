package com.ruoyi.project.service.impl;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.ruoyi.project.domain.JobIndustryChain;
import com.ruoyi.project.mapper.JobIndustryChainMapper;
import com.ruoyi.project.service.IJobIndustryChainService;

/**
 * 岗位产业链与岗位群映射服务实现。
 *
 * @author ruoyi
 */
@Service
public class JobIndustryChainServiceImpl implements IJobIndustryChainService
{
    @Autowired
    private JobIndustryChainMapper jobIndustryChainMapper;

    @Override
    public List<JobIndustryChain> selectChainList(JobIndustryChain query)
    {
        return jobIndustryChainMapper.selectChainList(query);
    }

    @Override
    public JobIndustryChain selectChainById(Long id)
    {
        return jobIndustryChainMapper.selectChainById(id);
    }

    @Override
    public JobIndustryChain selectChainByJobCode(String jobCode)
    {
        return jobIndustryChainMapper.selectChainByJobCode(jobCode);
    }

    @Override
    public JobIndustryChain selectChainByJobName(String jobName)
    {
        return jobIndustryChainMapper.selectChainByJobName(jobName);
    }

    @Override
    public int insertChain(JobIndustryChain record)
    {
        return jobIndustryChainMapper.insertChain(record);
    }

    @Override
    public int updateChain(JobIndustryChain record)
    {
        return jobIndustryChainMapper.updateChain(record);
    }

    @Override
    public int deleteChainByIds(Long[] ids)
    {
        return jobIndustryChainMapper.deleteChainByIds(ids);
    }
}
