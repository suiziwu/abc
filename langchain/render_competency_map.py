#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
把岗位能力图谱 JSON 渲染为自包含的可视化页面（思维导图 + 雷达图 + 能力明细）。

用法：
    python render_competency_map.py output/岗位能力图谱-Java后端开发工程师.json ../../岗位能力图谱-Java后端开发工程师.html
"""
import argparse
import json
import os

TEMPLATE = """<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>岗位能力图谱 · {title}</title>
<script src="https://cdn.jsdelivr.net/npm/echarts@5.5.0/dist/echarts.min.js"></script>
<style>
  :root {{
    --bg: #f4f7fb;
    --panel: #ffffff;
    --ink: #16233a;
    --muted: #6b7a90;
    --line: #e3eaf3;
    --brand: #1f6feb;
    --brand-soft: #e8f1ff;
    --accent: #12b886;
    --warn: #f08c00;
  }}
  * {{ box-sizing: border-box; }}
  body {{
    margin: 0;
    background: var(--bg);
    color: var(--ink);
    font-family: "Microsoft YaHei", "PingFang SC", "Noto Sans SC", system-ui, sans-serif;
  }}
  .wrap {{ max-width: 1360px; margin: 0 auto; padding: 28px 24px 56px; }}
  header {{
    background: linear-gradient(135deg, #0f2a52 0%, #1f4f8f 58%, #1f6feb 100%);
    border-radius: 18px;
    color: #fff;
    padding: 26px 30px;
    box-shadow: 0 12px 28px rgba(15, 42, 82, 0.18);
  }}
  header h1 {{ margin: 0 0 6px; font-size: 28px; letter-spacing: 1px; }}
  header .summary {{ margin: 10px 0 0; color: #d6e4ff; font-size: 14px; line-height: 1.7; max-width: 900px; }}
  .meta {{ display: flex; flex-wrap: wrap; gap: 10px; margin-top: 16px; }}
  .chip {{
    background: rgba(255, 255, 255, 0.14);
    border: 1px solid rgba(255, 255, 255, 0.22);
    border-radius: 999px;
    padding: 5px 13px;
    font-size: 12.5px;
    color: #eaf2ff;
  }}
  .chain {{ display: flex; flex-wrap: wrap; align-items: center; gap: 8px; margin-top: 16px; }}
  .chain .step {{
    background: #fff; color: #1f4f8f; border-radius: 8px; padding: 7px 13px;
    font-size: 13px; font-weight: 600; box-shadow: 0 2px 6px rgba(15, 42, 82, 0.12);
  }}
  .chain .arrow {{ color: #b9d2ff; font-size: 15px; }}
  .stats {{ display: grid; grid-template-columns: repeat(6, 1fr); gap: 12px; margin: 18px 0 6px; }}
  .stat {{
    background: var(--panel); border: 1px solid var(--line); border-radius: 12px;
    padding: 14px 16px; box-shadow: 0 4px 12px rgba(22, 35, 58, 0.05);
  }}
  .stat b {{ display: block; font-size: 23px; color: var(--brand); }}
  .stat span {{ color: var(--muted); font-size: 12.5px; }}
  .panel {{
    background: var(--panel); border: 1px solid var(--line); border-radius: 16px;
    padding: 18px 20px 22px; margin-top: 18px; box-shadow: 0 4px 14px rgba(22, 35, 58, 0.05);
  }}
  .panel h2 {{ margin: 2px 0 4px; font-size: 17px; }}
  .panel p.hint {{ margin: 0 0 10px; color: var(--muted); font-size: 12.5px; }}
  .tabs {{ display: flex; gap: 8px; margin: 20px 0 0; }}
  .tab {{
    border: 1px solid var(--line); background: #fff; color: var(--muted);
    border-radius: 999px; padding: 8px 18px; cursor: pointer; font-size: 13.5px;
  }}
  .tab.active {{ background: var(--brand); border-color: var(--brand); color: #fff; font-weight: 600; }}
  .view {{ display: none; }}
  .view.active {{ display: block; }}
  #mindmap {{ width: 100%; height: 720px; }}
  #radar {{ width: 100%; height: 520px; }}
  table {{ width: 100%; border-collapse: collapse; font-size: 13px; }}
  th, td {{ border-bottom: 1px solid var(--line); padding: 11px 10px; vertical-align: top; text-align: left; }}
  th {{ background: #f7faff; color: #42536b; font-weight: 600; white-space: nowrap; }}
  td.unit {{ line-height: 1.75; }}
  .tag {{
    display: inline-block; margin: 2px 4px 2px 0; padding: 1px 8px; border-radius: 999px;
    font-size: 12px; border: 1px solid transparent;
  }}
  .tag.skill {{ background: #eef7f2; color: #0f7b5f; border-color: #cdeadd; }}
  .tag.kp {{ background: #eef3ff; color: #1f4f8f; border-color: #d6e3ff; }}
  .tag.kp.missing {{ background: #fff6e8; color: #b96a00; border-color: #ffe2b8; }}
  .bar {{ height: 7px; background: #eaf0f8; border-radius: 999px; overflow: hidden; min-width: 90px; }}
  .bar i {{ display: block; height: 100%; background: linear-gradient(90deg, #5b9bff, #1f6feb); }}
  .src-list {{ margin: 8px 0 10px; padding-left: 20px; font-size: 13px; line-height: 2; color: #35435a; }}
  .src-list a {{ color: #1f6feb; text-decoration: none; word-break: break-all; }}
  .src-list a:hover {{ text-decoration: underline; }}
  .weight {{ display: flex; align-items: center; gap: 8px; }}
  footer {{ margin-top: 26px; color: var(--muted); font-size: 12px; line-height: 1.8; }}
  @media (max-width: 980px) {{
    .stats {{ grid-template-columns: repeat(3, 1fr); }}
    #mindmap {{ height: 620px; }}
  }}
</style>
</head>
<body>
<div class="wrap">
  <header>
    <h1>{title} · 岗位能力图谱</h1>
    <p class="summary">{summary}</p>
    <div class="meta">
      <span class="chip">职业编码：{code}</span>
      <span class="chip">匹配方式：{matched_by}</span>
      <span class="chip">生成置信度：{confidence}</span>
      <span class="chip">工作流：{workflow_name}</span>
    </div>
    <div class="chain" id="chain"></div>
  </header>

  <div class="stats" id="stats"></div>

  <section class="panel">
    <h2>数据来源与口径</h2>
    <ul class="src-list" id="sources"></ul>
    <p class="hint" id="data-note"></p>
  </section>

  <div class="tabs">
    <button class="tab active" data-view="mindmap-view">思维导图</button>
    <button class="tab" data-view="radar-view">能力雷达图</button>
    <button class="tab" data-view="detail-view">能力明细</button>
  </div>

  <section class="panel view active" id="mindmap-view">
    <h2>胜任该岗位需掌握什么</h2>
    <p class="hint">按“产业链定位 → 能力域 → 能力项 → 能力单元 → 技能点/知识点”展开，可拖拽、缩放、点击折叠。</p>
    <div id="mindmap"></div>
  </section>

  <section class="panel view" id="radar-view">
    <h2>能力结构雷达图</h2>
    <p class="hint">“标准技能要求权重”直接取自国家职业标准的技能要求权重表；“课程库自动匹配度”为该能力域知识点与项目课程库做名称相似度匹配（阈值 0.6）后的占比，是可复现的计算值，不代表人工认定的课程对应关系。</p>
    <div id="radar"></div>
  </section>

  <section class="panel view" id="detail-view">
    <h2>能力明细清单</h2>
    <p class="hint">蓝色知识点表示与项目课程库知识点自动匹配成功（名称相似度 ≥ 0.6），橙色表示未匹配；该匹配仅为文本相似度结果，需人工复核。</p>
    <table>
      <thead>
        <tr><th>能力项</th><th>能力域</th><th>权重</th><th>能力单元</th><th>技能点</th><th>知识点</th></tr>
      </thead>
      <tbody id="detail-body"></tbody>
    </table>
  </section>

  <footer id="footer"></footer>
</div>

<script>
const DATA = {payload};

function el(tag, className, text) {{
  const node = document.createElement(tag);
  if (className) node.className = className;
  if (text !== undefined) node.textContent = text;
  return node;
}}

// 逻辑链
(() => {{
  const box = document.getElementById("chain");
  DATA.classification_path.forEach((item, index) => {{
    if (index) box.appendChild(el("span", "arrow", "→"));
    const step = el("span", "step");
    step.textContent = item.level + "：" + item.name;
    box.appendChild(step);
  }});
}})();

// 统计
(() => {{
  const c = DATA.coverage || {{}};
  const stats = [
    [c.tasks, "典型工作任务"],
    [c.competencies, "能力项"],
    [c.units, "能力单元"],
    [c.skills, "技能点"],
    [c.knowledge, "知识点"],
    [c.knowledge_coverage + "%", "课程库匹配率"],
  ];
  const box = document.getElementById("stats");
  stats.forEach(([value, label]) => {{
    const card = el("div", "stat");
    card.appendChild(el("b", "", value));
    card.appendChild(el("span", "", label));
    box.appendChild(card);
  }});
}})();

// 数据来源
(() => {{
  const box = document.getElementById("sources");
  (DATA.sources || []).forEach(src => {{
    const li = document.createElement("li");
    li.innerHTML = "<b>" + src.name + "</b>（" + (src.version || "") + "，检索日期 " + (src.retrieved_at || "") +
      "）<br><a href='" + src.url + "' target='_blank' rel='noopener'>" + src.url + "</a>";
    box.appendChild(li);
  }});
  document.getElementById("data-note").textContent =
    (DATA.job.level_note || "") + " " + (DATA.data_note || "");
}})();

// 标签切换
document.querySelectorAll(".tab").forEach(tab => {{
  tab.addEventListener("click", () => {{
    document.querySelectorAll(".tab").forEach(item => item.classList.remove("active"));
    document.querySelectorAll(".view").forEach(item => item.classList.remove("active"));
    tab.classList.add("active");
    document.getElementById(tab.dataset.view).classList.add("active");
    if (tab.dataset.view === "mindmap-view") mindmap.resize();
    if (tab.dataset.view === "radar-view") radar.resize();
  }});
}});

// 思维导图
const mindmap = echarts.init(document.getElementById("mindmap"));
mindmap.setOption({{
  tooltip: {{ trigger: "item", formatter: p => p.name }},
  series: [{{
    type: "tree",
    data: [DATA.views.mindmap],
    orient: "LR",
    layout: "orthogonal",
    initialTreeDepth: 2,
    roam: true,
    symbol: "circle",
    symbolSize: 9,
    edgeShape: "curve",
    lineStyle: {{ color: "#c9d8ee", width: 1.4 }},
    itemStyle: {{ color: "#1f6feb", borderColor: "#fff", borderWidth: 1.5 }},
    label: {{
      position: "left",
      verticalAlign: "middle",
      align: "right",
      fontSize: 12,
      color: "#243247",
      backgroundColor: "rgba(255,255,255,0.86)",
      padding: [3, 6],
      borderRadius: 4
    }},
    leaves: {{
      label: {{ position: "right", align: "left", fontSize: 11.5, color: "#4a5b72" }}
    }},
    emphasis: {{ focus: "descendant" }},
    expandAndCollapse: true,
    animationDuration: 500
  }}]
}});

// 雷达图
const radar = echarts.init(document.getElementById("radar"));
radar.setOption({{
  tooltip: {{ trigger: "item" }},
  legend: {{ bottom: 6, itemWidth: 14, textStyle: {{ color: "#42536b" }} }},
  radar: {{
    center: ["50%", "46%"],
    radius: "64%",
    indicator: DATA.views.radar.indicators,
    splitNumber: 4,
    axisName: {{ color: "#42536b", fontSize: 13 }},
    splitLine: {{ lineStyle: {{ color: "#e3eaf3" }} }},
    splitArea: {{ areaStyle: {{ color: ["#fbfdff", "#f4f8fe"] }} }},
    axisLine: {{ lineStyle: {{ color: "#e3eaf3" }} }}
  }},
  series: [{{
    type: "radar",
    symbolSize: 5,
    data: DATA.views.radar.series.map((item, index) => ({{
      name: item.name,
      value: item.values,
      lineStyle: {{ width: 2.4, color: index === 0 ? "#1f6feb" : "#12b886" }},
      itemStyle: {{ color: index === 0 ? "#1f6feb" : "#12b886" }},
      areaStyle: {{ opacity: index === 0 ? 0.22 : 0.12 }}
    }}))
  }}]
}});

// 明细表
(() => {{
  const body = document.getElementById("detail-body");
  DATA.competencies.forEach(item => {{
    const tr = document.createElement("tr");

    const name = document.createElement("td");
    name.innerHTML = "<b>" + item.name + "</b><br><span style='color:#6b7a90;font-size:12px'>" + item.id + "</span>";

    const domain = document.createElement("td");
    domain.textContent = (DATA.domains || []).reduce((name, entry) =>
      entry.id === item.domain_id ? entry.name : name, item.domain_id || "");

    const weight = document.createElement("td");
    const wrap = el("div", "weight");
    const bar = el("div", "bar");
    const fill = el("i");
    fill.style.width = item.weight + "%";
    bar.appendChild(fill);
    wrap.appendChild(bar);
    wrap.appendChild(el("span", "", item.weight));
    weight.appendChild(wrap);

    const units = document.createElement("td");
    units.className = "unit";
    item.units.forEach(unit => {{
      units.appendChild(el("div", "", "· " + unit.statement + "（L" + unit.level + "｜" + (unit.source || "") + "）"));
    }});

    const skills = document.createElement("td");
    item.skills.forEach(skill => skills.appendChild(el("span", "tag skill", skill.name)));

    const knowledge = document.createElement("td");
    item.knowledge.forEach(point => {{
      const tag = el("span", "tag kp" + (point.covered ? "" : " missing"), point.name);
      tag.title = point.covered ? "已覆盖课程：" + point.subject : "课程库暂未覆盖";
      knowledge.appendChild(tag);
    }});

    [name, domain, weight, units, skills, knowledge].forEach(cell => tr.appendChild(cell));
    body.appendChild(tr);
  }});
}})();

document.getElementById("footer").textContent =
  "数据来源：" + (DATA.sources || []).map(item => item.name + "（" + item.version + "）").join("、") +
  "；生成时间：" + (DATA.meta && DATA.meta.generated_at || "") +
  "；引擎：" + (DATA.meta && DATA.meta.engine || "") +
  "（" + (DATA.meta && DATA.meta.workflow_steps || 0) + " 节点，" + (DATA.meta && DATA.meta.mode || "kb") + " 模式）";

window.addEventListener("resize", () => {{ mindmap.resize(); radar.resize(); }});
</script>
</body>
</html>
"""


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("graph")
    parser.add_argument("output")
    args = parser.parse_args()

    with open(args.graph, "r", encoding="utf-8") as f:
        payload = json.load(f)

    html = TEMPLATE.format(
        title=payload["job"]["name"],
        summary=payload["job"].get("summary", ""),
        code=payload["job"].get("code", ""),
        matched_by=payload["job"].get("matched_by", ""),
        confidence=payload["job"].get("confidence", ""),
        workflow_name=(payload.get("meta") or {}).get("workflow_file", ""),
        payload=json.dumps(payload, ensure_ascii=False, separators=(",", ":")),
    )
    directory = os.path.dirname(args.output)
    if directory and not os.path.isdir(directory):
        os.makedirs(directory)
    with open(args.output, "w", encoding="utf-8", newline="\n") as f:
        f.write(html)
    print("rendered: %s (%d KB)" % (args.output, round(os.path.getsize(args.output) / 1024.0, 1)))


if __name__ == "__main__":
    main()
