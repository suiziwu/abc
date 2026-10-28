# -*- coding: utf-8 -*-
"""人才培养方案智能数据工作流。

输入：专业群 / 区域 / 面向岗位 + 材料（官方报告、新闻、招聘平台数据、岗位样本）
输出：直接可渲染的数据表（dict / list），不落盘、不写死内容。

流水线：
    材料分片 → 并行抽取（岗位 / 技能 / 指标 / 趋势）
             → 合并去重、统计热度
             → 技能能力域归类（模型）
             → 生成预警与培养建议（模型，强制引用来源编号）
             → 引用校验 + 自动修复
             → 组装数据表 / 图表数据 / KPI

每一步的提示词都放在 config.json 里，改提示词不需要动代码。
"""
import json
import os
import re
import time
from concurrent.futures import ThreadPoolExecutor, as_completed
from difflib import SequenceMatcher

from .client import LLMClient, LLMError, LLMConfigError

# 证据强度：数字越大越可信
EVIDENCE_RANK = {"官方": 4, "国家职业标准": 4, "媒体": 3, "行业": 2, "招聘平台": 2, "企业": 1, "其他": 1}

SKILL_DOMAIN_ENUM = [
    "视觉检测", "机器人", "数控加工", "PLC与自动化", "数字孪生",
    "工业数据", "工业软件", "安全与规范", "通用素养", "其他",
]


class WorkflowError(RuntimeError):
    """工作流无法继续（输入不合法、全部片段抽取失败等）。"""


def _default_config():
    return {
        "api": {
            "provider": "deepseek",
            "base_url": "https://api.deepseek.com",
            "model": "deepseek-chat",
            "api_key": "",
            "api_key_env": "DEEPSEEK_API_KEY",
            "timeout": 120,
            "max_retries": 3,
            "temperature": 0.2,
            "max_tokens": 4096,
            "json_mode": True,
        },
        "workflow": {
            "max_workers": 4,
            "chunk_chars": 1000,
            "chunk_overlap": 80,
            "max_chunks": 24,
            "max_repair_rounds": 1,
            "evidence_min_similarity": 0.55,
            "max_skills_for_classify": 60,
        },
        "prompts": {},
    }


def _deep_merge(base, override):
    result = dict(base)
    for key, value in (override or {}).items():
        if isinstance(value, dict) and isinstance(result.get(key), dict):
            result[key] = _deep_merge(result[key], value)
        else:
            result[key] = value
    return result


def load_config(path=None):
    """读取 config.json；路径优先级：入参 > 环境变量 > 包所在目录。"""
    if not path:
        path = os.environ.get("TALENT_WORKFLOW_CONFIG", "").strip()
    if not path:
        path = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "config.json")
    if not os.path.exists(path):
        raise WorkflowError("找不到配置文件：%s" % path)
    with open(path, "r", encoding="utf-8-sig") as f:
        user_cfg = json.load(f)
    cfg = _deep_merge(_default_config(), user_cfg)
    if not cfg["prompts"]:
        raise WorkflowError("config.json 缺少 prompts 配置")
    return cfg


# --------------------------------------------------------------------------- 文本工具
def _fill(template, **values):
    """替换提示词里的 ${中文占位符}。

    不用 string.Template：它默认只识别 ASCII 标识符，${专业群} 这类中文占位符不会被替换。
    """
    text = template
    for key, value in values.items():
        text = text.replace("${%s}" % key, str(value))
    return text


def _clean(value, limit=200):
    if value is None:
        return ""
    text = str(value).replace("\u3000", " ").strip()
    text = re.sub(r"^[\"'“”‘’]+|[\"'“”‘’]+$", "", text)
    text = re.sub(r"\s+", " ", text)
    return text[:limit]


def _norm(text):
    """归一化：去空白与标点、英文小写，用于去重与匹配。"""
    text = str(text or "").lower()
    return re.sub(r"[\s\u3000·・\-—_/\\,，、;；:：()（）\[\]【】\"'“”‘’]+", "", text)


def _as_list(value):
    if isinstance(value, list):
        return value
    if value in (None, "", {}):
        return []
    return [value]


def _to_number(value):
    """把 "1000"、"1,000"、"1000万"、"41.4%" 统一成 float。"""
    if isinstance(value, bool):
        return None
    if isinstance(value, (int, float)):
        return float(value)
    text = str(value or "").replace(",", "").replace("，", "")
    match = re.search(r"-?\d+(?:\.\d+)?", text)
    return float(match.group()) if match else None


