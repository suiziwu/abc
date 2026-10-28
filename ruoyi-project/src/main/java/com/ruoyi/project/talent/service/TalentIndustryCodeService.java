package com.ruoyi.project.talent.service;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
    @Value("${talent.industry-dir:D:/000/fiveEi/08-行业对照表}")
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
        File file = new File(industryDir, "逐行业抓取结果.csv");
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

    private static boolean isBlank(String value)
    {
        return value == null || value.isEmpty();
    }
}
