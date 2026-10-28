package com.ruoyi.project.competency;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.ruoyi.project.domain.JobIndustryChain;
import com.ruoyi.project.mapper.JobProfileMapper;
import com.ruoyi.project.service.IJobCompetencyWeightService;
import com.ruoyi.project.service.IJobIndustryChainService;

/**
 * 岗位画像服务：用现有零散表（标准权重、产业链、招聘技能）拼出官方上下文。
 *
 * <p>不依赖岗位主表；岗位编码来自国家职业标准权重表，产业链来自 job_industry_chain，
 * 招聘技能来自 talent_job_posting / talent_job_skill。</p>
 *
 * @author ruoyi
 */
@Service
public class JobProfileService
{
    @Autowired
    private JobProfileMapper jobProfileMapper;

    @Autowired
    private IJobCompetencyWeightService jobCompetencyWeightService;

    @Autowired
    private IJobIndustryChainService jobIndustryChainService;

    /**
     * 按用户输入解析岗位，返回编码、标准名称、权重、产业链、招聘技能与提示词上下文。
     */
    public Map<String, Object> resolve(String keyword)
    {
        Map<String, Object> result = new LinkedHashMap<>();
        if (keyword == null || keyword.trim().isEmpty())
        {
            return result;
        }
        String query = keyword.trim();
        List<Map<String, Object>> candidates = jobProfileMapper.selectJobCandidates(query, 5);
        Map<String, Object> best = candidates.isEmpty() ? null : candidates.get(0);
        String code = best == null ? "" : text(best.get("job_code"));
        String name = best == null ? "" : text(best.get("job_name"));

        List<Map<String, Object>> weights = new ArrayList<>();
        if (!code.isEmpty())
        {
            weights = jobCompetencyWeightService.selectByJobCode(code);
        }
        if (weights.isEmpty())
        {
            weights = jobCompetencyWeightService.selectByJobName(query);
        }
        if (code.isEmpty() && !weights.isEmpty())
        {
            code = text(weights.get(0).get("job_code"));
            name = text(weights.get(0).get("job_name"));
        }

        JobIndustryChain chain = null;
        if (!code.isEmpty())
        {
            chain = jobIndustryChainService.selectChainByJobCode(code);
        }
        if (chain == null)
        {
            chain = jobIndustryChainService.selectChainByJobName(name.isEmpty() ? query : name);
        }

        List<String> skills = jobProfileMapper.selectPostingSkills(query, 20);

        result.put("code", code);
        result.put("name", name);
        result.put("weights", weights);
        result.put("chain", chain);
        result.put("skills", skills);
        result.put("context", buildContext(code, name, weights, chain, skills));
        result.put("chainContext", buildChainContext(code, name, chain));
        return result;
    }

    /**
     * 岗位联想候选：来自国家职业标准权重表的标准职业名称。
     */
    public List<Map<String, Object>> listCandidates(String keyword, int limit)
    {
        List<Map<String, Object>> result = new ArrayList<>();
        if (keyword == null || keyword.trim().isEmpty())
        {
            return result;
        }
        for (Map<String, Object> row : jobProfileMapper.selectJobCandidates(keyword.trim(), limit))
        {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", text(row.get("job_name")));
            item.put("code", text(row.get("job_code")));
            item.put("source", "standard");
            result.add(item);
        }
        return result;
    }

    private String buildContext(String code, String name, List<Map<String, Object>> weights,
            JobIndustryChain chain, List<String> skills)
    {
        StringBuilder builder = new StringBuilder();
        if (!code.isEmpty() || !name.isEmpty())
        {
            builder.append("【官方职业标准】\n");
            if (!code.isEmpty())
            {
                builder.append("职业编码：").append(code).append("\n");
            }
            if (!name.isEmpty())
            {
                builder.append("职业名称：").append(name).append("\n");
            }
            if (!weights.isEmpty())
            {
                builder.append("技能要求权重：\n");
                Map<String, List<String>> byLevel = new LinkedHashMap<>();
                for (Map<String, Object> row : weights)
                {
                    String level = text(row.get("level"));
                    String domain = text(row.get("domain_name"));
                    String weight = text(row.get("weight"));
                    if (domain.isEmpty())
                    {
                        continue;
                    }
                    List<String> items = byLevel.get(level);
                    if (items == null)
                    {
                        items = new ArrayList<>();
                        byLevel.put(level, items);
                    }
                    items.add(domain + (weight.isEmpty() ? "" : " " + weight + "%"));
                }
                for (Map.Entry<String, List<String>> entry : byLevel.entrySet())
                {
                    builder.append("- ").append(entry.getKey()).append("：")
                            .append(String.join("；", entry.getValue())).append("\n");
                }
            }
            else
            {
                builder.append("技能要求权重：官方未收录，请模型按任务重要度、频次、难度补充。\n");
            }
        }
        if (chain != null)
        {
            builder.append("【官方产业链与岗位群】\n");
            builder.append(text(chain.getChainName()));
            if (!text(chain.getStageName()).isEmpty())
            {
                builder.append(" → ").append(text(chain.getStageName()));
            }
            if (!text(chain.getClusterName()).isEmpty())
            {
                builder.append(" → ").append(text(chain.getClusterName()));
            }
            builder.append("\n");
        }
        else
        {
            builder.append("【产业链与岗位群】官方未收录，请模型补充产业链→环节→岗位群，并保证与岗位匹配。\n");
        }
        if (!skills.isEmpty())
        {
            builder.append("【招聘岗位技能关键词】\n");
            builder.append(String.join("、", skills)).append("\n");
        }
        else
        {
            builder.append("【招聘岗位技能关键词】暂无招聘数据，请模型补充。\n");
        }
        return builder.toString().trim();
    }

    private String buildChainContext(String code, String name, JobIndustryChain chain)
    {
        StringBuilder builder = new StringBuilder("【产业链与岗位群】\n");
        if (!code.isEmpty())
        {
            builder.append("职业编码：").append(code).append("\n");
        }
        if (!name.isEmpty())
        {
            builder.append("职业名称：").append(name).append("\n");
        }
        if (chain != null && !text(chain.getChainName()).isEmpty())
        {
            builder.append("官方分类：").append(text(chain.getChainName()));
            if (!text(chain.getStageName()).isEmpty())
            {
                builder.append(" → ").append(text(chain.getStageName()));
            }
            if (!text(chain.getClusterName()).isEmpty())
            {
                builder.append(" → ").append(text(chain.getClusterName()));
            }
            builder.append("\n请优先使用上述官方分类。");
        }
        else
        {
            builder.append("官方未收录该岗位，请根据岗位职责补充产业链→产业链环节→职业岗位群，")
                    .append("必须给出具体名称，禁止输出“待补充”。");
        }
        return builder.toString().trim();
    }

    private String text(Object value)
    {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
