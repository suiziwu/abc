# -*- coding: utf-8 -*-
"""语音转写扩展点示例：实现 Transcriber 协议即可接入任意 ASR（百炼/Whisper/本地服务）。

注意：该文件只是最小示例，不包含任何平台密钥。
真实项目可把 ``BailianTranscriber`` 放在业务代码里，核心包无需改动。
"""

from __future__ import annotations

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[2]))

from media_kit import parse_video  # noqa: E402


class DemoTranscriber:
    """演示用：返回固定文本；真实实现可调用 HTTP/WS 服务。"""

    def __init__(self, model: str = "demo-asr") -> None:
        self.model = model

    def transcribe(self, audio_path: str, *, language: str | None = None) -> dict:
        size = Path(audio_path).stat().st_size
        return {
            "model": self.model,
            "language": language or "zh",
            "sentences": [
                {"begin_time": 0, "end_time": 1200, "text": f"音频 {size} 字节，转写示例。"}
            ],
        }


if __name__ == "__main__":
    import argparse

    parser = argparse.ArgumentParser()
    parser.add_argument("source", help="视频路径（需 ffmpeg 可用）")
    args = parser.parse_args()
    result = parse_video(args.source, transcriber=DemoTranscriber())
    print("ok:", result.ok)
    if result.outputs.transcript:
        print("transcript:", result.outputs.transcript)
