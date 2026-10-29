package com.ruoyi.project.talent.service;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.ruoyi.project.talent.mapper.TalentMapper;

/**
 * 人才培养方案智能优化服务。
 *
 * <p>岗位数据来自本模块自有表（talent_job_posting / talent_job_skill / talent_major）；
 * 课程与知识点只读引用知识库（subject / knowledge_point），在知识库新增课程后看板自动同步。</p>
 *
 * <p>分析链路：已入库岗位 → 岗位方向归类 → 技能需求统计 → 知识库课程覆盖比对 → 预警与优化建议。</p>
 *
 * @author ruoyi
 */
@Service
public class TalentAnalysisService
{
    /** 参与趋势计算的最小批次样本量 */
    private static final int MIN_BATCH_JOBS = 20;

    /** 专业匹配取岗位需求前 N 项技能 */
    private static final int MATCH_SKILL_LIMIT = 30;

    /** 专业覆盖率低于该值触发预警（%） */
    private static final double COVERAGE_WARN_RATE = 35.0;

    /** 知识库中作为专业基础必修课的课程编码 */
    private static final List<String> BASIC_SUBJECT_CODES = Arrays.asList(
            "sub_algorithm", "sub_os", "sub_network", "sub_computer_org");

    /** 快照批次的内部标识（新导入但未标记采集批次的岗位），展示时替换为当前时间 */
    private static final String SNAPSHOT_LABEL = "当前快照";

    /** 快照展示时间格式 */
    private static final String SNAPSHOT_TIME_PATTERN = "yyyy-MM-dd HH:mm";

    @Autowired
    private TalentMapper talentMapper;

    /** 趋势口径与看板曲线卡保持一致：按清洗后数据文件的采集时间做累计岗位提及率 */
    @Autowired
    private TalentFolderTrendService folderTrendService;

    // ==================== 总览 / 技能分析 ====================

    /**
     * 看板总览指标。
     *
     * @param jobFamily 岗位方向，为空表示全部
     * @param batchDate 采集时间维度：空表示全部时间，当前快照 表示快照，其余为批次日期
     */
    public Map<String, Object> overview(String jobFamily, String batchDate)
    {
        String filter = batchFilter(batchDate);
        long total = nvl(talentMapper.countPostings(jobFamily, filter));
        long snapshot = isSnapshotScope(batchDate) ? nvl(talentMapper.countSnapshotPostings(jobFamily)) : 0;
        long batch = total - snapshot;
        List<Map<String, Object>> families = talentMapper.selectJobFamilyStat(filter);
        List<Map<String, Object>> batches = talentMapper.selectBatchList(jobFamily);
        List<Map<String, Object>> industries = talentMapper.selectIndustryStat(filter);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("jobFamily", jobFamily == null ? "" : jobFamily);
        result.put("batchDate", batchDate == null ? "" : batchDate);
        result.put("scopeLabel", scopeLabel(batchDate));
        result.put("families", families);
        result.put("familyCount", families.size());
        result.put("industries", industries);
        result.put("industryCount", industries.size());
        result.put("snapshotJobs", snapshot);
        result.put("batchJobs", batch);
        result.put("postingCount", total);
        result.put("skillCount", nvl(talentMapper.countSkills(jobFamily, filter)));
        result.put("majorCount", nvl(talentMapper.countMajors()));
        result.put("courseCount", nvl(talentMapper.countSubjects()));
        result.put("pointCount", nvl(talentMapper.countSubjectPoints()));
        result.put("basicCourseCount", nvl(talentMapper.countBasicCourses()));
        result.put("batchCount", batches.size());
        result.put("lastImportTime", talentMapper.selectLastImportTime());
        return result;
    }

