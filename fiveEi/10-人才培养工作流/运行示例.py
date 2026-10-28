# -*- coding: utf-8 -*-
"""演示脚本：读示例输入 → 调 DeepSeek → 直接打印数据表。

运行前先配置 Key（二选一）：
    1) 设置环境变量 DEEPSEEK_API_KEY
    2) 打开同目录 config.json，填写 api.api_key

然后：
    python 运行示例.py
"""
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from talent_workflow import generate, print_report  # noqa: E402


def main():
    base = os.path.dirname(os.path.abspath(__file__))
    with open(os.path.join(base, "示例输入.json"), "r", encoding="utf-8-sig") as f:
        payload = json.load(f)

    result = generate(payload, logger=lambda msg: print("· " + msg))
    print_report(result)

    # 页面/后端要拿结构化数据时，直接用这个 dict，不要落文件：
    #   print(json.dumps(result, ensure_ascii=False))
    #   return result
    return result


if __name__ == "__main__":
    main()
