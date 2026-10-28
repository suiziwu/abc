# -*- coding: utf-8 -*-
"""批量下载 759 份国家职业标准 PDF，提取 4.2 技能要求权重表。"""
import json
import os
import re
import sys
import time
import urllib.parse
import urllib.request
from concurrent.futures import ThreadPoolExecutor, as_completed

import pymupdf

BASE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(BASE, "data", "sources", "osta_skill_standards.json")
PDF_DIR = os.path.join(BASE, "data", "standards")
OUT = os.path.join(BASE, "data", "standards", "weights_all.json")
os.makedirs(PDF_DIR, exist_ok=True)

LEVEL_PAT = re.compile(r"(五级|四级|三级|二级|一级)")
SKIP_NAMES = {"技能要求", "技能", "要求", "项目", "技能等级项目", "职业功能",
              "合计", "总计", "小计", "总 计"}


def safe_name(code, name):
    cleaned = re.sub(r"[\\/:*?\"<>|]", "", name)
    return f"{code}_{cleaned}" if code else cleaned


def pdf_target(item):
    return os.path.join(PDF_DIR, f"{item['code']}_{item['id']}.pdf")


def download(item, unique_codes):
    path = pdf_target(item)
    if os.path.exists(path) and os.path.getsize(path) > 20000:
        return path
    legacy = os.path.join(PDF_DIR, item["code"] + ".pdf")
    if unique_codes.get(item["code"]) == 1 and os.path.exists(legacy) and os.path.getsize(legacy) > 20000:
        return legacy
    encoded = urllib.parse.quote(item["standardInfo"], safe="")
    url = "https://www.osta.org.cn/api/sys/downloadFile/decrypt?fileName=" + encoded
    req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
    with urllib.request.urlopen(req, timeout=180) as resp:
        data = resp.read()
    with open(path, "wb") as handle:
        handle.write(data)
    time.sleep(0.2)
    return path


def extract_page(page):
    level_cols = []
    body = []
    for table in page.find_tables().tables:
        rows = table.extract()
        if not rows:
            continue
        header_index = None
        for row_index, row in enumerate(rows[:5]):
            joined = " ".join(cell for cell in row if cell)
            if LEVEL_PAT.search(joined) and ("项目" in joined or "技能" in joined or "职业" in joined):
                header_index = row_index
                break
        if header_index is None:
            for row_index, row in enumerate(rows[:5]):
                joined = " ".join(cell for cell in row if cell)
                if len(LEVEL_PAT.findall(joined)) >= 2:
                    header_index = row_index
                    break
        if header_index is None:
            continue
        header = rows[header_index]
        page_cols = []
        for col_index, cell in enumerate(header):
            if cell and LEVEL_PAT.search(cell):
                page_cols.append((col_index, LEVEL_PAT.search(cell).group(1)))
        if not page_cols:
            continue
        if not level_cols:
            level_cols = page_cols
        for row in rows[header_index + 1:]:
            if not row:
                continue
            cells = [(cell or "").strip() for cell in row]
            item_name = None
            for cell in cells:
                normalized = re.sub(r"\s+", "", cell)
                if not normalized or normalized in SKIP_NAMES:
                    continue
                if re.fullmatch(r"[\d\.]+|—|-", normalized):
                    continue
                item_name = normalized
                break
            if not item_name or item_name in SKIP_NAMES:
                continue
            values = []
            for col_index, _ in page_cols:
                raw = row[col_index] if col_index < len(row) else None
                raw = (raw or "").strip()
                values.append(int(raw) if raw.isdigit() and raw.isascii() else None)
            if any(value is not None for value in values):
                body.append({"name": item_name, "values": values})
    return level_cols, body


PRO_DIR_PAT = re.compile(r"4\.2\.(\d+)\s*([^\n]{2,40})")


def merge_rows(rows):
    merged = {}
    order = []
    for row in rows:
        key = row["name"]
        if key not in merged:
            merged[key] = list(row["values"])
            order.append(key)
        else:
            existing = merged[key]
            for index, value in enumerate(row["values"]):
                if index < len(existing) and existing[index] is None and value is not None:
                    existing[index] = value
    return [{"name": name, "values": merged[name]} for name in order]


def parse_professional(doc):
    """工程技术人员标准：4.2 专业能力要求权重表，按专业方向分多张子表。"""
    start = None
    begin = max(0, doc.page_count - 20)
    for index in range(begin, doc.page_count):
        if "专业能力要求权重表" in doc[index].get_text():
            start = index
            break
    if start is None:
        return None
    levels = []
    rows = []
    pages = []
    for index in range(start, min(start + 10, doc.page_count)):
        text = doc[index].get_text()
        if index > start and ("5 附录" in text or "4.3" in text):
            break
        match = PRO_DIR_PAT.search(text)
        direction = re.sub(r"\s+", "", match.group(2)) if match else ""
        page_levels, page_rows = extract_page(doc[index])
        if not page_levels or not page_rows:
            continue
        if not levels:
            levels = [name for _, name in page_levels]
        for row in page_rows:
            name = (direction + "｜" + row["name"]) if direction else row["name"]
            rows.append({"name": name, "values": row["values"]})
        pages.append(index + 1)
    if not rows:
        return None
    return {"pages": pages, "levels": levels, "rows": merge_rows(rows)}