def _fmt_number(value):
    if value is None:
        return ""
    if abs(value - round(value)) < 1e-9:
        return str(int(round(value)))
    return ("%.2f" % value).rstrip("0").rstrip(".")


def _format_placeholders(valid_ids, chunk_source):
    """把模型写的来源编号纠正到本片段的真实来源。"""
    sid = _clean(chunk_source)
    return sid if sid in valid_ids else chunk_source


def _similarity(needle, haystack):
    """证据是否真的出自原文：完全包含记 1，否则看最长公共片段覆盖率。"""
    a = _norm(needle)
    b = _norm(haystack)
    if not a or not b:
        return 0.0
    if a in b:
        return 1.0
    match = SequenceMatcher(None, a, b, autojunk=False).find_longest_match(0, len(a), 0, len(b))
    return match.size / float(len(a))


# --------------------------------------------------------------------------- 输入
def _prepare_payload(payload):
    if not isinstance(payload, dict):
        raise WorkflowError("输入必须是 JSON 对象")
    sources = []
    for idx, raw in enumerate(_as_list(payload.get("数据源") or payload.get("材料"))):
        if not isinstance(raw, dict):
            continue
        text = _clean(raw.get("正文") or raw.get("内容") or raw.get("text"), limit=20000)
        if not text:
            continue
        sources.append({
            "id": _clean(raw.get("id") or raw.get("编号") or ("S%d" % (idx + 1)), 12),
            "标题": _clean(raw.get("标题") or raw.get("title"), 120),
            "来源": _clean(raw.get("来源") or raw.get("机构") or raw.get("source"), 60),
            "类型": _clean(raw.get("类型") or raw.get("type") or "媒体", 12),
            "发布时间": _clean(raw.get("发布时间") or raw.get("时间") or raw.get("date"), 24),
            "正文": text,
        })
    # 重编 id，保证唯一
    seen = {}
    for idx, item in enumerate(sources):
        base = item["id"] or ("S%d" % (idx + 1))
        sid = base
        n = 2
        while sid in seen:
            sid = "%s-%d" % (base, n)
            n += 1
        seen[sid] = True
        item["id"] = sid

    samples = []
    for raw in _as_list(payload.get("岗位样本") or payload.get("招聘数据")):
        if isinstance(raw, dict):
            samples.append(raw)

    jobs = [_clean(x, 60) for x in _as_list(payload.get("面向岗位") or payload.get("重点岗位"))]
    jobs = [j for j in jobs if j]

    if not sources and not samples:
        raise WorkflowError("输入里既没有「数据源」也没有「岗位样本」，无法生成数据表")

    return {
        "专业群": _clean(payload.get("专业群"), 60) or "未命名专业群",
        "区域": _clean(payload.get("区域"), 40),
        "面向岗位": jobs,
        "数据源": sources,
        "岗位样本": samples,
    }


def _build_chunks(sources, chunk_chars, overlap, max_chunks):
    chunks = []
    for src in sources:
        text = src["正文"]
        step = max(200, chunk_chars - overlap)
        if len(text) <= chunk_chars:
            parts = [text]
        else:
            parts = [text[i:i + chunk_chars] for i in range(0, len(text), step)]
        for idx, part in enumerate(parts):
            if not part.strip():
                continue
            chunks.append({
                "id": "C%02d" % (len(chunks) + 1),
                "source_id": src["id"],
                "text": part.strip(),
                "part": idx + 1,
                "parts": len(parts),
            })
    truncated = 0
    if len(chunks) > max_chunks:
        truncated = len(chunks) - max_chunks
        chunks = chunks[:max_chunks]
    return chunks, truncated


