# -*- coding: utf-8 -*-
"""导入岗位产业链/岗位群映射（来源：GB/T 4754-2017 与职业分类大典 2022）。"""
import os
from datetime import datetime

import pymysql

BASE = os.path.dirname(os.path.abspath(__file__))
DDL = os.path.join(BASE, "..", "sql", "job_industry_chain.sql")

SOURCE = "《国民经济行业分类》（GB/T 4754-2017）；《中华人民共和国职业分类大典（2022年版）》"
URL = "https://www.stats.gov.cn/sj/tjbz/gjtjbz/"

ROWS = [
    ("4-04-05-01", "计算机程序设计员",
     "信息传输、软件和信息技术服务业", "I",
     "国民经济行业分类门类 I：信息传输、软件和信息技术服务业",
     "软件开发", "行业大类 65 软件和信息技术服务业，中类 651 软件开发",
     "4-04-05", "软件和信息技术服务人员",
     "从事应用软件、计算机系统相关软件开发及信息咨询等服务工作的人员。"),
    ("4-04-05-02", "计算机软件测试员",
     "信息传输、软件和信息技术服务业", "I",
     "国民经济行业分类门类 I：信息传输、软件和信息技术服务业",
     "软件开发", "行业大类 65 软件和信息技术服务业，中类 651 软件开发",
     "4-04-05", "软件和信息技术服务人员",
     "从事应用软件、计算机系统相关软件开发及信息咨询等服务工作的人员。"),
    ("4-04-05-04", "数据库运行管理员",
     "信息传输、软件和信息技术服务业", "I",
     "国民经济行业分类门类 I：信息传输、软件和信息技术服务业",
     "运行维护服务", "行业大类 65 软件和信息技术服务业，中类 654 运行维护服务",
     "4-04-05", "软件和信息技术服务人员",
     "从事应用软件、计算机系统相关软件开发及信息咨询等服务工作的人员。"),
    (None, "数据库管理员",
     "信息传输、软件和信息技术服务业", "I",
     "国民经济行业分类门类 I：信息传输、软件和信息技术服务业",
     "运行维护服务", "行业大类 65 软件和信息技术服务业，中类 654 运行维护服务",
     "4-04-05", "软件和信息技术服务人员",
     "从事应用软件、计算机系统相关软件开发及信息咨询等服务工作的人员。"),
    ("4-04-04-01", "信息通信网络运行管理员",
     "电信、广播电视和卫星传输服务", "63",
     "国民经济行业分类大类 63：电信、广播电视和卫星传输服务",
     "电信", "行业大类 63，中类 631 电信",
     "4-04-04", "信息通信网络运行管理人员",
     "从事信息通信网络运行维护、系统管理等工作的人员。"),
    ("2-06-11-04", "金融产品销售专业人员",
     "金融业", "J",
     "国民经济行业分类门类 J：金融业",
     "资本市场服务", "行业大类 67 资本市场服务",
     "2-06-11", "证券期货基金专业人员",
     "从事证券、期货和基金发行、交易及金融产品销售等业务的专业人员。"),
]

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

now = datetime.now()
sql = ("INSERT INTO job_industry_chain "
       "(job_code, job_name, chain_name, chain_code, chain_desc, stage_name, stage_desc, "
       "cluster_code, cluster_name, cluster_desc, source, source_url, create_time) "
       "VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s) "
       "ON DUPLICATE KEY UPDATE job_code=VALUES(job_code), chain_name=VALUES(chain_name), "
       "chain_code=VALUES(chain_code), chain_desc=VALUES(chain_desc), stage_name=VALUES(stage_name), "
       "stage_desc=VALUES(stage_desc), cluster_code=VALUES(cluster_code), "
       "cluster_name=VALUES(cluster_name), cluster_desc=VALUES(cluster_desc), "
       "source=VALUES(source), source_url=VALUES(source_url)")
count = 0
for row in ROWS:
    cur.execute(sql, row + (SOURCE, URL, now))
    count += 1
print("upserted", count)

cur.execute("SELECT job_code, job_name, chain_name, stage_name, cluster_name FROM job_industry_chain ORDER BY id")
for row in cur.fetchall():
    print("   ", row)
conn.close()
