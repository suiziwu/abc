#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
知识库健康检查：MySQL 原文表 与 Milvus 向量库 的一致性巡检。

默认只读，不做任何修改。发现漂移时以非 0 退出码返回，便于挂到定时任务里告警。

用法：
    python tools/kb_health.py
    python tools/kb_health.py --subject 94
    python tools/kb_health.py --fix-orphans     # 删除「有向量、无原文」的孤儿向量

环境变量（都有默认值，按需覆盖）：
    KB_MYSQL_HOST / KB_MYSQL_PORT / KB_MYSQL_USER / KB_MYSQL_PASSWORD / KB_MYSQL_DB
    KB_MILVUS_URL / KB_MILVUS_COLLECTION
"""

import argparse
import json
import os
import sys
import urllib.request

try:
    import pymysql
except ImportError:
    print("需要 pymysql：pip install pymysql")
    sys.exit(2)

MYSQL = {
    "host": os.getenv("KB_MYSQL_HOST", "127.0.0.1"),
    "port": int(os.getenv("KB_MYSQL_PORT", "3306")),
    "user": os.getenv("KB_MYSQL_USER", "root"),
    "password": os.getenv("KB_MYSQL_PASSWORD", "123456"),
    "database": os.getenv("KB_MYSQL_DB", "personnel_information"),
}
MILVUS_URL = os.getenv("KB_MILVUS_URL", "http://127.0.0.1:19530").rstrip("/")
COLLECTION = os.getenv("KB_MILVUS_COLLECTION", "course_kb")

# 绕过系统代理，避免本地请求被 HTTP_PROXY 劫持
OPENER = urllib.request.build_opener(urllib.request.ProxyHandler({}))


def milvus(path, payload):
    req = urllib.request.Request(
        MILVUS_URL + path,
        data=json.dumps(payload).encode("utf-8"),
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    with OPENER.open(req, timeout=15) as resp:
        body = json.loads(resp.read().decode("utf-8"))
    if body.get("code", 0) != 0:
        raise RuntimeError("Milvus 返回错误：%s" % body.get("message"))
    return body.get("data")


def load_mysql(subject_id=None):
    conn = pymysql.connect(charset="utf8mb4", **MYSQL)
    cur = conn.cursor()
    where, args = "", []
    if subject_id:
        where, args = " where subject_id = %s", [subject_id]
    cur.execute("select doc_id, chunk_index, subject_id from kb_chunk" + where, args)
    rows = cur.fetchall()
    cur.execute(
        "select subject_id, source_type, count(*), count(distinct doc_pk), "
        "sum(case when content_hash is null then 1 else 0 end), "
        "sum(case when embedding is null or embedding = '' then 1 else 0 end) "
        "from kb_chunk" + where + " group by subject_id, source_type order by subject_id, source_type",
        args,
    )
    stats = cur.fetchall()
    cur.execute("select count(*) from kb_document" + where, args)
    documents = cur.fetchone()[0]
    conn.close()
    return rows, stats, documents


def load_milvus(subject_id=None):
    expr = "subject_id == %d" % subject_id if subject_id else ""
    rows, offset = [], 0
    while True:
        batch = milvus(
            "/v2/vectordb/entities/query",
            {
                "collectionName": COLLECTION,
                "filter": expr,
                "outputFields": ["id", "subject_id", "source_type"],
                "limit": 4096,
                "offset": offset,
            },
        ) or []
        rows.extend(batch)
        if len(batch) < 4096:
            break
        offset += len(batch)
    return rows


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--subject", type=int, default=None, help="只检查某个学科")
    parser.add_argument("--fix-orphans", action="store_true", help="删除孤儿向量（有向量无原文）")
    args = parser.parse_args()

    mysql_rows, stats, documents = load_mysql(args.subject)
    milvus_rows = load_milvus(args.subject)

    mysql_ids = {"%s#%s" % (r[0], r[1]) for r in mysql_rows}
    milvus_ids = {r["id"] for r in milvus_rows}
    orphans = sorted(milvus_ids - mysql_ids)
    missing = sorted(mysql_ids - milvus_ids)

    print("=" * 78)
    print("知识库健康检查  collection=%s  subject=%s" % (COLLECTION, args.subject or "全部"))
    print("=" * 78)
    print("MySQL 分块：%-6d  MySQL 文档：%-6d  Milvus 存活向量：%-6d" % (len(mysql_ids), documents, len(milvus_ids)))
    print("孤儿向量（有向量无原文）：%-6d  缺失向量（有原文无向量）：%-6d" % (len(orphans), len(missing)))

    print("\n%-8s %-12s %8s %8s %8s %8s" % ("学科", "语料类型", "分块", "文档", "无指纹", "无向量"))
    print("-" * 78)
    for subject_id, source_type, chunks, docs, no_hash, no_vec in stats:
        print("%-10s %-12s %8d %8d %8d %8d" % (subject_id, source_type, chunks, docs, no_hash or 0, no_vec or 0))

    if orphans:
        print("\n孤儿向量示例：%s" % ", ".join(orphans[:8]))
    if missing:
        print("\n缺失向量示例：%s" % ", ".join(missing[:8]))

    if args.fix_orphans and orphans:
        by_subject = {}
        for r in milvus_rows:
            if r["id"] in set(orphans):
                by_subject.setdefault(r.get("subject_id"), 0)
                by_subject[r.get("subject_id")] += 1
        for subject_id, count in by_subject.items():
            deleted = milvus(
                "/v2/vectordb/entities/delete",
                {"collectionName": COLLECTION, "filter": "subject_id == %d" % subject_id},
            )
            print("已按学科 %s 删除 %s 条（该学科共 %d 条孤儿向量）" % (subject_id, deleted, count))

    healthy = not orphans and not missing
    print("\n结论：%s" % ("健康，原文与向量一一对应" if healthy else "存在漂移，建议重建对应学科的知识库"))
    return 0 if healthy else 1


if __name__ == "__main__":
    sys.exit(main())
