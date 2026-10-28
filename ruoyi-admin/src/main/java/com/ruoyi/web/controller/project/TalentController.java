package com.ruoyi.web.controller.project;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.project.talent.service.TalentAnalysisService;
import com.ruoyi.project.talent.service.TalentCrawlService;
import com.ruoyi.project.talent.service.TalentCsvImportService;
import com.ruoyi.project.talent.service.TalentFolderTrendService;
import com.ruoyi.project.talent.service.TalentIndustryCodeService;

/**
 * 人才培养方案智能优化：岗位方向分析、技能需求趋势、专业匹配预警与优化建议。
 *
 * <p>全部数据来自本模块自有表，运行期不读取外部文件夹。</p>
 *
 * @author ruoyi
 */
@RestController
@RequestMapping("/project/talent")
public class TalentController extends BaseController
{
    @Autowired
    private TalentAnalysisService talentAnalysisService;

    @Autowired
    private TalentCsvImportService talentCsvImportService;

    @Autowired
    private TalentFolderTrendService talentFolderTrendService;

    @Autowired
    private TalentIndustryCodeService talentIndustryCodeService;

    @Autowired
    private TalentCrawlService talentCrawlService;

    /** 启动后台爬取（已有任务在跑时返回当前进度） */
    @PostMapping("/crawl/start")
    public AjaxResult startCrawl()
    {
        try
        {
            return success(talentCrawlService.start());
        }
        catch (Exception e)
        {
            logger.error("启动爬取失败", e);
            return error("启动爬取失败: " + e.getMessage());
        }
    }

    /** 查询爬取进度 */
    @GetMapping("/crawl/status")
    public AjaxResult crawlStatus()
    {
        try
        {
            return success(talentCrawlService.status());
        }
        catch (Exception e)
        {
            logger.error("查询爬取进度失败", e);
            return error("查询爬取进度失败: " + e.getMessage());
        }
    }

    /**
     * 导入 CSV 岗位数据（按文件名覆盖同来源数据）。
     *
     * @param file      CSV 文件
     * @param batchDate 采集批次，留空表示纳入当前快照
     */
    @Log(title = "人才培养数据导入", businessType = BusinessType.IMPORT)
    @PostMapping("/import")
    public AjaxResult importCsv(@RequestParam("file") MultipartFile file,
            @RequestParam(value = "batchDate", required = false) String batchDate)
    {
        try
        {
            return success(talentCsvImportService.importCsv(file, batchDate));
        }
        catch (Exception e)
        {
            logger.error("导入 CSV 岗位数据失败", e);
            return error("导入失败: " + e.getMessage());
        }
    }

    /**
     * 看板总览指标。
     *
     * @param jobFamily 岗位方向，为空表示全部
     * @param batchDate 采集时间维度，为空表示全部时间
     */
    @GetMapping("/overview")
    public AjaxResult overview(@RequestParam(value = "jobFamily", required = false) String jobFamily,
            @RequestParam(value = "batchDate", required = false) String batchDate)
    {
        try
        {
            return success(talentAnalysisService.overview(jobFamily, batchDate));
        }
        catch (Exception e)
        {
            logger.error("加载人才培养总览失败", e);
            return error("加载总览数据失败: " + e.getMessage());
        }
    }

    /**
     * 采集时间维度清单。
     */
    @GetMapping("/batches")
    public AjaxResult batches()
    {
        try
        {
            return success(talentAnalysisService.batches());
        }
        catch (Exception e)
        {
            logger.error("加载采集时间维度失败", e);
            return error("加载采集时间维度失败: " + e.getMessage());
        }
    }

