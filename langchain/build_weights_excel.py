# -*- coding: utf-8 -*-
"""把批量提取的权重表生成为 Excel 汇总表与轻量 JSON 索引。"""
import json
import os
from datetime import date

from openpyxl import Workbook
from openpyxl.styles import Alignment, Border, Font, PatternFill, Side
from openpyxl.utils import get_column_letter

BASE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(BASE, "data", "standards", "weights_all.json")
XLSX = r"d:\system\lerning\国家职业标准-技能要求权重表.xlsx"
JSON_OUT = os.path.join(BASE, "data", "standards", "competency_weights_index.json")
PDF_BASE = "https://www.osta.org.cn/api/sys/downloadFile/decrypt?fileName="

HEAD_FILL = PatternFill("solid", fgColor="1F6FEB")
HEAD_FONT = Font(color="FFFFFF", bold=True, size=10)
TITLE_FONT = Font(bold=True, size=14)
SUB_FONT = Font(color="666666", size=9)
THIN = Side(style="thin", color="D9E2EC")
BORDER = Border(left=THIN, right=THIN, top=THIN, bottom=THIN)


def load():
    data = json.load(open(SRC, encoding="utf-8"))
    for item in data["items"]:
        normalized = []
        for level in item.get("levels") or []:
            if isinstance(level, (list, tuple)) and len(level) == 2:
                normalized.append(level[1])
            else:
                normalized.append(level)
        item["levels"] = normalized
    ok_items = [item for item in data["items"] if item.get("ok")]
    pending = [item for item in data["items"] if item.get("rows") and not item.get("ok")]
    no_table = [item for item in data["items"] if not item.get("rows")]
    ok_items.sort(key=lambda item: item["code"])
    pending.sort(key=lambda item: item["code"])
    no_table.sort(key=lambda item: item["code"])
    return data, ok_items, pending + no_table


def write_pending(wb, pending):
    ws = wb.create_sheet("待核对")
    headers = ["职业名称", "职业编码", "行数", "页码", "异常说明", "标准PDF文件名"]
    for col, title in enumerate(headers, start=1):
        cell = ws.cell(row=1, column=col, value=title)
        cell.fill = HEAD_FILL
        cell.font = HEAD_FONT
        cell.border = BORDER
        cell.alignment = Alignment(horizontal="center")
    ws.freeze_panes = "A2"
    for row_index, item in enumerate(pending, start=2):
        values = [
            item["name"], item["code"], len(item.get("rows") or []),
            ",".join(str(p) for p in item.get("pages", [])),
            item.get("error") or "未设 4.2 权重表",
            item.get("standardInfoName", "")
        ]
        for col, value in enumerate(values, start=1):
            cell = ws.cell(row=row_index, column=col, value=value)
            cell.border = BORDER
    widths = [24, 12, 8, 10, 40, 46]
    for index, width in enumerate(widths, start=1):
        ws.column_dimensions[get_column_letter(index)].width = width
    return ws


