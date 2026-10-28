#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
岗位能力图谱生成器（v2，数据全部来自国家职业分类大典与国家职业标准）。

输入一个岗位名称，驱动「岗位能力图谱20261027」LangGraph 工作流
（职业分类定位 → 主要工作任务 → 能力项/能力单元 → 知识点技能点 → 图谱装配），
输出结构化的岗位能力图谱 JSON。

数据说明：
- 岗位、工作任务、能力项、能力单元、技能点、知识点、权重均来自 job_kb.json 中
  逐条标注了 source 的大典/标准原文；
- 「课程库匹配度」由知识点名称与项目课程库做自动相似度匹配得到，是可复现的计算值，
  不代表人工认定的课程对应关系。

用法：
    python generate_job_competency_graph.py "计算机程序设计员"
    python generate_job_competency_graph.py "软件测试工程师" --output output/test.json
    python generate_job_competency_graph.py "程序员" --llm openai
"""
import argparse
import datetime
import difflib
import json
import os
import re
import sys
from collections import Counter

from build_langgraph import build_graph

WORKFLOW = "gangwei-nengli-tupu-20261027.langgraph.json"
KB_FILE = "job_kb.json"

ROLE_STAGES = [
    ("产业链与岗位群分析师", "industry_chain"),
    ("典型工作任务拆解专家", "work_tasks"),
    ("岗位能力结构拆解专家", "competencies"),
    ("知识点与技能点映射专家", "elements"),
    ("岗位能力图谱装配专家", "graph"),
]

MATCH_THRESHOLD = 0.6


# ---------------------------------------------------------------------------
# 岗位解析
# ---------------------------------------------------------------------------
def resolve_job(kb, query):
    query = (query or "").strip()
    jobs = kb["jobs"]
    for job in jobs:
        if query == job["name"]:
            return job, "exact", 1.0
    for job in jobs:
        for alias in job.get("aliases", []):
            if query == alias:
                return job, "alias", 0.98
    lowered = query.lower()
    for job in jobs:
        names = [job["name"]] + job.get("aliases", [])
        for name in names:
            if lowered and (lowered in name.lower() or name.lower() in lowered):
                return job, "contains", 0.85
    candidates = []
    for job in jobs:
        candidates.extend([job["name"]] + job.get("aliases", []))
    matched = difflib.get_close_matches(query, candidates, n=5, cutoff=0.7)
    if matched:
        name = matched[0]
        for job in jobs:
            if name in ([job["name"]] + job.get("aliases", [])):
                return job, "fuzzy", 0.7
    return None, "none", 0.0


def suggestions(kb):
    return [{"id": job["id"], "name": job["name"], "code": job.get("code", ""),
             "aliases": job.get("aliases", [])} for job in kb["jobs"]]


# ---------------------------------------------------------------------------
# 步骤 1：职业分类层级
# ---------------------------------------------------------------------------
def build_industry_chain(job, matched_by, match_score):
    classification = job["logic_chain"]
    industry = job.get("industry_path") or []
    base = {"exact": 0.95, "alias": 0.9, "contains": 0.85, "fuzzy": 0.75}.get(matched_by, 0.6)
    return {
        "job": {"name": job["name"], "code": job["code"], "aliases": job.get("aliases", [])},
        "industry_path": industry,
        "classification_path": classification,
        "confidence": base,
        "sources": ["SRC-CODEX", "TEACH-MAP-2026-01"],
    }


# ---------------------------------------------------------------------------
# 步骤 2：主要工作任务
# ---------------------------------------------------------------------------
def weight_to_importance(weight):
    if weight >= 50:
        return 5
    if weight >= 30:
        return 4
    if weight >= 15:
        return 3
    return 2


def build_tasks(job):
    competency_by_id = {item["id"]: item for item in job["competencies"]}
    counts = Counter(duty["competency_id"] for duty in job["duties"])
    tasks = []
    for duty in job["duties"]:
        competency = competency_by_id[duty["competency_id"]]
        max_level = max([unit["level"] for unit in competency["units"]] or [2])
        tasks.append({
            "id": duty["id"],
            "name": duty["name"],
            "description": duty["name"],
            "source": duty["source"],
            "competency_ids": [duty["competency_id"]],
            "weight": round(float(competency["weight"]) / counts[duty["competency_id"]], 1),
            "importance": weight_to_importance(competency["weight"]),
            "frequency": None,
            "difficulty": min(5, max_level + 1),
            "data_basis": {
                "importance": "derived_from_competency_weight",
                "difficulty": "derived_from_unit_level",
                "frequency": "not_in_standard",
            },
        })
    return {"tasks": tasks}


# ---------------------------------------------------------------------------
# 步骤 3：能力项与能力单元
# ---------------------------------------------------------------------------
def build_competencies(job, tasks_stage):
    domain_id_by_name = {"D%d" % (index + 1): name for index, name in enumerate(job["domains"])}
    domain_id_by_name = {name: did for did, name in domain_id_by_name.items()}
    competencies = []
    for item in job["competencies"]:
        competencies.append({
            "id": item["id"],
            "name": item["name"],
            "domain": item["domain"],
            "domain_id": domain_id_by_name.get(item["domain"], "D1"),
            "weight": item["weight"],
            "task_ids": item["task_ids"],
            "source": item.get("source", ""),
            "units": [dict(unit) for unit in item["units"]],
        })
    return {"competencies": competencies}


# ---------------------------------------------------------------------------
# 步骤 4：技能点与知识点（含课程库自动匹配）
# ---------------------------------------------------------------------------
def normalize(name):
    return re.sub(r"[（(].*?[)）]|方法|基础知识|知识|的|与|及|、|\s", "", name or "")


def curriculum_index(kb):
    entries = []
    for subject in kb["curriculum"]["subjects"]:
        for point in subject["knowledge_points"]:
            entries.append({
                "name": point["name"],
                "normalized": normalize(point["name"]),
                "subject": subject["name"],
                "subject_code": subject.get("code", ""),
                "importance": point["importance"],
                "difficulty": point["difficulty"],
            })
    return entries


def match_curriculum(name, entries):
    target = normalize(name)
    best = None
    best_ratio = 0.0
    for entry in entries:
        candidate = entry["normalized"]
        if not target or not candidate:
            continue
        ratio = difflib.SequenceMatcher(None, target, candidate).ratio()
        if len(candidate) >= 4 and (candidate in target or target in candidate):
            ratio = max(ratio, 0.75)
        if ratio > best_ratio:
            best_ratio = ratio
            best = entry
    if best and best_ratio >= MATCH_THRESHOLD:
        return best, round(best_ratio, 3)
    return None, round(best_ratio, 3)


def build_elements(job, competencies_stage, kb):
    entries = curriculum_index(kb)
    result = []
    for competency in competencies_stage["competencies"]:
        units = []
        for unit in competency["units"]:
            unit_skills = [
                {"id": "S-%s-%d" % (unit["id"], index + 1), "name": name}
                for index, name in enumerate(unit.get("skills", []))
            ]
            unit_knowledge = []
            for index, name in enumerate(unit.get("knowledge", [])):
                matched, score = match_curriculum(name, entries)
                unit_knowledge.append({
                    "id": "K-%s-%d" % (unit["id"], index + 1),
                    "name": name,
                    "subject": matched["subject"] if matched else "",
                    "matched_point": matched["name"] if matched else "",
                    "match_score": score,
                    "covered": bool(matched),
                })
            units.append({
                "id": unit["id"],
                "statement": unit["statement"],
                "level": unit["level"],
                "source": unit.get("source", ""),
                "skills": unit_skills,
                "knowledge": unit_knowledge,
            })
        result.append({"id": competency["id"], "units": units})
    return {"elements": {"competencies": result}}


# ---------------------------------------------------------------------------
# 步骤 5：图谱装配
# ---------------------------------------------------------------------------
def build_sources(kb, job):
    wanted = {job.get("definition_source", "SRC-CODEX"), job.get("standard_source")}
    sources = []
    for item in kb["provenance"]:
        if item["id"] not in wanted:
            continue
        sources.append({
            "name": item["document"],
            "version": item.get("code", "") or "2022年版",
            "url": item.get("url", ""),
            "retrieved_at": item.get("retrieved_at", ""),
        })
    return sources


def build_graph_payload(kb, job, matched_by, match_score, stages, workflow_meta):
    chain_stage = stages["industry_chain"]
    industry_path = chain_stage["industry_path"]
    classification_path = chain_stage["classification_path"]
    tasks = stages["work_tasks"]["tasks"]
    competencies = stages["competencies"]["competencies"]
    units_by_competency = {item["id"]: item["units"]
                           for item in stages["elements"]["elements"]["competencies"]}

    domain_weights = job["domain_weights"]
    domains = []
    domain_id_by_name = {}
    for index, domain_name in enumerate(job["domains"], start=1):
        domain_id = "D%d" % index
        domain_id_by_name[domain_name] = domain_id
        domains.append({"id": domain_id, "name": domain_name, "weight": domain_weights[domain_name]})

    for competency in competencies:
        competency["units"] = units_by_competency.get(competency["id"], [])
        competency["domain_id"] = domain_id_by_name.get(competency["domain"], "D1")
        competency.pop("domain", None)

    # 全局技能点/知识点
    skill_map = {}
    knowledge_map = {}
    for competency in competencies:
        for unit in competency["units"]:
            for skill in unit["skills"]:
                item = skill_map.setdefault(skill["name"], {
                    "id": "SK%02d" % (len(skill_map) + 1),
                    "name": skill["name"],
                    "category": "岗位技能要求",
                    "competency_ids": [],
                })
                if competency["id"] not in item["competency_ids"]:
                    item["competency_ids"].append(competency["id"])
                skill["id"] = item["id"]
            for point in unit["knowledge"]:
                item = knowledge_map.setdefault(point["name"], {
                    "id": "KP%02d" % (len(knowledge_map) + 1),
                    "name": point["name"],
                    "subject": point["subject"],
                    "matched_point": point["matched_point"],
                    "match_score": point["match_score"],
                    "covered": point["covered"],
                    "competency_ids": [],
                })
                if competency["id"] not in item["competency_ids"]:
                    item["competency_ids"].append(competency["id"])
                point["id"] = item["id"]

    # 雷达图：岗位要求权重 + 课程库匹配度
    support = []
    for domain in domains:
        points = [point for competency in competencies if competency["domain_id"] == domain["id"]
                  for unit in competency["units"] for point in unit["knowledge"]]
        covered = [point for point in points if point["covered"]]
        support.append(round(100.0 * len(covered) / len(points), 1) if points else 0)

    radar = {
        "indicators": [{"name": domain["name"], "max": 100} for domain in domains],
        "series": [
            {"name": "岗位要求权重", "values": [domain["weight"] for domain in domains]},
            {"name": "课程库匹配度", "values": support},
        ],
    }

    # 思维导图：产业链 → 岗位群 → 能力项 → 能力单元 → 技能/知识点
    competency_nodes = []
    for competency in competencies:
        unit_nodes = []
        for unit in competency["units"]:
            unit_nodes.append({
                "name": "%s（L%d）" % (unit["statement"], unit["level"]),
                "value": unit["level"],
                "children": (
                    [{"name": "技能：" + skill["name"], "value": 1} for skill in unit["skills"]]
                    + [{"name": "知识：" + point["name"], "value": 1} for point in unit["knowledge"]]
                ),
            })
        competency_nodes.append({
            "name": "%s（权重%s%%）" % (competency["name"], competency["weight"]),
            "value": competency["weight"],
            "children": unit_nodes,
        })

    mindmap = {
        "name": "岗位：" + job["name"],
        "children": [{
            "name": "产业链：" + industry_path[0]["name"],
            "children": [{
                "name": "岗位群：" + industry_path[2]["name"],
                "children": competency_nodes,
            }],
        }],
    }

    # 关系图：与思维导图同一套层级，节点类别统一英文枚举
    nodes = []
    edges = []
    seen = set()

    def add_node(node_id, name, category, value=None):
        if node_id in seen:
            return
        seen.add(node_id)
        nodes.append({"id": node_id, "name": name, "category": category,
                      "value": value if value is not None else 1})

    add_node("IND1", "产业链：" + industry_path[0]["name"], "INDUSTRY_CHAIN", 100)
    add_node("CLU1", "岗位群：" + industry_path[2]["name"], "JOB_CLUSTER", 80)
    add_node("JOB1", job["name"], "JOB", 60)
    edges.append({"source": "IND1", "target": "CLU1", "relation": "IN_CLUSTER"})
    edges.append({"source": "CLU1", "target": "JOB1", "relation": "BELONGS_TO"})

    for competency in competencies:
        add_node(competency["id"], competency["name"], "COMPETENCY", competency["weight"])
        edges.append({"source": "JOB1", "target": competency["id"], "relation": "REQUIRES"})
        for unit in competency["units"]:
            add_node(unit["id"], unit["statement"], "COMPETENCY_UNIT", unit["level"])
            edges.append({"source": competency["id"], "target": unit["id"], "relation": "CONTAINS"})
            for skill in unit["skills"]:
                edges.append({"source": unit["id"], "target": skill["id"], "relation": "SUPPORTED_BY"})
            for point in unit["knowledge"]:
                edges.append({"source": unit["id"], "target": point["id"], "relation": "SUPPORTED_BY"})

    for skill in skill_map.values():
        add_node(skill["id"], skill["name"], "SKILL_POINT")
    for point in knowledge_map.values():
        add_node(point["id"], point["name"], "KNOWLEDGE_POINT")

    coverage = {
        "tasks": len(tasks),
        "competencies": len(competencies),
        "units": sum(len(item["units"]) for item in competencies),
        "skills": len(skill_map),
        "knowledge": len(knowledge_map),
        "knowledge_coverage": round(
            100.0 * len([point for point in knowledge_map.values() if point["covered"]]) / len(knowledge_map), 1
        ) if knowledge_map else 0,
        "weighted_coverage": None,
        "domains": len(domains),
        "domains_supported": len([value for value in support if value > 0]),
    }

    confidence = {"exact": 0.95, "alias": 0.9, "contains": 0.85, "fuzzy": 0.75}.get(matched_by, 0.6)
    if job["id"] == "JOB-4-04-05-02":
        confidence = round(confidence * 0.9, 2)  # 征求意见稿，置信度下调

    return {
        "schema_version": "3.0.0",
        "meta": {
            "generated_at": datetime.datetime.now().isoformat(timespec="seconds"),
            "mode": "kb",
            "engine": "python-kb",
            "engine_version": "3.0.0",
            "kb_version": kb.get("schema_version"),
            "graph_id": "KB-" + job["code"],
            "workflow_file": workflow_meta.get("name", ""),
            "workflow_steps": workflow_meta.get("steps"),
        },
        "job": {
            "id": job["id"],
            "name": job["name"],
            "code": job["code"],
            "level": job.get("level", ""),
            "level_note": job.get("level_note", ""),
            "digital_occupation": job.get("digital_occupation", False),
            "aliases": job.get("aliases", []),
            "aliases_note": job.get("aliases_note", ""),
            "summary": job.get("definition", ""),
            "confidence": confidence,
            "matched_by": matched_by,
        },
        "industry_path": industry_path,
        "classification_path": classification_path,
        "tasks": tasks,
        "domains": domains,
        "competencies": competencies,
        "elements": {
            "skills": list(skill_map.values()),
            "knowledge": list(knowledge_map.values()),
        },
        "views": {
            "mindmap": mindmap,
            "radar": radar,
            "graph": {"nodes": nodes, "edges": edges},
            "sankey": {"nodes": [], "links": []},
        },
        "coverage": coverage,
        "sources": build_sources(kb, job),
        "review": {
            "status": "approved",
            "reviewer": "standard-transcription",
            "reviewed_at": kb.get("updated_at"),
            "notes": "能力条目逐条对应国家职业标准原文；产业链为教学映射。",
        },
        "data_note": "能力条目逐条对应国家职业标准条款；产业链为教学映射，课程库匹配度为名称相似度自动计算结果。",
    }


# ---------------------------------------------------------------------------
# 模型：离线规则 / 真实大模型
# ---------------------------------------------------------------------------
class RuleBasedModel:
    """内置知识库 + 规则/模板模型：按 Prompt 角色返回对应阶段的结构化结果。"""

    def __init__(self, kb, job, matched_by, match_score):
        self.kb = kb
        self.job = job
        self.matched_by = matched_by
        self.match_score = match_score
        self.stages = {}
        self.calls = []

    def stage_for(self, system_prompt):
        for keyword, stage in ROLE_STAGES:
            if keyword in system_prompt:
                return stage
        raise ValueError("无法识别的 Prompt 角色: %s" % system_prompt[:40])

    def __call__(self, system_prompt, user_prompt):
        stage = self.stage_for(system_prompt)
        self.calls.append(stage)
        if stage == "industry_chain":
            value = build_industry_chain(self.job, self.matched_by, self.match_score)
        elif stage == "work_tasks":
            value = build_tasks(self.job)
        elif stage == "competencies":
            value = build_competencies(self.job, self.stages["work_tasks"])
        elif stage == "elements":
            value = build_elements(self.job, self.stages["competencies"], self.kb)
        elif stage == "graph":
            value = build_graph_payload(self.kb, self.job, self.matched_by, self.match_score, self.stages,
                                        {"name": os.path.basename(WORKFLOW)})
        else:
            raise ValueError("未知阶段: %s" % stage)
        self.stages[stage] = value
        return json.dumps(value, ensure_ascii=False)


class OpenAiModel:
    """真实大模型模型：OpenAI 兼容接口（百炼 qwen / DeepSeek 均可）。"""

    def __init__(self, base_url, api_key, model_name, temperature=0.3, timeout=120):
        self.base_url = base_url.rstrip("/")
        self.api_key = api_key
        self.model_name = model_name
        self.temperature = temperature
        self.timeout = timeout
        self.calls = []

    def __call__(self, system_prompt, user_prompt):
        import urllib.request

        body = json.dumps({
            "model": self.model_name,
            "temperature": self.temperature,
            "messages": [
                {"role": "system", "content": system_prompt},
                {"role": "user", "content": user_prompt},
            ],
        }).encode("utf-8")
        request = urllib.request.Request(
            self.base_url + "/chat/completions",
            data=body,
            headers={"Content-Type": "application/json", "Authorization": "Bearer " + self.api_key},
        )
        with urllib.request.urlopen(request, timeout=self.timeout) as response:
            payload = json.loads(response.read().decode("utf-8"))
        self.calls.append(payload.get("model", self.model_name))
        return payload["choices"][0]["message"]["content"]


# ---------------------------------------------------------------------------
# 主流程
# ---------------------------------------------------------------------------
def load_json(path):
    with open(path, "r", encoding="utf-8") as f:
        return json.load(f)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("job", help="岗位名称，例如：计算机程序设计员")
    parser.add_argument("--workflow", default=WORKFLOW)
    parser.add_argument("--kb", default=KB_FILE)
    parser.add_argument("--output", default=None)
    parser.add_argument("--llm", choices=["offline", "openai"], default="offline")
    parser.add_argument("--base-url", default=os.environ.get(
        "OPENAI_BASE_URL", "https://ws-6sx11dk2z85q2i91.cn-beijing.maas.aliyuncs.com/compatible-mode/v1"))
    parser.add_argument("--api-key", default=os.environ.get("DASHSCOPE_API_KEY", os.environ.get("OPENAI_API_KEY", "")))
    parser.add_argument("--model-name", default=os.environ.get("OPENAI_MODEL", "deepseek-v4.1-flash"))
    args = parser.parse_args()

    document = load_json(args.workflow)
    kb = load_json(args.kb)

    job, matched_by, match_score = resolve_job(kb, args.job)
    if job is None:
        print(json.dumps({
            "error": "岗位未收录",
            "input": args.job,
            "suggestions": suggestions(kb),
        }, ensure_ascii=False, indent=2))
        return 2

    if args.llm == "openai":
        if not args.api_key:
            raise SystemExit("未配置 API Key：请设置 DASHSCOPE_API_KEY / OPENAI_API_KEY，或使用 --llm offline")
        model = OpenAiModel(args.base_url, args.api_key, args.model_name)
    else:
        model = RuleBasedModel(kb, job, matched_by, match_score)

    app = build_graph(document, model)
    result = app.invoke(
        {"AGENT_USER_INPUT": args.job},
        config={"recursion_limit": document["execution"]["recursion_limit"]},
    )
    graph_json = result.get(document["execution"]["output_key"], "")

    try:
        payload = json.loads(graph_json)
    except json.JSONDecodeError as error:
        print("FAIL: 工作流输出不是合法 JSON: %s" % error)
        print(graph_json[:2000])
        return 1

    payload.setdefault("meta", {}).update({
        "workflow_file": os.path.basename(args.workflow),
        "workflow_sha256": document["extensions"]["source_sha256"],
        "workflow_steps": len(document["graph"]["nodes"]),
        "workflow_nodes": [node["runtime_name"] for node in document["graph"]["nodes"]],
        "model": args.llm,
    })

    output = args.output or os.path.join("output", "岗位能力图谱-%s.json" % job["name"])
    directory = os.path.dirname(output)
    if directory and not os.path.isdir(directory):
        os.makedirs(directory)
    with open(output, "w", encoding="utf-8", newline="\n") as f:
        json.dump(payload, f, ensure_ascii=False, indent=2)
        f.write("\n")

    print(json.dumps({
        "output": output,
        "job": payload["job"]["name"],
        "code": payload["job"]["code"],
        "matched_by": matched_by,
        "industry_path": [item["name"] for item in payload["industry_path"]],
        "classification_path": [item["name"] for item in payload["classification_path"]],
        "coverage": payload["coverage"],
        "confidence": payload["job"]["confidence"],
        "workflow_steps": payload["meta"]["workflow_steps"],
        "model_calls": len(model.calls),
    }, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    sys.exit(main())
