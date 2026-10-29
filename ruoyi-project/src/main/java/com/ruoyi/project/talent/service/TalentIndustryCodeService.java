package com.ruoyi.project.talent.service;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.ruoyi.project.talent.mapper.TalentIndustryCodeMapper;

/**
 * 行业编码对照表：数据来自数据库表 talent_industry_code（由 sql/talent_industry_code.sql 导入）。
 *
 * @author ruoyi
 */
@Service
public class TalentIndustryCodeService
{
    /** 默认行业目录（相对工程根目录），配置失效时由 TalentPathResolver 自动探测 */
    private static final String DEFAULT_INDUSTRY_DIR = "fiveEi/03-原始爬取数据";

    /** 找不到精确文件名时的备选目录：爬虫会把带时间戳的结果写在这里 */
    private static final String FALLBACK_INDUSTRY_DIR = "fiveEi/08-行业对照表";

    /** 逐行业抓取结果的文件名前缀（实际文件常带时间戳后缀，如 逐行业抓取结果_20261028_1140.csv） */
    private static final String RESULT_PREFIX = "逐行业抓取结果";

    private static final Logger log = LoggerFactory.getLogger(TalentIndustryCodeService.class);

    @Value("${talent.industry-dir:" + DEFAULT_INDUSTRY_DIR + "}")
    private String industryDir;

    @Autowired
    private TalentIndustryCodeMapper talentIndustryCodeMapper;