def write_matrix(wb, items):
    ws = wb.create_sheet("权重矩阵")
    ws["A1"] = "国家职业标准 4.2 技能要求权重表（逐条取自标准原文）"
    ws["A1"].font = TITLE_FONT
    ws["A2"] = f"共 {len(items)} 个职业；数据取自技能人才评价工作网公开标准 PDF；生成日期 {date.today()}"
    ws["A2"].font = SUB_FONT

    headers = ["序号", "职业名称", "职业编码", "职业功能 / 项目",
               "五级/初级工", "四级/中级工", "三级/高级工", "二级/技师", "一级/高级技师",
               "校验", "页码", "颁布文号"]
    row_index = 4
    for col, title in enumerate(headers, start=1):
        cell = ws.cell(row=row_index, column=col, value=title)
        cell.fill = HEAD_FILL
        cell.font = HEAD_FONT
        cell.alignment = Alignment(horizontal="center")
        cell.border = BORDER
    ws.freeze_panes = "A5"

    order = 0
    for item in items:
        order += 1
        start_row = row_index + 1
        columns = item.get("levels") or []
        for entry in item["rows"]:
            row_index += 1
            ws.cell(row=row_index, column=1, value=order).alignment = Alignment(horizontal="center")
            ws.cell(row=row_index, column=4, value=entry["name"]).border = BORDER
            for offset in range(5):
                if offset < len(columns):
                    value = entry["values"][offset] if offset < len(entry["values"]) else None
                    cell = ws.cell(row=row_index, column=5 + offset, value=value if value is not None else "—")
                    cell.alignment = Alignment(horizontal="center")
                    cell.border = BORDER
                else:
                    cell = ws.cell(row=row_index, column=5 + offset, value="")
                    cell.border = BORDER
            ws.cell(row=row_index, column=10, value="").border = BORDER
            ws.cell(row=row_index, column=11, value=",".join(str(p) for p in item.get("pages", []))).border = BORDER
            ws.cell(row=row_index, column=12, value=item.get("issueNumber", "")).border = BORDER
        end_row = row_index
        status = "OK" if item.get("ok") else "异常"
        for col, value in ((2, item["name"]), (3, item["code"]), (10, status)):
            cell = ws.cell(row=start_row, column=col, value=value)
            cell.border = BORDER
            cell.alignment = Alignment(horizontal="center")
        if end_row > start_row:
            for col in (2, 3, 10):
                ws.merge_cells(start_row=start_row, start_column=col, end_row=end_row, end_column=col)

    widths = [6, 22, 12, 34, 10, 10, 10, 10, 12, 18, 8, 24]
    for index, width in enumerate(widths, start=1):
        ws.column_dimensions[get_column_letter(index)].width = width
    return ws


def write_long(wb, items):
    ws = wb.create_sheet("长表-可筛选")
    headers = ["职业名称", "职业编码", "等级", "职业功能/项目", "权重(%)", "标准页码",
               "颁布时间", "颁布文号", "标准PDF文件名"]
    for col, title in enumerate(headers, start=1):
        cell = ws.cell(row=1, column=col, value=title)
        cell.fill = HEAD_FILL
        cell.font = HEAD_FONT
        cell.border = BORDER
        cell.alignment = Alignment(horizontal="center")
    ws.freeze_panes = "A2"
    ws.auto_filter.ref = "A1:I1"

    row_index = 1
    for item in items:
        columns = item.get("levels") or []
        for entry in item["rows"]:
            for offset, level in enumerate(columns):
                value = entry["values"][offset] if offset < len(entry["values"]) else None
                if value is None:
                    continue
                row_index += 1
                record = [
                    item["name"], item["code"], level + "工" if level.endswith("级") else level,
                    entry["name"], value, ",".join(str(p) for p in item.get("pages", [])),
                    item.get("issueTime", ""), item.get("issueNumber", ""),
                    item.get("standardInfoName", "")
                ]
                for col, cell_value in enumerate(record, start=1):
                    cell = ws.cell(row=row_index, column=col, value=cell_value)
                    cell.border = BORDER
    widths = [24, 12, 14, 36, 10, 10, 12, 26, 46]
    for index, width in enumerate(widths, start=1):
        ws.column_dimensions[get_column_letter(index)].width = width
    return ws


