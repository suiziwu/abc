package com.ruoyi.web.controller.project;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.project.competency.CompetencyGraphKbService;
import com.ruoyi.project.competency.CompetencyGraphNormalizer;
import com.ruoyi.project.competency.JobCatalogService;
import com.ruoyi.project.competency.JobProfileService;
import com.ruoyi.project.domain.JobCompetencyGraph;
import com.ruoyi.project.domain.JobIndustryChain;
import com.ruoyi.project.langchain.LangChainWorkflowException;
import com.ruoyi.project.langchain.LangChainWorkflowResult;
import com.ruoyi.project.langchain.LangChainWorkflowService;
import com.ruoyi.project.service.IJobCompetencyGraphService;
import com.ruoyi.project.service.IJobCompetencyWeightService;
import com.ruoyi.project.service.IJobIndustryChainService;

/**
 * 岗位能力图谱接口：KB 模式读预生成图谱，LLM 模式走工作流并归一化。
 *
 * @author ruoyi
 */
@RestController
@RequestMapping("/project/competency")
public class JobCompetencyGraphController extends BaseController
{
    /** 岗位能力图谱工作流标识 */
    private static final String WORKFLOW_ID = "competency-graph";

    private static final String MODE_KB = "kb";

    private static final String MODE_LLM = "llm";

    @Autowired
    private IJobCompetencyGraphService competencyGraphService;

    @Autowired
    private IJobCompetencyWeightService jobCompetencyWeightService;

    @Autowired
    private IJobIndustryChainService jobIndustryChainService;

    @Autowired
    private LangChainWorkflowService langChainWorkflowService;

    @Autowired
    private CompetencyGraphKbService competencyGraphKbService;

    @Autowired
    private CompetencyGraphNormalizer competencyGraphNormalizer;

    @Autowired
    private JobCatalogService jobCatalogService;

    @Autowired
    private JobProfileService jobProfileService;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 历史图谱列表（不含图谱全文）。
     */
    @GetMapping("/list")
    public TableDataInfo list(JobCompetencyGraph query)
    {
        startPage();
        List<JobCompetencyGraph> list = competencyGraphService.selectJobCompetencyGraphList(query);
        return getDataTable(list);
    }

    /**
     * 图谱详情（含全文）。
     */
    @GetMapping("/{id}")
    public AjaxResult detail(@PathVariable Long id)
    {
        JobCompetencyGraph record = competencyGraphService.selectJobCompetencyGraphById(id);
        if (record == null)
        {
            return error("图谱不存在");
        }
        Map<String, Object> graph = parseGraph(record.getGraphJson());
        Map<String, Object> data = buildDetail(record, graph == null ? new LinkedHashMap<>() : graph, false);
        data.put("graphJson", record.getGraphJson());
        return success(data);
    }

