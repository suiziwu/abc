# -*- coding: utf-8 -*-
"""纯文本类处理器：Markdown / TXT / HTML。"""

from __future__ import annotations

import os
from html.parser import HTMLParser
from pathlib import Path

from ...errors import ErrorCode, ParserError
from ...registry import register_document_handler
from ...types import DocumentMetadata, DocumentPage
from ..structure import headings_from_markdown, headings_from_pages
from .base import BaseDocumentHandler, DocumentExtract


def read_text(source: str) -> str:
    raw = Path(source).read_bytes()
    for encoding in ("utf-8-sig", "utf-8", "gbk", "gb18030", "latin-1"):
        try:
            return raw.decode(encoding)
        except UnicodeDecodeError:
            continue
    raise ParserError(ErrorCode.DOCUMENT_TEXT_EXTRACT_FAILED, "文本编码无法识别", stage="extract")


class _HtmlExtractor(HTMLParser):
    """收集可见文本与 h1-h6 标题。"""

    def __init__(self) -> None:
        super().__init__(convert_charrefs=True)
        self.lines: list[str] = []
        self.headings: list[tuple[int, str]] = []
        self._buffer: list[str] = []
        self._heading_level = 0
        self._skip_depth = 0

    def handle_starttag(self, tag, attrs):
        if tag in ("script", "style"):
            self._skip_depth += 1
        elif tag in (f"h{level}" for level in range(1, 7)):
            self._heading_level = int(tag[1])
            self._buffer = []
        elif tag in ("p", "div", "br", "li", "tr", "section", "article"):
            self._flush()

    def handle_endtag(self, tag):
        if tag in ("script", "style") and self._skip_depth:
            self._skip_depth -= 1
        elif tag.startswith("h") and len(tag) == 2 and tag[1].isdigit():
            text = "".join(self._buffer).strip()
            if text:
                self.headings.append((self._heading_level, text))
                self.lines.append(text)
            self._buffer = []
            self._heading_level = 0
        elif tag in ("p", "div", "li", "tr", "section", "article"):
            self._flush()

    def handle_data(self, data):
        if self._skip_depth:
            return
        text = data.strip()
        if text:
            self._buffer.append(text)

    def _flush(self):
        text = "".join(self._buffer).strip()
        if text:
            self.lines.append(text)
        self._buffer = []

    def close(self):
        super().close()
        self._flush()


@register_document_handler(".md", ".markdown", ".txt", ".html", ".htm")
class TextHandler(BaseDocumentHandler):
    name = "text"

    def extract(self, source: str, config, attachment_dir: str | None) -> DocumentExtract:
        suffix = Path(source).suffix.lower()
        raw = read_text(source)
        structure = []

        if suffix in (".html", ".htm"):
            parser = _HtmlExtractor()
            try:
                parser.feed(raw)
                parser.close()
            except Exception as exc:
                raise ParserError(
                    ErrorCode.DOCUMENT_TEXT_EXTRACT_FAILED, "HTML 解析失败", stage="extract", cause=exc
                ) from exc
            text = "\n".join(parser.lines)
            if config.structure.enabled:
                from ..structure import headings_from_html

                structure = headings_from_html(parser.headings)
        elif suffix in (".md", ".markdown"):
            text = raw
            if config.structure.enabled:
                structure = headings_from_markdown(raw, config.structure.max_level)
        else:
            text = raw
            page = DocumentPage(index=1, text=raw)
            if config.structure.enabled:
                structure = headings_from_pages([page], config.structure)

        metadata = DocumentMetadata(
            path=str(source),
            format=suffix,
            size_bytes=os.path.getsize(source),
            page_count=1,
            title=Path(source).stem,
            extra={"chars": len(raw)},
        )
        return DocumentExtract(
            metadata=metadata,
            pages=[DocumentPage(index=1, text=text)],
            structure=structure,
        )