def write_industry_map(wb, items):
    ws = wb.create_sheet("行业大类对照")
    group_map = {
        "互联网/AI": ["4-04", "2-02"],
        "电子/通信/半导体": ["4-04", "6-25", "6-24", "6-26"],
        "服务业": ["4-03", "4-10", "4-13", "4-14"],
        "消费品/批发/零售": ["4-01", "6-02", "6-05", "6-06"],
        "房地产/建筑": ["4-06", "6-29", "2-02"],
        "教育培训": ["2-08", "4-13"],
        "广告/传媒/文化/体育": ["2-09", "2-10", "4-13"],
        "制造业": ["6-20", "6-21", "6-24", "6-25", "6-26", "6-28"],
        "专业服务": ["2-06", "2-07", "4-07", "4-08"],
        "制药/医疗": ["2-05", "6-12", "4-14"],
        "汽车": ["6-22", "4-12", "2-02"],
        "交通运输/物流": ["4-02", "6-23", "6-30"],
        "能源/化工/环保": ["4-11", "6-11", "6-28", "4-09", "6-16"],
        "金融": ["2-06", "4-05"],
        "政府/非营利组织/其他": ["3-01", "3-02", "2-01", "5-01", "5-02", "5-03", "5-04"],
    }
    middle_names = {
        "4-04": "信息传输、软件和信息技术服务人员",
        "2-02": "工程技术人员",
        "6-25": "计算机、通信和其他电子设备制造人员",
        "6-24": "电气机械和器材制造人员",
        "6-26": "仪器仪表制造人员",
        "4-03": "住宿和餐饮服务人员",
        "4-10": "居民服务人员",
        "4-13": "文化和教育服务人员",
        "4-14": "健康、体育和休闲服务人员",
        "4-01": "批发与零售服务人员",
        "6-02": "食品、饮料生产加工人员",
        "6-05": "纺织品、服装和皮革、毛皮制品加工制作人员",
        "6-06": "木材加工、家具与木制品制作人员",
        "4-06": "房地产服务人员",
        "6-29": "建筑施工人员",
        "2-08": "教学人员",
        "2-09": "文学艺术、体育专业人员",
        "2-10": "新闻出版、文化专业人员",
        "6-20": "通用设备制造人员",
        "6-21": "专用设备制造人员",
        "6-28": "电力、热力、气体、水生产和输配人员",
        "2-06": "经济和金融专业人员",
        "2-07": "监察、法律、社会和宗教专业人员",
        "4-07": "租赁和商务服务人员",
        "4-08": "技术辅助服务人员",
        "2-05": "卫生专业技术人员",
        "6-12": "医药制造人员",
        "6-22": "汽车制造人员",
        "4-12": "修理及制作服务人员",
        "4-02": "交通运输、仓储物流和邮政业服务人员",
        "6-23": "铁路、船舶、航空设备制造人员",
        "6-30": "运输设备和通用工程机械操作人员及有关人员",
        "4-11": "电力、燃气及水供应服务人员",
        "6-11": "化学原料和化学制品制造人员",
        "4-09": "水利、环境和公共设施管理服务人员",
        "6-16": "采矿人员",
        "4-05": "金融服务人员",
        "3-01": "行政办事及辅助人员",
        "3-02": "安全和消防及辅助人员",
        "2-01": "科学研究人员",
        "5-01": "农业生产人员",
        "5-02": "林业生产人员",
        "5-03": "畜牧业生产人员",
        "5-04": "渔业生产人员",
    }
    headers = ["BOSS 一级行业", "建议关注的大典职业中类", "中类名称", "本次收录标准职业数"]
    for col, title in enumerate(headers, start=1):
        cell = ws.cell(row=1, column=col, value=title)
        cell.fill = HEAD_FILL
        cell.font = HEAD_FONT
        cell.border = BORDER
        cell.alignment = Alignment(horizontal="center")

    row_index = 1
    for group, prefixes in group_map.items():
        for prefix in prefixes:
            row_index += 1
            count = sum(1 for item in items if (item["code"] or "").startswith(prefix + "-"))
            ws.cell(row=row_index, column=1, value=group).border = BORDER
            ws.cell(row=row_index, column=2, value=prefix).border = BORDER
            ws.cell(row=row_index, column=3, value=middle_names.get(prefix, "")).border = BORDER
            ws.cell(row=row_index, column=4, value=count).border = BORDER

    row_index += 2
    note = ("BOSS 行业分类与《国家职业分类大典》不是同一体系，本页只按职业中类的名称含义给出粗对照，"
            "不是官方的一一映射；具体岗位请用“长表-可筛选”按职业名称或编码检索。")
    cell = ws.cell(row=row_index, column=1, value=note)
    cell.font = SUB_FONT
    ws.merge_cells(start_row=row_index, start_column=1, end_row=row_index, end_column=4)
    ws.column_dimensions["A"].width = 30
    ws.column_dimensions["B"].width = 14
    ws.column_dimensions["C"].width = 46
    ws.column_dimensions["D"].width = 18
    return ws