    /**
     * KB 模式已收录岗位。
     */
    @GetMapping("/jobs")
    public AjaxResult jobs()
    {
        List<Map<String, Object>> items = new ArrayList<>();
        for (Map<String, Object> job : competencyGraphKbService.listJobs())
        {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", job.get("id"));
            item.put("name", job.get("name"));
            item.put("code", job.get("code"));
            item.put("level", job.get("level"));
            item.put("aliases", job.get("aliases"));
            items.add(item);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("kbVersion", competencyGraphKbService.getKbVersion());
        data.put("jobs", items);
        return success(data);
    }

    /**
     * 岗位输入联想：优先返回已入库岗位，再补充大典/标准职业目录中的模糊匹配项。
     */
    @GetMapping("/suggest")
    public AjaxResult suggest(String keyword)
    {
        List<Map<String, Object>> result = new ArrayList<>();
        if (keyword == null || keyword.trim().isEmpty())
        {
            return success(result);
        }
        String query = keyword.trim();
        Set<String> seen = new HashSet<>();
        for (String name : competencyGraphService.selectDistinctJobNames(query))
        {
            if (name != null && seen.add(name))
            {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("name", name);
                item.put("code", "");
                item.put("source", "existing");
                result.add(item);
            }
        }
        for (Map<String, Object> candidate : jobProfileService.listCandidates(query, 6))
        {
            String name = text(candidate.get("name"), "");
            String code = text(candidate.get("code"), "");
            if (name.isEmpty())
            {
                continue;
            }
            if (seen.add(name))
            {
                result.add(candidate);
                continue;
            }
            for (Map<String, Object> item : result)
            {
                if (name.equals(item.get("name")) && text(item.get("code"), "").isEmpty() && !code.isEmpty())
                {
                    item.put("code", code);
                    item.put("source", "standard");
                    break;
                }
            }
        }
        for (Map<String, Object> job : jobCatalogService.suggest(query, 12))
        {
            String name = String.valueOf(job.get("name"));
            if (seen.add(name))
            {
                result.add(job);
            }
        }
        if (result.size() > 12)
        {
            result = new ArrayList<>(result.subList(0, 12));
        }
        return success(result);
    }

    /**
     * 生成岗位能力图谱。
     *
     * <p>先按岗位名称查库：已生成过则直接返回库中图谱（reused=true），不再调用模型；
     * 未生成过才调用工作流，并由 Normalizer 统一结构后入库。</p>
     *
     * @param body 请求体：{"job":"计算机程序设计员"}
     */
    @PostMapping("/generate")
    public AjaxResult generate(@RequestBody Map<String, String> body)
    {
        String job = body == null ? null : body.get("job");
        if (job == null || job.trim().isEmpty())
        {
            return error("参数 job 不能为空");
        }
        String jobName = job.trim();

        Map<String, Object> profile = jobProfileService.resolve(jobName);
        String officialCode = text(profile.get("code"), "");
        String canonicalName = text(profile.get("name"), "");
        String targetName = canonicalName.isEmpty() ? jobName : canonicalName;

        JobCompetencyGraph existing = competencyGraphService.selectLatestJobCompetencyGraphByName(jobName);
        if (existing == null && !targetName.equals(jobName))
        {
            existing = competencyGraphService.selectLatestJobCompetencyGraphByName(targetName);
        }
        if (existing != null)
        {
            Map<String, Object> cached = parseGraph(existing.getGraphJson());
            if (cached != null)
            {
                return success(buildDetail(existing, cached, true));
            }
        }

        Map<String, Object> extraState = new LinkedHashMap<>();
        String officialContext = text(profile.get("context"), "");
        if (!officialContext.isEmpty())
        {
            extraState.put("official_context", officialContext);
        }
        String chainContext = text(profile.get("chainContext"), "");
        if (!chainContext.isEmpty())
        {
            extraState.put("chain_context", chainContext);
        }

        LangChainWorkflowResult result;
        try
        {
            result = langChainWorkflowService.invoke(WORKFLOW_ID, targetName, extraState);
        }
        catch (LangChainWorkflowException e)
        {
            return error(e.getMessage());
        }
        Map<String, Object> raw;
        try
        {
            raw = objectMapper.readValue(extractJson(result.getOutput()),
                    new TypeReference<Map<String, Object>>() {});
        }
        catch (Exception e)
        {
            return error("模型返回内容不是合法 JSON：" + abbreviate(result.getOutput()));
        }
        Map<String, Object> graph;
        try
        {
            graph = competencyGraphNormalizer.normalize(raw, targetName);
        }
        catch (LangChainWorkflowException e)
        {
            return error("图谱归一化失败：" + e.getMessage());
        }

        if (!officialCode.isEmpty() || !canonicalName.isEmpty())
        {
            Map<String, Object> jobInfo = asMap(graph.get("job"));
            if (!officialCode.isEmpty())
            {
                jobInfo.put("code", officialCode);
            }
            if (!canonicalName.isEmpty())
            {
                jobInfo.put("name", canonicalName);
            }
            graph.put("job", jobInfo);
        }

        JobCompetencyGraph record;
        try
        {
            record = saveGraph(graph, MODE_LLM);
        }
        catch (Exception e)
        {
            return error("图谱序列化失败: " + e.getMessage());
        }

        Map<String, Object> data = buildDetail(record, graph, false);
        data.put("path", result.getPath());
        data.put("steps", result.getSteps());
        data.put("durationMs", result.getDurationMs());
        return success(data);
    }

    private Map<String, Object> parseGraph(String json)
    {
        if (json == null || json.trim().isEmpty())
        {
            return null;
        }
        try
        {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        }
        catch (Exception e)
        {
            return null;
        }
    }

    private Map<String, Object> buildDetail(JobCompetencyGraph record, Map<String, Object> graph, boolean reused)
    {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", record.getId());
        data.put("jobName", record.getJobName());
        data.put("jobCode", record.getJobCode());
        data.put("level", record.getLevel());
        data.put("mode", record.getMode());
        data.put("schemaVersion", record.getSchemaVersion());
        data.put("reviewStatus", record.getReviewStatus());
        data.put("confidence", record.getConfidence());
        data.put("graph", graph);
        data.put("reused", reused);
        Map<String, Object> jobInfo = asMap(graph.get("job"));
        Map<String, Object> official = buildOfficialWeights(jobInfo);
        if (official != null)
        {
            data.put("officialWeights", official);
        }
        JobIndustryChain chain = buildChainDetail(jobInfo);
        if (chain != null)
        {
            data.put("chainDetail", chain);
        }
        return data;
    }

    /**
     * 从数据库取该岗位的产业链/岗位群映射；优先按职业编码，其次按职业名称。
     */
    private JobIndustryChain buildChainDetail(Map<String, Object> jobInfo)
    {
        if (jobInfo == null || jobInfo.isEmpty())
        {
            return null;
        }
        String code = text(jobInfo.get("code"), "");
        String name = text(jobInfo.get("name"), "");
        JobIndustryChain chain = null;
        if (!code.isEmpty())
        {
            chain = jobIndustryChainService.selectChainByJobCode(code);
        }
        if (chain == null && !name.isEmpty())
        {
            chain = jobIndustryChainService.selectChainByJobName(name);
        }
        return chain;
    }

    /**
     * 从数据库取该职业的官方技能要求权重；没有则返回 null，由前端回退到图谱自带权重。
     */
    private Map<String, Object> buildOfficialWeights(Map<String, Object> jobInfo)
    {
        if (jobInfo == null || jobInfo.isEmpty())
        {
            return null;
        }
        String code = text(jobInfo.get("code"), "");
        String name = text(jobInfo.get("name"), "");
        List<Map<String, Object>> rows = new ArrayList<>();
        if (!code.isEmpty())
        {
            rows = jobCompetencyWeightService.selectByJobCode(code);
        }
        if (rows.isEmpty() && !name.isEmpty())
        {
            rows = jobCompetencyWeightService.selectByJobName(name);
        }
        if (rows.isEmpty())
        {
            return null;
        }

        List<String> levels = new ArrayList<>();
        Map<String, Map<String, Object>> domains = new LinkedHashMap<>();
        String sourceFile = "";
        String sourcePage = "";
        String issueNumber = "";
        for (Map<String, Object> row : rows)
        {
            String level = text(row.get("level"), "");
            if (!level.isEmpty() && !levels.contains(level))
            {
                levels.add(level);
            }
            String domainName = text(row.get("domain_name"), "");
            Map<String, Object> entry = domains.get(domainName);
            if (entry == null)
            {
                entry = new LinkedHashMap<>();
                entry.put("name", domainName);
                entry.put("weights", new LinkedHashMap<String, Object>());
                domains.put(domainName, entry);
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> weights = (Map<String, Object>) entry.get("weights");
            weights.put(level, row.get("weight"));
            if (sourceFile.isEmpty())
            {
                sourceFile = text(row.get("source_file"), "");
            }
            if (sourcePage.isEmpty())
            {
                sourcePage = text(row.get("source_page"), "");
            }
            if (issueNumber.isEmpty())
            {
                issueNumber = text(row.get("issue_number"), "");
            }
        }

        Map<String, Object> official = new LinkedHashMap<>();
        official.put("jobCode", code.isEmpty() ? text(rows.get(0).get("job_code"), "") : code);
        official.put("jobName", name.isEmpty() ? text(rows.get(0).get("job_name"), "") : name);
        official.put("levels", levels);
        official.put("domains", new ArrayList<>(domains.values()));
        official.put("source", "national_standard");
        official.put("sourceFile", sourceFile);
        official.put("sourcePage", sourcePage);
        official.put("issueNumber", issueNumber);
        return official;
    }

    /**
     * 删除图谱。
     */
    @DeleteMapping("/{ids}")
    public AjaxResult remove(@PathVariable Long[] ids)
    {
        return toAjax(competencyGraphService.deleteJobCompetencyGraphByIds(ids));
    }

    /**
     * 更新图谱审核状态：approved / pending。
     */
    @PutMapping("/review/{id}")
    public AjaxResult review(@PathVariable Long id, @RequestParam String status)
    {
        if (!"approved".equals(status) && !"pending".equals(status))
        {
            return error("状态只支持 approved / pending");
        }
        return toAjax(competencyGraphService.updateReviewStatus(id, status));
    }

    private JobCompetencyGraph saveGraph(Map<String, Object> graph, String mode) throws Exception
    {
        Map<String, Object> jobInfo = asMap(graph.get("job"));
        Map<String, Object> review = asMap(graph.get("review"));
        Map<String, Object> meta = asMap(graph.get("meta"));

        JobCompetencyGraph record = new JobCompetencyGraph();
        record.setJobName(text(jobInfo.get("name"), ""));
        record.setJobCode(text(jobInfo.get("code"), ""));
        record.setLevel(text(jobInfo.get("level"), ""));
        record.setSourceMode(mode);
        record.setSchemaVersion(text(graph.get("schema_version"), "3.0.0"));
        record.setMode(text(meta.get("mode"), mode));
        record.setReviewStatus(text(review.get("status"), MODE_KB.equals(mode) ? "approved" : "pending"));
        record.setEngineVersion(text(meta.get("engine_version"), ""));
        Object confidence = jobInfo.get("confidence");
        if (!(confidence instanceof Number))
        {
            confidence = graph.get("confidence");
        }
        if (confidence instanceof Number)
        {
            record.setConfidence(((Number) confidence).doubleValue());
        }
        record.setCoverageJson(objectMapper.writeValueAsString(graph.get("coverage")));
        record.setGraphJson(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(graph));
        record.setCreateBy(getUsername());
        record.setCreateTime(new Date());
        record.setDelFlag("0");
        competencyGraphService.insertJobCompetencyGraph(record);
        return record;
    }

    /**
     * 去掉 Markdown 代码块并截取最外层 JSON 对象。
     */
    private String extractJson(String raw)
    {
        String text = raw == null ? "" : raw.trim();
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start)
        {
            text = text.substring(start, end + 1);
        }
        return text;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value)
    {
        return value instanceof Map ? (Map<String, Object>) value : new LinkedHashMap<>();
    }

    private String text(Object value, String fallback)
    {
        return value == null || String.valueOf(value).trim().isEmpty() ? fallback : String.valueOf(value);
    }

    private String abbreviate(String value)
    {
        if (value == null)
        {
            return "";
        }
        return value.length() > 200 ? value.substring(0, 200) + "..." : value;
    }
}
