# -*- coding: utf-8 -*-
"""把 Python 离线生成的 v3 图谱同步到 Java 资源目录，并生成索引。

Java 的 KB 模式不重写规则引擎，而是读取这里预生成的 JSON。
"""
import datetime
import json
import os
import shutil

BASE = os.path.dirname(os.path.abspath(__file__))
OUTPUT = os.path.join(BASE, "output")
TARGET = os.path.abspath(os.path.join(
    BASE, "..", "ruoyi-project", "src", "main", "resources", "langchain", "competency-graphs"))
KB_PATH = os.path.join(BASE, "job_kb.json")


def main():
    os.makedirs(TARGET, exist_ok=True)
    with open(KB_PATH, "r", encoding="utf-8") as f:
        kb = json.load(f)

    jobs = []
    for job in kb["jobs"]:
        file_name = "岗位能力图谱-%s.json" % job["name"]
        source = os.path.join(OUTPUT, file_name)
        if not os.path.exists(source):
            print("missing:", source)
            continue
        shutil.copyfile(source, os.path.join(TARGET, file_name))
        jobs.append({
            "id": job["id"],
            "name": job["name"],
            "code": job["code"],
            "level": job.get("level", ""),
            "aliases": job.get("aliases", []),
            "file": file_name,
        })

    index = {
        "schema_version": "3.0.0",
        "kb_version": kb.get("schema_version"),
        "generated_at": datetime.datetime.now().isoformat(timespec="seconds"),
        "jobs": jobs,
    }
    with open(os.path.join(TARGET, "index.json"), "w", encoding="utf-8", newline="\n") as f:
        json.dump(index, f, ensure_ascii=False, indent=2)
        f.write("\n")
    print("synced:", [item["name"] for item in jobs])
    print("target:", TARGET)


if __name__ == "__main__":
    main()
