# -*- coding: utf-8 -*-
"""视频解析统一入口。

- 负责入参校验、格式路由、输出目录、异常收敛与计时；
- 具体解析由注册表中的处理器完成（插件机制）。
"""

from __future__ import annotations

import os
import time
from pathlib import Path

from ..config import VideoConfig
from ..errors import ErrorCode, ParserError
from ..registry import get_video_handler, supported_video_formats
from ..types import Transcriber, VideoOutputs, VideoResult


class VideoParser:
    """可复用的视频解析器。线程内复用即可，内部无共享状态。"""

    def __init__(self, config: VideoConfig | dict | None = None) -> None:
        if isinstance(config, dict):
            config = VideoConfig.from_dict(config)
        self.config = config or VideoConfig()

    # ------------------------------------------------------------------
    def parse(
        self,
        source: str | os.PathLike,
        *,
        transcriber: Transcriber | None = None,
        strict: bool | None = None,
    ) -> VideoResult:
        """解析视频。

        Args:
            source: 视频路径。
            transcriber: 可选语音转写实现（协议见 ``media_kit.types.Transcriber``）。
            strict: 为 True 时错误抛异常；默认取配置中的 ``strict``。

        Returns:
            VideoResult: ``ok=False`` 时 ``error`` 为标准错误码对象。
        """
        started = time.time()
        strict = self.config.strict if strict is None else strict
        output_dir = ""

        try:
            path = self._validate(source)
            fmt = path.suffix.lower()
            handler_cls = get_video_handler(str(path))
            if handler_cls is None:  # 理论上 validate 已拦下
                raise ParserError(ErrorCode.UNSUPPORTED_VIDEO_FORMAT, fmt, stage="dispatch")

            output_dir = self._output_dir(path)
            handler = handler_cls()
            result = handler.parse(str(path), self.config, output_dir, transcriber)
            result.elapsed_sec = time.time() - started
            return result

        except ParserError as exc:
            result = VideoResult(
                ok=False,
                outputs=VideoOutputs(output_dir=output_dir),
                error=exc.info,
                elapsed_sec=time.time() - started,
            )
            if strict:
                raise
            return result
        except Exception as exc:  # 兜底，避免把第三方异常直接抛给调用方
            err = ParserError(ErrorCode.INTERNAL_ERROR, "视频解析出现未预期错误", stage="internal", cause=exc)
            result = VideoResult(
                ok=False,
                outputs=VideoOutputs(output_dir=output_dir),
                error=err.info,
                elapsed_sec=time.time() - started,
            )
            if strict:
                raise err from exc
            return result

    # ------------------------------------------------------------------
    def _validate(self, source: str | os.PathLike) -> Path:
        path = Path(source).expanduser()
        if not path.exists():
            raise ParserError(ErrorCode.VIDEO_FILE_NOT_FOUND, str(path), stage="validate")
        if not path.is_file():
            raise ParserError(ErrorCode.VIDEO_FILE_NOT_FOUND, f"{path} 不是普通文件", stage="validate")
        if not os.access(path, os.R_OK):
            raise ParserError(ErrorCode.VIDEO_PERMISSION_DENIED, str(path), stage="validate")
        if path.stat().st_size == 0:
            raise ParserError(ErrorCode.VIDEO_FILE_EMPTY, str(path), stage="validate")
        size_mb = path.stat().st_size / 1024 / 1024
        if size_mb > self.config.max_size_mb:
            raise ParserError(
                ErrorCode.VIDEO_TOO_LARGE,
                f"文件 {size_mb:.1f} MB 超过上限 {self.config.max_size_mb} MB",
                stage="validate",
            )

        fmt = path.suffix.lower()
        allowed = self.config.allowed_formats
        registered = supported_video_formats()
        if not fmt or (allowed is not None and fmt not in allowed) or fmt not in registered:
            raise ParserError(
                ErrorCode.UNSUPPORTED_VIDEO_FORMAT,
                f"格式 {fmt or '(无扩展名)'} 不在支持列表；已注册：{', '.join(registered)}",
                stage="validate",
            )
        return path

    def _output_dir(self, path: Path) -> str:
        directory = self.config.output_dir
        target = Path(directory).expanduser() if directory else path.parent / f"{path.stem}_parsed"
        target.mkdir(parents=True, exist_ok=True)
        return str(target)
