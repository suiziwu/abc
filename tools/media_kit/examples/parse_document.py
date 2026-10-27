# -*- coding: utf-8 -*-
"""文档解析示例。

运行：
    python media_kit/examples/parse_document.py D:/path/book.pdf --out D:/tmp/out --attachments
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[2]))

from media_kit import DocumentConfig, parse_document  # noqa: E402


def main() -> int:
    parser = argparse.ArgumentParser(description="media_kit 文档解析示例")
    parser.add_argument("source", help="文档路径（pdf/docx/pptx/xlsx/csv/md/txt/html）")
    parser.add_argument("--out", help="输出目录（默认：源文件同级的 <名称>_parsed）")
    parser.add_argument("--attachments", action="store_true", help="提取文档内嵌图片/媒体")
    parser.add_argument("--no-ocr", action="store_true", help="扫描版 PDF 不做 OCR")
    parser.add_argument("--max-chars", type=int, default=0, help="截断正文长度（0=不截断）")
    parser.add_argument("--strict", action="store_true", help="出错时抛出异常")
    parser.add_argument("--text", action="store_true", help="同时打印纯文本")
    args = parser.parse_args()

    config = DocumentConfig.from_dict(
        {
            "output_dir": args.out,
            "strict": args.strict,
            "pdf": {"ocr_fallback": not args.no_ocr},
            "text": {"max_chars": args.max_chars},
            "attachments": {"enabled": args.attachments},
        }
    )
    result = parse_document(args.source, config=config)
    payload = result.to_dict()
    if not args.text:
        payload["text"] = payload["text"][:500] + ("…" if len(payload["text"]) > 500 else "")
    print(json.dumps(payload, ensure_ascii=False, indent=2))
    return 0 if result.ok else 1


if __name__ == "__main__":
    raise SystemExit(main())
