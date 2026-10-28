package com.ruoyi.project.mapper;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Param;

/**
 * 岗位画像查询：用现有零散表拼出岗位信息，不依赖岗位主表。
 *
 * @author ruoyi
 */
public interface JobProfileMapper
{
    /**
     * 从国家职业标准权重表推断标准职业编码与名称。
     */
    List<Map<String, Object>> selectJobCandidates(@Param("keyword") String keyword,
            @Param("limit") int limit);

    /**
     * 从招聘岗位与技能表补充技能关键词。
     */
    List<String> selectPostingSkills(@Param("keyword") String keyword,
            @Param("limit") int limit);
}
