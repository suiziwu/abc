# -*- coding: utf-8 -*-
"""标准化错误码与异常。

约定：
- E1xxx 视频解析
- E2xxx 文档解析
- E3xxx 公共/通用
- E9xxx 内部错误

所有对外结果中的错误都用 :class:`ErrorInfo` 表示；``strict=True`` 时抛出
:class:`ParserError`，其 ``info`` 与返回值里的错误结构一致。
"""

from __future__ import annotations

from dataclasses import dataclass
from enum import Enum
from typing import Any


class ErrorCode(str, Enum):
    """标准错误码（字符串枚举，便于直接写入 JSON）。"""

    # ---- 视频 ----
    UNSUPPORTED_VIDEO_FORMAT = "E1001"
    VIDEO_FILE_NOT_FOUND = "E1002"
    VIDEO_FILE_EMPTY = "E1003"
    VIDEO_PERMISSION_DENIED = "E1004"
    VIDEO_INVALID = "E1005"
    VIDEO_PROBE_FAILED = "E1006"
    VIDEO_TRANSCODE_FAILED = "E1007"
    VIDEO_AUDIO_EXTRACT_FAILED = "E1008"
    VIDEO_FRAME_EXTRACT_FAILED = "E1009"
    VIDEO_TRANSCRIBE_FAILED = "E1010"
    VIDEO_TOO_LARGE = "E1011"

    # ---- 文档 ----
    UNSUPPORTED_DOCUMENT_FORMAT = "E2001"
    DOCUMENT_FILE_NOT_FOUND = "E2002"
    DOCUMENT_FILE_EMPTY = "E2003"
    DOCUMENT_TEXT_EXTRACT_FAILED = "E2004"
    DOCUMENT_STRUCTURE_PARSE_FAILED = "E2005"
    DOCUMENT_ATTACHMENT_EXTRACT_FAILED = "E2006"
    DOCUMENT_OCR_UNAVAILABLE = "E2007"
    DOCUMENT_OCR_FAILED = "E2008"
    DOCUMENT_TOO_LARGE = "E2009"
    DOCUMENT_NO_TEXT_LAYER = "E2010"

    # ---- 公共 ----
    DEPENDENCY_MISSING = "E3001"
    TIMEOUT = "E3002"
    INVALID_CONFIG = "E3003"
    INTERNAL_ERROR = "E9000"


DEFAULT_MESSAGES: dict[ErrorCode, str] = {
    ErrorCode.UNSUPPORTED_VIDEO_FORMAT: "不支持的视频格式",
    ErrorCode.VIDEO_FILE_NOT_FOUND: "视频文件不存在或不可访问",
    ErrorCode.VIDEO_FILE_EMPTY: "视频文件为空",
    ErrorCode.VIDEO_PERMISSION_DENIED: "没有读取视频文件的权限",
    ErrorCode.VIDEO_INVALID: "视频文件损坏或不是合法的媒体文件",
    ErrorCode.VIDEO_PROBE_FAILED: "读取视频元数据失败",
    ErrorCode.VIDEO_TRANSCODE_FAILED: "视频转码/转封装失败",
    ErrorCode.VIDEO_AUDIO_EXTRACT_FAILED: "音轨提取失败",
    ErrorCode.VIDEO_FRAME_EXTRACT_FAILED: "关键帧抽取失败",
    ErrorCode.VIDEO_TRANSCRIBE_FAILED: "语音转写失败",
    ErrorCode.VIDEO_TOO_LARGE: "视频文件超过大小限制",
    ErrorCode.UNSUPPORTED_DOCUMENT_FORMAT: "不支持的文档格式",
    ErrorCode.DOCUMENT_FILE_NOT_FOUND: "文档不存在或不可访问",
    ErrorCode.DOCUMENT_FILE_EMPTY: "文档文件为空",
    ErrorCode.DOCUMENT_TEXT_EXTRACT_FAILED: "文档文本提取失败",
    ErrorCode.DOCUMENT_STRUCTURE_PARSE_FAILED: "文档结构解析失败",
    ErrorCode.DOCUMENT_ATTACHMENT_EXTRACT_FAILED: "文档附件提取失败",
    ErrorCode.DOCUMENT_OCR_UNAVAILABLE: "OCR 组件不可用",
    ErrorCode.DOCUMENT_OCR_FAILED: "OCR 处理失败",
    ErrorCode.DOCUMENT_TOO_LARGE: "文档超过大小限制",
    ErrorCode.DOCUMENT_NO_TEXT_LAYER: "文档没有可提取的文本层（可能是扫描件）",
    ErrorCode.DEPENDENCY_MISSING: "缺少运行依赖",
    ErrorCode.TIMEOUT: "处理超时",
    ErrorCode.INVALID_CONFIG: "配置参数不合法",
    ErrorCode.INTERNAL_ERROR: "内部错误",
}


@dataclass
class ErrorInfo:
    """可序列化的错误信息。"""

    code: ErrorCode
    message: str
    detail: str = ""
    stage: str = ""
    cause: str = ""

    def to_dict(self) -> dict[str, Any]:
        return {
            "code": self.code.value,
            "name": self.code.name,
            "message": self.message,
            "detail": self.detail,
            "stage": self.stage,
            "cause": self.cause,
        }

    def __str__(self) -> str:  # pragma: no cover - 便于日志
        return f"[{self.code.value}] {self.message}" + (f"：{self.detail}" if self.detail else "")


class ParserError(Exception):
    """解析异常：携带标准错误码、阶段与底层原因。"""

    def __init__(
        self,
        code: ErrorCode,
        detail: str = "",
        *,
        stage: str = "",
        cause: BaseException | None = None,
        message: str | None = None,
    ) -> None:
        self.code = code
        self.message = message or DEFAULT_MESSAGES.get(code, code.name)
        self.detail = detail
        self.stage = stage
        self.cause = cause
        super().__init__(f"[{code.value}] {self.message}" + (f"：{detail}" if detail else ""))

    @property
    def info(self) -> ErrorInfo:
        return ErrorInfo(
            code=self.code,
            message=self.message,
            detail=self.detail,
            stage=self.stage,
            cause=f"{type(self.cause).__name__}: {self.cause}" if self.cause else "",
        )

    def to_dict(self) -> dict[str, Any]:
        return self.info.to_dict()


def error_info(code: ErrorCode, detail: str = "", *, stage: str = "", cause: BaseException | None = None) -> ErrorInfo:
    """快捷构造 ErrorInfo。"""
    return ParserError(code, detail, stage=stage, cause=cause).info