    /**
     * 技能需求排行（基于清洗快照，按岗位方向过滤）。
     */
    public List<Map<String, Object>> skillRank(String jobFamily, String category, int limit, String batchDate)
    {
        limit = Math.max(1, Math.min(limit, 200));   // 防止 limit=0/-1 生成非法 SQL
        String filter = batchFilter(batchDate);
        long base = Math.max(1L, nvl(talentMapper.countPostings(jobFamily, filter)));
        List<Map<String, Object>> rank = talentMapper.selectSkillRank(jobFamily, category, limit, filter);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : rank)
        {
            long jobCount = asLong(row.get("jobCount"));
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("skillName", row.get("skillName"));
            item.put("skillCategory", row.get("skillCategory"));
            item.put("jobCount", jobCount);
            item.put("rate", round(jobCount * 100.0 / base));
            result.add(item);
        }
        return result;
    }

    /**
     * 技能热度曲线（历史批次 + 当前快照归一化后的岗位提及率）。
     *
     * <p>曲线始终展示完整时间轴；batchDate 仅用于标记当前筛选时点，便于与其它卡片对照。</p>
     */
    public Map<String, Object> skillTrend(int topN, String jobFamily, String batchDate)
    {
        return skillTrend(topN, jobFamily, batchDate, MIN_BATCH_JOBS);
    }

    /**
     * 技能热度曲线（可放宽单批次样本阈值）。
     *
     * @param minJobs 单批次最小样本量，前端在默认阈值下取不到数据时会传 1 兜底
     */
    public Map<String, Object> skillTrend(int topN, String jobFamily, String batchDate, int minJobs)
    {
        topN = Math.max(1, Math.min(topN, 30));   // 防止 topN<=0 生成非法 SQL
        minJobs = Math.max(1, minJobs);
        List<Map<String, Object>> rank = talentMapper.selectSkillRank(jobFamily, null, topN, "");
        List<String> skills = new ArrayList<>();
        for (Map<String, Object> row : rank)
        {
            String name = asString(row.get("skillName"));
            if (!name.isEmpty())
            {
                skills.add(name);
            }
        }

        List<String> batchDates = new ArrayList<>();
        Map<String, Long> totals = new LinkedHashMap<>();
        for (Map<String, Object> batch : talentMapper.selectBatchList(jobFamily))
        {
            long jobCount = asLong(batch.get("jobCount"));
            // 样本量过小的批次不参与趋势计算，避免曲线失真
            if (jobCount < minJobs)
            {
                continue;
            }
            addDay(batchDates, totals, normalizeDay(asString(batch.get("batchDate"))), jobCount);
        }

        // 当前快照按入库日期拆成多个点；只给一个“当前快照”的话，曲线永远只有一个点
        long snapshotJobs = nvl(talentMapper.countSnapshotPostings(jobFamily));
        boolean snapshotIncluded = snapshotJobs > 0;
        for (Map<String, Object> row : talentMapper.selectSnapshotDayStats(jobFamily))
        {
            addDay(batchDates, totals, normalizeDay(asString(row.get("batchDate"))), asLong(row.get("jobCount")));
        }
        Collections.sort(batchDates);

        Map<String, Map<String, Long>> counts = new LinkedHashMap<>();
        for (String skill : skills)
        {
            counts.put(skill, new LinkedHashMap<String, Long>());
        }
        if (!skills.isEmpty() && !batchDates.isEmpty())
        {
            for (Map<String, Object> row : talentMapper.selectSkillTrend(skills, jobFamily))
            {
                addSkillCount(counts, asString(row.get("skillName")),
                        normalizeDay(asString(row.get("batchDate"))), asLong(row.get("jobCount")));
            }
            for (Map<String, Object> row : talentMapper.selectSnapshotSkillTrend(skills, jobFamily))
            {
                addSkillCount(counts, asString(row.get("skillName")),
                        normalizeDay(asString(row.get("batchDate"))), asLong(row.get("jobCount")));
            }
        }

        // 若某个历史批次对当前展示技能全部 0 命中，说明该批次口径与当前技能体系不一致，剔除避免曲线整体掉到 0
        if (!skills.isEmpty())
        {
            List<String> drop = new ArrayList<>();
            for (String date : batchDates)
            {
                long hits = 0;
                for (String skill : skills)
                {
                    Long value = counts.get(skill).get(date);
                    if (value != null)
                    {
                        hits += value;
                    }
                }
                if (hits == 0)
                {
                    drop.add(date);
                }
            }
            batchDates.removeAll(drop);
            for (String date : drop)
            {
                totals.remove(date);
            }
        }

        List<Map<String, Object>> series = new ArrayList<>();
        for (String skill : skills)
        {
            List<Double> data = new ArrayList<>();
            for (String date : batchDates)
            {
                long total = Math.max(1L, totals.get(date));
                Long hit = counts.get(skill).get(date);
                data.add(round(hit == null ? 0.0 : hit * 100.0 / total));
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", skill);
            item.put("data", data);
            series.add(item);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("jobFamily", jobFamily == null ? "" : jobFamily);
        result.put("selectedBatch", batchDate == null ? "" : batchDate);
        result.put("batches", batchDates);
        result.put("totals", new ArrayList<>(totals.values()));
        result.put("series", series);
        result.put("minBatchJobs", minJobs);
        result.put("snapshotIncluded", snapshotIncluded);
        result.put("snapshotJobs", snapshotJobs);
        return result;
    }

    /** 把 "2026-10-27 18:46" 归一到 "2026-10-27"，让同一天的批次合并成一个时间点 */
    private static String normalizeDay(String date)
    {
        if (date == null)
        {
            return "";
        }
        String value = date.trim();
        return value.length() > 10 ? value.substring(0, 10) : value;
    }

    /** 追加/合并某天的岗位总数 */
    private static void addDay(List<String> dates, Map<String, Long> totals, String day, long count)
    {
        if (day == null || day.isEmpty() || count <= 0)
        {
            return;
        }
        if (!dates.contains(day))
        {
            dates.add(day);
        }
        Long old = totals.get(day);
        totals.put(day, old == null ? count : old + count);
    }

    /** 追加/合并某天某技能的提及数 */
    private static void addSkillCount(Map<String, Map<String, Long>> counts, String skill, String day, long count)
    {
        Map<String, Long> byDay = counts.get(skill);
        if (byDay == null || day == null || day.isEmpty() || count <= 0)
        {
            return;
        }
        Long old = byDay.get(day);
        byDay.put(day, old == null ? count : old + count);
    }

    /**
     * 技能类别分布。
     */
    public List<Map<String, Object>> categoryStat(String jobFamily, String batchDate)
    {
        List<Map<String, Object>> rows = talentMapper.selectCategoryStat(jobFamily, batchFilter(batchDate));
        long total = 0;
        for (Map<String, Object> row : rows)
        {
            total += asLong(row.get("value"));
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : rows)
        {
            long value = asLong(row.get("value"));
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", row.get("name"));
            item.put("value", value);
            item.put("rate", round(total == 0 ? 0.0 : value * 100.0 / total));
            result.add(item);
        }
        return result;
    }

    // ==================== 专业 / 课程 ====================

    /**
     * 专业匹配度：每个专业面向自身岗位方向的岗位需求技能，与知识库课程（课程名称 + 描述 + 知识点名称）的覆盖情况。
     *
     * <p>课程数据只读引用知识库 subject / knowledge_point，在知识库新增课程后自动生效，无需改动本模块。</p>
     */
    public List<Map<String, Object>> majorMatch(String jobFamily, String batchDate)
    {
        String filter = batchFilter(batchDate);
        List<SubjectDoc> docs = loadSubjectDocs();
        List<Map<String, Object>> majors = talentMapper.selectMajors();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> major : majors)
        {
            String family = asString(major.get("jobFamily"));
            if (jobFamily != null && !jobFamily.isEmpty() && !family.equals(jobFamily))
            {
                continue;
            }
            Long majorId = asLong(major.get("majorId"));
            List<Map<String, Object>> skills = talentMapper.selectSkillRank(family, null, MATCH_SKILL_LIMIT, filter);
            FamilyMatch match = matchFamily(docs, skills);
            int total = match.matched.size() + match.gaps.size();
            double rate = total == 0 ? 0.0 : match.matched.size() * 100.0 / total;
            long jobCount = nvl(talentMapper.countPostings(family, filter));
            boolean sampleEnough = jobCount >= MIN_BATCH_JOBS;
            List<String> relatedCourses = new ArrayList<>();
            for (SubjectDoc doc : match.relatedSubjects)
            {
                relatedCourses.add(doc.courseName);
            }

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("majorId", majorId);
            item.put("majorCode", major.get("majorCode"));
            item.put("majorName", major.get("majorName"));
            item.put("jobFamily", family);
            item.put("graduate", major.get("graduate"));
            item.put("courseCount", relatedCourses.size());
            item.put("relatedCourses", head(relatedCourses, 8));
            item.put("jobCount", jobCount);
            item.put("sampleEnough", sampleEnough);
            item.put("totalSkills", total);
            item.put("matchedCount", match.matched.size());
            item.put("gapCount", match.gaps.size());
            item.put("matchRate", round(rate));
            item.put("level", !sampleEnough ? "样本不足"
                    : rate >= 55.0 ? "良好" : rate >= COVERAGE_WARN_RATE ? "一般" : "偏低");
            item.put("matchedSkills", head(match.matched, 12));
            item.put("gapSkills", head(match.gaps, 8));
            result.add(item);
        }
        // 样本量大的方向优先展示，其次按匹配度排序
        result.sort((a, b) -> {
            long jobA = asLong(a.get("jobCount"));
            long jobB = asLong(b.get("jobCount"));
            if (jobA != jobB)
            {
                return Long.compare(jobB, jobA);
            }
            return Double.compare(toDouble(b.get("matchRate")), toDouble(a.get("matchRate")));
        });
        return result;
    }

    /**
     * 课程体系：知识库课程清单，标注专业基础必修课与当前方向命中的技能。
     */
    public Map<String, Object> curriculum(String jobFamily, String batchDate)
    {
        List<SubjectDoc> docs = loadSubjectDocs();
        FamilyMatch match = null;
        if (jobFamily != null && !jobFamily.isEmpty())
        {
            match = matchFamily(docs,
                    talentMapper.selectSkillRank(jobFamily, null, MATCH_SKILL_LIMIT, batchFilter(batchDate)));
        }

        List<Map<String, Object>> basic = new ArrayList<>();
        List<Map<String, Object>> professional = new ArrayList<>();
        List<Map<String, Object>> all = new ArrayList<>();
        for (SubjectDoc doc : docs)
        {
            boolean isBasic = BASIC_SUBJECT_CODES.contains(doc.courseCode);
            List<String> hits = match == null ? Collections.emptyList()
                    : match.hits.getOrDefault(doc.subjectId, Collections.emptyList());
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("subjectId", doc.subjectId);
            item.put("courseCode", doc.courseCode);
            item.put("courseName", doc.courseName);
            item.put("description", doc.description);
            item.put("pointCount", doc.pointCount);
            item.put("pointNames", head(doc.pointNames, 12));
            item.put("basic", isBasic);
            item.put("courseGroup", isBasic ? "专业基础课" : "专业课程");
            item.put("matchedSkills", head(hits, 12));
            item.put("matchedCount", hits.size());
            all.add(item);
            if (isBasic)
            {
                basic.add(item);
            }
            else
            {
                professional.add(item);
            }
        }
        // 当前方向命中的课程优先展示
        professional.sort((a, b) -> Long.compare(asLong(b.get("matchedCount")), asLong(a.get("matchedCount"))));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("jobFamily", jobFamily == null ? "" : jobFamily);
        result.put("subjects", all);
        result.put("basicCourses", basic);
        result.put("majorCourses", professional);
        result.put("basicCount", nvl(talentMapper.countBasicCourses()));
        result.put("courseCount", nvl(talentMapper.countSubjects()));
        result.put("pointCount", nvl(talentMapper.countSubjectPoints()));
        return result;
    }

    // ==================== 预警 / 建议 ====================

    /**
     * 专业预警：岗位方向无对应专业、专业课程覆盖率偏低、采集批次样本不足。
     */
    public List<Map<String, Object>> warnings(String jobFamily, String batchDate)
    {
        List<Map<String, Object>> result = new ArrayList<>();
        List<Map<String, Object>> matches = majorMatch(jobFamily, batchDate);

        // 0) 数据状态自检：快照为空时，岗位数/技能需求/专业匹配都会变成 0，必须在页面上说明原因
        if (isSnapshotScope(batchDate) && nvl(talentMapper.countSnapshotPostings(null)) == 0)
        {
            result.add(warning("high", "数据状态异常", "当前没有快照岗位，看板指标会全部为 0",
                    "本模块的岗位数、技能需求与专业匹配都基于快照（batch_date 为空）计算。"
                            + "请将最近一批岗位按「快照」重新导入（导入时采集批次留空），或检查是否有脚本把快照批次回填成了历史批次。",
                    "依据：talent_job_posting 中 batch_date 为空的记录数为 0"));
        }

        // 1) 岗位方向没有对应专业（『其他』是兜底方向，不参与专业设置预警）
        for (Map<String, Object> row : talentMapper.selectFamiliesWithoutMajor(batchFilter(batchDate)))
        {
            String family = asString(row.get("jobFamily"));
            if ("其他".equals(family))
            {
                continue;
            }
            if (jobFamily != null && !jobFamily.isEmpty() && !family.equals(jobFamily))
            {
                continue;
            }
            long jobs = asLong(row.get("jobCount"));
            result.add(warning(jobs >= 200 ? "high" : "medium", "专业设置预警",
                    "岗位方向『" + family + "』尚无对应专业",
                    "该方向已采集 " + jobs + " 条岗位，但当前培养方案中没有面向该方向的专业，建议新增专业方向，或将相关课程作为选修模块并入相近专业。",
                    "依据：岗位表中该方向岗位 " + jobs + " 条"));
        }

        // 2) 专业课程覆盖率偏低（仅统计样本量足够的方向）
        List<Map<String, Object>> lows = new ArrayList<>();
        List<String> thinFamilies = new ArrayList<>();
        for (Map<String, Object> match : matches)
        {
            if (!Boolean.TRUE.equals(match.get("sampleEnough")))
            {
                String family = asString(match.get("jobFamily"));
                if (!thinFamilies.contains(family))
                {
                    thinFamilies.add(family);
                }
                continue;
            }
            if (asLong(match.get("totalSkills")) > 0 && toDouble(match.get("matchRate")) < COVERAGE_WARN_RATE)
            {
                lows.add(match);
            }
        }
        int shown = 0;
        for (Map<String, Object> match : lows)
        {
            if (shown >= 5)
            {
                break;
            }
            result.add(warning("medium", "专业覆盖预警",
                    "『" + match.get("majorName") + "』课程覆盖偏低（" + match.get("matchRate") + "%）",
                    "该专业面向『" + match.get("jobFamily") + "』方向，知识库关联 " + match.get("courseCount") + " 门课程；在岗位需求前 "
                            + match.get("totalSkills") + " 项技能中覆盖 " + match.get("matchedCount") + " 项，缺口如『"
                            + joinSkills(match.get("gapSkills")) + "』，建议在知识库增设对应课程或实训环节。",
                    "依据：该方向岗位 " + match.get("jobCount") + " 条，知识库课程文本命中比对"));
            shown++;
        }
        if (lows.size() > shown)
        {
            result.add(warning("medium", "专业覆盖预警",
                    "另有 " + (lows.size() - shown) + " 个专业课程覆盖率低于 " + (int) COVERAGE_WARN_RATE + "%",
                    "这些专业方向的岗位需求技能与知识库课程文本重合度较低，建议结合岗位需求整体修订培养方案。",
                    "依据：专业知识库课程覆盖率统计"));
        }

        // 3) 小样本方向汇总提示
        if (!thinFamilies.isEmpty())
        {
            List<String> shownFamilies = head(thinFamilies, 8);
            String more = thinFamilies.size() > shownFamilies.size()
                    ? " 等 " + thinFamilies.size() + " 个方向" : "";
            result.add(warning("low", "样本不足",
                    thinFamilies.size() + " 个岗位方向样本量低于 " + MIN_BATCH_JOBS + " 条",
                    "涉及方向：" + String.join("、", shownFamilies) + more
                            + "。这些方向来自逐行业对照表，每个二级行业仅 2 条岗位样本，覆盖率与匹配度仅作参考，建议扩大该行业采集范围后再据此调整培养方案。",
                    "依据：各岗位方向快照岗位量统计"));
        }

        // 4) 样本量不足的采集批次
        for (Map<String, Object> batch : talentMapper.selectBatchList(jobFamily))
        {
            long jobCount = asLong(batch.get("jobCount"));
            if (jobCount < MIN_BATCH_JOBS)
            {
                result.add(warning("low", "样本不足",
                        batch.get("batchDate") + " 批次仅 " + jobCount + " 条岗位",
                        "样本量低于 " + MIN_BATCH_JOBS + " 条，未参与趋势计算，建议扩大采集范围后再评估该批次的变化。",
                        "依据：采集批次样本量统计"));
            }
        }

        if (result.isEmpty())
        {
            result.add(warning("low", "运行正常", "暂未发现预警",
                    "各专业对其岗位方向的高需求技能覆盖处于合理区间，采集批次样本量充足。",
                    "依据：本模块入库岗位与课程体系比对结果"));
        }
        return result;
    }

    /**
     * 智能优化建议：技能热度变化 + 课程缺口 + 专业覆盖率。
     *
     * <p>每条建议都附带数据依据与置信度，避免无法追溯的结论。</p>
     */
    public List<Map<String, Object>> suggestions(String jobFamily, String batchDate)
    {
        List<Map<String, Object>> result = new ArrayList<>();
        List<Map<String, Object>> matches = majorMatch(jobFamily, batchDate);
        String basis = basisOf(jobFamily, batchDate);

        // 0) 非 IT 行业方向样本普遍较小，样本不足时不输出趋势类结论
        if (jobFamily != null && !jobFamily.isEmpty())
        {
            long postings = nvl(talentMapper.countPostings(jobFamily, batchFilter(batchDate)));
            if (postings < MIN_BATCH_JOBS)
            {
                result.add(suggestion("数据说明", "『" + jobFamily + "』样本仅 " + postings + " 条，暂不输出技能趋势结论",
                        "该方向已入库 " + postings + " 条岗位（少于 " + MIN_BATCH_JOBS
                                + " 条），技能热度与覆盖率仅作参考。建议扩大该行业/方向的采集范围后再生成培养方案调整结论。",
                        new String[] { "样本不足", jobFamily }, basis, "低"));
                Map<String, Object> thin = targetMajor(matches);
                if (thin != null && asLong(thin.get("totalSkills")) > 0)
                {
                    result.add(suggestion("课程对照",
                            "『" + thin.get("majorName") + "』课程覆盖 " + thin.get("matchedCount") + "/" + thin.get("totalSkills") + " 项岗位技能",
                            "基于该方向 " + postings + " 条岗位提取的 " + thin.get("totalSkills") + " 项技能，当前 "
                                    + thin.get("courseCount") + " 门课程覆盖 " + thin.get("matchedCount")
                                    + " 项" + (asLong(thin.get("gapCount")) > 0
                                            ? "，尚未覆盖『" + joinSkills(thin.get("gapSkills")) + "』" : "")
                                    + "。因样本量有限，建议将其作为课程内容核对清单，不建议直接调减课时。",
                            new String[] { "课程对照", asString(thin.get("jobFamily")) }, basis, "低"));
                }
                return result;
            }
        }

        // 1) 技能热度变化（上升 / 回落）：与上方技能热度曲线同源同口径（清洗后数据按文件时间累计），
        //    不再用数据库批次各自独立归一，避免小体量批次把跨度放大
        Map<String, Object> trend = folderTrendService.skillTrend(10, jobFamily, null, MIN_BATCH_JOBS);
        String trendBasis = trendBasisOf(trend);
        @SuppressWarnings("unchecked")
        List<String> batches = (List<String>) trend.get("batches");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> series = (List<Map<String, Object>>) trend.get("series");

        List<Map<String, Object>> growths = new ArrayList<>();
        for (Map<String, Object> line : series)
        {
            @SuppressWarnings("unchecked")
            List<Double> data = (List<Double>) line.get("data");
            if (data.size() < 2)
            {
                continue;
            }
            double first = data.get(0);
            double last = data.get(data.size() - 1);
            Map<String, Object> growth = new LinkedHashMap<>();
            growth.put("skill", line.get("name"));
            growth.put("first", round(first));
            growth.put("last", round(last));
            growth.put("delta", round(last - first));
            growth.put("from", batches.get(0));
            growth.put("to", batches.get(batches.size() - 1));
            growths.add(growth);
        }
        growths.sort((a, b) -> Double.compare(toDouble(b.get("delta")), toDouble(a.get("delta"))));

        int added = 0;
        for (Map<String, Object> growth : growths)
        {
            if (added >= 2 || toDouble(growth.get("delta")) < 1.0)
            {
                break;
            }
            // 波动过大的技能多为批次采集关键词差异造成，不作为培养方案调整依据
            if (toDouble(growth.get("delta")) > 25.0)
            {
                continue;
            }
            String skill = asString(growth.get("skill"));
            result.add(suggestion("技能趋势", "『" + skill + "』岗位需求上升 " + growth.get("delta") + " 个百分点",
                    "从 " + growth.get("from") + " 到 " + growth.get("to") + "，提及率由 " + growth.get("first") + "% 升至 "
                            + growth.get("last") + "%。建议在" + majorTextFor(skill, matches, jobFamily)
                            + "增设『" + skill + "』实训模块并更新课程大纲。",
                    new String[] { "技能热度", skill }, trendBasis, trendConfidence(batches.size())));
            added++;
        }
        if (growths.isEmpty())
        {
            // 无足够批次时给出说明，避免让用户误以为算法失效
            result.add(suggestion("数据说明", "暂无法生成技能热度趋势",
                    "当前" + (jobFamily == null || jobFamily.isEmpty() ? "" : "该方向") + "可对比的采集批次不足 2 期（单期样本需 ≥ "
                            + MIN_BATCH_JOBS + " 条），趋势类建议暂不输出。",
                    new String[] { "技能热度" }, trendBasis, "低"));
        }

        // 需求回落：仅取回落最明显的一项，提示压缩课时
        for (int i = growths.size() - 1; i >= 0; i--)
        {
            Map<String, Object> growth = growths.get(i);
            double delta = toDouble(growth.get("delta"));
            if (delta > -1.0)
            {
                break;
            }
            String skill = asString(growth.get("skill"));
            if (delta < -25.0)
            {
                result.add(suggestion("数据说明", "『" + skill + "』提及率波动 " + Math.abs(delta) + " 个百分点，暂不作为调课依据",
                        "该技能提及率由 " + growth.get("first") + "% 变为 " + growth.get("last")
                                + "%，波动幅度已超过正常区间，通常由各批次采集关键词构成不同导致。建议先核对批次样本构成，再决定是否调整课程。",
                        new String[] { "数据质量", skill }, trendBasis, "低"));
                break;
            }
            result.add(suggestion("技能趋势", "『" + skill + "』岗位需求回落 " + Math.abs(delta) + " 个百分点",
                    "提及率由 " + growth.get("first") + "% 降至 " + growth.get("last")
                            + "%。建议适当压缩该技能的基础课时，把实训资源向增长技能倾斜，避免课程内容与岗位需求脱节。",
                    new String[] { "技能热度", skill }, trendBasis, trendConfidence(batches.size())));
            break;
        }

        // 2) 课程缺口（以当前方向对应专业为准）
        Map<String, Object> target = targetMajor(matches);
        if (target != null)
        {
            @SuppressWarnings("unchecked")
            List<String> gaps = (List<String>) target.get("gapSkills");
            int gapCount = (int) asLong(target.get("gapCount"));
            if (gapCount > 0 && gaps != null && !gaps.isEmpty())
            {
                result.add(suggestion("课程缺口",
                        "『" + target.get("majorName") + "』有 " + gapCount + " 项高需求技能未进入课程",
                        "岗位需求前 " + target.get("totalSkills") + " 项技能中，" + target.get("matchedCount") + " 项已被知识库课程覆盖，"
                                + "『" + String.join("、", gaps) + "』等尚未在知识库课程名称/描述/知识点中出现。建议新增对应课程或综合实训项目。",
                        new String[] { "课程重构", asString(target.get("jobFamily")) },
                        "依据：该方向岗位 " + target.get("jobCount") + " 条，取需求前 " + target.get("totalSkills")
                                + " 技能与知识库 " + target.get("courseCount") + " 门课程逐项比对",
                        confidence(overview(asString(target.get("jobFamily")), batchDate))));
            }
        }

        // 3) 覆盖率最低且样本足够的专业（majorMatch 按岗位量排序，不能直接取末位）
        Map<String, Object> lowest = null;
        for (Map<String, Object> item : matches)
        {
            if (!Boolean.TRUE.equals(item.get("sampleEnough")) || asLong(item.get("totalSkills")) <= 0)
            {
                continue;
            }
            if (lowest == null || toDouble(item.get("matchRate")) < toDouble(lowest.get("matchRate")))
            {
                lowest = item;
            }
        }
        if (lowest != null && !lowest.equals(target) && toDouble(lowest.get("matchRate")) < COVERAGE_WARN_RATE)
        {
            result.add(suggestion("专业优化",
                    "『" + lowest.get("majorName") + "』培养方案需重点调整",
                    "该专业面向『" + lowest.get("jobFamily") + "』方向，课程对岗位需求技能的覆盖率为 " + lowest.get("matchRate")
                            + "%（" + lowest.get("matchedCount") + "/" + lowest.get("totalSkills")
                            + "），建议引入企业真实岗位案例，按岗位技能要求重组课程模块与实训环节。",
                    new String[] { "专业预警", asString(lowest.get("jobFamily")) },
                    "依据：该方向岗位 " + lowest.get("jobCount") + " 条，知识库课程文本命中比对",
                    confidence(overview(asString(lowest.get("jobFamily")), batchDate))));
        }

        if (result.isEmpty())
        {
            result.add(suggestion("运行正常", "暂无优化建议", "当前岗位数据与课程体系的匹配情况处于合理区间，建议保持周期性采集以持续监测。",
                    new String[] { "运行正常" }, basis, "中"));
        }
        return result;
    }

    // ==================== 数据来源说明 ====================

    /**
     * 数据来源与指标口径（岗位来自本模块自有表，课程只读引用知识库）。
     */
    public Map<String, Object> dataSource(String batchDate)
    {
        String filter = batchFilter(batchDate);
        long total = nvl(talentMapper.countPostings(null, filter));
        long snapshot = isSnapshotScope(batchDate) ? nvl(talentMapper.countSnapshotPostings(null)) : 0;
        long batch = total - snapshot;
        List<Map<String, Object>> families = talentMapper.selectJobFamilyStat(filter);
        for (Map<String, Object> family : families)
        {
            long jobs = asLong(family.get("jobCount"));
            family.put("rate", round(total == 0 ? 0.0 : jobs * 100.0 / total));
        }
        List<Map<String, Object>> industries = talentMapper.selectIndustryStat(filter);
        for (Map<String, Object> industry : industries)
        {
            long jobs = asLong(industry.get("jobCount"));
            industry.put("rate", round(total == 0 ? 0.0 : jobs * 100.0 / total));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("batchDate", batchDate == null ? "" : batchDate);
        result.put("scopeLabel", scopeLabel(batchDate));
        result.put("snapshotJobs", snapshot);
        result.put("batchJobs", batch);
        result.put("postingCount", total);
        result.put("skillCount", nvl(talentMapper.countSkills(null, filter)));
        result.put("lastUpdate", talentMapper.selectLastImportTime());
        List<Map<String, Object>> batches = talentMapper.selectBatchList(null);
        result.put("batches", batches);
        result.put("batchCount", batches.size());
        result.put("families", families);
        result.put("familyCount", families.size());
        result.put("industries", industries);
        result.put("industryCount", industries.size());

        List<String> flow = new ArrayList<>();
        flow.add("采集：按招聘网站公开职位信息与行业对照表离线采集岗位");
        flow.add("导入：通过看板「导入 CSV」入库，同一文件重复导入自动覆盖");
        flow.add("清洗：去重、去空技能、技能标签大小写归一，形成结构化技能快照");
        flow.add("归类：按采集关键词 + 职位名称，将岗位划分为 " + families.size() + " 个岗位方向（IT 细分方向与非 IT 行业方向）");
        flow.add("比对：岗位需求技能与知识库课程（名称 + 描述 + 知识点）逐项匹配");
        flow.add("输出：技能热度曲线、专业匹配度、专业预警与优化建议");
        result.put("flow", flow);

        List<String> caliber = new ArrayList<>();
        caliber.add("时间维度：按采集批次（batch_date）切分数据；「全部时间」跨批次汇总，未标记批次的最近入库岗位单独作为一个时间维度（展示为看板刷新时间），其余为对应采集时间");
        caliber.add("技能热度：某技能在当期岗位中的提及数 ÷ 当期岗位数；历史批次逐期归一化，末点为未标记批次的最近入库岗位");
        caliber.add("技能需求排行：当前所选时间维度中提及该技能的岗位数（去重），占比以同方向、同时间维度的岗位数为分母");
        caliber.add("专业匹配度：知识库课程（课程名称 + 描述 + 知识点名称）命中该专业岗位方向需求前 " + MATCH_SKILL_LIMIT + " 技能的比例，专业基础课同样计入覆盖");
        caliber.add("预警规则：课程覆盖率 < " + (int) COVERAGE_WARN_RATE + "%、岗位方向无对应专业、单批次样本 < " + MIN_BATCH_JOBS + " 条");
        caliber.add("样本量：快照岗位数 < " + MIN_BATCH_JOBS + " 条的方向标记为「样本不足」，其匹配度仅作参考，不输出趋势类调课建议");
        caliber.add("优化建议：仅基于上述可追溯指标生成，不做人工预设结论；每条建议标注数据依据与置信度");
        result.put("caliber", caliber);

        List<String> limitations = new ArrayList<>();
        limitations.add("样本来自公开招聘职位信息，存在城市、薪资与岗位结构偏差，不能等同于全行业岗位总量");
        limitations.add("原始批次按采集关键词抓取，不同批次关键词构成不同，趋势变化会混入采集策略变化的影响");
        limitations.add("行业对照表按每个二级行业抽取 2 条岗位，仅代表该行业的部分岗位形态，小样本方向结论需谨慎使用");
        limitations.add("技能由职位描述关键词匹配识别，同义写法（如 K8s / Kubernetes）在清洗阶段已归一，但仍可能有遗漏");
        limitations.add("课程覆盖率为知识库课程文本与岗位技能的自动匹配结果，不代表教务层面的正式课程映射，需专业教师复核后使用");
        result.put("limitations", limitations);
        return result;
    }

    // ==================== 时间维度 ====================

    /**
     * 采集时间维度清单：所有批次 + 当前快照，供看板时间筛选与时间表展示。
     *
     * <p>时间按爬取数据的采集批次（batch_date）划分，数据随时间累积不会互相覆盖。</p>
     */
    public List<Map<String, Object>> batches()
    {
        List<Map<String, Object>> rows = talentMapper.selectBatchOverview();
        long total = 0;
        for (Map<String, Object> row : rows)
        {
            total += asLong(row.get("jobCount"));
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : rows)
        {
            String date = asString(row.get("batchDate"));
            boolean snapshot = date.isEmpty();
            long jobs = asLong(row.get("jobCount"));
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("batchDate", date);
            item.put("label", snapshot ? snapshotLabel() : date);
            item.put("query", snapshot ? SNAPSHOT_LABEL : date);
            item.put("snapshot", snapshot);
            item.put("jobCount", jobs);
            item.put("skillCount", asLong(row.get("skillCount")));
            item.put("familyCount", asLong(row.get("familyCount")));
            item.put("share", round(total == 0 ? 0.0 : jobs * 100.0 / total));
            item.put("firstTime", row.get("firstTime"));
            item.put("lastTime", row.get("lastTime"));
            result.add(item);
        }
        // 当前快照置顶，其余按采集时间倒序（最新批次在前）
        result.sort((a, b) -> {
            boolean snapshotA = Boolean.TRUE.equals(a.get("snapshot"));
            boolean snapshotB = Boolean.TRUE.equals(b.get("snapshot"));
            if (snapshotA != snapshotB)
            {
                return snapshotA ? -1 : 1;
            }
            return asString(b.get("batchDate")).compareTo(asString(a.get("batchDate")));
        });
        return result;
    }

    // ==================== 内部工具 ====================

    /**
     * 时间维度解析：null 表示全部时间；当前快照 转换为空批次；其余按采集批次精确匹配。
     */
    private String batchFilter(String batchDate)
    {
        if (batchDate == null || batchDate.isEmpty())
        {
            return null;
        }
        return SNAPSHOT_LABEL.equals(batchDate) ? "" : batchDate;
    }

    private boolean isSnapshotScope(String batchDate)
    {
        return batchDate == null || batchDate.isEmpty() || SNAPSHOT_LABEL.equals(batchDate);
    }

    private String scopeLabel(String batchDate)
    {
        if (batchDate == null || batchDate.isEmpty())
        {
            return "全部时间";
        }
        return SNAPSHOT_LABEL.equals(batchDate) ? snapshotLabel() : batchDate;
    }

    /**
     * 快照维度的展示时间：优先用「最近一次入库时间」，没有数据时才退回当前时间。
     *
     * <p>以前直接取 {@code new Date()}，下拉里显示的时间每次刷新都在变，
     * 和技能热度曲线按文件时间生成的时间点对不上，看起来像“爬完没更新”。</p>
     */
    private String snapshotLabel()
    {
        String latest = talentMapper.selectLastImportTime();
        if (latest != null && latest.trim().length() >= SNAPSHOT_TIME_PATTERN.length())
        {
            return latest.trim().substring(0, SNAPSHOT_TIME_PATTERN.length());
        }
        return new SimpleDateFormat(SNAPSHOT_TIME_PATTERN).format(new Date());
    }

    private Map<String, Object> warning(String level, String type, String title, String content, String basis)
    {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("level", level);
        item.put("type", type);
        item.put("title", title);
        item.put("content", content);
        item.put("basis", basis);
        return item;
    }

    private Map<String, Object> suggestion(String type, String title, String content, String[] tags, String basis,
            String confidence)
    {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("type", type);
        item.put("title", title);
        item.put("content", content);
        List<String> tagList = new ArrayList<>();
        Collections.addAll(tagList, tags);
        item.put("tags", tagList);
        item.put("basis", basis);
        item.put("confidence", confidence);
        return item;
    }

    /**
     * 数据置信度：按当前方向入库岗位量粗分。
     */
    private String confidence(Map<String, Object> overview)
    {
        long postings = asLong(overview.get("postingCount"));
        if (postings >= 2000)
        {
            return "高";
        }
        if (postings >= 500)
        {
            return "中";
        }
        return "低";
    }

    /**
     * 趋势类结论的置信度：取决于可对比的采集批次数量。
     */
    private String trendConfidence(int batchCount)
    {
        if (batchCount >= 3)
        {
            return "中";
        }
        return "低";
    }

    /**
     * 趋势类建议的数据依据：与上方技能热度曲线同源，写明文件数与累计岗位量。
     */
    private String trendBasisOf(Map<String, Object> trend)
    {
        List<?> batches = (List<?>) trend.get("batches");
        List<?> totals = (List<?>) trend.get("totals");
        long total = totals == null || totals.isEmpty() ? 0L : asLong(totals.get(totals.size() - 1));
        return "依据：『04-清洗后数据』" + (batches == null ? 0 : batches.size()) + " 个采集文件，累计 "
                + total + " 条岗位，按文件采集时间做累计岗位提及率";
    }

    private Map<String, Object> targetMajor(List<Map<String, Object>> matches)
    {
        for (Map<String, Object> match : matches)
        {
            if (asLong(match.get("totalSkills")) > 0)
            {
                return match;
            }
        }
        return null;
    }

    /**
     * 某技能应落到哪个专业：优先找已覆盖该技能的专业，否则用当前方向对应专业。
     */
    private String majorTextFor(String skill, List<Map<String, Object>> matches, String jobFamily)
    {
        for (Map<String, Object> match : matches)
        {
            @SuppressWarnings("unchecked")
            List<String> matched = (List<String>) match.get("matchedSkills");
            if (matched != null && matched.contains(skill))
            {
                return "『" + match.get("majorName") + "』";
            }
        }
        if (jobFamily != null && !jobFamily.isEmpty() && !matches.isEmpty())
        {
            return "『" + matches.get(0).get("majorName") + "』";
        }
        return "相关专业";
    }

    /**
     * 加载知识库课程及其知识点，拼成用于技能匹配的文本。
     *
     * <p>只读查询 subject / knowledge_point；在知识库新增课程后自动进入比对范围。</p>
     */
    private List<SubjectDoc> loadSubjectDocs()
    {
        Map<Long, List<String>> points = new LinkedHashMap<>();
        for (Map<String, Object> point : talentMapper.selectSubjectPoints())
        {
            Long subjectId = asLong(point.get("subjectId"));
            List<String> names = points.get(subjectId);
            if (names == null)
            {
                names = new ArrayList<>();
                points.put(subjectId, names);
            }
            String name = asString(point.get("pointName"));
            if (!name.isEmpty())
            {
                names.add(name);
            }
        }

        List<SubjectDoc> docs = new ArrayList<>();
        for (Map<String, Object> subject : talentMapper.selectSubjects())
        {
            Long subjectId = asLong(subject.get("subjectId"));
            SubjectDoc doc = new SubjectDoc();
            doc.subjectId = subjectId;
            doc.courseName = asString(subject.get("courseName"));
            doc.courseCode = asString(subject.get("courseCode"));
            doc.description = asString(subject.get("description"));
            doc.pointCount = asLong(subject.get("pointCount"));
            doc.pointNames = points.getOrDefault(subjectId, Collections.emptyList());
            doc.text = doc.courseName + " " + doc.description + " " + String.join(" ", doc.pointNames);
            docs.add(doc);
        }
        return docs;
    }

    /**
     * 将某岗位方向的技能需求与知识库课程文本逐项匹配。
     */
    private FamilyMatch matchFamily(List<SubjectDoc> docs, List<Map<String, Object>> skills)
    {
        FamilyMatch match = new FamilyMatch();
        for (Map<String, Object> skill : skills)
        {
            String name = asString(skill.get("skillName"));
            if (name.isEmpty())
            {
                continue;
            }
            Pattern pattern = skillPattern(name);
            boolean hit = false;
            for (SubjectDoc doc : docs)
            {
                if (pattern.matcher(doc.text).find())
                {
                    hit = true;
                    List<String> hits = match.hits.get(doc.subjectId);
                    if (hits == null)
                    {
                        hits = new ArrayList<>();
                        match.hits.put(doc.subjectId, hits);
                    }
                    if (!hits.contains(name))
                    {
                        hits.add(name);
                    }
                }
            }
            if (hit)
            {
                match.matched.add(name);
            }
            else
            {
                match.gaps.add(name);
            }
        }
        for (SubjectDoc doc : docs)
        {
            if (match.hits.containsKey(doc.subjectId))
            {
                match.relatedSubjects.add(doc);
            }
        }
        return match;
    }

    /** 知识库课程（只读视图） */
    private static final class SubjectDoc
    {
        private Long subjectId;
        private String courseName;
        private String courseCode;
        private String description;
        private long pointCount;
        private List<String> pointNames;
        private String text;
    }

    /** 某岗位方向与知识库课程的匹配结果 */
    private static final class FamilyMatch
    {
        private final List<String> matched = new ArrayList<>();
        private final List<String> gaps = new ArrayList<>();
        private final List<SubjectDoc> relatedSubjects = new ArrayList<>();
        private final Map<Long, List<String>> hits = new LinkedHashMap<>();
    }

    /**
     * 技能匹配模式：英文技能使用字母数字边界，避免 Go 命中 Django 之类的问题；中文技能直接包含匹配。
     */
    private Pattern skillPattern(String skill)
    {
        String trimmed = skill.trim();
        if (trimmed.matches(".*[A-Za-z].*"))
        {
            return Pattern.compile("(?<![A-Za-z0-9+#])" + Pattern.quote(trimmed) + "(?![A-Za-z0-9+#])",
                    Pattern.CASE_INSENSITIVE);
        }
        return Pattern.compile(Pattern.quote(trimmed), Pattern.CASE_INSENSITIVE);
    }

    private String basisOf(String jobFamily, String batchDate)
    {
        String filter = batchFilter(batchDate);
        long total = nvl(talentMapper.countPostings(jobFamily, filter));
        List<Map<String, Object>> batches = talentMapper.selectBatchList(jobFamily);
        StringBuilder sb = new StringBuilder("依据：公开招聘岗位数据（本地入库）");
        if (jobFamily != null && !jobFamily.isEmpty())
        {
            sb.append("，岗位方向『").append(jobFamily).append("』");
        }
        sb.append("，时间维度「").append(scopeLabel(batchDate)).append("」共 ").append(total).append(" 条岗位");
        if (!batches.isEmpty())
        {
            sb.append("（库内批次 ").append(batches.get(0).get("batchDate"));
            if (batches.size() > 1)
            {
                sb.append(" ~ ").append(batches.get(batches.size() - 1).get("batchDate"));
            }
            sb.append("）");
        }
        return sb.toString();
    }

    private String joinSkills(Object value)
    {
        if (!(value instanceof List))
        {
            return "-";
        }
        List<?> list = (List<?>) value;
        List<String> names = new ArrayList<>();
        for (int i = 0; i < list.size() && i < 5; i++)
        {
            names.add(String.valueOf(list.get(i)));
        }
        return names.isEmpty() ? "-" : String.join("、", names);
    }

    private List<String> head(List<String> values, int size)
    {
        if (values.size() <= size)
        {
            return new ArrayList<>(values);
        }
        return new ArrayList<>(values.subList(0, size));
    }

    private String asString(Object value)
    {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private long nvl(Long value)
    {
        return value == null ? 0L : value;
    }

    private long asLong(Object value)
    {
        if (value == null)
        {
            return 0L;
        }
        if (value instanceof Number)
        {
            return ((Number) value).longValue();
        }
        try
        {
            return Long.parseLong(String.valueOf(value));
        }
        catch (NumberFormatException e)
        {
            return 0L;
        }
    }

    private double toDouble(Object value)
    {
        if (value instanceof Number)
        {
            return ((Number) value).doubleValue();
        }
        try
        {
            return Double.parseDouble(String.valueOf(value));
        }
        catch (Exception e)
        {
            return 0.0;
        }
    }

    private double round(double value)
    {
        return Math.round(value * 10.0) / 10.0;
    }
}
