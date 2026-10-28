package com.ruoyi.project.competency;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.ruoyi.project.langchain.LangChainWorkflowException;

/**
 * 把大模型输出统一成 competency-graph.schema.json v3。
 *
 * <p>LLM 只负责语义内容（任务、能力项、要素），视图层（思维导图/雷达/关系图/覆盖率）
 * 全部由本类确定性重建，避免在线与离线两套结构。</p>
 *
 * @author ruoyi
 */
@Component
public class CompetencyGraphNormalizer
{
    public Map<String, Object> normalize(Map<String, Object> raw, String requestedJob)
    {
        if ("3.0.0".equals(text(raw.get("schema_version"), "")))
        {
            return raw;
        }

        Map<String, Object> jobRaw = asMap(raw.get("job"));
        String jobName = text(jobRaw.get("name"), requestedJob);
        String jobCode = text(jobRaw.get("code"), "");
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("schema_version", "3.0.0");
        out.put("meta", buildMeta());
        out.put("job", buildJob(jobRaw, jobName, jobCode, raw));
        List<Map<String, Object>> industryPath = buildIndustryPath(raw, jobName, jobCode);
        out.put("industry_path", industryPath);
        List<Map<String, Object>> classificationPath = buildClassificationPath(raw);
        out.put("classification_path", classificationPath);

        List<Map<String, Object>> tasks = normalizeTasks(raw);
        out.put("tasks", tasks);

        Map<String, List<Map<String, Object>>> skillsByCompetency = groupElements(raw, "skills");
        Map<String, List<Map<String, Object>>> knowledgeByCompetency = groupElements(raw, "knowledge");

        List<Map<String, Object>> rawCompetencies = asList(raw.get("competencies"));
        if (rawCompetencies.isEmpty())
        {
            throw new LangChainWorkflowException("模型输出缺少 competencies，无法装配图谱");
        }

        Map<String, Double> domainWeights = new LinkedHashMap<>();
        for (Map<String, Object> competency : rawCompetencies)
        {
            String domain = text(competency.get("domain"), text(competency.get("domain_name"), "通用能力"));
            double weight = number(competency.get("weight"), 0);
            domainWeights.put(domain, domainWeights.getOrDefault(domain, 0D) + weight);
        }
        if (domainWeights.size() == 1 && domainWeights.values().iterator().next() == 0)
        {
            double equal = Math.round(100.0 / domainWeights.size() * 10) / 10.0;
            domainWeights.put(domainWeights.keySet().iterator().next(), 100D);
        }

        List<Map<String, Object>> domains = new ArrayList<>();
        Map<String, String> domainIdByName = new LinkedHashMap<>();
        int domainIndex = 0;
        for (Map.Entry<String, Double> entry : domainWeights.entrySet())
        {
            domainIndex++;
            String domainId = "D" + domainIndex;
            domainIdByName.put(entry.getKey(), domainId);
            Map<String, Object> domain = new LinkedHashMap<>();
            domain.put("id", domainId);
            domain.put("name", entry.getKey());
            domain.put("weight", entry.getValue());
            domains.add(domain);
        }
        out.put("domains", domains);

        List<Map<String, Object>> competencies = new ArrayList<>();
        Map<String, Map<String, Object>> skillMap = new LinkedHashMap<>();
        Map<String, Map<String, Object>> knowledgeMap = new LinkedHashMap<>();

        for (Map<String, Object> rawCompetency : rawCompetencies)
        {
            String competencyId = text(rawCompetency.get("id"), "C" + (competencies.size() + 1));
            String competencyName = text(rawCompetency.get("name"), competencyId);
            String domainName = text(rawCompetency.get("domain"), text(rawCompetency.get("domain_name"), "通用能力"));
            List<Map<String, Object>> poolSkills = skillsByCompetency.get(competencyId);
            List<Map<String, Object>> poolKnowledge = knowledgeByCompetency.get(competencyId);
            List<Map<String, Object>> units = normalizeUnits(rawCompetency, competencyId, competencyName,
                    poolSkills, poolKnowledge);
            if (units.isEmpty())
            {
                throw new LangChainWorkflowException("能力项「" + competencyName + "」缺少技能或知识点，无法通过契约校验");
            }

            for (Map<String, Object> unit : units)
            {
                for (Map<String, Object> skill : asList(unit.get("skills")))
                {
                    registerElement(skillMap, skill, competencyId, "SK", "岗位技能要求");
                }
                for (Map<String, Object> point : asList(unit.get("knowledge")))
                {
                    registerElement(knowledgeMap, point, competencyId, "KP", "");
                }
            }

            Map<String, Object> competency = new LinkedHashMap<>();
            competency.put("id", competencyId);
            competency.put("domain_id", domainIdByName.getOrDefault(domainName, "D1"));
            competency.put("name", competencyName);
            competency.put("weight", number(rawCompetency.get("weight"), 0));
            competency.put("task_ids", toStringList(rawCompetency.get("task_ids")));
            competency.put("source", text(rawCompetency.get("source"), "LLM"));
            competency.put("units", units);
            competencies.add(competency);
        }
        out.put("competencies", competencies);

        // 任务未带 competency_ids 时，用能力项的 task_ids 反推，保证每条任务至少挂一个能力项
        Map<String, String> taskToCompetency = new LinkedHashMap<>();
        for (Map<String, Object> competency : competencies)
        {
            for (String taskId : toStringList(competency.get("task_ids")))
            {
                taskToCompetency.putIfAbsent(taskId, text(competency.get("id"), ""));
            }
        }
        for (Map<String, Object> task : tasks)
        {
            List<String> ids = toStringList(task.get("competency_ids"));
            if (ids.isEmpty())
            {
                String competencyId = taskToCompetency.get(text(task.get("id"), ""));
                if (competencyId == null && !competencies.isEmpty())
                {
                    competencyId = text(competencies.get(0).get("id"), "");
                }
                if (competencyId != null && !competencyId.isEmpty())
                {
                    ids.add(competencyId);
                }
            }
            task.put("competency_ids", ids);
        }

        Map<String, Object> elements = new LinkedHashMap<>();
        elements.put("skills", new ArrayList<>(skillMap.values()));
        elements.put("knowledge", new ArrayList<>(knowledgeMap.values()));
        out.put("elements", elements);

        Map<String, Object> views = new LinkedHashMap<>();
        views.put("mindmap", buildMindmap(jobName, industryPath, competencies));
        views.put("radar", buildRadar(domains));
        views.put("graph", buildGraph(jobName, industryPath, competencies,
                skillMap, knowledgeMap));
        Map<String, Object> sankey = new LinkedHashMap<>();
        sankey.put("nodes", new ArrayList<>());
        sankey.put("links", new ArrayList<>());
        views.put("sankey", sankey);
        out.put("views", views);

        out.put("coverage", buildCoverage(tasks, competencies, skillMap, knowledgeMap, domains));
        out.put("sources", buildSources(raw));
        Map<String, Object> review = new LinkedHashMap<>();
        review.put("status", "pending");
        review.put("reviewer", null);
        review.put("reviewed_at", null);
        review.put("notes", "LLM 生成，需人工审核；视图由 Java Normalizer 确定性重建。");
        out.put("review", review);
        out.put("data_note", "本图由大模型生成并归一化，能力条目未逐条核对标准原文；审核通过前不得作为标准结论。");
        return out;
    }

