# -*- coding: utf-8 -*-
"""文档处理器基类。

子类只需实现 ``extract``，返回 :class:`DocumentExtract`；
正文拼接、空白归一化、截断、结果包装等公共逻辑由基类统一完成。
"""

from __future__ import annotations

import os
import re
from abc import ABC, abstractmethod
from dataclasses import dataclass, field

from ...config import DocumentConfig
from ...errors import ErrorInfo
from ...types import AttachmentInfo, DocumentMetadata, DocumentPage, DocumentResult, Heading


@dataclass
class DocumentExtract:
    """处理器内部产物。"""

    metadata: DocumentMetadata
    pages: list[DocumentPage] = field(default_factory=list)
    structure: list[Heading] = field(default_factory=list)
    attachments: list[AttachmentInfo] = field(default_factory=list)
    warnings: list[ErrorInfo] = field(default_factory=list)


def normalize_whitespace(text: str) -> str:
    """去行尾空白、压缩连续空行。"""
    lines = [line.rstrip() for line in text.replace("\r\n", "\n").replace("\r", "\n").split("\n")]
    text = "\n".join(lines)
    return re.sub(r"\n{3,}", "\n\n", text).strip()


class BaseDocumentHandler(ABC):
    """文档格式处理器基类。"""

    name: str = "base"
    formats: tuple[str, ...] = ()

    @abstractmethod
    def extract(
        self,
        source: str,
        config: DocumentConfig,
        attachment_dir: str | None,
    ) -> DocumentExtract:
        """提取元数据、分页文本、结构与附件。"""

    # ------------------------------------------------------------------
    def parse(
        self,
        source: str,
        config: DocumentConfig,
        attachment_dir: str | None,
    ) -> DocumentResult:
        data = self.extract(source, config, attachment_dir)

        text = (config.text.page_separator or "\n\n").join(p.text for p in data.pages if p.text)
        if config.text.normalize_whitespace:
            text = normalize_whitespace(text)
        if config.text.max_chars and len(text) > config.text.max_chars:
            text = text[: config.text.max_chars]

        return DocumentResult(
            ok=True,
            metadata=data.metadata,
            text=text,
            pages=data.pages,
            structure=data.structure,
            attachments=data.attachments,
            warnings=data.warnings,
        )

    # ------------------------------------------------------------------
    @staticmethod
    def _attachment_dir(config: DocumentConfig, base_dir: str) -> str | None:
        if not config.attachments.enabled:
            return None
        target = os.path.join(base_dir, "attachments")
        os.makedirs(target, exist_ok=True)
        return target