# --------------------------------------------------------------------------- 抽取
def _sanitize_extraction(data, chunk, valid_ids):
    """把模型输出清洗成固定结构，来源编号纠正到本片段。"""
    sid = chunk["source_id"]
    cleaned = {"岗位": [], "技能": [], "指标": [], "趋势": []}

    for rec in _as_list(data.get("岗位")):
        if not isinstance(rec, dict):
            continue
        name = _clean(rec.get("名称") or rec.get("岗位"), 60)
        if not name:
            continue
        cleaned["岗位"].append({
            "名称": name,
            "职业编码": _clean(rec.get("职业编码"), 40),
            "来源": _format_placeholders(valid_ids, rec.get("来源") or sid),
            "证据": _clean(rec.get("证据"), 120),
        })

    for rec in _as_list(data.get("技能")):
        if not isinstance(rec, dict):
            continue
        name = _clean(rec.get("技能") or rec.get("名称"), 40)
        if not name:
            continue
        domain = _clean(rec.get("能力域"), 12)
        cleaned["技能"].append({
            "技能": name,
            "能力域": domain if domain in SKILL_DOMAIN_ENUM else "",
            "关联岗位": [_clean(x, 60) for x in _as_list(rec.get("关联岗位")) if _clean(x, 60)],
            "来源": _format_placeholders(valid_ids, rec.get("来源") or sid),
            "证据": _clean(rec.get("证据"), 120),
        })

    for rec in _as_list(data.get("指标")):
        if not isinstance(rec, dict):
            continue
        value = _to_number(rec.get("数值"))
        if value is None:
            continue
        cleaned["指标"].append({
            "指标": _clean(rec.get("指标") or rec.get("名称"), 40),
            "数值": value,
            "单位": _clean(rec.get("单位"), 12),
            "口径": _clean(rec.get("口径"), 120),
            "年份": _clean(rec.get("年份"), 12),
            "来源": _format_placeholders(valid_ids, rec.get("来源") or sid),
            "证据": _clean(rec.get("证据"), 120),
        })

    for rec in _as_list(data.get("趋势")):
        if not isinstance(rec, dict):
            continue
        value = _to_number(rec.get("数值"))
        if value is None:
            continue
        cleaned["趋势"].append({
            "岗位": _clean(rec.get("岗位") or rec.get("名称"), 60),
            "指标": _clean(rec.get("指标"), 40),
            "数值": value,
            "单位": _clean(rec.get("单位"), 12),
            "统计期": _clean(rec.get("统计期") or rec.get("年份"), 24),
            "来源": _format_placeholders(valid_ids, rec.get("来源") or sid),
            "证据": _clean(rec.get("证据"), 120),
        })

    return cleaned


def _extract_chunk(client, cfg, payload, chunk):
    src = next((s for s in payload["数据源"] if s["id"] == chunk["source_id"]), None)
    header = "来源编号：%s\n机构：%s\n类型：%s\n发布时间：%s\n标题：%s\n" % (
        chunk["source_id"],
        (src or {}).get("来源", ""),
        (src or {}).get("类型", ""),
        (src or {}).get("发布时间", ""),
        (src or {}).get("标题", ""),
    )
    if chunk["parts"] > 1:
        header += "片段：%d/%d\n" % (chunk["part"], chunk["parts"])
    fragment = header + "正文：\n" + chunk["text"]

    prompt = _fill(
        cfg["prompts"]["extract_user"],
        专业群=payload["专业群"],
        区域=payload["区域"] or "不限",
        面向岗位="、".join(payload["面向岗位"]) or "不限",
        片段=fragment,
        技能能力域="/".join(SKILL_DOMAIN_ENUM),
    )
    data = client.chat_json(
        cfg["prompts"]["extract_system"],
        prompt,
        tag="抽取 %s" % chunk["id"],
        temperature=0.0,
    )
    valid_ids = [s["id"] for s in payload["数据源"]] + ["岗位样本"]
    result = _sanitize_extraction(data, chunk, valid_ids)
    result["_chunk"] = chunk["id"]
    result["_source"] = chunk["source_id"]
    return result


def _extract_all(client, cfg, payload, chunks, log):
    results = []
    errors = []
    workers = max(1, min(int(cfg["workflow"]["max_workers"]), len(chunks)))
    if workers == 1:
        for chunk in chunks:
            try:
                results.append(_extract_chunk(client, cfg, payload, chunk))
                log("  抽取片段 %s 完成" % chunk["id"])
            except Exception as exc:  # noqa: BLE001 - 单片段失败不应中断全局
                errors.append("片段 %s 抽取失败：%s" % (chunk["id"], exc))
                log("  片段 %s 抽取失败：%s" % (chunk["id"], exc))
    else:
        with ThreadPoolExecutor(max_workers=workers) as pool:
            futures = {pool.submit(_extract_chunk, client, cfg, payload, c): c for c in chunks}
            for future in as_completed(futures):
                chunk = futures[future]
                try:
                    results.append(future.result())
                    log("  抽取片段 %s 完成" % chunk["id"])
                except Exception as exc:  # noqa: BLE001
                    errors.append("片段 %s 抽取失败：%s" % (chunk["id"], exc))
                    log("  片段 %s 抽取失败：%s" % (chunk["id"], exc))
    results.sort(key=lambda item: item.get("_chunk", ""))
    if not results:
        raise WorkflowError("全部片段抽取失败：%s" % "；".join(errors[:3]))
    return results, errors


