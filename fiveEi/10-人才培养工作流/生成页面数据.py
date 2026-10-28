# -*- coding: utf-8 -*-
"""预生成模式：跑一次工作流，把结果写成页面可直接加载的 数据.js（同时留一份 数据.json）。

用法：
    python 生成页面数据.py                     # 用 示例输入.json，调接口重新生成
    python 生成页面数据.py 我的输入.json        # 指定输入
    python 生成页面数据.py --复用              # 不调接口，用现有 数据.json 重写页面数据

页面打开时只读 数据.js，不调用任何接口，秒开、零成本；数据要更新时重跑本脚本即可。
每次生成都会给 index.html 里的 数据.js 加一个版本号，避免浏览器缓存旧数据。
"""
import json
import os
import re
import sys
import time

BASE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, BASE)

from talent_workflow import generate  # noqa: E402

OUT_DIR = os.path.join(BASE, "网页看板")
INDEX_HTML = os.path.join(OUT_DIR, "index.html")


def bump_script_version():
    """给 index.html 的 数据.js 换一个版本号，强制浏览器重新加载。"""
    if not os.path.exists(INDEX_HTML):
        return None
    with open(INDEX_HTML, "r", encoding="utf-8-sig") as f:
        html = f.read()
    version = time.strftime("%Y%m%d%H%M%S")
    new_html, count = re.subn(r'\./数据\.js(?:\?v=[^"]*)?', "./数据.js?v=%s" % version, html)
    if count:
        with open(INDEX_HTML, "w", encoding="utf-8") as f:
            f.write(new_html)
    return version


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    reuse = "--复用" in sys.argv[1:]
    input_path = args[0] if args else os.path.join(BASE, "示例输入.json")

    data_json = os.path.join(OUT_DIR, "数据.json")
    if reuse:
        if not os.path.exists(data_json):
            print("[错误] 没有可复用的 数据.json，请先正常运行一次")
            return 2
        with open(data_json, "r", encoding="utf-8-sig") as f:
            result = json.load(f)
        print("复用现有数据：%s" % data_json)
    else:
        if not os.path.exists(input_path):
            print("[错误] 找不到输入文件：%s" % input_path)
            return 2
        with open(input_path, "r", encoding="utf-8-sig") as f:
            payload = json.load(f)
        started = time.time()
        print("正在生成：%s" % os.path.basename(input_path))
        result = generate(payload, logger=lambda msg: print("· " + msg))
        print("生成完成，用时 %.1fs" % (time.time() - started))

    os.makedirs(OUT_DIR, exist_ok=True)
    with open(data_json, "w", encoding="utf-8") as f:
        json.dump(result, f, ensure_ascii=False, indent=2)

    # 页面用 <script src="./数据.js"> 同步加载，避免 fetch 在 file:// 下被浏览器拦截
    with open(os.path.join(OUT_DIR, "数据.js"), "w", encoding="utf-8") as f:
        f.write("/* 由 生成页面数据.py 自动生成，请勿手改；重新运行脚本即可更新 */\n")
        f.write("window.TALENT_DATA = ")
        json.dump(result, f, ensure_ascii=False, indent=2)
        f.write(";\n")

    version = bump_script_version()
    validation = result.get("validation") or {}
    print("\n页面数据已更新%s" % (("（版本 %s）" % version) if version else ""))
    print("  页面数据：%s" % os.path.join(OUT_DIR, "数据.js"))
    print("  结构备份：%s" % data_json)
    print("  KPI %d 项 / 表 %d 张 / 引用校验：%s" % (
        len(result.get("kpi", [])), len(result.get("tables", {})),
        "通过" if validation.get("通过") else "待核"))
    print("\n直接双击打开：%s" % INDEX_HTML)
    return 0


if __name__ == "__main__":
    sys.exit(main())
