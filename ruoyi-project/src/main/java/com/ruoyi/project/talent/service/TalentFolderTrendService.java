package com.ruoyi.project.talent.service;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 技能热度曲线：直接读取「04-清洗后数据」文件夹，按文件修改时间分段。
 *
 * <p>每个 CSV 视为一个采集批次，横轴取文件修改时间；技能提及率由该文件内容现算，
 * 不再依赖数据库中的历史批次。</p>
 *
 * @author ruoyi
 */
@Service
public class TalentFolderTrendService
{
    private static final Logger log = LoggerFactory.getLogger(TalentFolderTrendService.class);

    /** 清洗后数据里可能出现的技能列，按列取值而不是从描述中抽取 */
    private static final String[] SKILL_COLUMNS = {
            "核心技能标签", "后端技能", "前端技能", "AI/大模型技能", "工程化/运维技能",
            "编程语言", "框架/库", "数据库/中间件", "工具/环境"
    };

    @Value("${talent.cleaned-data-dir:D:/000/fiveEi/04-清洗后数据}")
    private String cleanedDataDir;

    public Map<String, Object> skillTrend(int topN, String jobFamily, String batchDate, int minJobs)
    {
        topN = Math.max(1, Math.min(topN, 30));
        minJobs = Math.max(1, minJobs);

        List<Point> points = loadPoints(jobFamily);
        List<Point> kept = new ArrayList<>();
        for (Point point : points)
        {
            if (point.jobCount >= minJobs)
            {
                kept.add(point);
            }
        }

        // 先按全部文件累计命中次数，挑出展示的技能
        Map<String, Long> skillTotals = new LinkedHashMap<>();
        for (Point point : kept)
        {
            for (Map.Entry<String, Long> entry : point.skillCounts.entrySet())
            {
                skillTotals.merge(entry.getKey(), entry.getValue(), Long::sum);
            }
        }
        List<Map.Entry<String, Long>> ranked = new ArrayList<>(skillTotals.entrySet());
        ranked.sort((a, b) -> {
            int compare = Long.compare(b.getValue(), a.getValue());
            return compare != 0 ? compare : a.getKey().compareTo(b.getKey());
        });
        List<String> skills = new ArrayList<>();
        for (Map.Entry<String, Long> entry : ranked)
        {
            if (skills.size() >= topN)
            {
                break;
            }
            skills.add(entry.getKey());
        }

        List<String> batches = new ArrayList<>();
        List<Long> totals = new ArrayList<>();
        List<Long> batchTotals = new ArrayList<>();
        List<Map<String, Object>> files = new ArrayList<>();

        // 累计口径：每个时间点计算「截至该时点」的累计岗位与累计命中，
        // 避免小体量批次（几十上百条）把曲线拉出深谷
        long cumulativeJobs = 0;
        for (Point point : kept)
        {
            cumulativeJobs += point.jobCount;
            batchTotals.add(point.jobCount);
            batches.add(point.batch);
            totals.add(cumulativeJobs);
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("fileName", point.fileName);
            meta.put("batch", point.batch);
            meta.put("jobCount", point.jobCount);
            meta.put("cumulativeJobs", cumulativeJobs);
            files.add(meta);
        }

        List<Map<String, Object>> series = new ArrayList<>();
        for (String skill : skills)
        {
            List<Double> data = new ArrayList<>();
            long hits = 0;
            for (int i = 0; i < kept.size(); i++)
            {
                Long hit = kept.get(i).skillCounts.get(skill);
                hits += hit == null ? 0L : hit;
                long total = Math.max(1L, totals.get(i));
                data.add(Math.round(hits * 10000.0 / total) / 100.0);
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", skill);
            item.put("data", data);
            series.add(item);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("jobFamily", jobFamily == null ? "" : jobFamily);
        result.put("selectedBatch", batchDate == null ? "" : batchDate);
        result.put("batches", batches);
        result.put("totals", totals);
        result.put("batchTotals", batchTotals);
        result.put("rateMode", "cumulative");
        result.put("series", series);
        result.put("minBatchJobs", minJobs);
        result.put("snapshotIncluded", false);
        result.put("snapshotJobs", 0);
        result.put("sourceMode", "folder");
        result.put("folder", cleanedDataDir);
        result.put("files", files);
        return result;
    }

    private List<Point> loadPoints(String jobFamily)
    {
        List<Point> points = new ArrayList<>();
        File dir = new File(cleanedDataDir);
        File[] files = dir.listFiles((d, name) -> name.toLowerCase(Locale.ROOT).endsWith(".csv"));
        if (files == null || files.length == 0)
        {
            log.warn("清洗后数据目录不存在或没有 CSV：{}", cleanedDataDir);
            return points;
        }
        Arrays.sort(files, Comparator.comparingLong(File::lastModified));
        for (File file : files)
        {
            try
            {
                Point point = parseFile(file, jobFamily);
                if (point != null && point.jobCount > 0)
                {
                    points.add(point);
                }
            }
            catch (Exception e)
            {
                log.warn("读取清洗后数据失败：{}", file.getName(), e);
            }
        }
        return points;
    }

    private Point parseFile(File file, String jobFamily) throws Exception
    {
        byte[] bytes = Files.readAllBytes(file.toPath());
        int offset = 0;
        if (bytes.length >= 3 && (bytes[0] & 0xFF) == 0xEF && (bytes[1] & 0xFF) == 0xBB && (bytes[2] & 0xFF) == 0xBF)
        {
            offset = 3;
        }
        Charset charset = TalentCsvImportService.detectCharset(bytes, offset);
        List<List<String>> rows = TalentCsvImportService.parseCsv(new InputStreamReader(
                new ByteArrayInputStream(bytes, offset, bytes.length - offset), charset));
        if (rows.size() < 2)
        {
            return null;
        }

        Map<String, Integer> header = TalentCsvImportService.mapHeader(rows.get(0));
        Integer titleIdx = TalentCsvImportService.pick(header, "职位名称", "岗位名称", "job_title", "title");
        if (titleIdx == null)
        {
            return null;
        }
        Integer descIdx = TalentCsvImportService.pick(header, "职位描述", "岗位描述", "description");
        Integer levelIdx = TalentCsvImportService.pick(header, "层级", "level");
        Integer industryIdx = TalentCsvImportService.pick(header, "行业名称", "二级行业", "industry");
        List<Integer> skillIdx = new ArrayList<>();
        for (String column : SKILL_COLUMNS)
        {
            Integer idx = TalentCsvImportService.pick(header, column);
            if (idx != null)
            {
                skillIdx.add(idx);
            }
        }
        boolean labelMode = !skillIdx.isEmpty();

        Point point = new Point();
        point.fileName = file.getName();
        point.batch = new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date(file.lastModified()));
        point.skillCounts = new LinkedHashMap<>();

        for (int i = 1; i < rows.size(); i++)
        {
            List<String> cells = rows.get(i);
            String level = TalentCsvImportService.cell(cells, levelIdx);
            if (levelIdx != null && !level.isEmpty() && !"二级".equals(level))
            {
                continue;
            }
            String title = TalentCsvImportService.clean(TalentCsvImportService.cell(cells, titleIdx));
            if (title.isEmpty() || "未知职位".equals(title))
            {
                continue;
            }
            String desc = TalentCsvImportService.clean(TalentCsvImportService.cell(cells, descIdx));
            String family = TalentCsvImportService.resolveFamily(title, desc,
                    TalentCsvImportService.cell(cells, industryIdx));
            if (jobFamily != null && !jobFamily.isEmpty() && !jobFamily.equals(family))
            {
                continue;
            }

            Set<String> skills = labelMode
                    ? readSkillCells(cells, skillIdx)
                    : new LinkedHashSet<>(TalentCsvImportService.extractSkills(title + " " + desc, family));
            point.jobCount++;
            for (String skill : skills)
            {
                point.skillCounts.merge(skill, 1L, Long::sum);
            }
        }
        return point;
    }

    private static Set<String> readSkillCells(List<String> cells, List<Integer> skillIdx)
    {
        Set<String> skills = new LinkedHashSet<>();
        for (Integer idx : skillIdx)
        {
            String value = TalentCsvImportService.cell(cells, idx);
            if (value.isEmpty())
            {
                continue;
            }
            for (String item : value.split("[,，、;；]"))
            {
                String skill = item.trim();
                if (!skill.isEmpty())
                {
                    skills.add(skill);
                }
            }
        }
        return skills;
    }

    private static class Point
    {
        private String fileName;
        private String batch;
        private long jobCount;
        private Map<String, Long> skillCounts;
    }
}
