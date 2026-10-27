# -*- coding: utf-8 -*-
"""文档结构（标题层级）识别。

优先使用文档自带目录（PDF 书签 / Markdown 标题 / PPT 标题 / Excel 工作表），
缺失时按可配置的标题正则从正文行中识别。
"""

from __future__ import annotations

import re
from typing import Iterable, Sequence

from ..config import StructureConfig
from ..types import DocumentPage, Heading

# 默认规则：第X章 / 第X节 / 1、1.1、1.1.1 …… 后面可跟任意标题
DEFAULT_HEADING_REGEX = (
    r"^(?:(?P<chapter>第[一二三四五六七八九十百零\d]+[章节])|(?P<number>\d{1,2}(?:\.\d{1,2}){0,3}))"
    r"[\s、.．:：]*(?P<title>.{1,60})$"
)


def _level_of(chapter: str | None, number: str | None, max_level: int) -> int:
    if chapter:
        return 1 if chapter.endswith("章") else 2
    level = (number.count(".") + 1) if number else 1
    return max(1, min(level, max_level))


def headings_from_pages(
    pages: Sequence[DocumentPage],
    config: StructureConfig,
) -> list[Heading]:
    """从页面文本中识别标题，返回带页码的层级列表。"""
    if not config.enabled:
        return []
    pattern = re.compile(config.heading_regex or DEFAULT_HEADING_REGEX)
    result: list[Heading] = []
    seen: set[tuple[int | None, str]] = set()

    for page in pages:
        for raw in page.text.splitlines():
            line = raw.strip()
            if not line or len(line) > 60 or "。" in line:
                continue
            match = pattern.match(line)
            if not match:
                continue
            groups = match.groupdict()
            chapter = groups.get("chapter")
            number = groups.get("number") or ""
            title = (groups.get("title") or "").strip(" .．、:：")
            if chapter:
                title = f"{chapter} {title}".strip()
            if not title or len(title) < 2:
                continue
            level = _level_of(chapter, number, config.max_level)
            if level > config.max_level:
                continue
            key = (page.index, title)
            if key in seen:
                continue
            seen.add(key)
            result.append(Heading(level=level, title=title, page=page.index, number=number))
    return result


def headings_from_markdown(text: str, max_level: int = 6) -> list[Heading]:
    """Markdown 的 # 标题。"""
    result: list[Heading] = []
    for line in text.splitlines():
        m = re.match(r"^(#{1,6})\s+(.+?)\s*$", line)
        if not m:
            continue
        level = len(m.group(1))
        if level <= max_level:
            result.append(Heading(level=level, title=m.group(2), page=None, number=""))
    return result


def headings_from_html(items: Iterable[tuple[int, str]]) -> list[Heading]:
    """(level, text) 序列转 Heading。"""
    return [Heading(level=level, title=text, page=None, number="") for level, text in items]
