#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
使用 LangGraph 从转换后的 JSON 清单构建并执行 StateGraph。

该脚本证明转换产物可被 LangChain/LangGraph 工具链直接加载与执行。
用法：
    python build_langgraph.py rencai-huaxiang-20260914150500.langgraph.json
"""
import argparse
import json
import sys
from typing import TypedDict

from langgraph.graph import StateGraph, END

from validate_workflow import validate_semantics


def render_template(template, state, mapping=None):
    """按 input_mapping（变量名 -> 状态键）渲染 {{var}} 模板；无 mapping 时按状态键全量替换。"""
    if mapping:
        for variable, state_key in mapping.items():
            template = template.replace("{{%s}}" % variable, str(state.get(state_key, "")))
        return template
    for key, value in state.items():
        template = template.replace("{{%s}}" % key, "" if value is None else str(value))
    return template


def build_graph(document, llm_call):
    graph_spec = document["graph"]
    nodes = {node["runtime_name"]: node for node in graph_spec["nodes"]}
    state_keys = list(document["state"]["schema"].keys())

    WorkflowState = TypedDict("WorkflowState", {key: object for key in state_keys}, total=False)

    def start_node(state):
        start = next(node for node in graph_spec["nodes"] if node["type"] == "start")
        result = dict(document["state"]["defaults"])
        for name, meta in (start.get("state_writes") or {}).items():
            result[name] = state.get(name, meta.get("default") or "")
        return result

    def code_node(state):
        node = next(node for node in graph_spec["nodes"] if node["type"] == "code")
        code_spec = node["code"]
        namespace = {}
        exec(compile(code_spec["source"], "<iflytek-code-node>", "exec"), namespace)
        main = namespace[code_spec["entrypoint"]]
        input_mapping = code_spec.get("input_mapping") or {}
        argument = state.get(input_mapping.get("input"), "")
        result = main(argument)
        if not isinstance(result, dict):
            raise TypeError("代码节点必须返回 dict")
        for key in code_spec.get("state_writes") or []:
            if key not in result:
                raise KeyError("代码节点缺少输出: %s" % key)
        return {key: result[key] for key in code_spec.get("state_writes") or []}

    def route_node(state):
        return {}

    def route_selector(state):
        conditional = graph_spec["conditional_edges"][0]
        for branch in conditional["branches"]:
            conditions = branch.get("conditions") or []
            if not conditions:
                if branch.get("is_default"):
                    return "default"
                continue
            matches = []
            for condition in conditions:
                left = condition["left"]
                right = condition["right"]
                left_value = state.get(left.get("state_key"), "") if left.get("kind") == "ref" else left.get("value")
                right_value = right.get("value") if right.get("kind") == "literal" else state.get(right.get("state_key"), "")
                operator = condition.get("operator")
                if operator == "eq":
                    matches.append(str(left_value) == str(right_value))
                elif operator == "ne":
                    matches.append(str(left_value) != str(right_value))
                elif operator == "contains":
                    matches.append(str(right_value) in str(left_value))
                else:
                    raise ValueError("不支持的操作符: %s" % operator)
            logical = branch.get("logical_operator", "and")
            if (logical == "and" and all(matches)) or (logical == "or" and any(matches)):
                return branch["branch_id"]
        return "default"

    def llm_node_factory(node):
        def run(state):
            prompt = node["llm"]["prompt"]
            user_prompt = render_template(
                prompt["user_template"],
                state,
                prompt.get("input_mapping") or {},
            )
            answer = llm_call(prompt["system"], user_prompt)
            return {node["llm"]["output"]["state_key"]: answer}
        return run

    def end_node(state):
        node = next(node for node in graph_spec["nodes"] if node["type"] == "end")
        output_spec = node["output"]
        output = render_template(output_spec["template"], state)
        return {document["execution"]["output_key"]: output}

    builder = StateGraph(WorkflowState)
    for name, node in nodes.items():
        if node["type"] == "start":
            builder.add_node(name, start_node)
        elif node["type"] == "code":
            builder.add_node(name, code_node)
        elif node["type"] == "router":
            builder.add_node(name, route_node)
        elif node["type"] == "llm":
            builder.add_node(name, llm_node_factory(node))
        elif node["type"] == "end":
            builder.add_node(name, end_node)

    # 普通边（不含带 sourceHandle 的分支边）
    for edge in graph_spec["edges"]:
        if edge.get("source_handle"):
            continue
        source_runtime = next(
            node["runtime_name"] for node in graph_spec["nodes"] if node["id"] == edge["source"]
        )
        target_runtime = next(
            node["runtime_name"] for node in graph_spec["nodes"] if node["id"] == edge["target"]
        )
        builder.add_edge(source_runtime, target_runtime)

    # 条件边
    for conditional in graph_spec["conditional_edges"]:
        path_map = {}
        for branch in conditional["branches"]:
            path_map[branch["branch_id"]] = branch["target_runtime"]
        path_map["default"] = next(
            node["runtime_name"] for node in graph_spec["nodes"]
            if node["id"] == conditional["default_target"]
        )
        builder.add_conditional_edges(conditional["source_runtime"], route_selector, path_map)

    builder.set_entry_point(graph_spec["entrypoint_runtime"])
    builder.set_finish_point(graph_spec["finish_runtime"])
    return builder.compile()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("workflow", nargs="?", default="rencai-huaxiang-20260914150500.langgraph.json")
    args = parser.parse_args()

    with open(args.workflow, "r", encoding="utf-8") as f:
        document = json.load(f)
    validate_semantics(document)

    calls = []

    def fake_llm(system_prompt, user_prompt):
        calls.append({"system": system_prompt, "user": user_prompt})
        if "课程架构师" in system_prompt:
            return "SUBJECT_SELECTOR_RESULT"
        if "技术面试出题专家" in system_prompt:
            return "QUESTION_GENERATOR_RESULT"
        if "错题知识点诊断专家" in system_prompt:
            return "WRONG_ANSWER_DIAGNOSER_RESULT"
        return "UNKNOWN"

    app = build_graph(document, fake_llm)

    cases = [
        ("[0]后端开发工程师", "SUBJECT_SELECTOR_RESULT"),
        ("[1]Java开发工程师", "QUESTION_GENERATOR_RESULT"),
        ("[2]错题诊断", "WRONG_ANSWER_DIAGNOSER_RESULT"),
        ("无前缀输入", ""),
    ]
    for user_input, expected in cases:
        calls.clear()
        result = app.invoke(
            {"AGENT_USER_INPUT": user_input},
            config={"recursion_limit": document["execution"]["recursion_limit"]},
        )
        actual = result.get(document["execution"]["output_key"], "")
        if actual != expected:
            raise AssertionError(
                "输入 %r 期望 %r，实际 %r" % (user_input, expected, actual)
            )
        print("PASS input=%r output=%r llm_calls=%d" % (user_input, actual, len(calls)))
        if expected:
            if len(calls) != 1:
                raise AssertionError("应恰好调用一次 LLM，实际 %d" % len(calls))
            expected_user = user_input[3:]
            if calls[0]["user"] != expected_user:
                raise AssertionError(
                    "LLM 用户输入应为 %r，实际 %r" % (expected_user, calls[0]["user"])
                )

    print("PASS: LangGraph StateGraph 构建、条件分支、LLM 调用、输出聚合全部通过")


if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        print("FAIL: %s: %s" % (type(error).__name__, error))
        sys.exit(1)