# --------------------------------------------------------------------------- 合并
def _merge_signals(signals, payload):
    jobs = {}
    skills = {}
    metrics = []
    trends = []
    metric_seen = set()
    trend_seen = set()

    def add_job(name, code, sid, evidence):
        key = _norm(name)
        if not key:
            return
        item = jobs.setdefault(key, {"名称": name, "职业编码": "", "来源": set(), "证据": [], "样本数": 0})
        if code and not item["职业编码"]:
            item["职业编码"] = code
        item["来源"].add(sid)
        if evidence and evidence not in item["证据"]:
            item["证据"].append(evidence)

    def add_skill(name, domain, related, sid, evidence):
        key = _norm(name)
        if not key or len(name) > 24:
            return
        item = skills.setdefault(key, {
            "名称": name, "能力域": "", "岗位": set(), "来源": set(), "证据": [], "次数": 0,
        })
        if domain and not item["能力域"]:
            item["能力域"] = domain
        for job in related:
            if job:
                item["岗位"].add(job)
        item["来源"].add(sid)
        if evidence and evidence not in item["证据"]:
            item["证据"].append(evidence)
        item["次数"] += 1

    for sig in signals:
        sid = sig.get("_source", "")
        for rec in sig.get("岗位", []):
            add_job(rec["名称"], rec.get("职业编码", ""), rec.get("来源") or sid, rec.get("证据", ""))
        for rec in sig.get("技能", []):
            add_skill(rec["技能"], rec.get("能力域", ""), rec.get("关联岗位", []),
                      rec.get("来源") or sid, rec.get("证据", ""))
        for rec in sig.get("指标", []):
            key = (rec["指标"], round(rec["数值"], 4), rec["单位"], rec.get("来源") or sid)
            if key in metric_seen:
                continue
            metric_seen.add(key)
            metrics.append(rec)
        for rec in sig.get("趋势", []):
            key = (rec.get("岗位", ""), rec["指标"], round(rec["数值"], 4), rec["单位"],
                   rec.get("统计期", ""), rec.get("来源") or sid)
            if key in trend_seen:
                continue
            trend_seen.add(key)
            trends.append(rec)

    # 岗位样本：作为招聘平台侧的技能提及，直接参与热度计算
    for row in payload["岗位样本"]:
        title = _clean(row.get("职位名称") or row.get("岗位") or row.get("title"), 60)
        if title:
            add_job(title, _clean(row.get("职业编码"), 40), "岗位样本", "")
        raw_skills = row.get("技能") or row.get("核心技能标签") or row.get("skills") or ""
        if isinstance(raw_skills, list):
            tokens = [_clean(x, 40) for x in raw_skills]
        else:
            tokens = [_clean(x, 40) for x in re.split(r"[,，、;；/|]+", str(raw_skills))]
        for token in tokens:
            if token and token not in ("无", "无明确技能"):
                add_skill(token, "", [title] if title else [], "岗位样本", "")

    return {
        "岗位": list(jobs.values()),
        "技能": list(skills.values()),
        "指标": metrics,
        "趋势": trends,
    }


def _classify_skills(client, cfg, payload, skills, log):
    """给没有能力域的技能补归类；失败不致命，统一标记“未分类”。"""
    pending = [s for s in skills if not s.get("能力域")]
    limit = int(cfg["workflow"].get("max_skills_for_classify") or 60)
    pending = pending[:limit]
    if not pending:
        return {}, []
    names = [s["名称"] for s in pending]
    prompt = _fill(
        cfg["prompts"]["classify_user"],
        专业群=payload["专业群"],
        技能列表="、".join(names),
        技能能力域="/".join(SKILL_DOMAIN_ENUM),
    )
    try:
        data = client.chat_json(cfg["prompts"]["classify_system"], prompt, tag="技能归类", temperature=0.0)
    except Exception as exc:  # noqa: BLE001
        log("  技能归类失败：%s" % exc)
        return {}, ["技能能力域归类失败：%s" % exc]

    mapping = {}
    for rec in _as_list(data.get("分类")):
        if not isinstance(rec, dict):
            continue
        name = _clean(rec.get("技能"), 40)
        domain = _clean(rec.get("能力域"), 12)
        if name and domain in SKILL_DOMAIN_ENUM:
            mapping[_norm(name)] = domain
    return mapping, []


