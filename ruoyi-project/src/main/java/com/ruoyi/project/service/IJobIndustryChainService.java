package com.ruoyi.project.service;

import java.util.List;

import com.ruoyi.project.domain.JobIndustryChain;

/**
 * 岗位产业链与岗位群映射服务。
 *
 * @author ruoyi
 */
public interface IJobIndustryChainService
{
    List<JobIndustryChain> selectChainList(JobIndustryChain query);

    JobIndustryChain selectChainById(Long id);

    JobIndustryChain selectChainByJobCode(String jobCode);

    JobIndustryChain selectChainByJobName(String jobName);

    int insertChain(JobIndustryChain record);

    int updateChain(JobIndustryChain record);

    int deleteChainByIds(Long[] ids);
}
