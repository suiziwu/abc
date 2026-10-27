#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
将讯飞星火工作流 DSL（YAML）转换为 LangGraph StateGraph 语义的 JSON 图清单。

转换原则：
1. 原始 YAML 完整保留在 extensions.source 中，任何字段都不丢弃；
2. graph.nodes / graph.edges 提供 LangGraph 执行所需的规范化结构；
3. 节点、边、分支、Prompt、代码、坐标、图标等均可被 Java 侧直接读取。

用法：
    python convert_iflytek_workflow.py source/人才画像20260914150500.yml rencai-huaxiang-20260914150500.langgraph.json
"""
import argparse
import hashlib
import json
import os
import re
import sys

import yaml

NODE_TYPE_MAP = {
    "开始节点": "start",
    "结束节点": "end",
    "代码": "code",
    "分支器": "router",
    "大模型": "llm",
}

LLM_ROLE_RULES = [
    ("subject_selector", "课程架构师"),
    ("question_generator", "技术面试出题专家"),
    ("wrong_answer_diagnoser", "错题知识点诊断专家"),
]

RUNTIME_NAMES = {
    "start": "start",
    "end": "end",
    "code": "split_input",
    "router": "route",
}


def role_for_llm(node_param):
    system_template = node_param.get("systemTemplate") or ""
    for role, keyword in LLM_ROLE_RULES:
        if keyword in system_template:
            return role
    return None


def find_value_ref(schema):
    value = (schema or {}).get("value") or {}
    if value.get("type") == "ref":
        return value.get("content") or {}
    return None


def normalize_inputs(inputs):
    result = []
    for item in inputs or []:
        schema = item.get("schema") or {}
        ref = find_value_ref(schema)
        entry = {
            "id": item.get("id"),
            "name": item.get("name"),
            "name_err_msg": item.get("nameErrMsg"),
            "type": schema.get("type"),
            "file_type": item.get("fileType"),
            "schema": schema,
        }
        if ref:
            entry["from"] = {
                "kind": "ref",
                "node_id": ref.get("nodeId"),
                "output": ref.get("name"),
                "output_id": ref.get("id"),
            }
        elif (schema.get("value") or {}).get("type") == "literal":
            entry["from"] = {
                "kind": "literal",
                "value": schema["value"].get("content"),
            }
        result.append(entry)
    return result


def normalize_outputs(outputs):
    result = []
    for item in outputs or []:
        schema = item.get("schema") or {}
        result.append({
            "id": item.get("id"),
            "name": item.get("name"),
            "name_err_msg": item.get("nameErrMsg"),
            "type": schema.get("type"),
            "default": schema.get("default"),
            "schema": schema,
        })
    return result


def convert(source_path, output_path, runtime_base_url=None, runtime_model_name=None,
            runtime_api_key_env=None):
    with open(source_path, "r", encoding="utf-8") as f:
        raw_text = f.read()
    source = yaml.safe_load(raw_text)

    flow_meta = source.get("flowMeta") or {}
    flow_data = source.get("flowData") or {}
    nodes = flow_data.get("nodes") or []
    edges = flow_data.get("edges") or []

    if not nodes:
        raise ValueError("flowData.nodes 为空，无法转换")

    node_by_id = {node["id"]: node for node in nodes}
    if len(node_by_id) != len(nodes):
        raise ValueError("存在重复的节点 ID")

    # 1. 识别节点类型与 LLM 角色
    llm_roles = []
    for node in nodes:
        if NODE_TYPE_MAP.get(node.get("type")) == "llm":
            role = role_for_llm((node.get("data") or {}).get("nodeParam") or {})
            llm_roles.append(role)

    # 2. 结束节点输入 -> LLM 输出 key 映射（output/output2/output3）
    end_node = next((n for n in nodes if NODE_TYPE_MAP.get(n.get("type")) == "end"), None)
    if end_node is None:
        raise ValueError("缺少结束节点")
    end_inputs = (end_node.get("data") or {}).get("inputs") or []
    end_ref_by_node = {}
    end_input_keys = []
    for item in end_inputs:
        ref = find_value_ref(item.get("schema") or {})
        if ref:
            end_ref_by_node[ref.get("nodeId")] = item.get("name")
            end_input_keys.append({
                "end_input": item.get("name"),
                "state_key": item.get("name"),
                "node_id": ref.get("nodeId"),
                "output": ref.get("name"),
                "output_id": ref.get("id"),
            })

    # 3. 路由分支 -> 目标节点
    branch_target = {}
    for edge in edges:
        if edge.get("sourceHandle"):
            branch_target[edge["sourceHandle"]] = edge.get("target")

    # 4. 规范化节点
    normalized_nodes = []
    llm_index = 0
    for node in nodes:
        iflytek_type = node.get("type")
        node_type = NODE_TYPE_MAP.get(iflytek_type)
        if node_type is None:
            raise ValueError("未知节点类型: %s" % iflytek_type)
        data = node.get("data") or {}
        node_meta = data.get("nodeMeta") or {}
        node_param = data.get("nodeParam") or {}

        if node_type in RUNTIME_NAMES:
            runtime_name = RUNTIME_NAMES[node_type]
        else:
            role = llm_roles[llm_index] if llm_index < len(llm_roles) else None
            llm_index += 1
            runtime_name = role or ("llm_%d" % llm_index)

        normalized = {
            "id": node.get("id"),
            "runtime_name": runtime_name,
            "type": node_type,
            "iflytek_type": iflytek_type,
            "name": data.get("label"),
            "alias": node_meta.get("aliasName"),
            "node_meta": node_meta,
            "description": data.get("description"),
            "position": node.get("position"),
            "position_absolute": node.get("positionAbsolute"),
            "size": {
                "width": node.get("width"),
                "height": node.get("height"),
            },
            "icon": data.get("icon"),
            "flags": {
                "dragging": node.get("dragging"),
                "selected": node.get("selected"),
                "updatable": data.get("updatable"),
                "allow_input_reference": data.get("allowInputReference"),
                "allow_output_reference": data.get("allowOutputReference"),
                "label_edit": data.get("labelEdit"),
            },
            "references": data.get("references") or [],
            "status": data.get("status"),
            "inputs": normalize_inputs(data.get("inputs")),
            "outputs": normalize_outputs(data.get("outputs")),
            "node_param": node_param,
        }

        if node_type == "start":
            normalized["state_writes"] = {
                out.get("name"): {
                    "type": (out.get("schema") or {}).get("type"),
                    "default": (out.get("schema") or {}).get("default"),
                    "required": out.get("required"),
                }
                for out in (data.get("outputs") or [])
            }

        elif node_type == "code":
            input_mapping = {}
            for item in normalized["inputs"]:
                ref = (item.get("from") or {}).get("output")
                if ref:
                    input_mapping[item.get("name")] = ref
            code_source = node_param.get("code") or ""
            head_match = re.search(r"input\[:(\d+)\]", code_source)
            tail_match = re.search(r"input\[(\d+):\]", code_source)
            operation = None
            if head_match and tail_match and head_match.group(1) == tail_match.group(1):
                split_index = int(head_match.group(1))
                operation = {
                    "type": "split_at",
                    "input_state_key": input_mapping.get("input"),
                    "split_index": split_index,
                    "outputs": {
                        "head": normalized["outputs"][0].get("name"),
                        "tail": normalized["outputs"][1].get("name"),
                    },
                    "semantics": "python: code = input[:%d]; item = input[%d:]" % (
                        split_index, split_index
                    ),
                }
            normalized["code"] = {
                "language": "python",
                "entrypoint": "main",
                "source": code_source,
                "uid": node_param.get("uid"),
                "app_id": node_param.get("appId"),
                "input_mapping": input_mapping,
                "state_writes": [out.get("name") for out in normalized["outputs"]],
                "operation": operation,
            }

        elif node_type == "router":
            inputs_by_id = {item.get("id"): item for item in (data.get("inputs") or [])}
            branches = []
            for case in node_param.get("cases") or []:
                conditions = []
                for cond in case.get("conditions") or []:
                    left_input = inputs_by_id.get(cond.get("leftVarIndex")) or {}
                    right_input = inputs_by_id.get(cond.get("rightVarIndex")) or {}
                    left_ref = find_value_ref(left_input.get("schema") or {})
                    right_value = (right_input.get("schema") or {}).get("value") or {}
                    left = {
                        "input_id": cond.get("leftVarIndex"),
                        "input_name": left_input.get("name"),
                    }
                    if left_ref:
                        left.update({
                            "kind": "ref",
                            "node_id": left_ref.get("nodeId"),
                            "output": left_ref.get("name"),
                            "state_key": left_ref.get("name"),
                        })
                    else:
                        left.update({"kind": "literal", "value": left.get("value")})

                    if right_value.get("type") == "literal":
                        right = {
                            "input_id": cond.get("rightVarIndex"),
                            "input_name": right_input.get("name"),
                            "kind": "literal",
                            "value": right_value.get("content"),
                        }
                    else:
                        right_ref = find_value_ref(right_input.get("schema") or {})
                        right = {
                            "input_id": cond.get("rightVarIndex"),
                            "input_name": right_input.get("name"),
                            "kind": "ref" if right_ref else "unknown",
                        }
                        if right_ref:
                            right.update({
                                "node_id": right_ref.get("nodeId"),
                                "output": right_ref.get("name"),
                                "state_key": right_ref.get("name"),
                            })
                    conditions.append({
                        "id": cond.get("id"),
                        "operator": cond.get("compareOperator"),
                        "left": left,
                        "right": right,
                    })

                branch_id = case.get("id")
                target = branch_target.get(branch_id)
                target_node = node_by_id.get(target) if target else None
                branches.append({
                    "branch_id": branch_id,
                    "level": case.get("level"),
                    "logical_operator": case.get("logicalOperator"),
                    "conditions": conditions,
                    "is_default": case.get("level") == 999 or not conditions,
                    "target": target,
                    "target_runtime": None,
                })

            normalized["router"] = {
                "uid": node_param.get("uid"),
                "app_id": node_param.get("appId"),
                "branches": branches,
                "default_target": next(
                    (b["target"] for b in branches if b.get("is_default")), None
                ),
            }

        elif node_type == "llm":
            input_mapping = {}
            for item in normalized["inputs"]:
                ref = (item.get("from") or {}).get("output")
                if ref:
                    input_mapping[item.get("name")] = ref
            output_items = data.get("outputs") or []
            output_name = output_items[0].get("name") if output_items else "output"
            output_id = output_items[0].get("id") if output_items else None
            state_key = end_ref_by_node.get(node.get("id"), output_name)
            normalized["llm"] = {
                "model": "default",
                "prompt": {
                    "system": node_param.get("systemTemplate"),
                    "system_template_format": "text",
                    "user_template": node_param.get("template"),
                    "input_mapping": input_mapping,
                },
                "output": {
                    "name": output_name,
                    "id": output_id,
                    "state_key": state_key,
                    "end_input": end_ref_by_node.get(node.get("id")),
                },
                "provider": {
                    "source": node_param.get("source"),
                    "model_name": node_param.get("modelName"),
                    "model_id": node_param.get("modelId"),
                    "llm_id": node_param.get("llmId"),
                    "service_id": node_param.get("serviceId"),
                    "domain": node_param.get("domain"),
                    "app_id": node_param.get("appId"),
                    "uid": node_param.get("uid"),
                    "url": node_param.get("url"),
                    "temperature": (node_param.get("extraParams") or {}).get("temperature"),
                    "enable_chat_history": node_param.get("enableChatHistoryV2"),
                    "auditing": node_param.get("auditing"),
                    "resp_format": node_param.get("respFormat"),
                    "is_think": node_param.get("isThink"),
                    "search_disable": node_param.get("searchDisable"),
                },
            }

        elif node_type == "end":
            template = node_param.get("template") or ""
            normalized["output"] = {
                "template": template,
                "stream_output": node_param.get("streamOutput"),
                "output_mode": node_param.get("outputMode"),
                "input_keys": end_input_keys,
                "state_keys": [item["state_key"] for item in end_input_keys],
            }

        normalized_nodes.append(normalized)

    # 5. 回填路由目标 runtime_name
    runtime_by_id = {node["id"]: node["runtime_name"] for node in normalized_nodes}
    for node in normalized_nodes:
        if node["type"] == "router":
            for branch in node["router"]["branches"]:
                branch["target_runtime"] = runtime_by_id.get(branch.get("target"))

    # 6. 规范化边
    normalized_edges = []
    for edge in edges:
        if edge.get("source") not in node_by_id or edge.get("target") not in node_by_id:
            raise ValueError("边引用了不存在的节点: %s" % edge.get("id"))
        normalized_edges.append({
            "id": edge.get("id"),
            "source": edge.get("source"),
            "target": edge.get("target"),
            "source_handle": edge.get("sourceHandle"),
            "target_handle": edge.get("targetHandle"),
            "type": edge.get("type"),
            "edge_type": (edge.get("data") or {}).get("edgeType"),
            "marker_end": edge.get("markerEnd"),
        })

    # 7. 模型配置（三个 LLM 节点配置一致，取第一个）
    llm_nodes = [n for n in normalized_nodes if n["type"] == "llm"]
    if not llm_nodes:
        raise ValueError("缺少大模型节点")
    first_provider = llm_nodes[0]["llm"]["provider"]
    base_url = (first_provider.get("url") or "").replace("/chat/completions", "")
    models = {
        "default": {
            "provider": "openai-compatible",
            "base_url": base_url,
            "model_name": first_provider.get("model_name"),
            "temperature": first_provider.get("temperature"),
            "timeout_seconds": 120,
            "api_key_env": "DEEPSEEK_API_KEY",
            "provider_options": first_provider,
        }
    }
    if runtime_base_url:
        models["default"]["base_url"] = runtime_base_url
    if runtime_model_name:
        models["default"]["model_name"] = runtime_model_name
    if runtime_api_key_env:
        models["default"]["api_key_env"] = runtime_api_key_env

    # 8. 状态定义
    state_schema = {}
    for node in normalized_nodes:
        if node["type"] == "start":
            for name, meta in (node.get("state_writes") or {}).items():
                state_schema[name] = {
                    "type": meta.get("type") or "string",
                    "default": meta.get("default"),
                    "required": meta.get("required"),
                    "producer": node["runtime_name"],
                }
        elif node["type"] == "code":
            for out in node["outputs"]:
                state_schema[out["name"]] = {
                    "type": out.get("type") or "string",
                    "default": out.get("default"),
                    "producer": node["runtime_name"],
                }
        elif node["type"] == "llm":
            out = node["llm"]["output"]
            state_schema[out["state_key"]] = {
                "type": "string",
                "default": "",
                "producer": node["runtime_name"],
            }
    state_schema["final_output"] = {
        "type": "string",
        "default": "",
        "producer": "end",
    }

    # 9. 执行元信息
    end_normalized = next(n for n in normalized_nodes if n["type"] == "end")
    start_normalized = next(n for n in normalized_nodes if n["type"] == "start")
    execution = {
        "entrypoint": start_normalized["runtime_name"],
        "finish_point": end_normalized["runtime_name"],
        "input_keys": list((start_normalized.get("state_writes") or {}).keys()),
        "output_template": end_normalized["output"]["template"],
        "output_state_keys": [item["state_key"] for item in end_normalized["output"]["input_keys"]],
        "output_key": "final_output",
        "recursion_limit": 25,
    }

    # 10. 顶层结构
    advanced_config_raw = flow_meta.get("advancedConfig")
    advanced_config = None
    if isinstance(advanced_config_raw, str) and advanced_config_raw.strip():
        try:
            advanced_config = json.loads(advanced_config_raw)
        except json.JSONDecodeError:
            advanced_config = None

    converted = {
        "schema_version": "1.0.0",
        "framework": "langchain/langgraph",
        "format": "langgraph-state-graph",
        "flow": {
            "id": flow_meta.get("name"),
            "name": flow_meta.get("name"),
            "description": flow_meta.get("description"),
            "category": flow_meta.get("category"),
            "dsl_version": flow_meta.get("dslVersion"),
            "avatar_icon": flow_meta.get("avatarIcon"),
            "avatar_color": flow_meta.get("avatarColor"),
            "advanced_config": advanced_config,
            "advanced_config_raw": advanced_config_raw,
        },
        "state": {
            "schema": state_schema,
            "defaults": {
                name: (meta.get("default") if meta.get("default") is not None else "")
                for name, meta in state_schema.items()
            },
        },
        "models": models,
        "graph": {
            "entrypoint": start_normalized["id"],
            "finish_point": end_normalized["id"],
            "entrypoint_runtime": start_normalized["runtime_name"],
            "finish_runtime": end_normalized["runtime_name"],
            "nodes": normalized_nodes,
            "edges": normalized_edges,
            "conditional_edges": [
                {
                    "source": node["id"],
                    "source_runtime": node["runtime_name"],
                    "branches": node["router"]["branches"],
                    "default_target": node["router"]["default_target"],
                }
                for node in normalized_nodes
                if node["type"] == "router"
            ],
        },
        "execution": execution,
        "extensions": {
            "source_format": "iflytek-spark-workflow-dsl",
            "source_dsl_version": flow_meta.get("dslVersion"),
            "source_file": os.path.basename(source_path),
            "source_sha256": hashlib.sha256(raw_text.encode("utf-8")).hexdigest(),
            "converter": "langchain/convert_iflytek_workflow.py",
            "source": source,
        },
    }

    # 11. 语义自检
    assert len(converted["graph"]["nodes"]) == len(nodes)
    assert len(converted["graph"]["edges"]) == len(edges)
    assert len({n["id"] for n in converted["graph"]["nodes"]}) == len(nodes)
    for edge in converted["graph"]["edges"]:
        assert edge["source"] in node_by_id
        assert edge["target"] in node_by_id
    for router in converted["graph"]["conditional_edges"]:
        for branch in router["branches"]:
            assert branch["target"] in node_by_id, branch
            assert branch["target_runtime"] is not None, branch

    with open(output_path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(converted, f, ensure_ascii=False, indent=2)
        f.write("\n")

    print("converted: %s" % output_path)
    print("nodes=%d edges=%d branches=%d prompts=%d" % (
        len(converted["graph"]["nodes"]),
        len(converted["graph"]["edges"]),
        sum(len(r["branches"]) for r in converted["graph"]["conditional_edges"]),
        len(llm_nodes),
    ))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("source")
    parser.add_argument("output")
    parser.add_argument("--base-url", default=None,
                        help="覆盖 models.default.base_url（例如百炼 OpenAI 兼容地址）")
    parser.add_argument("--model-name", default=None,
                        help="覆盖 models.default.model_name（例如 qwen3.7-flash）")
    parser.add_argument("--api-key-env", default=None,
                        help="覆盖 models.default.api_key_env（例如 DASHSCOPE_API_KEY）")
    args = parser.parse_args()
    convert(args.source, args.output, args.base_url, args.model_name, args.api_key_env)


if __name__ == "__main__":
    main()