# --------------------------------------------------------------------------- 数据表
def _source_label(ids, src_by_id, with_date=True):
    labels = []
    for sid in ids:
        if sid == "岗位样本":
            labels.append("岗位样本")
            continue
        src = src_by_id.get(sid)
        if not src:
            continue
        date = src.get("发布时间", "")
        label = "%s[%s]" % (src.get("来源") or "来源", sid)
        if with_date and date:
            label += "·" + date
        labels.append(label)
    return "；".join(labels) or "—"


def _evidence_strength(ids, src_by_id):
    best = 0
    label = "其他"
    for sid in ids:
        if sid == "岗位样本":
            rank, name = 2, "招聘平台"
        else:
            src = src_by_id.get(sid)
            if not src:
                continue
            name = src.get("类型") or "其他"
            rank = EVIDENCE_RANK.get(name, 1)
        if rank > best:
            best, label = rank, name
    return label


def _signal_for(name, metrics, trends):
    hits = []
    job_key = _norm(name)
    for item in metrics:
        blob = (item.get("口径", "") + item.get("证据", "") + item.get("指标", ""))
        if name and name in blob:
            value = "%s%s" % (_fmt_number(item["数值"]), item.get("单位", ""))
            hits.append("%s %s" % (item.get("指标") or "指标", value))
    for item in trends:
        job_field = item.get("岗位", "")
        if job_field:
            # 趋势里明确写了岗位：只认同一个岗位，避免一句话列多个岗位时互相串数据
            matched = (_norm(job_field) == job_key or name in job_field or job_field in name)
        else:
            matched = bool(name) and name in item.get("证据", "")
        if not matched:
            continue
        value = "%s%s" % (_fmt_number(item["数值"]), item.get("单位", ""))
        period = ("（%s）" % item["统计期"]) if item.get("统计期") else ""
        hits.append("%s %s%s" % (item.get("指标") or "趋势", value, period))
    unique = []
    for hit in hits:
        if hit not in unique:
            unique.append(hit)
    return "；".join(unique[:3]) or "定性提及，详见来源"


def _build_tables(payload, merged, sources, warnings):
    src_by_id = {s["id"]: s for s in sources}
    metrics = merged["指标"]
    trends = merged["趋势"]

    # 面向岗位必须出现在表里：抽取结果缺失时补“待补录”，页面不会空
    existing = {_norm(j["名称"]) for j in merged["岗位"]}
    for name in payload["面向岗位"]:
        if _norm(name) not in existing:
            merged["岗位"].append({
                "名称": name, "职业编码": "", "来源": set(), "证据": [], "样本数": 0, "补充": True,
            })
            warnings.append("面向岗位「%s」未在材料中抽取到，已标记为待补录" % name)

    job_rows = []
    for job in sorted(merged["岗位"], key=lambda x: (-len(x["来源"]), x["名称"])):
        ids = sorted(job["来源"], key=lambda sid: (sid != "岗位样本", sid))
        job_rows.append({
            "核心岗位": job["名称"],
            "职业编码/依据": job.get("职业编码") or "—",
            "需求信号": _signal_for(job["名称"], metrics, trends),
            "来源与统计时间": _source_label(ids, src_by_id),
            "证据强度": _evidence_strength(ids, src_by_id),
            "证据原文": (job["证据"][0] if job["证据"] else "—"),
        })

    skill_rows = []
    if merged["技能"]:
        max_count = max(s["次数"] for s in merged["技能"]) or 1
        for item in sorted(merged["技能"], key=lambda x: (-x["次数"], x["名称"]))[:40]:
            ids = sorted(item["来源"], key=lambda sid: (sid != "岗位样本", sid))
            skill_rows.append({
                "技能": item["名称"],
                "能力域": item.get("能力域") or "未分类",
                "提及次数": item["次数"],
                "关联岗位": "、".join(sorted(item["岗位"])[:4]) or "—",
                "热度指数": int(round(100.0 * item["次数"] / max_count)),
                "证据来源": _source_label(ids, src_by_id, with_date=False),
            })

    # KPI
    gap_items = [m for m in metrics if "万" in (m.get("单位") or "")]
    official = [s for s in sources if (s.get("类型") or "") in ("官方", "国家职业标准")]
    kpi = [
        {"指标": "核心岗位", "数值": len(job_rows), "单位": "个", "口径": "材料识别 + 专业群指定"},
        {"指标": "技能项", "数值": len(merged["技能"]), "单位": "项", "口径": "材料与岗位样本归并后"},
        {"指标": "数据来源", "数值": len(sources) + (1 if payload["岗位样本"] else 0), "单位": "条",
         "口径": "官方/媒体/行业/招聘平台"},
        {"指标": "官方来源", "数值": len(official), "单位": "条", "口径": "人社部、工信部、国家职业标准等"},
    ]
    if gap_items:
        top = max(gap_items, key=lambda x: x["数值"])
        kpi.append({
            "指标": "最大人才缺口", "数值": int(top["数值"]) if top["数值"] >= 10 else top["数值"],
            "单位": top.get("单位") or "", "口径": top.get("口径") or top.get("指标") or "",
        })
    else:
        kpi.append({"指标": "最大人才缺口", "数值": "—", "单位": "", "口径": "材料中未给出量化缺口"})

    charts = {
        "人才缺口": [{
            "方向": m.get("口径") or m.get("指标") or "",
            "缺口": m["数值"], "单位": m.get("单位") or "", "年份": m.get("年份") or "",
            "来源": m.get("来源") or "", "证据强度": _evidence_strength([m.get("来源")], src_by_id),
        } for m in gap_items],
        "招聘增速": [{
            "岗位": t.get("岗位") or "", "指标": t.get("指标") or "同比",
            "增速": t["数值"], "统计期": t.get("统计期") or "",
            "来源": t.get("来源") or "",
        } for t in trends if (t.get("单位") or "") == "%"],
        "需求指数": [{
            "岗位": t.get("岗位") or "", "指数": t["数值"], "来源": t.get("来源") or "",
        } for t in trends if "指数" in (t.get("指标") or "")],
    }

    return {
        "岗位需求表": {"columns": ["核心岗位", "职业编码/依据", "需求信号", "来源与统计时间", "证据强度", "证据原文"],
                  "rows": job_rows},
        "技能热度表": {"columns": ["技能", "能力域", "提及次数", "关联岗位", "热度指数", "证据来源"],
                  "rows": skill_rows},
    }, kpi, charts


