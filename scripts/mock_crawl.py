# -*- coding: utf-8 -*-
"""联调用模拟爬虫：输出与 BOSS 爬虫一致的进度行，并写一份 CSV 供自动导入。"""
import csv
import os
import time

OUT = os.environ.get("MOCK_OUT", r"D:\000\abc\scripts\_crawl_out")
os.makedirs(OUT, exist_ok=True)

print("开始爬取关键词：【测试】在【青岛】的职位（目标20条）", flush=True)
for i in range(1, 9):
    print("第 %d/8 个职位详情页..." % i, flush=True)
    print("已保存 %d/20 条" % (i * 2), flush=True)
    time.sleep(1.2)

path = os.path.join(OUT, "mock_jobs.csv")
with open(path, "w", encoding="utf-8-sig", newline="") as f:
    writer = csv.writer(f)
    writer.writerow(["层级", "行业名称", "上级行业", "职位名称", "职位描述", "城市", "关键词"])
    writer.writerow(["二级", "计算机软件", "信息技术", "Mock数据工程师",
                     "负责Python与数据平台开发，熟悉SQL、可视化与质量检验", "青岛", "测试"])
print("已导出JSON: %s (共1条)" % path, flush=True)
print("可视化页面已生成", flush=True)
