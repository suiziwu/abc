# -*- coding: utf-8 -*-
"""把 BOSS 直聘 industry 字典（zpCommon/data/industry.json）整理成一一对应的行业编码表。"""
import json
import csv
import os
from openpyxl import Workbook
from openpyxl.styles import Font, PatternFill, Alignment, Border, Side
from openpyxl.utils import get_column_letter

BASE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(BASE, "原始字典-industry.json")
URL_TPL = "https://www.zhipin.com/web/geek/jobs?city=101120200&industry={}"

with open(SRC, "r", encoding="utf-8") as f:
    raw = json.load(f)

assert raw["code"] == 0, raw.get("message")

rows = []  # code, name, level, parent_code, parent_name
parents = []
for i, top in enumerate(raw["zpData"], 1):
    rows.append([top["code"], top["name"], "一级", "", ""])
    parents.append((top["code"], top["name"], []))
    for sub in (top.get("subLevelModelList") or []):
        rows.append([sub["code"], sub["name"], "二级", top["code"], top["name"]])
        parents[-1][2].append((sub["code"], sub["name"]))

# 校验：全部为 6 位、1 开头、且编码唯一
codes = [r[0] for r in rows]
assert len(codes) == len(set(codes)), "存在重复编码"
bad = [c for c in codes if len(str(c)) != 6 or not str(c).startswith("1")]
assert not bad, f"非 6 位 1 开头编码: {bad}"

print(f"一级 {len(parents)} 个，二级 {len(rows) - len(parents)} 个，合计 {len(rows)} 个编码")

# ---------- CSV ----------
csv_path = os.path.join(BASE, "行业编码对照表.csv")
with open(csv_path, "w", encoding="utf-8-sig", newline="") as f:
    w = csv.writer(f)
    w.writerow(["序号", "行业编码", "行业名称", "层级", "上级行业编码", "上级行业", "职位列表URL"])
    for i, (code, name, lv, pc, pn) in enumerate(rows, 1):
        w.writerow([i, code, name, lv, pc, pn, URL_TPL.format(code)])

# ---------- XLSX ----------
wb = Workbook()
ws = wb.active
ws.title = "行业编码对照表"

HEAD_FILL = PatternFill("solid", fgColor="1F4E79")
HEAD_FONT = Font(bold=True, color="FFFFFF", size=11)
LV1_FILL = PatternFill("solid", fgColor="DDEBF7")
THIN = Side(style="thin", color="BFBFBF")
BORDER = Border(left=THIN, right=THIN, top=THIN, bottom=THIN)

headers = ["序号", "行业编码", "行业名称", "层级", "上级行业编码", "上级行业", "职位列表URL"]
ws.append(headers)
for c in ws[1]:
    c.fill, c.font = HEAD_FILL, HEAD_FONT
    c.alignment = Alignment(horizontal="center", vertical="center")

for i, (code, name, lv, pc, pn) in enumerate(rows, 1):
    ws.append([i, code, name, lv, pc, pn, URL_TPL.format(code)])

for r in ws.iter_rows(min_row=2, max_row=ws.max_row, max_col=7):
    for c in r:
        c.border = BORDER
        c.alignment = Alignment(vertical="center")
    if r[3].value == "一级":
        for c in r:
            c.fill = LV1_FILL
            c.font = Font(bold=True, color="1F4E79")
    r[1].alignment = Alignment(horizontal="center", vertical="center")
    r[3].alignment = Alignment(horizontal="center", vertical="center")

widths = {"A": 6, "B": 11, "C": 24, "D": 8, "E": 13, "F": 22, "G": 62}
for col, wd in widths.items():
    ws.column_dimensions[col].width = wd
ws.freeze_panes = "A2"
ws.auto_filter.ref = f"A1:G{ws.max_row}"
ws.row_dimensions[1].height = 24

# 汇总表
ws2 = wb.create_sheet("按大类汇总")
ws2.append(["一级行业编码", "一级行业名称", "二级行业数量", "包含的二级行业（编码 名称）"])
for c in ws2[1]:
    c.fill, c.font = HEAD_FILL, HEAD_FONT
    c.alignment = Alignment(horizontal="center", vertical="center", wrap_text=True)
for code, name, subs in parents:
    ws2.append([code, name, len(subs), "；".join(f"{c} {n}" for c, n in subs)])
for r in ws2.iter_rows(min_row=2, max_row=ws2.max_row, max_col=4):
    for c in r:
        c.border = BORDER
        c.alignment = Alignment(vertical="top", wrap_text=True)
    r[0].alignment = Alignment(horizontal="center", vertical="center")
    r[2].alignment = Alignment(horizontal="center", vertical="center")
for col, wd in {"A": 13, "B": 26, "C": 12, "D": 96}.items():
    ws2.column_dimensions[col].width = wd
ws2.freeze_panes = "A2"
ws2.row_dimensions[1].height = 30

# 说明表
ws3 = wb.create_sheet("说明")
notes = [
    ["数据来源", "https://www.zhipin.com/wapi/zpCommon/data/industry.json（BOSS直聘 PC 端“行业”筛选器使用的官方字典）"],
    ["抓取时间", "2026-10-27"],
    ["编码规则", "全部为 6 位数字、以 1 开头；100xxx~101xxx 为一级大类，其下 6 位子编码为二级细分行业"],
    ["URL 用法", "https://www.zhipin.com/web/geek/jobs?city=101120200&industry=编码 ，把 industry= 后面的数字换成 B 列编码"],
    ["统计口径", f"一级大类 {len(parents)} 个，二级细分 {len(rows) - len(parents)} 个，合计 {len(rows)} 个编码"],
    ["备注", "浏览器直接访问时页面为前端渲染，行业名称由上述字典接口返回；职位列表接口另有风控（code=37 环境异常），需登录浏览器环境才能取到职位数据。"],
]
ws3.append(["项目", "内容"])
for c in ws3[1]:
    c.fill, c.font = HEAD_FILL, HEAD_FONT
for k, v in notes:
    ws3.append([k, v])
for r in ws3.iter_rows(min_row=2, max_row=ws3.max_row, max_col=2):
    r[0].font = Font(bold=True)
    for c in r:
        c.alignment = Alignment(vertical="top", wrap_text=True)
        c.border = BORDER
ws3.column_dimensions["A"].width = 14
ws3.column_dimensions["B"].width = 110

xlsx_path = os.path.join(BASE, "行业编码对照表.xlsx")
wb.save(xlsx_path)

# ---------- 控制台：Markdown ----------
out = []
out.append("| 序号 | 编码 | 行业名称 | 层级 | 上级行业 |")
out.append("|---:|---|---|---|---|")
for i, (code, name, lv, pc, pn) in enumerate(rows, 1):
    out.append(f"| {i} | {code} | {name} | {lv} | {pn or '—'} |")
md = "\n".join(out)
with open(os.path.join(BASE, "行业编码表.md"), "w", encoding="utf-8") as f:
    f.write(md)

print("XLSX:", xlsx_path)
print("CSV :", csv_path)
print("rows:", len(rows))
