package com.ruoyi.project.talent.mapper;

import java.util.List;
import java.util.Map;

/**
 * 行业编码对照表 Mapper接口（数据源：talent_industry_code 表）
 *
 * @author ruoyi
 */
public interface TalentIndustryCodeMapper
{
    /** 全部行业编码记录（按源表序号排序） */
    List<Map<String, Object>> selectAll();

    /** 最近一次导入时间（yyyy-MM-dd HH:mm） */
    String selectLatestCreateTime();
}
