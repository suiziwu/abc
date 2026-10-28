# -*- coding: utf-8 -*-
"""把 Python 离线生成的图谱导入 job_competency_graph，便于前端直接查看。

仅用于本地演示/验收：source_mode 标记为 kb，不覆盖大模型生成记录。
"""
import json
import os
import subprocess
import tempfile

BASE = os.path.dirname(os.path.abspath(__file__))
OUTPUT = os.path.join(BASE, "output")
MYSQL = r"C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe"

FILES = [
    "岗位能力图谱-计算机程序设计员.json",
    "岗位能力图谱-计算机软件测试员.json",
    "岗位能力图谱-信息通信网络运行管理员.json",
    "岗位能力图谱-数据库运行管理员.json",
]


def esc(value):
    return str(value if value is not None else "").replace("\\", "\\\\").replace("'", "''")


rows = []
for name in FILES:
    path = os.path.join(OUTPUT, name)
    if not os.path.exists(path):
        print("skip", name)
        continue
    with open(path, "r", encoding="utf-8") as f:
        payload = json.load(f)
    job = payload.get("job", {})
    rows.append((
        job.get("name", ""),
        job.get("code", ""),
        job.get("level", ""),
        payload.get("confidence"),
        json.dumps(payload.get("coverage", {}), ensure_ascii=False),
        json.dumps(payload, ensure_ascii=False),
    ))

statements = ["DELETE FROM job_competency_graph WHERE source_mode = 'kb';"]
for job_name, job_code, level, confidence, coverage, graph in rows:
    confidence_sql = "NULL" if confidence is None else str(confidence)
    statements.append(
        "INSERT INTO job_competency_graph "
        "(job_name, job_code, level, source_mode, confidence, coverage_json, graph_json, create_by, create_time, del_flag) "
        "VALUES ('%s', '%s', '%s', 'kb', %s, '%s', '%s', 'admin', NOW(), '0');"
        % (esc(job_name), esc(job_code), esc(level), confidence_sql, esc(coverage), esc(graph))
    )

sql = "\n".join(statements)
fd, sql_path = tempfile.mkstemp(suffix=".sql")
os.close(fd)
with open(sql_path, "w", encoding="utf-8") as f:
    f.write(sql)
try:
    result = subprocess.run(
        [MYSQL, "-uroot", "-p123456", "-D", "personnel_information",
         "--default-character-set=utf8mb4", "-e", "source " + sql_path.replace("\\", "/")],
        capture_output=True, text=True, encoding="utf-8", errors="replace")
    print(result.stdout)
    print(result.stderr)
    print("imported rows:", len(rows))
finally:
    os.remove(sql_path)
