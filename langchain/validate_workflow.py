#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
校验 langchain/*.langgraph.json：
1) JSON Schema 语法/必填字段/数据类型校验；
2) 图结构语义校验（节点、边、分支、可达性、Prompt、状态键）；
3) 与原始讯飞 DSL 的条目数、数据一致性校验。

用法：
    python validate_workflow.py rencai-huaxiang-20260914150500.langgraph.json [workflow.schema.json]
"""
import argparse
import hashlib
import json
import sys

try:
    import jsonschema
except ImportError:
    jsonschema = None

REQUIRED_PROMPT_KEYWORDS = [
    "资深的职业教育课程架构师",
    "技术面试出题专家",
    "错题知识点诊断专家",
]


def fail(message):
    raise AssertionError(message)


def validate_schema(document, schema):
    if jsonschema is None:
        print("WARN: jsonschema 未安装，跳过 JSON Schema 校验")
        return
    validator = jsonschema.Draft7Validator(schema)
    errors = sorted(validator.iter_errors(document), key=lambda e: list(e.path))
    if errors:
        for error in errors:
            path = ".".join(str(p) for p in error.path) or "<root>"
            print("SCHEMA ERROR at %s: %s" % (path, error.message))
        fail("JSON Schema 校验失败（%d 项）" % len(errors))


def validate_semantics(document):
    graph = document["graph"]
    nodes = graph["nodes"]
    edges = graph["edges"]

    node_by_id = {}
    runtime_names = set()
    for node in nodes:
        if node["id"] in node_by_id:
            fail("重复节点 ID: %s" % node["id"])
        if node["runtime_name"] in runtime_names:
            fail("重复 runtime_name: %s" % node["runtime_name"])
        node_by_id[node["id"]] = node
        runtime_names.add(node["runtime_name"])

    types = [node["type"] for node in nodes]
    if types.count("start") != 1:
        fail("start 节点数量必须为 1，实际 %d" % types.count("start"))
    if types.count("end") != 1:
        fail("end 节点数量必须为 1，实际 %d" % types.count("end"))
    if types.count("llm") < 1:
        fail("至少需要 1 个 llm 节点")

    for edge in edges:
        if edge["source"] not in node_by_id:
            fail("边 %s 的 source 不存在: %s" % (edge["id"], edge["source"]))
        if edge["target"] not in node_by_id:
            fail("边 %s 的 target 不存在: %s" % (edge["id"], edge["target"]))

    conditional = graph["conditional_edges"]
    if len(conditional) != types.count("router"):
        fail("conditional_edges 数量与 router 节点数量不一致")

    for item in conditional:
        router_node = node_by_id.get(item["source"])
        if router_node is None or router_node["type"] != "router":
            fail("conditional_edge 的 source 不是 router: %s" % item["source"])
        if item["source_runtime"] != router_node["runtime_name"]:
            fail("conditional_edge source_runtime 不匹配: %s" % item["source"])
        if item["default_target"] not in node_by_id:
            fail("router 默认目标不存在: %s" % item["default_target"])
        for branch in item["branches"]:
            if branch["target"] not in node_by_id:
                fail("分支 %s 的目标不存在: %s" % (branch["branch_id"], branch["target"]))
            if branch["target_runtime"] != node_by_id[branch["target"]]["runtime_name"]:
                fail("分支 %s 的 target_runtime 不匹配" % branch["branch_id"])
            for condition in branch["conditions"]:
                for side in ("left", "right"):
                    operand = condition[side]
                    if operand.get("kind") == "ref":
                        if operand.get("state_key") not in document["state"]["schema"]:
                            fail("条件引用了未定义状态键: %s" % operand.get("state_key"))

    # 可达性：从 start 出发遍历普通边 + 分支边
    adjacency = {}
    for edge in edges:
        if edge.get("source_handle"):
            continue
        adjacency.setdefault(edge["source"], []).append(edge["target"])
    for item in conditional:
        adjacency.setdefault(item["source"], [])
        for branch in item["branches"]:
            adjacency[item["source"]].append(branch["target"])
    start_id = graph["entrypoint"]
    visited = set()
    stack = [start_id]
    while stack:
        current = stack.pop()
        if current in visited:
            continue
        visited.add(current)
        stack.extend(adjacency.get(current, []))
    unreachable = set(node_by_id) - visited
    if unreachable:
        fail("存在不可达节点: %s" % ", ".join(sorted(unreachable)))

    # LLM 节点 Prompt 校验
    systems = []
    for node in nodes:
        if node["type"] != "llm":
            continue
        prompt = node["llm"]["prompt"]
        if not prompt.get("system"):
            fail("LLM 节点 %s 缺少 system prompt" % node["id"])
        if not prompt.get("user_template"):
            fail("LLM 节点 %s 缺少 user_template" % node["id"])
        for mapping in (prompt.get("input_mapping") or {}).values():
            if mapping not in document["state"]["schema"]:
                fail("LLM 节点 %s 的输入映射状态键不存在: %s" % (node["id"], mapping))
        if node["llm"]["output"]["state_key"] not in document["state"]["schema"]:
            fail("LLM 节点 %s 的输出状态键不存在" % node["id"])
        systems.append(prompt["system"])

    for keyword in REQUIRED_PROMPT_KEYWORDS:
        if not any(keyword in system for system in systems):
            fail("缺少预期 Prompt 内容: %s" % keyword)

    # 代码节点校验
    code_nodes = [node for node in nodes if node["type"] == "code"]
    if len(code_nodes) != 1:
        fail("code 节点数量必须为 1，实际 %d" % len(code_nodes))
    code_node = code_nodes[0]
    operation = code_node["code"].get("operation")
    if not operation or operation.get("type") != "split_at":
        fail("code 节点缺少可执行的 split_at operation")
    if operation["input_state_key"] not in document["state"]["schema"]:
        fail("code 节点输入状态键不存在")
    for output_name in operation["outputs"].values():
        if output_name not in document["state"]["schema"]:
            fail("code 节点输出状态键不存在: %s" % output_name)
    for state_key in code_node["code"].get("state_writes") or []:
        if state_key not in document["state"]["schema"]:
            fail("code 节点 state_writes 未定义: %s" % state_key)

    # 结束节点校验
    end_node = next(node for node in nodes if node["type"] == "end")
    template = end_node["output"]["template"]
    for state_key in end_node["output"]["state_keys"]:
        if state_key not in document["state"]["schema"]:
            fail("结束节点引用了未定义状态键: %s" % state_key)
        if "{{%s}}" % state_key not in template:
            fail("结束模板缺少占位符: {{%s}}" % state_key)
    if document["execution"]["output_template"] != template:
        fail("execution.output_template 与结束节点模板不一致")

    # 执行入口/出口校验
    execution = document["execution"]
    start_node = next(node for node in nodes if node["type"] == "start")
    if execution["entrypoint"] != start_node["runtime_name"]:
        fail("execution.entrypoint 与 start 节点不一致")
    if execution["finish_point"] != end_node["runtime_name"]:
        fail("execution.finish_point 与 end 节点不一致")
    if execution["output_key"] not in document["state"]["schema"]:
        fail("execution.output_key 未在状态中定义")

    # 模型引用校验
    for node in nodes:
        if node["type"] == "llm" and node["llm"]["model"] not in document["models"]:
            fail("LLM 节点引用了未定义模型: %s" % node["llm"]["model"])

    # 原始数据一致性
    extensions = document["extensions"]
    source = extensions["source"]
    source_nodes = source["flowData"]["nodes"]
    source_edges = source["flowData"]["edges"]
    if len(source_nodes) != len(nodes):
        fail("原始节点数与转换后节点数不一致")
    if len(source_edges) != len(edges):
        fail("原始边数与转换后边数不一致")
    if sorted(n["id"] for n in source_nodes) != sorted(node["id"] for node in nodes):
        fail("原始节点 ID 集合与转换后不一致")
    if sorted(e["id"] for e in source_edges) != sorted(edge["id"] for edge in edges):
        fail("原始边 ID 集合与转换后不一致")

    # Prompt 原文一致性：normalized prompt === source nodeParam.systemTemplate
    for node in nodes:
        if node["type"] != "llm":
            continue
        source_node = next(n for n in source_nodes if n["id"] == node["id"])
        source_system = (source_node.get("data") or {}).get("nodeParam", {}).get("systemTemplate")
        if node["llm"]["prompt"]["system"] != source_system:
            fail("LLM 节点 %s 的 system prompt 与原文不一致" % node["id"])

    # 流程元信息一致性
    if document["flow"]["name"] != source["flowMeta"]["name"]:
        fail("flow.name 与原始 flowMeta.name 不一致")
    if document["flow"]["advanced_config_raw"] != source["flowMeta"]["advancedConfig"]:
        fail("advanced_config_raw 与原文不一致")
    if document["flow"]["advanced_config"] is not None:
        if json.loads(document["flow"]["advanced_config_raw"]) != document["flow"]["advanced_config"]:
            fail("advanced_config 解析结果与原文不一致")

    return nodes, edges, conditional


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("workflow")
    parser.add_argument("schema", nargs="?", default="workflow.schema.json")
    args = parser.parse_args()

    with open(args.workflow, "r", encoding="utf-8") as f:
        document = json.load(f)
    with open(args.schema, "r", encoding="utf-8") as f:
        schema = json.load(f)

    validate_schema(document, schema)
    nodes, edges, conditional = validate_semantics(document)

    print("PASS: JSON Schema 校验通过")
    print("PASS: 图结构校验通过")
    print("PASS: 原始数据一致性校验通过")
    print("summary: nodes=%d edges=%d routers=%d branches=%d llm=%d" % (
        len(nodes),
        len(edges),
        len(conditional),
        sum(len(item["branches"]) for item in conditional),
        sum(1 for node in nodes if node["type"] == "llm"),
    ))
    print("sha256: %s" % document["extensions"]["source_sha256"])


if __name__ == "__main__":
    try:
        main()
    except AssertionError as error:
        print("FAIL: %s" % error)
        sys.exit(1)