# --------------------------------------------------------------------------- 建议
def _compact_for_advice(tables, merged, kpi, limit_skills=15):
    return {
        "KPI": kpi,
        "岗位需求表": tables["岗位需求表"]["rows"][:10],
        "技能热度表": tables["技能热度表"]["rows"][:limit_skills],
        "量化指标": merged["指标"][:12],
        "招聘趋势": merged["趋势"][:12],
    }


def _source_brief(sources, samples=None):
    brief = [{
        "编号": s["id"],
        "机构": s.get("来源") or "",
        "类型": s.get("类型") or "",
        "时间": s.get("发布时间") or "",
        "标题": s.get("标题") or "",
        "摘要": s["正文"][:120],
    } for s in sources]
    if samples:
        brief.append({
            "编号": "岗位样本",
            "机构": "招聘平台岗位样本",
            "类型": "招聘平台",
            "时间": "",
            "标题": "用户录入的岗位样本（%d 条）" % len(samples),
            "摘要": "、".join(_clean(x.get("职位名称") or x.get("岗位"), 30) for x in samples[:8]),
        })
    return brief


def _source_index(sources, samples):
    index = {s["id"]: s for s in sources}
    if samples:
        index["岗位样本"] = {
            "id": "岗位样本",
            "标题": "用户录入的岗位样本",
            "来源": "招聘平台",
            "类型": "招聘平台",
            "发布时间": "",
            "正文": json.dumps(samples, ensure_ascii=False),
        }
    return index


def _advise(client, cfg, payload, tables, merged, kpi, sources, log):
    prompt = _fill(
        cfg["prompts"]["advise_user"],
        专业群=payload["专业群"],
        区域=payload["区域"] or "不限",
        面向岗位="、".join(payload["面向岗位"]) or "不限",
        统计=json.dumps(_compact_for_advice(tables, merged, kpi), ensure_ascii=False, indent=1),
        来源=json.dumps(_source_brief(sources, payload["岗位样本"]), ensure_ascii=False, indent=1),
    )
    data = client.chat_json(cfg["prompts"]["advise_system"], prompt, tag="建议生成", temperature=0.3)
    return _sanitize_advice(data)


