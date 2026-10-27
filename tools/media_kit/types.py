# -*- coding: utf-8 -*-
"""标准化的输入/输出数据类型。

对外只暴露 dataclass，均可通过 ``to_dict()`` 转成可 JSON 序列化的 dict，
方便直接返回给 HTTP 接口或写入数据库。
"""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any, Protocol, runtime_checkable

from .errors import ErrorInfo


# ---------------------------------------------------------------------------
# 通用
# ---------------------------------------------------------------------------
@dataclass
class VideoStreamInfo:
    codec: str = ""
    width: int = 0
    height: int = 0
    fps: float = 0.0
    pix_fmt: str = ""
    bit_rate: int = 0

    def to_dict(self) -> dict[str, Any]:
        return {
            "codec": self.codec,
            "width": self.width,
            "height": self.height,
            "fps": self.fps,
            "pix_fmt": self.pix_fmt,
            "bit_rate": self.bit_rate,
        }


@dataclass
class AudioStreamInfo:
    codec: str = ""
    sample_rate: int = 0
    channels: int = 0
    bit_rate: int = 0

    def to_dict(self) -> dict[str, Any]:
        return {
            "codec": self.codec,
            "sample_rate": self.sample_rate,
            "channels": self.channels,
            "bit_rate": self.bit_rate,
        }


@dataclass
class VideoMetadata:
    path: str
    format: str
    size_bytes: int
    duration_sec: float = 0.0
    bit_rate: int = 0
    container: str = ""
    video: VideoStreamInfo | None = None
    audio: AudioStreamInfo | None = None

    def to_dict(self) -> dict[str, Any]:
        return {
            "path": self.path,
            "format": self.format,
            "size_bytes": self.size_bytes,
            "duration_sec": self.duration_sec,
            "bit_rate": self.bit_rate,
            "container": self.container,
            "video": self.video.to_dict() if self.video else None,
            "audio": self.audio.to_dict() if self.audio else None,
        }


@dataclass
class VideoOutputs:
    """解析产物路径。未启用的阶段为 None / 空列表。"""

    output_dir: str = ""
    normalized_path: str | None = None
    audio_path: str | None = None
    frames: list[str] = field(default_factory=list)
    transcript: dict[str, Any] | None = None

    def to_dict(self) -> dict[str, Any]:
        return {
            "output_dir": self.output_dir,
            "normalized_path": self.normalized_path,
            "audio_path": self.audio_path,
            "frames": list(self.frames),
            "transcript": self.transcript,
        }


@dataclass
class VideoResult:
    ok: bool
    metadata: VideoMetadata | None = None
    outputs: VideoOutputs = field(default_factory=VideoOutputs)
    error: ErrorInfo | None = None
    warnings: list[ErrorInfo] = field(default_factory=list)
    elapsed_sec: float = 0.0

    def to_dict(self) -> dict[str, Any]:
        return {
            "ok": self.ok,
            "metadata": self.metadata.to_dict() if self.metadata else None,
            "outputs": self.outputs.to_dict(),
            "error": self.error.to_dict() if self.error else None,
            "warnings": [w.to_dict() for w in self.warnings],
            "elapsed_sec": round(self.elapsed_sec, 3),
        }


# ---------------------------------------------------------------------------
# 文档
# ---------------------------------------------------------------------------
@dataclass
class DocumentPage:
    index: int
    text: str
    ocr: bool = False
    meta: dict[str, Any] = field(default_factory=dict)

    def to_dict(self) -> dict[str, Any]:
        return {"index": self.index, "text": self.text, "ocr": self.ocr, "meta": self.meta}


@dataclass
class Heading:
    level: int
    title: str
    page: int | None = None
    number: str = ""

    def to_dict(self) -> dict[str, Any]:
        return {"level": self.level, "title": self.title, "page": self.page, "number": self.number}


@dataclass
class AttachmentInfo:
    name: str
    path: str
    content_type: str
    source: str
    size_bytes: int

    def to_dict(self) -> dict[str, Any]:
        return {
            "name": self.name,
            "path": self.path,
            "content_type": self.content_type,
            "source": self.source,
            "size_bytes": self.size_bytes,
        }


@dataclass
class DocumentMetadata:
    path: str
    format: str
    size_bytes: int
    page_count: int = 0
    title: str = ""
    author: str = ""
    producer: str = ""
    extra: dict[str, Any] = field(default_factory=dict)

    def to_dict(self) -> dict[str, Any]:
        return {
            "path": self.path,
            "format": self.format,
            "size_bytes": self.size_bytes,
            "page_count": self.page_count,
            "title": self.title,
            "author": self.author,
            "producer": self.producer,
            "extra": self.extra,
        }


@dataclass
class DocumentResult:
    ok: bool
    metadata: DocumentMetadata | None = None
    text: str = ""
    pages: list[DocumentPage] = field(default_factory=list)
    structure: list[Heading] = field(default_factory=list)
    attachments: list[AttachmentInfo] = field(default_factory=list)
    error: ErrorInfo | None = None
    warnings: list[ErrorInfo] = field(default_factory=list)
    elapsed_sec: float = 0.0

    def to_dict(self) -> dict[str, Any]:
        return {
            "ok": self.ok,
            "metadata": self.metadata.to_dict() if self.metadata else None,
            "text": self.text,
            "pages": [p.to_dict() for p in self.pages],
            "structure": [h.to_dict() for h in self.structure],
            "attachments": [a.to_dict() for a in self.attachments],
            "error": self.error.to_dict() if self.error else None,
            "warnings": [w.to_dict() for w in self.warnings],
            "elapsed_sec": round(self.elapsed_sec, 3),
        }


# ---------------------------------------------------------------------------
# 可插拔的语音转写接口
# ---------------------------------------------------------------------------
@runtime_checkable
class Transcriber(Protocol):
    """语音转写扩展点。

    实现该协议即可接入任意 ASR（百炼、本地 Whisper 等），核心流程不依赖具体服务。

    约定返回：
        {
          "model": "模型名",
          "sentences": [{"begin_time": 0, "end_time": 1200, "text": "..."}],
          # 或至少提供 "text": "完整文本"
        }
    """

    def transcribe(self, audio_path: str, *, language: str | None = None) -> dict[str, Any]:
        ...
