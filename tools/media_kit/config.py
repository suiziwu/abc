# -*- coding: utf-8 -*-
"""解析配置（全部可选，均有合理默认值）。

支持用 dict 覆盖默认值，便于从 YAML/JSON/数据库读取：

    cfg = VideoConfig.from_dict({
        "transcode": {"enabled": True, "mode": "copy"},
        "audio": {"enabled": True, "sample_rate": 16000},
        "frames": {"enabled": True, "interval_sec": 5},
    })

未知字段会被忽略，字段类型错误或取值越界会抛出 ``E3003 INVALID_CONFIG``。
"""

from __future__ import annotations

from dataclasses import dataclass, field, fields
from typing import Any, get_type_hints

from .errors import ErrorCode, ParserError


def _build(cls: type, data: dict[str, Any] | None):
    """按 dataclass 字段过滤并构造实例，支持嵌套 dataclass。

    注意：模块启用了 ``from __future__ import annotations``，字段类型是字符串，
    因此用 ``get_type_hints`` 解析出真实类型。
    """
    if data is None:
        return cls()
    if not isinstance(data, dict):
        raise ParserError(ErrorCode.INVALID_CONFIG, f"{cls.__name__} 需要 dict，实际为 {type(data).__name__}", stage="config")
    hints = get_type_hints(cls)
    kwargs: dict[str, Any] = {}
    for item in fields(cls):
        if item.name not in data:
            continue
        value = data[item.name]
        nested = hints.get(item.name)
        if isinstance(nested, type) and hasattr(nested, "__dataclass_fields__"):
            kwargs[item.name] = _build(nested, value)
        else:
            kwargs[item.name] = value
    return cls(**kwargs)


def _positive(name: str, value: Any) -> None:
    if not isinstance(value, (int, float)) or value <= 0:
        raise ParserError(ErrorCode.INVALID_CONFIG, f"{name} 必须为正数，实际为 {value!r}", stage="config")


@dataclass
class TranscodeConfig:
    """视频转码/转封装。mode=copy 时只换容器（快、无损）。"""

    enabled: bool = False
    container: str = "mp4"
    mode: str = "copy"  # copy | encode
    video_codec: str = "libx264"
    audio_codec: str = "aac"
    crf: int = 23
    preset: str = "veryfast"
    faststart: bool = True

    def validate(self) -> None:
        if self.mode not in ("copy", "encode"):
            raise ParserError(ErrorCode.INVALID_CONFIG, f"transcode.mode 只能是 copy/encode，实际 {self.mode!r}", stage="config")
        if not self.container.strip("."):
            raise ParserError(ErrorCode.INVALID_CONFIG, "transcode.container 不能为空", stage="config")
        if not 0 <= int(self.crf) <= 51:
            raise ParserError(ErrorCode.INVALID_CONFIG, f"transcode.crf 应在 0-51，实际 {self.crf}", stage="config")


@dataclass
class AudioConfig:
    """音轨提取（可对接任意 ASR）。"""

    enabled: bool = True
    format: str = "mp3"  # mp3 | wav | m4a
    sample_rate: int = 16000
    channels: int = 1
    bitrate: str = "48k"

    def validate(self) -> None:
        if self.format not in ("mp3", "wav", "m4a"):
            raise ParserError(ErrorCode.INVALID_CONFIG, f"audio.format 只支持 mp3/wav/m4a，实际 {self.format!r}", stage="config")
        if int(self.sample_rate) < 8000:
            raise ParserError(ErrorCode.INVALID_CONFIG, f"audio.sample_rate 不能低于 8000，实际 {self.sample_rate}", stage="config")
        if int(self.channels) not in (1, 2):
            raise ParserError(ErrorCode.INVALID_CONFIG, f"audio.channels 只能是 1/2，实际 {self.channels}", stage="config")


@dataclass
class FrameConfig:
    """按固定间隔抽帧。"""

    enabled: bool = False
    interval_sec: float = 10.0
    max_frames: int = 50
    image_format: str = "jpg"  # jpg | png
    quality: int = 3  # ffmpeg -q:v，2-5 常用

    def validate(self) -> None:
        _positive("frames.interval_sec", self.interval_sec)
        if int(self.max_frames) < 1:
            raise ParserError(ErrorCode.INVALID_CONFIG, f"frames.max_frames 至少为 1，实际 {self.max_frames}", stage="config")
        if self.image_format not in ("jpg", "png"):
            raise ParserError(ErrorCode.INVALID_CONFIG, f"frames.image_format 只支持 jpg/png，实际 {self.image_format!r}", stage="config")


