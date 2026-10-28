# -*- coding: utf-8 -*-
"""把工作流返回的数据表渲染成终端文本（也用于命令行预览）。

只做展示，不参与业务计算；页面侧直接消费 workflow.generate() 返回的 dict 即可。
"""
import unicodedata


def _width(text):
    """中文按 2 个字符宽计算。"""
    width = 0
    for char in str(text):
        width += 2 if unicodedata.east_asian_width(char) in ("F", "W") else 1
    return width


def _clip(text, limit):
    text = str(text).replace("\n", " ").replace("\r", " ")
    if _width(text) <= limit:
        return text
    out = ""
    width = 0
    for char in text:
        w = 2 if unicodedata.east_asian_width(char) in ("F", "W") else 1
        if width + w > limit - 1:
            break
        out += char
        width += w
    return out + "…"


def _pad(text, width, align="left"):
    space = max(0, width - _width(text))
    if align == "right":
        return " " * space + text
    return text + " " * space


def render_table(columns, rows, max_col_width=32):
    """返回可直接 print 的表格文本；columns 是列名列表，rows 是 dict 列表。"""
    if not rows:
        return "（暂无数据）"
    columns = list(columns)
    cells = []
    for row in rows:
        cells.append([_clip(row.get(col, ""), max_col_width) for col in columns])
    widths = []
    for idx, col in enumerate(columns):
        widths.append(max([_width(col)] + [_width(line[idx]) for line in cells]))

    sep = "+" + "+".join("-" * (w + 2) for w in widths) + "+"
    lines = [sep, "| " + " | ".join(_pad(col, widths[i]) for i, col in enumerate(columns)) + " |", sep]
    for line in cells:
        lines.append("| " + " | ".join(_pad(line[i], widths[i]) for i in range(len(columns))) + " |")
    lines.append(sep)
    return "\n".join(lines)


def print_report(result, out=None):
    """打印 KPI、全部数据表、图表数据、校验结果与告警。"""
    write = (out or print)
    meta = result.get("meta", {})

    write("=" * 78)
    write("人才培养方案智能数据 | %s" % meta.get("专业群", ""))
    write("区域：%s    模型：%s    耗时：%ss    片段：%s" % (
        meta.get("区域", ""), meta.get("模型", ""), meta.get("耗时秒", ""), meta.get("片段数", "")))
    write("生成时间：%s" % meta.get("生成时间", ""))
    validation = result.get("validation") or {}
    write("引用校验：%s（检查 %s 条，修复轮次 %s）" % (
        "通过" if validation.get("通过") else "存在待核项",
        validation.get("检查条数", 0), validation.get("轮次", 0)))
    write("=" * 78)

    write("\n【核心指标】")
    write(render_table(["指标", "数值", "单位", "口径"], result.get("kpi", [])))

    for name, table in (result.get("tables") or {}).items():
        write("\n【%s】" % name)
        write(render_table(table.get("columns", []), table.get("rows", [])))

    charts = result.get("charts") or {}
    for name, rows in charts.items():
        if not rows:
            continue
        write("\n【图表数据 · %s】" % name)
        write(render_table(list(rows[0].keys()), rows))

    sources = result.get("sources") or []
    if sources:
        write("\n【来源清单】")
        write(render_table(["id", "来源", "类型", "发布时间", "标题"], sources))

    problems = validation.get("问题") or []
    if problems:
        write("\n【校验未通过项】")
        for item in problems:
            write("  - " + item)

    warnings = result.get("warnings") or []
    if warnings:
        write("\n【告警】")
        for item in warnings:
            write("  ! " + item)
