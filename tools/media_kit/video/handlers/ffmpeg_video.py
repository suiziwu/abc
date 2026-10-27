# -*- coding: utf-8 -*-
"""基于 ffmpeg 的通用视频处理器，覆盖主流容器格式。"""

from __future__ import annotations

from ...registry import register_video_handler
from ...types import VideoMetadata
from .. import ffmpeg
from .base import BaseVideoHandler


@register_video_handler(
    ".mp4", ".m4v", ".mov", ".mkv", ".webm", ".avi", ".flv",
    ".ts", ".mts", ".m2ts", ".mpg", ".mpeg", ".wmv", ".ogv",
)
class FFmpegVideoHandler(BaseVideoHandler):
    """所有常见容器共用一套 ffmpeg 流程；特殊容器可在插件中覆写动作。"""

    name = "ffmpeg"

    def probe(self, source: str, *, timeout: int) -> VideoMetadata:
        return ffmpeg.probe(source, timeout=min(timeout, 120))
