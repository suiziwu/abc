# -*- coding: utf-8 -*-
"""视频解析示例。

运行：
    python media_kit/examples/parse_video.py D:/path/demo.ts --out D:/tmp/out --transcode --frames
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[2]))

from media_kit import VideoConfig, parse_video  # noqa: E402


def main() -> int:
    parser = argparse.ArgumentParser(description="media_kit 视频解析示例")
    parser.add_argument("source", help="视频文件路径")
    parser.add_argument("--out", help="输出目录（默认：源文件同级的 <名称>_parsed）")
    parser.add_argument("--transcode", action="store_true", help="转封装为 mp4")
    parser.add_argument("--frames", action="store_true", help="按间隔抽帧")
    parser.add_argument("--interval", type=float, default=10.0, help="抽帧间隔秒数")
    parser.add_argument("--no-audio", action="store_true", help="不抽取音轨")
    parser.add_argument("--strict", action="store_true", help="出错时抛出异常而不是返回错误对象")
    args = parser.parse_args()

    config = VideoConfig.from_dict(
        {
            "output_dir": args.out,
            "strict": args.strict,
            "audio": {"enabled": not args.no_audio},
            "transcode": {"enabled": args.transcode, "mode": "copy", "container": "mp4"},
            "frames": {"enabled": args.frames, "interval_sec": args.interval, "max_frames": 20},
        }
    )
    result = parse_video(args.source, config=config)
    print(json.dumps(result.to_dict(), ensure_ascii=False, indent=2))
    return 0 if result.ok else 1


if __name__ == "__main__":
    raise SystemExit(main())