    public Map<String, Object> list() throws Exception
    {
        List<Map<String, Object>> list = talentIndustryCodeMapper.selectAll();
        int level1 = 0;
        int level2 = 0;
        for (Map<String, Object> item : list)
        {
            Object level = item.get("level");
            if ("一级".equals(level))
            {
                level1++;
            }
            else if ("二级".equals(level))
            {
                level2++;
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("rows", list);
        result.put("total", list.size());
        result.put("level1Count", level1);
        result.put("level2Count", level2);
        result.put("updatedAt", talentIndustryCodeMapper.selectLatestCreateTime());
        result.put("sourceFile", "talent_industry_code");
        return result;
    }

    /**
     * 预览「逐行业抓取结果.csv」中某个行业已爬取的岗位。
     *
     * <p>二级行业按行业编码精确匹配；一级行业按上级行业汇总其下所有二级行业的抓取岗位。</p>
     *
     * @param code  行业编码
     * @param name  行业名称
     * @param level 层级（一级 / 二级）
     */
    public Map<String, Object> crawlJobs(String code, String name, String level) throws Exception
    {
        File file = resolveCsvFile();
        if (!file.isFile())
        {
            throw new IllegalStateException("逐行业抓取结果.csv 不存在：" + file.getAbsolutePath());
        }
        byte[] bytes = Files.readAllBytes(file.toPath());
        int offset = 0;
        if (bytes.length >= 3 && (bytes[0] & 0xFF) == 0xEF && (bytes[1] & 0xFF) == 0xBB && (bytes[2] & 0xFF) == 0xBF)
        {
            offset = 3;
        }
        Charset charset = TalentCsvImportService.detectCharset(bytes, offset);
        List<List<String>> rows = TalentCsvImportService.parseCsv(new InputStreamReader(
                new ByteArrayInputStream(bytes, offset, bytes.length - offset), charset));

        List<Map<String, Object>> jobs = new ArrayList<>();
        int total = 0;
        if (rows.size() > 1)
        {
            Map<String, Integer> header = TalentCsvImportService.mapHeader(rows.get(0));
            Integer codeIdx = TalentCsvImportService.pick(header, "行业编码", "行业代码", "code");
            Integer nameIdx = TalentCsvImportService.pick(header, "行业名称", "名称", "name");
            Integer levelIdx = TalentCsvImportService.pick(header, "层级", "level");
            Integer parentIdx = TalentCsvImportService.pick(header, "上级行业", "父行业", "parent");
            Integer titleIdx = TalentCsvImportService.pick(header, "职位名称", "岗位名称", "jobTitle");
            Integer descIdx = TalentCsvImportService.pick(header, "职位描述", "岗位描述", "description");
            boolean byParent = "一级".equals(level);
            for (int i = 1; i < rows.size(); i++)
            {
                List<String> cells = rows.get(i);
                String title = TalentCsvImportService.clean(TalentCsvImportService.cell(cells, titleIdx));
                if (title.isEmpty())
                {
                    continue;
                }
                total++;
                String rowCode = TalentCsvImportService.clean(TalentCsvImportService.cell(cells, codeIdx));
                String rowName = TalentCsvImportService.clean(TalentCsvImportService.cell(cells, nameIdx));
                String parent = TalentCsvImportService.clean(TalentCsvImportService.cell(cells, parentIdx));
                boolean hit = (!isBlank(code) && code.equals(rowCode))
                        || (!isBlank(name) && name.equals(rowName))
                        || (byParent && !isBlank(name) && name.equals(parent));
                // 「未知职位」是一级行业的占位行，没有实际岗位信息
                if (!hit || "未知职位".equals(title))
                {
                    continue;
                }
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("industryCode", rowCode);
                item.put("industryName", rowName);
                item.put("level", TalentCsvImportService.clean(TalentCsvImportService.cell(cells, levelIdx)));
                item.put("parentName", parent);
                item.put("jobTitle", title);
                item.put("description", TalentCsvImportService.clean(TalentCsvImportService.cell(cells, descIdx)));
                jobs.add(item);
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("rows", jobs);
        result.put("matched", jobs.size());
        result.put("total", total);
        result.put("fileName", file.getName());
        result.put("updatedAt", new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date(file.lastModified())));
        return result;
    }

    /**
     * 行业目录：配置失效时回落到工程根目录下的 {@code fiveEi/03-原始爬取数据}。
     *
     * <p>原先默认值是写死的 {@code D:/000/fiveEi/08-行业对照表}，换机器或移动工程后必然不存在，
     * 导致「行业岗位」预览直接抛异常；这里与 TalentCrawlService / TalentFolderTrendService 一致，
     * 统一交给 TalentPathResolver 按工程根目录解析。</p>
     */
    private File industryDir()
    {
        File dir = TalentPathResolver.resolveDir(industryDir, DEFAULT_INDUSTRY_DIR, false);
        return dir == null ? new File(industryDir == null ? DEFAULT_INDUSTRY_DIR : industryDir) : dir;
    }

    /**
     * 定位「逐行业抓取结果」CSV。
     *
     * <p>爬虫每次运行会生成带时间戳的文件（如 {@code 逐行业抓取结果_20261028_1140.csv}），
     * 精确文件名不一定存在，所以按以下顺序找：</p>
     *
     * <ol>
     *   <li>行业目录下的精确文件名 {@code 逐行业抓取结果.csv}；</li>
     *   <li>同目录下最新的 {@code 逐行业抓取结果*.csv}；</li>
     *   <li>备选目录 {@code fiveEi/08-行业对照表} 下再按同样规则找一次。</li>
     * </ol>
     *
     * @return 命中的文件；都找不到时返回「行业目录下的精确文件名」，以便报错信息指向预期位置
     */
    private File resolveCsvFile()
    {
        File dir = industryDir();
        File exact = new File(dir, RESULT_PREFIX + ".csv");
        if (exact.isFile())
        {
            return exact;
        }

        File latest = latestCsv(dir);
        if (latest != null)
        {
            log.warn("未找到 {}，自动改用同目录最新结果：{}", exact.getName(), latest.getAbsolutePath());
            return latest;
        }

        File fallbackDir = TalentPathResolver.resolveDir(null, FALLBACK_INDUSTRY_DIR, false);
        if (fallbackDir != null)
        {
            File fallbackExact = new File(fallbackDir, RESULT_PREFIX + ".csv");
            if (fallbackExact.isFile())
            {
                log.warn("行业目录 {} 下没有结果文件，自动改用：{}", dir.getAbsolutePath(),
                        fallbackExact.getAbsolutePath());
                return fallbackExact;
            }
            File fallbackLatest = latestCsv(fallbackDir);
            if (fallbackLatest != null)
            {
                log.warn("行业目录 {} 下没有结果文件，自动改用：{}", dir.getAbsolutePath(),
                        fallbackLatest.getAbsolutePath());
                return fallbackLatest;
            }
        }
        return exact;
    }

    /** 取目录下最新的 逐行业抓取结果*.csv；没有返回 null */
    private static File latestCsv(File dir)
    {
        File[] files = dir.listFiles(new FileFilter()
        {
            @Override
            public boolean accept(File f)
            {
                return f.isFile() && f.getName().startsWith(RESULT_PREFIX)
                        && f.getName().endsWith(".csv");
            }
        });
        if (files == null || files.length == 0)
        {
            return null;
        }
        Arrays.sort(files, new Comparator<File>()
        {
            @Override
            public int compare(File a, File b)
            {
                return Long.compare(b.lastModified(), a.lastModified());
            }
        });
        return files[0];
    }

    private static boolean isBlank(String value)
    {
        return value == null || value.isEmpty();
    }
}
