# -*- coding: utf-8 -*-
"""ffmpeg 封装：定位可执行文件、探测元数据、转码、抽音轨、抽帧。

不依赖第三方 Python 绑定；优先使用环境变量 / PATH 中的 ffmpeg，
找不到时回退到 ``imageio-ffmpeg`` 自带的静态二进制。
"""

from __future__ import annotations

import os
import re
import shutil
import subprocess
from dataclasses import dataclass
from pathlib import Path
from typing import Sequence

from ..config import AudioConfig, FrameConfig, TranscodeConfig
from ..errors import ErrorCode, ParserError
from ..types import AudioStreamInfo, VideoMetadata, VideoStreamInfo

_FFMPEG_CACHE: str | None = None


def ffmpeg_path() -> str:
    """返回 ffmpeg 可执行文件路径；找不到时抛 E3001。"""
    global _FFMPEG_CACHE
    if _FFMPEG_CACHE and os.path.exists(_FFMPEG_CACHE):
        return _FFMPEG_CACHE

    candidates = [os.environ.get("MEDIA_KIT_FFMPEG"), shutil.which("ffmpeg")]
    try:  # imageio-ffmpeg 是可选依赖
        import imageio_ffmpeg

        candidates.append(imageio_ffmpeg.get_ffmpeg_exe())
    except Exception:  # pragma: no cover - 环境相关
        pass

    for candidate in candidates:
        if candidate and os.path.exists(candidate):
            _FFMPEG_CACHE = candidate
            return candidate

    raise ParserError(
        ErrorCode.DEPENDENCY_MISSING,
        "未找到 ffmpeg：请安装 ffmpeg、加入 PATH，或 pip install imageio-ffmpeg",
        stage="dependency",
    )


def run(args: Sequence[str], *, timeout: int = 600, stage: str = "ffmpeg") -> str:
    """执行 ffmpeg 命令，失败抛标准错误。"""
    cmd = [ffmpeg_path(), "-hide_banner", "-nostdin", *args]
    try:
        proc = subprocess.run(
            cmd,
            capture_output=True,
            text=True,
            encoding="utf-8",
            errors="replace",
            timeout=timeout,
        )
    except subprocess.TimeoutExpired as exc:
        raise ParserError(ErrorCode.TIMEOUT, f"ffmpeg 执行超时（{timeout}s）", stage=stage, cause=exc) from exc
    if proc.returncode != 0:
        raise ParserError(
            ErrorCode.INTERNAL_ERROR,
            f"ffmpeg 执行失败（exit={proc.returncode}）",
            stage=stage,
            cause=RuntimeError((proc.stderr or "")[-800:]),
        )
    return proc.stderr or ""


@dataclass
class ProbeResult:
    metadata: VideoMetadata
    raw: str


def _kb_to_bps(value: str | None) -> int:
    return int(float(value) * 1000) if value else 0


def probe(path: str, *, timeout: int = 120) -> VideoMetadata:
    """用 ``ffmpeg -i`` 解析媒体信息（无 ffprobe 依赖）。"""
    cmd = [ffmpeg_path(), "-hide_banner", "-nostdin", "-i", path]
    try:
        proc = subprocess.run(
            cmd, capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=timeout
        )
    except subprocess.TimeoutExpired as exc:
        raise ParserError(ErrorCode.TIMEOUT, "探测媒体信息超时", stage="probe", cause=exc) from exc

    stderr = proc.stderr or ""
    low = stderr.lower()
    if "no such file or directory" in low:
        raise ParserError(ErrorCode.VIDEO_FILE_NOT_FOUND, path, stage="probe")
    if "invalid data found" in low or "moov atom not found" in low or "could not find codec parameters" in low:
        raise ParserError(ErrorCode.VIDEO_INVALID, "文件不是可解析的媒体", stage="probe")

    duration = 0.0
    m = re.search(r"Duration:\s*(\d+):(\d+):(\d+(?:\.\d+)?)", stderr)
    if m:
        duration = int(m.group(1)) * 3600 + int(m.group(2)) * 60 + float(m.group(3))
    bit_rate = 0
    m = re.search(r"bitrate:\s*(\d+)\s*kb/s", stderr)
    if m:
        bit_rate = int(m.group(1)) * 1000

    container = ""
    m = re.search(r"Input #0,\s*([^,]+),\s*from", stderr)
    if m:
        container = m.group(1).strip()

    video = None
    line = next((l for l in stderr.splitlines() if "Video:" in l), "")
    if line:
        codec = line.split("Video:", 1)[1].strip().split(" ", 1)[0].split("(", 1)[0]
        size = re.search(r"(\d{2,5})x(\d{2,5})", line)
        fps = re.search(r"([\d.]+)\s*fps", line)
        pix = re.search(r",\s*([a-z0-9_]+)\(", line)
        video = VideoStreamInfo(
            codec=codec,
            width=int(size.group(1)) if size else 0,
            height=int(size.group(2)) if size else 0,
            fps=float(fps.group(1)) if fps else 0.0,
            pix_fmt=pix.group(1) if pix else "",
        )

    audio = None
    line = next((l for l in stderr.splitlines() if "Audio:" in l), "")
    if line:
        codec = line.split("Audio:", 1)[1].strip().split(" ", 1)[0].split("(", 1)[0]
        rate = re.search(r"(\d+)\s*Hz", line)
        channels = 0
        if "stereo" in line:
            channels = 2
        elif "mono" in line:
            channels = 1
        else:
            ch = re.search(r"(\d+)\s*channels", line)
            channels = int(ch.group(1)) if ch else 0
        kbps = re.search(r",\s*(\d+)\s*kb/s", line)
        audio = AudioStreamInfo(
            codec=codec,
            sample_rate=int(rate.group(1)) if rate else 0,
            channels=channels,
            bit_rate=int(kbps.group(1)) * 1000 if kbps else 0,
        )

    if duration <= 0 and video is None and audio is None:
        raise ParserError(ErrorCode.VIDEO_PROBE_FAILED, "未解析到时长或音视频流", stage="probe")

    suffix = Path(path).suffix.lower()
    return VideoMetadata(
        path=str(path),
        format=suffix,
        size_bytes=os.path.getsize(path),
        duration_sec=round(duration, 3),
        bit_rate=bit_rate,
        container=container,
        video=video,
        audio=audio,
    )


