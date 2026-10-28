# -*- coding: utf-8 -*-
"""生成岗位候选目录：大典职业 + 国家职业标准职业名，供前端输入联想。"""
import json
import os
import re

BASE = os.path.dirname(os.path.abspath(__file__))
CODEX = os.path.join(BASE, "data", "sources", "职业分类大典-2022.txt")
STANDARDS = os.path.join(BASE, "data", "sources", "osta_skill_standards.json")
OUT = os.path.join(BASE, "..", "ruoyi-project", "src", "main", "resources",
                   "langchain", "job-catalog.json")

# 大典正文中的“编码\u3000职业名称”行
CODEX_PATTERN = re.compile(r"(?m)^(\d-\d{2}-\d{2}-\d{2})\u3000([^\n]{2,40})$")


def clean(name):
    text = re.sub(r"\s+", "", name or "")
    return text.strip()


def main():
    jobs = {}
    text = open(CODEX, encoding="utf-8").read()
    for code, name in CODEX_PATTERN.findall(text):
        normalized = clean(name)
        if not normalized:
            continue
        jobs.setdefault(normalized, {"name": normalized, "code": code, "source": "codex"})

    standards = json.load(open(STANDARDS, encoding="utf-8"))
    for item in standards:
        normalized = clean(item.get("name"))
        if not normalized:
            continue
        entry = jobs.get(normalized)
        if entry is None:
            jobs[normalized] = {
                "name": normalized,
                "code": clean(item.get("code")),
                "source": "standard"
            }
        else:
            entry["standard"] = True

    items = sorted(jobs.values(), key=lambda item: (item["code"], item["name"]))
    payload = {
        "version": "1.0.0",
        "source": "《中华人民共和国职业分类大典（2022年版）》与技能人才评价工作网公开标准",
        "count": len(items),
        "jobs": items
    }
    with open(OUT, "w", encoding="utf-8") as handle:
        json.dump(payload, handle, ensure_ascii=False, indent=1)
    print("jobs", len(items), "->", OUT)


if __name__ == "__main__":
    main()