@dataclass
class VideoConfig:
    """视频解析总配置。"""

    allowed_formats: tuple[str, ...] | None = None  # None = 使用注册表中的全部格式
    max_size_mb: int = 4096
    timeout_sec: int = 600
    strict: bool = False
    output_dir: str | None = None  # None = <源文件目录>/<文件名>_parsed
    transcode: TranscodeConfig = field(default_factory=TranscodeConfig)
    audio: AudioConfig = field(default_factory=AudioConfig)
    frames: FrameConfig = field(default_factory=FrameConfig)

    @classmethod
    def from_dict(cls, data: dict[str, Any] | None = None) -> "VideoConfig":
        cfg = _build(cls, data)
        if cfg.allowed_formats is not None:
            cfg.allowed_formats = tuple(
                f if str(f).startswith(".") else f".{f}" for f in cfg.allowed_formats
            )
        cfg.transcode.validate()
        cfg.audio.validate()
        cfg.frames.validate()
        _positive("max_size_mb", cfg.max_size_mb)
        _positive("timeout_sec", cfg.timeout_sec)
        return cfg


@dataclass
class PdfConfig:
    """PDF 处理：优先取文本层，扫描件回退 OCR。"""

    ocr_fallback: bool = True
    ocr_dpi: int = 200
    ocr_max_pages: int = 0  # 0 = 全部
    ocr_text_score: float = 0.5

    def validate(self) -> None:
        if not 50 <= int(self.ocr_dpi) <= 600:
            raise ParserError(ErrorCode.INVALID_CONFIG, f"pdf.ocr_dpi 应在 50-600，实际 {self.ocr_dpi}", stage="config")
        if int(self.ocr_max_pages) < 0:
            raise ParserError(ErrorCode.INVALID_CONFIG, "pdf.ocr_max_pages 不能为负", stage="config")


@dataclass
class TextConfig:
    normalize_whitespace: bool = True
    max_chars: int = 0  # 0 = 不截断
    page_separator: str = "\n\n"

    def validate(self) -> None:
        if int(self.max_chars) < 0:
            raise ParserError(ErrorCode.INVALID_CONFIG, "text.max_chars 不能为负", stage="config")


@dataclass
class StructureConfig:
    """结构解析：PDF 书签优先，其次按标题正则从文本中识别。"""

    enabled: bool = True
    max_level: int = 4
    heading_regex: str | None = None  # None = 内置中文章节/编号标题规则

    def validate(self) -> None:
        if not 1 <= int(self.max_level) <= 6:
            raise ParserError(ErrorCode.INVALID_CONFIG, f"structure.max_level 应在 1-6，实际 {self.max_level}", stage="config")
        if self.heading_regex:
            import re

            try:
                re.compile(self.heading_regex)
            except re.error as exc:
                raise ParserError(ErrorCode.INVALID_CONFIG, f"structure.heading_regex 非法：{exc}", stage="config")


@dataclass
class AttachmentConfig:
    """附件（图片/嵌入媒体）提取。"""

    enabled: bool = False
    min_size_bytes: int = 0

    def validate(self) -> None:
        if int(self.min_size_bytes) < 0:
            raise ParserError(ErrorCode.INVALID_CONFIG, "attachments.min_size_bytes 不能为负", stage="config")


@dataclass
class DocumentConfig:
    """文档解析总配置。"""

    allowed_formats: tuple[str, ...] | None = None
    max_size_mb: int = 1024
    timeout_sec: int = 600
    strict: bool = False
    output_dir: str | None = None
    pdf: PdfConfig = field(default_factory=PdfConfig)
    text: TextConfig = field(default_factory=TextConfig)
    structure: StructureConfig = field(default_factory=StructureConfig)
    attachments: AttachmentConfig = field(default_factory=AttachmentConfig)

    @classmethod
    def from_dict(cls, data: dict[str, Any] | None = None) -> "DocumentConfig":
        cfg = _build(cls, data)
        if cfg.allowed_formats is not None:
            cfg.allowed_formats = tuple(
                f if str(f).startswith(".") else f".{f}" for f in cfg.allowed_formats
            )
        cfg.pdf.validate()
        cfg.text.validate()
        cfg.structure.validate()
        cfg.attachments.validate()
        _positive("max_size_mb", cfg.max_size_mb)
        _positive("timeout_sec", cfg.timeout_sec)
        return cfg