def transcode(src: str, dst: str, cfg: TranscodeConfig, *, timeout: int = 600) -> str:
    """转码/转封装到目标容器，返回输出路径。"""
    args = ["-y", "-i", src]
    if cfg.mode == "copy":
        args += ["-c", "copy"]
    else:
        args += ["-c:v", cfg.video_codec, "-preset", cfg.preset, "-crf", str(cfg.crf), "-c:a", cfg.audio_codec]
    if cfg.faststart and dst.lower().endswith((".mp4", ".mov", ".m4v")):
        args += ["-movflags", "+faststart"]
    args.append(dst)
    try:
        run(args, timeout=timeout, stage="transcode")
    except ParserError as exc:
        raise ParserError(ErrorCode.VIDEO_TRANSCODE_FAILED, f"转码失败：{Path(src).name}", stage="transcode", cause=exc) from exc
    if not os.path.exists(dst) or os.path.getsize(dst) == 0:
        raise ParserError(ErrorCode.VIDEO_TRANSCODE_FAILED, f"转码输出为空：{dst}", stage="transcode")
    return dst


def extract_audio(src: str, dst: str, cfg: AudioConfig, *, timeout: int = 600) -> str:
    """抽取音轨（单声道 16k mp3 等），返回输出路径。"""
    args = ["-y", "-i", src, "-vn", "-ac", str(cfg.channels), "-ar", str(cfg.sample_rate)]
    if cfg.format == "wav":
        args += ["-c:a", "pcm_s16le"]
    elif cfg.format == "m4a":
        args += ["-c:a", "aac", "-b:a", cfg.bitrate]
    else:
        args += ["-c:a", "libmp3lame", "-b:a", cfg.bitrate]
    args.append(dst)
    try:
        run(args, timeout=timeout, stage="audio")
    except ParserError as exc:
        raise ParserError(ErrorCode.VIDEO_AUDIO_EXTRACT_FAILED, "音轨提取失败", stage="audio", cause=exc) from exc
    if not os.path.exists(dst) or os.path.getsize(dst) == 0:
        raise ParserError(ErrorCode.VIDEO_AUDIO_EXTRACT_FAILED, f"音轨输出为空：{dst}", stage="audio")
    return dst


def extract_frames(src: str, pattern: str, cfg: FrameConfig, *, timeout: int = 600) -> list[str]:
    """按间隔抽帧，返回按文件名排序的图片列表。"""
    args = [
        "-y", "-i", src,
        "-vf", f"fps=1/{cfg.interval_sec:g}",
        "-frames:v", str(cfg.max_frames),
    ]
    if cfg.image_format == "jpg":
        args += ["-q:v", str(cfg.quality)]
    args.append(pattern)
    try:
        run(args, timeout=timeout, stage="frames")
    except ParserError as exc:
        raise ParserError(ErrorCode.VIDEO_FRAME_EXTRACT_FAILED, "抽帧失败", stage="frames", cause=exc) from exc
    directory = os.path.dirname(pattern) or "."
    prefix = os.path.basename(pattern).split("%")[0]
    files = sorted(str(p) for p in Path(directory).glob(prefix + "*") if p.is_file())
    return files
