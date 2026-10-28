package com.ruoyi.project.mapper;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Param;

import com.ruoyi.project.domain.JobCompetencyWeight;

/**
 * 国家职业标准技能要求权重 Mapper。
 *
 * @author ruoyi
 */
public interface JobCompetencyWeightMapper
{
    List<Map<String, Object>> selectWeightsByJobCode(@Param("jobCode") String jobCode);

    List<Map<String, Object>> selectWeightsByJobName(@Param("jobName") String jobName);

    List<JobCompetencyWeight> selectWeightList(JobCompetencyWeight query);

    JobCompetencyWeight selectWeightById(Long id);

    int insertWeight(JobCompetencyWeight record);

    int updateWeight(JobCompetencyWeight record);

    int deleteWeightByIds(Long[] ids);
}
