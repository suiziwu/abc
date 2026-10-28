# -*- coding: utf-8 -*-
"""为 job_kb.json 补充教学口径产业链映射（industry_path），升级到 3.0.0。

说明：industry_path 是教学映射，不是《国民经济行业分类》官方口径，
在数据中以 mapping_type=teaching 与 source_id 明确标注。
"""
import json
import os

BASE = os.path.dirname(os.path.abspath(__file__))
KB_PATH = os.path.join(BASE, "job_kb.json")

MAPPING = {
    "JOB-4-04-05-01": {
        "chain": {"id": "IND-SW", "name": "软件和信息技术服务业"},
        "segment": {"id": "IND-SW-DEV", "name": "软件开发与交付"},
        "cluster": {"id": "CLU-SW-APP", "name": "应用软件开发岗位群"},
    },
    "JOB-4-04-05-02": {
        "chain": {"id": "IND-SW", "name": "软件和信息技术服务业"},
        "segment": {"id": "IND-SW-QA", "name": "软件质量保障"},
        "cluster": {"id": "CLU-SW-QA", "name": "软件测试与质量保障岗位群"},
    },
    "JOB-4-04-04-01": {
        "chain": {"id": "IND-ICT", "name": "信息通信业"},
        "segment": {"id": "IND-ICT-OPS", "name": "网络运行维护"},
        "cluster": {"id": "CLU-ICT-OPS", "name": "信息通信网络运行维护岗位群"},
    },
    "JOB-4-04-05-04": {
        "chain": {"id": "IND-SW", "name": "软件和信息技术服务业"},
        "segment": {"id": "IND-SW-DATA", "name": "数据服务与运维"},
        "cluster": {"id": "CLU-SW-DBA", "name": "数据库运维岗位群"},
    },
}

NOTE = "教学映射：依据专业教学标准与企业岗位调研归纳，非《国民经济行业分类》官方口径。"


def main():
    with open(KB_PATH, "r", encoding="utf-8") as f:
        kb = json.load(f)

    for job in kb["jobs"]:
        mapping = MAPPING.get(job["id"])
        if not mapping:
            continue
        job["industry_path"] = [
            {"level": "产业链", "id": mapping["chain"]["id"], "name": mapping["chain"]["name"],
             "mapping_type": "teaching", "source_id": "TEACH-MAP-2026-01"},
            {"level": "产业链环节", "id": mapping["segment"]["id"], "name": mapping["segment"]["name"],
             "mapping_type": "teaching", "source_id": "TEACH-MAP-2026-01"},
            {"level": "职业岗位群", "id": mapping["cluster"]["id"], "name": mapping["cluster"]["name"],
             "mapping_type": "teaching", "source_id": "TEACH-MAP-2026-01"},
            {"level": "职业岗位", "id": job["id"], "name": job["name"],
             "mapping_type": "standard", "source_id": job["code"]},
        ]
        job["industry_mapping_note"] = NOTE

    kb["schema_version"] = "3.0.0"
    kb["updated_at"] = "2026-10-27"
    with open(KB_PATH, "w", encoding="utf-8", newline="\n") as f:
        json.dump(kb, f, ensure_ascii=False, indent=2)
        f.write("\n")
    print("industry_path added:", [job["id"] for job in kb["jobs"] if job.get("industry_path")])


if __name__ == "__main__":
    main()
