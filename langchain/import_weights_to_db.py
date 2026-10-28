# -*- coding: utf-8 -*-
"""把国家职业标准权重索引导入 MySQL job_competency_weight 表。"""
import json
import os
from datetime import datetime

import pymysql

BASE = os.path.dirname(os.path.abspath(__file__))
INDEX = os.path.join(BASE, "data", "standards", "competency_weights_index.json")
DDL = os.path.join(BASE, "..", "sql", "job_competency_weight.sql")

conn = pymysql.connect(
    host="127.0.0.1", port=3306, user="root", password="123456",
    database="personnel_information", charset="utf8mb4", autocommit=True
)
cur = conn.cursor()

with open(DDL, encoding="utf-8") as handle:
    ddl = "\n".join(line for line in handle if not line.strip().startswith("--"))
for statement in filter(None, (part.strip() for part in ddl.split(";"))):
    cur.execute(statement)
print("table ready")

cur.execute("TRUNCATE TABLE job_competency_weight")

data = json.load(open(INDEX, encoding="utf-8"))
jobs = data.get("jobs") or []
rows = []
now = datetime.now()
for job in jobs:
    source = job.get("source") or {}
    pages = ",".join(str(page) for page in (source.get("pages") or []))
    for domain in job.get("domains") or []:
        weights = domain.get("weights") or {}
        for level, weight in weights.items():
            rows.append((
                job.get("code"), job.get("name"), level, domain.get("name"),
                weight, "national_standard", source.get("file"), pages,
                source.get("issue_number"), now
            ))

sql = ("INSERT INTO job_competency_weight "
       "(job_code, job_name, level, domain_name, weight, source, source_file, source_page, issue_number, create_time) "
       "VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s)")
cur.executemany(sql, rows)
print("inserted", len(rows))

cur.execute("SELECT COUNT(DISTINCT job_code), COUNT(DISTINCT job_name), COUNT(*) FROM job_competency_weight")
print("jobs/names/rows", cur.fetchone())

cur.execute("SELECT job_code, job_name, level, domain_name, weight FROM job_competency_weight "
            "WHERE job_code='4-04-05-01' AND level='三级' ORDER BY id")
for row in cur.fetchall():
    print("   ", row)
conn.close()
