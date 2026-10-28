# -*- coding: utf-8 -*-
"""命令行入口：读入 JSON 输入，调用工作流，把数据表打到标准输出（不写文件）。"""
import argparse
import json
import sys
import time

from .client import LLMClient
from .render import print_report
from .workflow import generate, load_config, WorkflowError


def _read_input(path):
    if path:
        with open(path, "r", encoding="utf-8-sig") as f:
            return json.load(f)
    raw = sys.stdin.read()
    if not raw.strip():
        raise WorkflowError("没有输入：请用 --input 指定 JSON 文件，或从标准输入传入 JSON")
    return json.loads(raw)


def _check(cfg):
    client = LLMClient(cfg["api"])
    started = time.time()
    reply = client.chat_text("你是一个连通性测试助手。", "只回复两个字：正常", max_tokens=16)
    elapsed = time.time() - started
    print("接口连通正常（%.2fs）｜模型：%s｜回复：%s" % (elapsed, cfg["api"]["model"], reply.strip()[:40]))


def main(argv=None):
    try:  # 控制台编码不可控时，宁可替换字符也不要抛 UnicodeEncodeError
        sys.stdout.reconfigure(errors="replace")
        sys.stderr.reconfigure(errors="replace")
    except AttributeError:
        pass

    parser = argparse.ArgumentParser(
        prog="talent_workflow",
        description="人才培养方案智能数据工作流：输入材料，产出可渲染的数据表（不落文件）",
    )
    parser.add_argument("-i", "--input", help="输入 JSON 文件；缺省从标准输入读取")
    parser.add_argument("-c", "--config", help="config.json 路径")
    parser.add_argument("-f", "--format", choices=["table", "json"], default="table", help="输出格式，默认 table")
    parser.add_argument("--check", action="store_true", help="只测试 DeepSeek 接口连通性")
    parser.add_argument("--api-key", help="临时指定 API Key（优先级最高）")
    parser.add_argument("-q", "--quiet", action="store_true", help="不打印进度")
    args = parser.parse_args(argv)

    try:
        cfg = load_config(args.config)
        if args.api_key:
            cfg["api"]["api_key"] = args.api_key
        if args.check:
            _check(cfg)
            return 0

        payload = _read_input(args.input)
        logger = (lambda msg: None) if args.quiet else (lambda msg: print("· " + msg, file=sys.stderr))
        result = generate(payload, config=cfg, logger=logger)

        if args.format == "json":
            print(json.dumps(result, ensure_ascii=False, indent=2))
        else:
            print_report(result)
        return 0
    except (WorkflowError, ValueError) as exc:
        print("[错误] %s" % exc, file=sys.stderr)
        return 2
    except KeyboardInterrupt:
        print("已中断", file=sys.stderr)
        return 130


if __name__ == "__main__":
    sys.exit(main())