def _sanitize_advice(data):
    advice = {"预警": [], "建议": [], "数据缺口": []}

    for rec in _as_list(data.get("预警")):
        if not isinstance(rec, dict):
            continue
        level = _clean(rec.get("等级"), 6)
        if level not in ("紧急", "关注", "提示", "良好"):
            level = "提示"
        advice["预警"].append({
            "等级": level,
            "标题": _clean(rec.get("标题"), 60),
            "关键指标": _clean(rec.get("关键指标"), 120),
            "建议动作": _clean(rec.get("建议动作"), 200),
            "依据": [str(x) for x in _as_list(rec.get("依据")) if str(x).strip()],
            "证据": _clean(rec.get("证据"), 160),
        })

    for idx, rec in enumerate(_as_list(data.get("建议")), 1):
        if not isinstance(rec, dict):
            continue
        level = _clean(rec.get("优先级"), 4)
        if level not in ("高", "中", "低"):
            level = "中"
        advice["建议"].append({
            "序号": idx,
            "方向": _clean(rec.get("方向"), 20) or "课程内容",
            "建议内容": _clean(rec.get("建议内容"), 300),
            "依据": [str(x) for x in _as_list(rec.get("依据")) if str(x).strip()],
            "优先级": level,
            "预期效果": _clean(rec.get("预期效果"), 120),
        })

    for rec in _as_list(data.get("数据缺口")):
        if not isinstance(rec, dict):
            continue
        advice["数据缺口"].append({
            "缺口项": _clean(rec.get("缺口项"), 40),
            "说明": _clean(rec.get("说明"), 160),
            "影响": _clean(rec.get("影响"), 120),
        })

    return advice


def _validate_advice(advice, src_by_id, min_similarity):
    problems = []
    checked = 0

    for idx, rec in enumerate(advice.get("预警", [])):
        checked += 1
        ids = rec.get("依据") or []
        label = "预警[%s]" % (rec.get("标题") or idx + 1)
        if not ids:
            problems.append("%s 未标注依据来源" % label)
            continue
        unknown = [i for i in ids if i not in src_by_id]
        if unknown:
            problems.append("%s 引用了不存在的来源 %s" % (label, "、".join(unknown)))
        cited = [src_by_id[i] for i in ids if i in src_by_id]
        if cited and rec.get("证据"):
            best = max(_similarity(rec["证据"], src["正文"]) for src in cited)
            if best < min_similarity:
                problems.append("%s 的“证据”与引用来源原文不匹配（相似度 %.2f）" % (label, best))
        # 预警是事实陈述：其中出现的数字必须能在引用来源里找到
        text = rec.get("关键指标") or ""
        if cited:
            corpus = "".join(src["正文"] for src in cited)
            for num in re.findall(r"\d+(?:\.\d+)?", text):
                if num not in corpus.replace(",", ""):
                    problems.append("%s 的“关键指标”中的数字 %s 未在引用来源中找到" % (label, num))

    for idx, rec in enumerate(advice.get("建议", [])):
        checked += 1
        ids = rec.get("依据") or []
        label = "建议[%d]" % (rec.get("序号") or idx + 1)
        if not ids:
            problems.append("%s 未标注依据来源" % label)
        unknown = [i for i in ids if i not in src_by_id]
        if unknown:
            problems.append("%s 引用了不存在的来源 %s" % (label, "、".join(unknown)))
        if len(rec.get("建议内容") or "") < 15:
            problems.append("%s 的建议内容过于笼统" % label)

    if checked == 0:
        problems.append("没有生成任何预警或培养建议")
    return {
        "问题": problems,
        "检查条数": checked,
        "通过": (not problems) and checked > 0,
    }


def _repair(client, cfg, advice, problems, sources, log, samples=None):
    prompt = _fill(
        cfg["prompts"]["repair_user"],
        上一次输出=json.dumps(advice, ensure_ascii=False, indent=1),
        错误="\n".join("- " + p for p in problems),
        来源=json.dumps(_source_brief(sources, samples), ensure_ascii=False, indent=1),
    )
    data = client.chat_json(cfg["prompts"]["repair_system"], prompt, tag="校验修复", temperature=0.0)
    return _sanitize_advice(data)


