package com.ruoyi.project.service;

import java.util.List;
import java.util.Map;

import com.ruoyi.project.domain.JobCompetencyWeight;

/**
 * 国家职业标准技能要求权重服务。
 *
 * @author ruoyi
 */
public interface IJobCompetencyWeightService
{
    List<Map<String, Object>> selectByJobCode(String jobCode);

    List<Map<String, Object>> selectByJobName(String jobName);

    List<JobCompetencyWeight> selectWeightList(JobCompetencyWeight query);

    JobCompetencyWeight selectWeightById(Long id);

    int insertWeight(JobCompetencyWeight record);

    int updateWeight(JobCompetencyWeight record);

    int deleteWeightByIds(Long[] ids);
}
