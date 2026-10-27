# -*- coding: utf-8 -*-
"""视频处理器基类：定义固定流程，格式处理器只需实现 ``probe``。

流程（可通过配置开关）：
    元数据探测 -> 转码/转封装 -> 抽音轨 -> 抽帧 -> 可选语音转写

新增格式时继承本类并实现 ``probe``，再用 ``@register_video_handler`` 注册即可。
"""

from __future__ import annotations

import os
from abc import ABC, abstractmethod

from ...config import VideoConfig
from ...errors import ErrorCode, ParserError, error_info
from ...types import Transcriber, VideoMetadata, VideoOutputs, VideoResult


class BaseVideoHandler(ABC):
    """视频格式处理器基类。"""

    name: str = "base"
    formats: tuple[str, ...] = ()

    @abstractmethod
    def probe(self, source: str, *, timeout: int) -> VideoMetadata:
        """解析元数据（时长、分辨率、编码、音轨等）。"""

    # ------------------------------------------------------------------
    # 固定流程
    # ------------------------------------------------------------------
    def parse(
        self,
        source: str,
        config: VideoConfig,
        output_dir: str,
        transcriber: Transcriber | None = None,
    ) -> VideoResult:
        metadata = self.probe(source, timeout=config.timeout_sec)
        outputs = VideoOutputs(output_dir=output_dir)
        warnings = []

        if config.transcode.enabled:
            dst = os.path.join(output_dir, f"normalized.{config.transcode.container.strip('.')}")
            outputs.normalized_path = self._transcode(source, dst, config)

        if config.audio.enabled:
            dst = os.path.join(output_dir, f"audio.{config.audio.format}")
            outputs.audio_path = self._extract_audio(source, dst, config)

        if config.frames.enabled:
            frame_dir = os.path.join(output_dir, "frames")
            os.makedirs(frame_dir, exist_ok=True)
            pattern = os.path.join(frame_dir, f"frame_%04d.{config.frames.image_format}")
            outputs.frames = self._extract_frames(source, pattern, config)

        if transcriber is not None and outputs.audio_path:
            try:
                outputs.transcript = transcriber.transcribe(outputs.audio_path)
            except ParserError as exc:
                warnings.append(exc.info)
            except Exception as exc:  # 转写失败不影响视频解析结果
                warnings.append(
                    error_info(
                        ErrorCode.VIDEO_TRANSCRIBE_FAILED,
                        "语音转写失败，视频其它产物仍可用",
                        stage="transcribe",
                        cause=exc,
                    )
                )

        return VideoResult(ok=True, metadata=metadata, outputs=outputs, warnings=warnings)

    # ------------------------------------------------------------------
    # 具体动作（默认用 ffmpeg，特殊格式可覆写）
    # ------------------------------------------------------------------
    def _transcode(self, source: str, dst: str, config: VideoConfig) -> str:
        from ..ffmpeg import transcode

        return transcode(source, dst, config.transcode, timeout=config.timeout_sec)

    def _extract_audio(self, source: str, dst: str, config: VideoConfig) -> str:
        from ..ffmpeg import extract_audio

        return extract_audio(source, dst, config.audio, timeout=config.timeout_sec)

    def _extract_frames(self, source: str, pattern: str, config: VideoConfig) -> list[str]:
        from ..ffmpeg import extract_frames

        return extract_frames(source, pattern, config.frames, timeout=config.timeout_sec)