# --------------------------------------------------------------------------- 主入口
def generate(payload, config_path=None, config=None, client=None, logger=None):
    """执行完整工作流，返回可直接渲染的数据表结构（不读写任何文件）。

    参数：
        payload: dict，输入数据（见 README / 示例输入.json）
        config_path: 可选，config.json 路径
        config: 可选，已加载的配置 dict（优先级高于 config_path）
        client: 可选，自定义 LLM 客户端（测试时可注入假客户端）
        logger: 可选，进度回调 fn(message)
    返回：
        dict，包含 meta / kpi / tables / charts / sources / validation / warnings
    """
    log = logger or (lambda msg: None)
    started = time.time()
    cfg = config or load_config(config_path)
    payload = _prepare_payload(payload)

    if client is None:
        client = LLMClient(cfg["api"], logger=log)

    warnings = []
    chunks, truncated = _build_chunks(
        payload["数据源"],
        int(cfg["workflow"]["chunk_chars"]),
        int(cfg["workflow"]["chunk_overlap"]),
        int(cfg["workflow"]["max_chunks"]),
    )
    if truncated:
        warnings.append("材料过长，已截断 %d 个片段（只处理前 %d 片）" % (truncated, len(chunks)))
    log("材料分片：%d 片" % len(chunks))

    signals, extract_errors = _extract_all(client, cfg, payload, chunks, log) if chunks else ([], [])
    warnings.extend(extract_errors)

    merged = _merge_signals(signals, payload)
    log("抽取结果：岗位 %d、技能 %d、指标 %d、趋势 %d" % (
        len(merged["岗位"]), len(merged["技能"]), len(merged["指标"]), len(merged["趋势"])))

    domain_map, classify_warnings = _classify_skills(client, cfg, payload, merged["技能"], log)
    warnings.extend(classify_warnings)
    for item in merged["技能"]:
        if not item.get("能力域") and _norm(item["名称"]) in domain_map:
            item["能力域"] = domain_map[_norm(item["名称"])]

    tables, kpi, charts = _build_tables(payload, merged, payload["数据源"], warnings)

    # 建议生成失败时降级：页面仍能拿到统计表，只是没有智能建议
    validation = {"问题": [], "检查条数": 0, "通过": False, "轮次": 0}
    src_index = _source_index(payload["数据源"], payload["岗位样本"])
    try:
        advice = _advise(client, cfg, payload, tables, merged, kpi, payload["数据源"], log)
        max_rounds = int(cfg["workflow"].get("max_repair_rounds") or 0)
        for round_no in range(max_rounds + 1):
            validation = _validate_advice(advice, src_index,
                                          float(cfg["workflow"]["evidence_min_similarity"]))
            validation["轮次"] = round_no + 1
            if validation["通过"] or round_no >= max_rounds:
                break
            log("校验发现 %d 个问题，自动修复一轮" % len(validation["问题"]))
            advice = _repair(client, cfg, advice, validation["问题"], payload["数据源"], log,
                             payload["岗位样本"])
    except (LLMError, LLMConfigError) as exc:
        warnings.append("智能建议生成失败，已降级为仅统计数据：%s" % exc)
        advice = {"预警": [], "建议": [], "数据缺口": []}

    tables["预警表"] = {
        "columns": ["等级", "标题", "关键指标", "建议动作", "依据", "证据"],
        "rows": [{
            "等级": r["等级"], "标题": r["标题"], "关键指标": r["关键指标"],
            "建议动作": r["建议动作"], "依据": "、".join(r["依据"]), "证据": r["证据"],
        } for r in advice["预警"]],
    }
    tables["培养建议表"] = {
        "columns": ["序号", "方向", "建议内容", "依据", "优先级", "预期效果"],
        "rows": [{
            "序号": r["序号"], "方向": r["方向"], "建议内容": r["建议内容"],
            "依据": "、".join(r["依据"]), "优先级": r["优先级"], "预期效果": r["预期效果"],
        } for r in advice["建议"]],
    }
    tables["数据缺口表"] = {
        "columns": ["缺口项", "说明", "影响"],
        "rows": advice["数据缺口"],
    }

    if not advice["建议"]:
        warnings.append("未生成培养建议：请检查材料是否包含足够的岗位与技能信息")

    stats = getattr(client, "stats", {})
    result = {
        "meta": {
            "生成时间": time.strftime("%Y-%m-%d %H:%M:%S"),
            "专业群": payload["专业群"],
            "区域": payload["区域"] or "不限",
            "模型": cfg["api"].get("model"),
            "耗时秒": round(time.time() - started, 1),
            "片段数": len(chunks),
            "数据源数": len(payload["数据源"]),
            "岗位样本数": len(payload["岗位样本"]),
            "调用统计": stats,
            "校验": validation,
        },
        "kpi": kpi,
        "tables": tables,
        "charts": charts,
        "sources": [{
            "id": s["id"], "标题": s["标题"], "来源": s["来源"],
            "类型": s["类型"], "发布时间": s["发布时间"],
        } for s in payload["数据源"]],
        "validation": validation,
        "warnings": warnings,
    }
    return result