def parse_weights(pdf_path):
    doc = pymupdf.open(pdf_path)
    hit_pages = []
    start = max(0, doc.page_count - 20)
    for index in range(start, doc.page_count):
        text = doc[index].get_text()
        if "技能要求权重表" in text:
            hit_pages.append(index)
    if not hit_pages:
        parsed = parse_professional(doc)
        doc.close()
        return parsed
    levels = []
    rows = []
    used_pages = []
    for index in hit_pages:
        text = doc[index].get_text()
        direction_match = re.search(r"技能要求权重表（([^）%]{1,20})）", text)
        direction = re.sub(r"\s+", "", direction_match.group(1)) if direction_match else ""
        page_levels, page_rows = extract_page(doc[index])
        if page_levels and not levels:
            levels = [name for _, name in page_levels]
        if page_rows:
            for row in page_rows:
                rows.append({
                    "name": (direction + "｜" + row["name"]) if direction else row["name"],
                    "values": row["values"]
                })
            used_pages.append(index + 1)
        next_index = index + 1
        if next_index < doc.page_count and next_index not in hit_pages:
            text = doc[next_index].get_text()
            if "4." in text and "权重表" in text and "续表" in text:
                more_levels, more_rows = extract_page(doc[next_index])
                if more_rows:
                    for row in more_rows:
                        rows.append({
                            "name": (direction + "｜" + row["name"]) if direction else row["name"],
                            "values": row["values"]
                        })
                    used_pages.append(next_index + 1)
    doc.close()
    merged = {}
    order = []
    for row in rows:
        key = row["name"]
        if key not in merged:
            merged[key] = list(row["values"])
            order.append(key)
        else:
            existing = merged[key]
            for index, value in enumerate(row["values"]):
                if index < len(existing) and existing[index] is None and value is not None:
                    existing[index] = value
    deduped_pages = []
    for page in used_pages:
        if page not in deduped_pages:
            deduped_pages.append(page)
    return {
        "pages": deduped_pages,
        "levels": levels,
        "rows": [{"name": name, "values": merged[name]} for name in order]
    }


def process(item, unique_codes):
    record = {
        "id": item["id"],
        "code": item["code"],
        "name": item["name"],
        "issueTime": (item.get("issueTime") or "")[:10],
        "issueNumber": item.get("issueNumber") or "",
        "standardInfoName": item.get("standardInfoName") or "",
        "pdf": "",
        "pages": [],
        "levels": [],
        "rows": [],
        "column_sums": [],
        "ok": False,
        "error": ""
    }
    try:
        path = download(item, unique_codes)
        record["pdf"] = os.path.basename(path)
        parsed = parse_weights(path)
        if parsed is None:
            record["error"] = "未找到技能要求权重表"
        else:
            record.update(parsed)
            groups = {}
            for row in record["rows"]:
                key = row["name"].split("｜")[0] if "｜" in row["name"] else "总体"
                groups.setdefault(key, []).append(row)
            group_sums = {}
            for key, group in groups.items():
                sums = []
                for column in range(len(record["levels"])):
                    sums.append(sum(
                        row["values"][column] or 0 for row in group
                        if column < len(row["values"])
                    ))
                group_sums[key] = sums
            record["group_sums"] = group_sums
            record["column_sums"] = list(next(iter(group_sums.values()))) if len(group_sums) == 1 else []
            record["ok"] = bool(record["rows"]) and all(
                all(value == 100 for value in sums) for sums in group_sums.values()
            )
            if not record["ok"]:
                record["error"] = "列合计异常 " + json.dumps(group_sums, ensure_ascii=False)
    except Exception as exc:  # noqa: BLE001
        record["error"] = str(exc)[:200]
    return record


def save(results, total):
    results.sort(key=lambda item: (item["code"], item["id"]))
    payload = {
        "source": "https://www.osta.org.cn/skillStandard",
        "retrieved_at": "2026-10-28",
        "total": total,
        "items": results
    }
    tmp = OUT + ".tmp"
    with open(tmp, "w", encoding="utf-8") as handle:
        json.dump(payload, handle, ensure_ascii=False, indent=1)
    os.replace(tmp, OUT)


def main():
    items = json.load(open(SRC, encoding="utf-8"))
    unique_codes = {}
    for item in items:
        unique_codes[item["code"]] = unique_codes.get(item["code"], 0) + 1
    results = []
    if os.path.exists(OUT):
        results = json.load(open(OUT, encoding="utf-8")).get("items", [])
    done = {item.get("id") for item in results}
    pending = [item for item in items if item["id"] not in done]
    total = len(items)
    with ThreadPoolExecutor(max_workers=8) as pool:
        futures = [pool.submit(process, item, unique_codes) for item in pending]
        for index, future in enumerate(as_completed(futures), start=1):
            results.append(future.result())
            if index % 40 == 0:
                save(results, total)
                ok = sum(1 for item in results if item.get("ok"))
                print(f"{len(results)}/{total} ok={ok}", flush=True)
    save(results, total)
    ok = sum(1 for item in results if item.get("ok"))
    print("saved", OUT, len(results), "ok", ok)


if __name__ == "__main__":
    main()
