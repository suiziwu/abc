package com.ruoyi.project.talent.mapper;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Param;

/**
 * 人才培养（岗位技能需求 / 课程体系）Mapper接口
 *
 * @author ruoyi
 */
public interface TalentMapper
{
    /** 快照岗位数（可按岗位方向过滤） */
    Long countSnapshotPostings(@Param("jobFamily") String jobFamily);

    /** 原始批次岗位数（可按岗位方向过滤） */
    Long countBatchPostings(@Param("jobFamily") String jobFamily);

    /** 岗位数：batchDate 为 null 表示全部时间，"" 表示当前快照，其余为采集批次 */
    Long countPostings(@Param("jobFamily") String jobFamily, @Param("batchDate") String batchDate);

    /** 技能去重总数（可按岗位方向、采集时间过滤） */
    Long countSkills(@Param("jobFamily") String jobFamily, @Param("batchDate") String batchDate);

    /** 专业总数 */
    Long countMajors();

    /** 知识库课程（subject）总数 */
    Long countSubjects();

    /** 知识库知识点（knowledge_point）总数 */
    Long countSubjectPoints();

    /** 专业基础课（必修）数量 */
    Long countBasicCourses();

    /** 知识库课程清单（含知识点数） */
    List<Map<String, Object>> selectSubjects();

    /** 知识库知识点清单（用于课程内容匹配） */
    List<Map<String, Object>> selectSubjectPoints();

    /** 最近入库时间 */
    String selectLastImportTime();

    /** 岗位方向分布（含对应专业名称、所属行业） */
    List<Map<String, Object>> selectJobFamilyStat(@Param("batchDate") String batchDate);

    /** 行业分布（一级行业与岗位数） */
    List<Map<String, Object>> selectIndustryStat(@Param("batchDate") String batchDate);

    /** 采集批次与岗位数 */
    List<Map<String, Object>> selectBatchList(@Param("jobFamily") String jobFamily);

    /** 采集时间维度清单（含岗位数、技能数、方向数、采集时间范围） */
    List<Map<String, Object>> selectBatchOverview();

    /** 技能需求排行 */
    List<Map<String, Object>> selectSkillRank(@Param("jobFamily") String jobFamily,
            @Param("category") String category, @Param("limit") int limit,
            @Param("batchDate") String batchDate);

    /** 技能类别分布 */
    List<Map<String, Object>> selectCategoryStat(@Param("jobFamily") String jobFamily,
            @Param("batchDate") String batchDate);

    /** 指定技能在各批次的岗位提及数 */
    List<Map<String, Object>> selectSkillTrend(@Param("skills") List<String> skills,
            @Param("jobFamily") String jobFamily);

    /** 当前快照按入库日期统计岗位数（用于把快照拆成时间点） */
    List<Map<String, Object>> selectSnapshotDayStats(@Param("jobFamily") String jobFamily);

    /** 当前快照按入库日期统计技能提及数 */
    List<Map<String, Object>> selectSnapshotSkillTrend(@Param("skills") List<String> skills,
            @Param("jobFamily") String jobFamily);

    /** 专业清单 */
    List<Map<String, Object>> selectMajors();

    /** 暂无对应专业的岗位方向 */
    List<Map<String, Object>> selectFamiliesWithoutMajor(@Param("batchDate") String batchDate);

    /** 按来源与采集时间删除岗位技能（同批次重复导入前清理） */
    int deleteSkillsBySource(@Param("source") String source, @Param("batchDate") String batchDate);

    /** 按来源与采集时间删除岗位（同批次重复导入前清理） */
    int deletePostingsBySource(@Param("source") String source, @Param("batchDate") String batchDate);

    /** 新增岗位（回填自增主键） */
    int insertPosting(com.ruoyi.project.talent.domain.TalentJobPosting posting);

    /** 新增岗位技能标签 */
    int insertSkill(@Param("postingId") Long postingId, @Param("batchDate") String batchDate,
            @Param("skillName") String skillName, @Param("skillCategory") String skillCategory);

    /** 技能 → 类别字典（talent_skill_category） */
    List<Map<String, Object>> selectSkillCategoryDict();
}
