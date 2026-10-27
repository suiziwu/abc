# -*- coding: utf-8 -*-
"""media_kit 公共 API。

快速开始::

    from media_kit import parse_video, parse_document

    video = parse_video("demo.ts", config={"audio": {"enabled": True}})
    print(video.ok, video.metadata.duration_sec, video.outputs.audio_path)

    doc = parse_document("book.pdf")
    print(doc.ok, len(doc.pages), doc.text[:100])

统一对象模型与错误码定义见 ``media_kit.types`` / ``media_kit.errors``。
"""

from __future__ import annotations

from .config import (
    AttachmentConfig,
    AudioConfig,
    DocumentConfig,
    FrameConfig,
    PdfConfig,
    StructureConfig,
    TextConfig,
    TranscodeConfig,
    VideoConfig,
)
from .document.parser import DocumentParser
from .errors import DEFAULT_MESSAGES, ErrorCode, ErrorInfo, ParserError
from .registry import (
    load_builtin_plugins,
    register_document_handler,
    register_video_handler,
    registry_snapshot,
    supported_document_formats,
    supported_video_formats,
)
from .types import (
    AttachmentInfo,
    AudioStreamInfo,
    DocumentMetadata,
    DocumentPage,
    DocumentResult,
    Heading,
    Transcriber,
    VideoMetadata,
    VideoOutputs,
    VideoResult,
    VideoStreamInfo,
)
from .video.parser import VideoParser

__version__ = "1.0.0"

# 导入即注册内置处理器（插件机制）
load_builtin_plugins()

__all__ = [
    "__version__",
    # 入口
    "parse_video",
    "parse_document",
    "MediaKit",
    # 解析器
    "VideoParser",
    "DocumentParser",
    # 配置
    "VideoConfig",
    "DocumentConfig",
    "TranscodeConfig",
    "AudioConfig",
    "FrameConfig",
    "PdfConfig",
    "TextConfig",
    "StructureConfig",
    "AttachmentConfig",
    # 类型
    "VideoResult",
    "VideoMetadata",
    "VideoOutputs",
    "VideoStreamInfo",
    "AudioStreamInfo",
    "DocumentResult",
    "DocumentMetadata",
    "DocumentPage",
    "Heading",
    "AttachmentInfo",
    "Transcriber",
    # 错误
    "ErrorCode",
    "ErrorInfo",
    "ParserError",
    "DEFAULT_MESSAGES",
    # 插件
    "register_video_handler",
    "register_document_handler",
    "supported_video_formats",
    "supported_document_formats",
    "registry_snapshot",
]


def parse_video(source, config: VideoConfig | dict | None = None, *, transcriber=None, strict=None) -> VideoResult:
    """解析视频（一行式调用）。"""
    return VideoParser(config).parse(source, transcriber=transcriber, strict=strict)


def parse_document(source, config: DocumentConfig | dict | None = None, *, strict=None) -> DocumentResult:
    """解析文档（一行式调用）。"""
    return DocumentParser(config).parse(source, strict=strict)


class MediaKit:
    """持有配置的复用门面，适合在服务中长期使用。"""

    def __init__(
        self,
        video_config: VideoConfig | dict | None = None,
        document_config: DocumentConfig | dict | None = None,
    ) -> None:
        self.video = VideoParser(video_config)
        self.document = DocumentParser(document_config)

    def parse_video(self, source, *, transcriber=None, strict=None) -> VideoResult:
        return self.video.parse(source, transcriber=transcriber, strict=strict)

    def parse_document(self, source, *, strict=None) -> DocumentResult:
        return self.document.parse(source, strict=strict)
