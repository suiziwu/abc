# -*- coding: utf-8 -*-
"""把四份标准的 4.2 技能要求权重表页合并成一个便于查看的摘录 PDF。"""
import os

import pymupdf

BASE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.abspath(os.path.join(BASE, "..", "..", "岗位能力权重表-4份标准摘录.pdf"))

SOURCES = [
    ("数据库运行管理员 4-04-05-04（2025年版，三级/高级工列）",
     os.path.join(BASE, "data", "sources", "数据库运行管理员-2025.pdf")),
    ("计算机程序设计员 4-04-05-01（2022年版，三级/高级工列）",
     os.path.join(BASE, "data", "sources", "计算机程序设计员-国家职业技能标准-2022.pdf")),
    ("计算机软件测试员 4-04-05-02（2024征求意见稿，四级/中级工列）",
     os.path.join(BASE, "data", "sources", "计算机软件测试员-国家职业标准-征求意见稿-2024.pdf")),
    ("信息通信网络运行管理员 4-04-04-01（2019年版，三级/高级工列）",
     os.path.join(BASE, "data", "sources", "信息通信网络运行管理员-2019.pdf")),
]


def main():
    merged = pymupdf.open()
    toc = []
    for index, (title, path) in enumerate(SOURCES, start=1):
        source = pymupdf.open(path)
        last = source.page_count - 1
        merged.insert_pdf(source, from_page=last, to_page=last)
        toc.append([1, title, index])
        source.close()
    merged.set_toc(toc)
    merged.save(OUT)
    merged.close()
    print(OUT)


if __name__ == "__main__":
    main()
