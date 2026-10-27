# -*- coding: utf-8 -*-
"""文档解析统一入口：校验、路由、附件目录、异常收敛与计时。"""

from __future__ import annotations

import os
import time
from pathlib import Path

from ..config import DocumentConfig
from ..errors import ErrorCode, ParserError
from ..registry import get_document_handler, supported_document_formats
from ..types import DocumentResult


class DocumentParser:
    """可复用的文档解析器。"""

    def __init__(self, config: DocumentConfig | dict | None = None) -> None:
        if isinstance(config, dict):
            config = DocumentConfig.from_dict(config)
        self.config = config or DocumentConfig()

    # ------------------------------------------------------------------
    def parse(self, source: str | os.PathLike, *, strict: bool | None = None) -> DocumentResult:
        """解析文档。

        Args:
            source: 文档路径（pdf/docx/pptx/xlsx/xlsm/csv/md/txt/html）。
            strict: 为 True 时错误抛异常；默认取配置中的 ``strict``。

        Returns:
            DocumentResult: ``ok=False`` 时 ``error`` 为标准错误码对象。
        """
        started = time.time()
        strict = self.config.strict if strict is None else strict
        output_dir = ""

        try:
            path = self._validate(source)
            fmt = path.suffix.lower()
            handler_cls = get_document_handler(str(path))
            if handler_cls is None:
                raise ParserError(ErrorCode.UNSUPPORTED_DOCUMENT_FORMAT, fmt, stage="dispatch")

            output_dir = self._output_dir(path)
            attachment_dir = None
            if self.config.attachments.enabled:
                attachment_dir = os.path.join(output_dir, "attachments")
                os.makedirs(attachment_dir, exist_ok=True)

            handler = handler_cls()
            result = handler.parse(str(path), self.config, attachment_dir)
            result.elapsed_sec = time.time() - started
            return result

        except ParserError as exc:
            result = DocumentResult(ok=False, error=exc.info, elapsed_sec=time.time() - started)
            if strict:
                raise
            return result
        except Exception as exc:  # 兜底
            err = ParserError(ErrorCode.INTERNAL_ERROR, "文档解析出现未预期错误", stage="internal", cause=exc)
            result = DocumentResult(ok=False, error=err.info, elapsed_sec=time.time() - started)
            if strict:
                raise err from exc
            return result

    # ------------------------------------------------------------------
    def _validate(self, source: str | os.PathLike) -> Path:
        path = Path(source).expanduser()
        if not path.exists() or not path.is_file():
            raise ParserError(ErrorCode.DOCUMENT_FILE_NOT_FOUND, str(path), stage="validate")
        if not os.access(path, os.R_OK):
            raise ParserError(ErrorCode.DOCUMENT_FILE_NOT_FOUND, f"无读取权限：{path}", stage="validate")
        if path.stat().st_size == 0:
            raise ParserError(ErrorCode.DOCUMENT_FILE_EMPTY, str(path), stage="validate")
        size_mb = path.stat().st_size / 1024 / 1024
        if size_mb > self.config.max_size_mb:
            raise ParserError(
                ErrorCode.DOCUMENT_TOO_LARGE,
                f"文件 {size_mb:.1f} MB 超过上限 {self.config.max_size_mb} MB",
                stage="validate",
            )

        fmt = path.suffix.lower()
        allowed = self.config.allowed_formats
        registered = supported_document_formats()
        if not fmt or (allowed is not None and fmt not in allowed) or fmt not in registered:
            raise ParserError(
                ErrorCode.UNSUPPORTED_DOCUMENT_FORMAT,
                f"格式 {fmt or '(无扩展名)'} 不在支持列表；已注册：{', '.join(registered)}",
                stage="validate",
            )
        return path

    def _output_dir(self, path: Path) -> str:
        directory = self.config.output_dir
        target = Path(directory).expanduser() if directory else path.parent / f"{path.stem}_parsed"
        target.mkdir(parents=True, exist_ok=True)
        return str(target)