    private Map<String, Object> buildMeta()
    {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("generated_at", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        meta.put("mode", "llm");
        meta.put("engine", "java-normalizer");
        meta.put("engine_version", "3.0.0");
        meta.put("kb_version", null);
        meta.put("graph_id", "LLM-" + UUID.randomUUID().toString());
        return meta;
    }

    private Map<String, Object> buildJob(Map<String, Object> jobRaw, String jobName, String jobCode,
            Map<String, Object> raw)
    {
        Map<String, Object> job = new LinkedHashMap<>();
        job.put("id", text(jobRaw.get("id"), "LLM-" + jobCode));
        job.put("name", jobName);
        job.put("code", jobCode);
        job.put("level", text(jobRaw.get("level"), ""));
        job.put("digital_occupation", Boolean.TRUE.equals(jobRaw.get("digital_occupation")));
        job.put("aliases", toStringList(jobRaw.get("aliases")));
        job.put("summary", text(jobRaw.get("summary"), jobName));
        job.put("confidence", number(raw.get("confidence"), jobRaw.get("confidence")));
        job.put("matched_by", "llm");
        return job;
    }

    private List<Map<String, Object>> buildIndustryPath(Map<String, Object> raw, String jobName, String jobCode)
    {
        Map<String, Object> chain = asMap(raw.get("industry_chain"));
        String chainName = cleanPlaceholder(text(chain.get("chain"), ""));
        String segmentName = cleanPlaceholder(text(chain.get("segment"), ""));
        String clusterName = cleanPlaceholder(text(chain.get("cluster"), ""));
        // 官方数据缺失时，允许用模型输出的 logic_chain 补全，避免留下“待补充”
        List<Map<String, Object>> logicChain = asList(raw.get("logic_chain"));
        for (Map<String, Object> item : logicChain)
        {
            String level = text(item.get("level"), "");
            String name = text(item.get("name"), "");
            if (name.isEmpty())
            {
                continue;
            }
            boolean chainLevel = level.contains("产业链") && !level.contains("环节");
            boolean segmentLevel = level.contains("环节");
            boolean clusterLevel = level.contains("岗位群");
            if (chainName.isEmpty() && chainLevel)
            {
                chainName = name;
            }
            else if (segmentName.isEmpty() && segmentLevel)
            {
                segmentName = name;
            }
            else if (clusterName.isEmpty() && clusterLevel)
            {
                clusterName = name;
            }
        }
        // 模型未按 level 命名时，按 prompt 约定的四级顺序兜底
        if (chainName.isEmpty() && logicChain.size() > 0)
        {
            chainName = text(logicChain.get(0).get("name"), "");
        }
        if (segmentName.isEmpty() && logicChain.size() > 1)
        {
            segmentName = text(logicChain.get(1).get("name"), "");
        }
        if (clusterName.isEmpty() && logicChain.size() > 2)
        {
            clusterName = text(logicChain.get(2).get("name"), "");
        }
        List<Map<String, Object>> path = new ArrayList<>();
        path.add(pathItem("产业链", "IND-LLM-1", chainName.isEmpty() ? "待补充产业链" : chainName,
                "teaching", "LLM-TEACH"));
        path.add(pathItem("产业链环节", "IND-LLM-2", segmentName.isEmpty() ? "待补充产业链环节" : segmentName,
                "teaching", "LLM-TEACH"));
        path.add(pathItem("职业岗位群", "CLU-LLM-1", clusterName.isEmpty() ? "待补充岗位群" : clusterName,
                "teaching", "LLM-TEACH"));
        path.add(pathItem("职业岗位", "JOB-LLM", jobName, "standard", jobCode));
        return path;
    }

    private String cleanPlaceholder(String value)
    {
        if (value == null)
        {
            return "";
        }
        String text = value.trim();
        return text.startsWith("待补充") ? "" : text;
    }

    private Map<String, Object> pathItem(String level, String id, String name, String mappingType, String sourceId)
    {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("level", level);
        item.put("id", id);
        item.put("name", name);
        item.put("mapping_type", mappingType);
        item.put("source_id", sourceId);
        return item;
    }

    private List<Map<String, Object>> buildClassificationPath(Map<String, Object> raw)
    {
        List<Map<String, Object>> result = new ArrayList<>();
        List<Map<String, Object>> chain = asList(raw.get("logic_chain"));
        String[] levels = {"职业大类", "职业中类", "职业小类", "职业细类"};
        for (int index = 0; index < levels.length; index++)
        {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("level", levels[index]);
            String name = index < chain.size() ? text(chain.get(index).get("name"), "") : "";
            item.put("name", name.isEmpty() ? "待补充" + levels[index] : name);
            result.add(item);
        }
        return result;
    }

    private List<Map<String, Object>> normalizeTasks(Map<String, Object> raw)
    {
        List<Map<String, Object>> tasks = new ArrayList<>();
        List<Map<String, Object>> rawTasks = asList(raw.get("tasks"));
        for (int index = 0; index < rawTasks.size(); index++)
        {
            Map<String, Object> rawTask = rawTasks.get(index);
            Map<String, Object> task = new LinkedHashMap<>();
            task.put("id", text(rawTask.get("id"), "T" + String.format("%02d", index + 1)));
            task.put("name", text(rawTask.get("name"), "未命名任务"));
            task.put("description", text(rawTask.get("description"), text(rawTask.get("name"), "")));
            task.put("competency_ids", toStringList(rawTask.get("competency_ids")));
            task.put("weight", number(rawTask.get("weight"), 0));
            task.put("importance", integerOrNull(rawTask.get("importance")));
            task.put("frequency", integerOrNull(rawTask.get("frequency")));
            task.put("difficulty", integerOrNull(rawTask.get("difficulty")));
            task.put("source", text(rawTask.get("source"), "LLM"));
            Map<String, Object> basis = new LinkedHashMap<>();
            basis.put("origin", "llm");
            basis.put("note", "缺失字段以 null 保留，不做主观填充");
            task.put("data_basis", basis);
            tasks.add(task);
        }
        return tasks;
    }

    private List<Map<String, Object>> normalizeUnits(Map<String, Object> competency, String competencyId,
            String competencyName, List<Map<String, Object>> poolSkills, List<Map<String, Object>> poolKnowledge)
    {
        List<Map<String, Object>> rawUnits = asList(competency.get("units"));
        boolean unitLevelElements = false;
        for (Map<String, Object> unit : rawUnits)
        {
            if (!asList(unit.get("skills")).isEmpty() && !asList(unit.get("knowledge")).isEmpty())
            {
                unitLevelElements = true;
                break;
            }
        }

        List<Map<String, Object>> units = new ArrayList<>();
        if (unitLevelElements)
        {
            for (Map<String, Object> rawUnit : rawUnits)
            {
                List<Map<String, Object>> skills = asList(rawUnit.get("skills"));
                List<Map<String, Object>> knowledge = asList(rawUnit.get("knowledge"));
                if (skills.isEmpty() || knowledge.isEmpty())
                {
                    continue;
                }
                units.add(buildUnit(text(rawUnit.get("id"), "U" + (units.size() + 1)),
                        text(rawUnit.get("statement"), competencyName),
                        integer(rawUnit.get("level"), 2),
                        text(rawUnit.get("source"), "LLM"), skills, knowledge, competencyId));
            }
        }

        if (units.isEmpty())
        {
            if (poolSkills.isEmpty() || poolKnowledge.isEmpty())
            {
                return units;
            }
            units.add(buildUnit("U-" + competencyId,
                    competencyName + "（模型按能力项汇总）", 2, "LLM",
                    poolSkills, poolKnowledge, competencyId));
        }
        return units;
    }

    private Map<String, Object> buildUnit(String unitId, String statement, int level, String source,
            List<Map<String, Object>> skills, List<Map<String, Object>> knowledge, String competencyId)
    {
        List<Map<String, Object>> unitSkills = new ArrayList<>();
        for (int index = 0; index < skills.size(); index++)
        {
            Map<String, Object> skill = new LinkedHashMap<>();
            skill.put("id", "S-" + competencyId + "-" + (index + 1));
            skill.put("name", text(skills.get(index).get("name"), ""));
            if ("".equals(skill.get("name")))
            {
                continue;
            }
            unitSkills.add(skill);
        }

        List<Map<String, Object>> unitKnowledge = new ArrayList<>();
        for (int index = 0; index < knowledge.size(); index++)
        {
            Map<String, Object> rawPoint = knowledge.get(index);
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("id", "K-" + competencyId + "-" + (index + 1));
            point.put("name", text(rawPoint.get("name"), ""));
            if ("".equals(point.get("name")))
            {
                continue;
            }
            point.put("subject", text(rawPoint.get("subject"), ""));
            point.put("covered", Boolean.TRUE.equals(rawPoint.get("covered")));
            unitKnowledge.add(point);
        }

        Map<String, Object> unit = new LinkedHashMap<>();
        unit.put("id", unitId);
        unit.put("statement", statement);
        unit.put("level", level);
        unit.put("source", source);
        unit.put("skills", unitSkills);
        unit.put("knowledge", unitKnowledge);
        return unit;
    }

    private Map<String, List<Map<String, Object>>> groupElements(Map<String, Object> raw, String key)
    {
        Map<String, List<Map<String, Object>>> grouped = new LinkedHashMap<>();
        Map<String, Object> elements = asMap(raw.get("elements"));
        for (Map<String, Object> item : asList(elements.get(key)))
        {
            List<String> competencyIds = new ArrayList<>(toStringList(item.get("competency_ids")));
            String single = text(item.get("competency_id"), "");
            if (!single.isEmpty() && !competencyIds.contains(single))
            {
                competencyIds.add(single);
            }
            for (String competencyId : competencyIds)
            {
                grouped.computeIfAbsent(competencyId, id -> new ArrayList<>()).add(item);
            }
        }
        return grouped;
    }

    private void registerElement(Map<String, Map<String, Object>> elementMap, Map<String, Object> element,
            String competencyId, String prefix, String category)
    {
        String name = text(element.get("name"), "");
        if (name.isEmpty())
        {
            return;
        }
        Map<String, Object> entry = elementMap.get(name);
        if (entry == null)
        {
            entry = new LinkedHashMap<>();
            entry.put("id", prefix + String.format("%02d", elementMap.size() + 1));
            entry.put("name", name);
            if (!category.isEmpty())
            {
                entry.put("category", category);
            }
            else
            {
                entry.put("subject", text(element.get("subject"), ""));
                entry.put("covered", Boolean.TRUE.equals(element.get("covered")));
            }
            entry.put("competency_ids", new ArrayList<String>());
            elementMap.put(name, entry);
        }
        List<String> ids = toStringList(entry.get("competency_ids"));
        if (!ids.contains(competencyId))
        {
            ids.add(competencyId);
        }
        entry.put("competency_ids", ids);
        element.put("id", entry.get("id"));
    }

    private Map<String, Object> buildMindmap(String jobName, List<Map<String, Object>> industryPath,
            List<Map<String, Object>> competencies)
    {
        String chainName = industryPath.isEmpty() ? "待补充产业链"
                : text(industryPath.get(0).get("name"), "待补充产业链");
        String clusterName = industryPath.size() < 3 ? "待补充岗位群"
                : text(industryPath.get(2).get("name"), "待补充岗位群");

        List<Map<String, Object>> competencyNodes = new ArrayList<>();
        for (Map<String, Object> competency : competencies)
        {
            List<Map<String, Object>> unitNodes = new ArrayList<>();
            for (Map<String, Object> unit : asList(competency.get("units")))
            {
                List<Map<String, Object>> leaves = new ArrayList<>();
                for (Map<String, Object> skill : asList(unit.get("skills")))
                {
                    leaves.add(leaf("技能：" + text(skill.get("name"), "")));
                }
                for (Map<String, Object> point : asList(unit.get("knowledge")))
                {
                    leaves.add(leaf("知识：" + text(point.get("name"), "")));
                }
                Map<String, Object> unitNode = new LinkedHashMap<>();
                unitNode.put("name", text(unit.get("statement"), "") + "（L" + integer(unit.get("level"), 2) + "）");
                unitNode.put("value", integer(unit.get("level"), 2));
                unitNode.put("children", leaves);
                unitNodes.add(unitNode);
            }
            Map<String, Object> competencyNode = new LinkedHashMap<>();
            competencyNode.put("name", text(competency.get("name"), "") + "（权重"
                    + trimNumber(number(competency.get("weight"), 0)) + "%）");
            competencyNode.put("value", number(competency.get("weight"), 0));
            competencyNode.put("children", unitNodes);
            competencyNodes.add(competencyNode);
        }

        Map<String, Object> cluster = new LinkedHashMap<>();
        cluster.put("name", "岗位群：" + clusterName);
        cluster.put("children", competencyNodes);

        List<Map<String, Object>> chainChildren = new ArrayList<>();
        chainChildren.add(cluster);
        Map<String, Object> chain = new LinkedHashMap<>();
        chain.put("name", "产业链：" + chainName);
        chain.put("children", chainChildren);

        List<Map<String, Object>> rootChildren = new ArrayList<>();
        rootChildren.add(chain);
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("name", "岗位：" + jobName);
        root.put("children", rootChildren);
        return root;
    }

    private Map<String, Object> branch(String name, Map<String, Object> child)
    {
        List<Map<String, Object>> children = new ArrayList<>();
        children.add(child);
        return branch(name, children);
    }

    private Map<String, Object> branch(String name, List<Map<String, Object>> children)
    {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("name", name);
        node.put("children", children);
        return node;
    }

    private Map<String, Object> chainNode(List<Map<String, Object>> path)
    {
        Map<String, Object> node = null;
        for (int index = path.size() - 1; index >= 0; index--)
        {
            List<Map<String, Object>> children = new ArrayList<>();
            if (node != null)
            {
                children.add(node);
            }
            Map<String, Object> current = new LinkedHashMap<>();
            current.put("name", text(path.get(index).get("name"), ""));
            current.put("children", children);
            node = current;
        }
        return node == null ? leaf("待补充") : node;
    }

    private Map<String, Object> leaf(String name)
    {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("name", name);
        return node;
    }

    private Map<String, Object> buildRadar(List<Map<String, Object>> domains)
    {
        List<Map<String, Object>> indicators = new ArrayList<>();
        List<Object> values = new ArrayList<>();
        for (Map<String, Object> domain : domains)
        {
            Map<String, Object> indicator = new LinkedHashMap<>();
            indicator.put("name", text(domain.get("name"), ""));
            indicator.put("max", 100);
            indicators.add(indicator);
            values.add(number(domain.get("weight"), 0));
        }
        List<Map<String, Object>> series = new ArrayList<>();
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("name", "岗位要求权重");
        item.put("values", values);
        series.add(item);
        Map<String, Object> radar = new LinkedHashMap<>();
        radar.put("indicators", indicators);
        radar.put("series", series);
        return radar;
    }

    private Map<String, Object> buildGraph(String jobName, List<Map<String, Object>> industryPath,
            List<Map<String, Object>> competencies, Map<String, Map<String, Object>> skillMap,
            Map<String, Map<String, Object>> knowledgeMap)
    {
        List<Map<String, Object>> nodes = new ArrayList<>();
        List<Map<String, Object>> edges = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        String chainName = industryPath.isEmpty() ? "待补充产业链"
                : text(industryPath.get(0).get("name"), "待补充产业链");
        String clusterName = industryPath.size() < 3 ? "待补充岗位群"
                : text(industryPath.get(2).get("name"), "待补充岗位群");

        addNode(nodes, seen, "IND1", "产业链：" + chainName, "INDUSTRY_CHAIN", 100);
        addNode(nodes, seen, "CLU1", "岗位群：" + clusterName, "JOB_CLUSTER", 80);
        addNode(nodes, seen, "JOB1", jobName, "JOB", 60);
        edges.add(edge("IND1", "CLU1", "IN_CLUSTER"));
        edges.add(edge("CLU1", "JOB1", "BELONGS_TO"));

        for (Map<String, Object> competency : competencies)
        {
            String competencyId = text(competency.get("id"), "");
            addNode(nodes, seen, competencyId, text(competency.get("name"), ""), "COMPETENCY",
                    number(competency.get("weight"), 0));
            edges.add(edge("JOB1", competencyId, "REQUIRES"));
            for (Map<String, Object> unit : asList(competency.get("units")))
            {
                String unitId = text(unit.get("id"), "");
                addNode(nodes, seen, unitId, text(unit.get("statement"), ""), "COMPETENCY_UNIT",
                        integer(unit.get("level"), 2));
                edges.add(edge(competencyId, unitId, "CONTAINS"));
                for (Map<String, Object> skill : asList(unit.get("skills")))
                {
                    edges.add(edge(unitId, text(skill.get("id"), ""), "SUPPORTED_BY"));
                }
                for (Map<String, Object> point : asList(unit.get("knowledge")))
                {
                    edges.add(edge(unitId, text(point.get("id"), ""), "SUPPORTED_BY"));
                }
            }
        }

        for (Map<String, Object> skill : skillMap.values())
        {
            addNode(nodes, seen, text(skill.get("id"), ""), text(skill.get("name"), ""), "SKILL_POINT", 1);
        }
        for (Map<String, Object> point : knowledgeMap.values())
        {
            addNode(nodes, seen, text(point.get("id"), ""), text(point.get("name"), ""), "KNOWLEDGE_POINT", 1);
        }

        Map<String, Object> graph = new LinkedHashMap<>();
        graph.put("nodes", nodes);
        graph.put("edges", edges);
        return graph;
    }

    private void addNode(List<Map<String, Object>> nodes, Set<String> seen, String id, String name,
            String category, double value)
    {
        if (id.isEmpty() || !seen.add(id))
        {
            return;
        }
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("id", id);
        node.put("name", name);
        node.put("category", category);
        node.put("value", value);
        nodes.add(node);
    }

    private Map<String, Object> edge(String source, String target, String relation)
    {
        Map<String, Object> edge = new LinkedHashMap<>();
        edge.put("source", source);
        edge.put("target", target);
        edge.put("relation", relation);
        return edge;
    }

    private Map<String, Object> buildCoverage(List<Map<String, Object>> tasks,
            List<Map<String, Object>> competencies,
            Map<String, Map<String, Object>> skillMap, Map<String, Map<String, Object>> knowledgeMap,
            List<Map<String, Object>> domains)
    {
        int units = 0;
        for (Map<String, Object> competency : competencies)
        {
            units += asList(competency.get("units")).size();
        }
        Map<String, Object> coverage = new LinkedHashMap<>();
        coverage.put("tasks", tasks.size());
        coverage.put("competencies", competencies.size());
        coverage.put("units", units);
        coverage.put("skills", skillMap.size());
        coverage.put("knowledge", knowledgeMap.size());
        coverage.put("knowledge_coverage", 0);
        coverage.put("weighted_coverage", null);
        coverage.put("domains", domains.size());
        coverage.put("domains_supported", 0);
        return coverage;
    }

    private List<Map<String, Object>> buildSources(Map<String, Object> raw)
    {
        List<Map<String, Object>> sources = new ArrayList<>();
        for (Map<String, Object> item : asList(raw.get("sources")))
        {
            Map<String, Object> source = new LinkedHashMap<>();
            source.put("name", text(item.get("name"), "未标注来源"));
            source.put("version", text(item.get("version"), ""));
            source.put("url", text(item.get("url"), ""));
            source.put("retrieved_at", text(item.get("retrieved_at"), ""));
            sources.add(source);
        }
        if (sources.isEmpty())
        {
            Map<String, Object> source = new LinkedHashMap<>();
            source.put("name", "大模型生成，未经标准原文核对");
            source.put("version", "");
            source.put("url", "");
            source.put("retrieved_at", "");
            sources.add(source);
        }
        return sources;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value)
    {
        return value instanceof Map ? (Map<String, Object>) value : new LinkedHashMap<>();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> asList(Object value)
    {
        return value instanceof List ? (List<Map<String, Object>>) value : new ArrayList<>();
    }

    private static List<String> toStringList(Object value)
    {
        List<String> result = new ArrayList<>();
        if (value instanceof List)
        {
            for (Object item : (List<?>) value)
            {
                if (item != null)
                {
                    result.add(String.valueOf(item));
                }
            }
        }
        else if (value != null && !String.valueOf(value).trim().isEmpty())
        {
            result.add(String.valueOf(value));
        }
        return result;
    }

    private static String text(Object value, String fallback)
    {
        return value == null || String.valueOf(value).trim().isEmpty() ? fallback : String.valueOf(value);
    }

    private static double number(Object value, double fallback)
    {
        if (value instanceof Number)
        {
            return ((Number) value).doubleValue();
        }
        try
        {
            return value == null ? fallback : Double.parseDouble(String.valueOf(value));
        }
        catch (NumberFormatException e)
        {
            return fallback;
        }
    }

    private static double number(Object primary, Object secondary)
    {
        double value = number(primary, Double.NaN);
        return Double.isNaN(value) ? number(secondary, 0) : value;
    }

    private static int integer(Object value, int fallback)
    {
        if (value instanceof Number)
        {
            return ((Number) value).intValue();
        }
        try
        {
            return value == null ? fallback : Integer.parseInt(String.valueOf(value));
        }
        catch (NumberFormatException e)
        {
            return fallback;
        }
    }

    private static Integer integerOrNull(Object value)
    {
        if (value == null)
        {
            return null;
        }
        int parsed = integer(value, Integer.MIN_VALUE);
        return parsed == Integer.MIN_VALUE ? null : parsed;
    }

    private static String trimNumber(double value)
    {
        if (value == Math.floor(value))
        {
            return String.valueOf((long) value);
        }
        return String.valueOf(Math.round(value * 10) / 10.0);
    }
}
