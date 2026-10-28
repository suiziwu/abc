package com.ruoyi.project.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.ruoyi.project.domain.JobIndustryChain;

/**
 * 岗位产业链与岗位群映射 Mapper。
 *
 * @author ruoyi
 */
public interface JobIndustryChainMapper
{
    List<JobIndustryChain> selectChainList(JobIndustryChain query);

    JobIndustryChain selectChainById(Long id);

    JobIndustryChain selectChainByJobCode(@Param("jobCode") String jobCode);

    JobIndustryChain selectChainByJobName(@Param("jobName") String jobName);

    int insertChain(JobIndustryChain record);

    int updateChain(JobIndustryChain record);

    int deleteChainByIds(Long[] ids);
}