    /**
     * 技能需求排行。
     */
    @GetMapping("/skill-rank")
    public AjaxResult skillRank(@RequestParam(value = "jobFamily", required = false) String jobFamily,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "batchDate", required = false) String batchDate,
            @RequestParam(value = "limit", defaultValue = "20") Integer limit)
    {
        try
        {
            List<Map<String, Object>> data = talentAnalysisService.skillRank(jobFamily, category, limit, batchDate);
            return success(data);
        }
        catch (Exception e)
        {
            logger.error("加载技能需求排行失败", e);
            return error("加载技能需求排行失败: " + e.getMessage());
        }
    }

    /**
     * 行业编码对照表（数据来自 talent_industry_code 表）。
     */
    @GetMapping("/industry-codes")
    public AjaxResult industryCodes()
    {
        try
        {
            return success(talentIndustryCodeService.list());
        }
        catch (Exception e)
        {
            logger.error("加载行业编码对照表失败", e);
            return error("加载行业编码对照表失败: " + e.getMessage());
        }
    }

    /**
     * 某行业已爬取的岗位（预览 08-行业对照表/逐行业抓取结果.csv）。
     */
    @GetMapping("/industry-jobs")
    public AjaxResult industryJobs(@RequestParam(value = "code", required = false) String code,
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "level", required = false) String level)
    {
        try
        {
            return success(talentIndustryCodeService.crawlJobs(code, name, level));
        }
        catch (Exception e)
        {
            logger.error("预览行业爬取岗位失败", e);
            return error("预览行业爬取岗位失败: " + e.getMessage());
        }
    }

    /**
     * 技能热度曲线。
     */
    @GetMapping("/skill-trend")
    public AjaxResult skillTrend(@RequestParam(value = "jobFamily", required = false) String jobFamily,
            @RequestParam(value = "batchDate", required = false) String batchDate,
            @RequestParam(value = "topN", defaultValue = "8") Integer topN,
            @RequestParam(value = "minJobs", defaultValue = "20") Integer minJobs)
    {
        try
        {
            // 曲线按「04-清洗后数据」下各 CSV 的修改时间分段；目录不可用时回退数据库批次
            Map<String, Object> trend = talentFolderTrendService.skillTrend(topN, jobFamily, batchDate, minJobs);
            Object batches = trend.get("batches");
            if (batches instanceof List && !((List<?>) batches).isEmpty())
            {
                return success(trend);
            }
            return success(talentAnalysisService.skillTrend(topN, jobFamily, batchDate, minJobs));
        }
        catch (Exception e)
        {
            logger.error("加载技能热度曲线失败", e);
            return error("加载技能热度曲线失败: " + e.getMessage());
        }
    }

    /**
     * 技能类别分布。
     */
    @GetMapping("/category-stat")
    public AjaxResult categoryStat(@RequestParam(value = "jobFamily", required = false) String jobFamily,
            @RequestParam(value = "batchDate", required = false) String batchDate)
    {
        try
        {
            return success(talentAnalysisService.categoryStat(jobFamily, batchDate));
        }
        catch (Exception e)
        {
            logger.error("加载技能类别分布失败", e);
            return error("加载技能类别分布失败: " + e.getMessage());
        }
    }

    /**
     * 专业匹配度（专业课程对自身岗位方向需求技能的覆盖情况）。
     */
    @GetMapping("/major-match")
    public AjaxResult majorMatch(@RequestParam(value = "jobFamily", required = false) String jobFamily,
            @RequestParam(value = "batchDate", required = false) String batchDate)
    {
        try
        {
            return success(talentAnalysisService.majorMatch(jobFamily, batchDate));
        }
        catch (Exception e)
        {
            logger.error("计算专业匹配度失败", e);
            return error("计算专业匹配度失败: " + e.getMessage());
        }
    }

    /**
     * 专业预警提示。
     */
    @GetMapping("/warnings")
    public AjaxResult warnings(@RequestParam(value = "jobFamily", required = false) String jobFamily,
            @RequestParam(value = "batchDate", required = false) String batchDate)
    {
        try
        {
            return success(talentAnalysisService.warnings(jobFamily, batchDate));
        }
        catch (Exception e)
        {
            logger.error("加载专业预警失败", e);
            return error("加载专业预警失败: " + e.getMessage());
        }
    }

    /**
     * 智能优化建议（附数据依据与置信度）。
     */
    @GetMapping("/suggestions")
    public AjaxResult suggestions(@RequestParam(value = "jobFamily", required = false) String jobFamily,
            @RequestParam(value = "batchDate", required = false) String batchDate)
    {
        try
        {
            return success(talentAnalysisService.suggestions(jobFamily, batchDate));
        }
        catch (Exception e)
        {
            logger.error("生成优化建议失败", e);
            return error("生成优化建议失败: " + e.getMessage());
        }
    }

    /**
     * 课程体系（专业基础必修课 + 岗位方向专业课程 + 专业清单）。
     */
    @GetMapping("/curriculum")
    public AjaxResult curriculum(@RequestParam(value = "jobFamily", required = false) String jobFamily,
            @RequestParam(value = "batchDate", required = false) String batchDate)
    {
        try
        {
            return success(talentAnalysisService.curriculum(jobFamily, batchDate));
        }
        catch (Exception e)
        {
            logger.error("加载课程体系失败", e);
            return error("加载课程体系失败: " + e.getMessage());
        }
    }

    /**
     * 数据来源与指标口径。
     */
    @GetMapping("/data-source")
    public AjaxResult dataSource(@RequestParam(value = "batchDate", required = false) String batchDate)
    {
        try
        {
            return success(talentAnalysisService.dataSource(batchDate));
        }
        catch (Exception e)
        {
            logger.error("加载数据来源说明失败", e);
            return error("加载数据来源说明失败: " + e.getMessage());
        }
    }
}
