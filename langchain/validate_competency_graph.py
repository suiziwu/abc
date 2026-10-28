# -*- coding: utf-8 -*-
"""校验岗位能力图谱 JSON 是否符合 competency-graph.schema.json v3。

用法：
    python validate_competency_graph.py output/岗位能力图谱-计算机程序设计员.json
    python validate_competency_graph.py            # 校验 output 下全部 JSON
"""
import glob
import json
import os
import sys

from jsonschema import Draft7Validator

BASE = os.path.dirname(os.path.abspath(__file__))
SCHEMA_PATH = os.path.join(BASE, "competency-graph.schema.json")


def main():
    with open(SCHEMA_PATH, "r", encoding="utf-8") as f:
        schema = json.load(f)
    validator = Draft7Validator(schema)

    files = sys.argv[1:] or sorted(glob.glob(os.path.join(BASE, "output", "*.json")))
    failed = 0
    for path in files:
        with open(path, "r", encoding="utf-8") as f:
            payload = json.load(f)
        if payload.get("schema_version") != "3.0.0":
            print("SKIP %s（schema_version=%s）" % (os.path.basename(path), payload.get("schema_version")))
            continue
        errors = sorted(validator.iter_errors(payload), key=lambda item: list(item.path))
        if errors:
            failed += 1
            print("FAIL %s（%d 处）" % (os.path.basename(path), len(errors)))
            for error in errors[:8]:
                location = "/".join(str(part) for part in error.path) or "<root>"
                print("  - %s: %s" % (location, error.message))
        else:
            print("OK   %s" % os.path.basename(path))
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