def write_notes(wb, data, items, pending_count):
    ws = wb.create_sheet("说明")
    lines = [
        ("国家职业标准 技能要求权重表", ""),
        ("数据来源", "技能人才评价工作网（https://www.osta.org.cn/skillStandard）公开的国家职业标准 PDF"),
        ("抓取日期", "2026-10-28"),
        ("覆盖范围", f"该站公开标准共 {data.get('total', 0)} 条；校验通过 {len(items)} 个，"
                     f"待核对或未设 4.2 权重表 {pending_count} 个（见“待核对”页）"),
        ("权重口径", "逐条取自各标准“4.2 技能要求权重表”原文；表中“—”表示该等级不设该项，不是 0"),
        ("等级列", "按标准实际设置的等级列展示；四列标准没有五级/初级工，表格对应列留空"),
        ("专业方向", "工程技术人员类标准（2-02）的 4.2 为“专业能力要求权重表”，按专业方向分多张子表；"
                     "本表在项目名前加“方向｜”前缀区分"),
        ("校验", "每个等级列的项目权重合计应为 100；列合计异常的记录在“权重矩阵”的“列合计”列标注"),
        ("未收录", "部分标准没有 4.2 权重表，或 PDF 为扫描件无法解析，未列入本表"),
        ("使用限制", "本表只含权重，不含各职业的技能要求、相关知识要求原文；完整能力图谱需另行解析标准第 3 章"),
    ]
    ws["A1"] = "说明"
    ws["A1"].font = TITLE_FONT
    for index, (key, value) in enumerate(lines[1:], start=3):
        ws.cell(row=index, column=1, value=key).font = Font(bold=True)
        ws.cell(row=index, column=2, value=value).alignment = Alignment(wrap_text=True)
    ws.column_dimensions["A"].width = 16
    ws.column_dimensions["B"].width = 110


def write_json(items):
    payload = {
        "schema": "competency-weights/1.0",
        "source": "https://www.osta.org.cn/skillStandard",
        "retrieved_at": "2026-10-28",
        "note": "仅技能要求权重表；完整岗位能力图谱需结合标准第 3 章工作要求解析",
        "jobs": [
            {
                "code": item["code"],
                "name": item["name"],
                "levels": item.get("levels", []),
                "domains": [
                    {"name": entry["name"], "weights": {
                        level: entry["values"][index]
                        for index, level in enumerate(item.get("levels", []))
                        if index < len(entry["values"])
                    }}
                    for entry in item["rows"]
                ],
                "source": {
                    "file": item.get("standardInfoName", ""),
                    "pages": item.get("pages", []),
                    "issue_time": item.get("issueTime", ""),
                    "issue_number": item.get("issueNumber", "")
                }
            }
            for item in items
        ]
    }
    json.dump(payload, open(JSON_OUT, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
    return JSON_OUT


def main():
    data, items, pending = load()
    wb = Workbook()
    wb.remove(wb.active)
    write_matrix(wb, items)
    write_long(wb, items)
    write_industry_map(wb, items)
    write_pending(wb, pending)
    write_notes(wb, data, items, len(pending))
    wb.save(XLSX)
    json_path = write_json(items)
    print("xlsx", XLSX)
    print("json", json_path)
    print("jobs", len(items))


if __name__ == "__main__":
    main()
