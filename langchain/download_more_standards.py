import os
import sys
import urllib.request

import pymupdf

BASE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(BASE, "data", "sources")
os.makedirs(OUT, exist_ok=True)

SOURCES = [
    (
        "信息通信网络运行管理员-2019.pdf",
        "https://chinajob.mohrss.gov.cn/upload/resources/jnbzpdf/4ccc6a76ab6420078cc004.pdf",
    ),
    (
        "数据库运行管理员-2025.pdf",
        "https://www.osta.org.cn/api/sys/downloadFile/decrypt?fileName=4eNsZIOv3WFNDfbnldJHeA%2F2025%2F3%2F2%2F289d19d1c4d34aa296dad4a3a3e5020e.pdf",
    ),
]

for name, url in SOURCES:
    pdf_path = os.path.join(OUT, name)
    if not os.path.exists(pdf_path):
        print("downloading", name)
        request = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
        with urllib.request.urlopen(request, timeout=180) as response:
            data = response.read()
        with open(pdf_path, "wb") as f:
            f.write(data)
    doc = pymupdf.open(pdf_path)
    text = "\n".join(page.get_text() for page in doc)
    txt_path = pdf_path.replace(".pdf", ".txt")
    with open(txt_path, "w", encoding="utf-8", newline="\n") as f:
        f.write(text)
    print(name, "pages=", doc.page_count, "chars=", len(text))
