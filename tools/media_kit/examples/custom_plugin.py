# -*- coding: utf-8 -*-
"""插件扩展示例：不改核心流程，新增一个文档格式和一种视频扩展名。

运行：
    python media_kit/examples/custom_plugin.py
"""

from __future__ import annotations

import os
import sys
import tempfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[2]))

from media_kit import (  # noqa: E402
    parse_document,
    register_document_handler,
    register_video_handler,
    supported_document_formats,
)
from media_kit.document.handlers.base import BaseDocumentHandler, DocumentExtract  # noqa: E402
from media_kit.types import DocumentMetadata, DocumentPage, Heading  # noqa: E402
from media_kit.video.handlers.ffmpeg_video import FFmpegVideoHandler  # noqa: E402


@register_document_handler(".log")
class LogDocumentHandler(BaseDocumentHandler):
    """把日志文件当作一页文档，按行输出；含 ERROR 的行标记为标题便于检索。"""

    name = "log"

    def extract(self, source: str, config, attachment_dir: str | None) -> DocumentExtract:
        with open(source, encoding="utf-8", errors="replace") as fp:
            lines = [line.rstrip() for line in fp]
        structure = [
            Heading(level=2, title=line[:60], page=1)
            for line in lines
            if "ERROR" in line
        ] if config.structure.enabled else []
        return DocumentExtract(
            metadata=DocumentMetadata(
                path=source, format=".log", size_bytes=os.path.getsize(source), page_count=1
            ),
            pages=[DocumentPage(index=1, text="\n".join(lines))],
            structure=structure,
        )


# 新增一个视频扩展名：直接复用 ffmpeg 流程（容器可被 ffmpeg 解码即可）
register_video_handler(".divx")(FFmpegVideoHandler)

print("文档格式：", supported_document_formats())

tmp = tempfile.mkdtemp(prefix="mk_plugin_")
log_path = os.path.join(tmp, "demo.log")
with open(log_path, "w", encoding="utf-8") as fp:
    fp.write("INFO  start\nERROR disk full\nINFO  done\n")

result = parse_document(log_path)
print("ok:", result.ok, "| structure:", [h.title for h in result.structure])
print("text:", result.text.replace("\n", " | "))
