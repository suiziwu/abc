# -*- coding: utf-8 -*-
"""视频解析组件测试：多格式元数据、转码、抽音轨、抽帧、转写与错误码。"""

from __future__ import annotations

import json
import os
import tempfile
import unittest

from media_kit import ErrorCode, MediaKit, ParserError, VideoConfig, parse_video
from media_kit.tests import fixtures


class VideoParserTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.tmp = tempfile.mkdtemp(prefix="mk_video_test_")
        os.makedirs(cls.tmp, exist_ok=True)
        cls.clips = {}
        for ext in (".mp4", ".ts", ".mkv", ".webm", ".mov"):
            cls.clips[ext] = fixtures.make_video(os.path.join(cls.tmp, f"sample{ext}"))

    @classmethod
    def tearDownClass(cls) -> None:
        fixtures.cleanup(cls.tmp)

    # ------------------------------------------------------------------
    def test_metadata_across_formats(self) -> None:
        for ext, path in self.clips.items():
            with self.subTest(ext=ext):
                result = parse_video(path, config={"output_dir": os.path.join(self.tmp, f"out{ext}")})
                self.assertTrue(result.ok, result.error)
                meta = result.metadata
                self.assertIsNotNone(meta)
                self.assertGreaterEqual(meta.duration_sec, 0.8)
                self.assertLessEqual(meta.duration_sec, 1.5)
                self.assertIsNotNone(meta.video, "应解析到视频流")
                self.assertEqual(meta.video.width, 320)
                self.assertEqual(meta.video.height, 240)
                self.assertIsNotNone(meta.audio, "应解析到音轨")

    def test_audio_extraction(self) -> None:
        result = parse_video(
            self.clips[".mp4"],
            config={"output_dir": os.path.join(self.tmp, "out_audio"), "audio": {"enabled": True, "format": "mp3"}},
        )
        self.assertTrue(result.ok, result.error)
        self.assertTrue(result.outputs.audio_path and os.path.exists(result.outputs.audio_path))
        self.assertGreater(os.path.getsize(result.outputs.audio_path), 0)

    def test_transcode_ts_to_mp4(self) -> None:
        result = parse_video(
            self.clips[".ts"],
            config={
                "output_dir": os.path.join(self.tmp, "out_transcode"),
                "transcode": {"enabled": True, "mode": "copy", "container": "mp4"},
                "audio": {"enabled": False},
            },
        )
        self.assertTrue(result.ok, result.error)
        self.assertTrue(result.outputs.normalized_path and os.path.exists(result.outputs.normalized_path))
        self.assertTrue(result.outputs.normalized_path.endswith(".mp4"))

    def test_frame_extraction(self) -> None:
        result = parse_video(
            self.clips[".mp4"],
            config={
                "output_dir": os.path.join(self.tmp, "out_frames"),
                "audio": {"enabled": False},
                "frames": {"enabled": True, "interval_sec": 0.4, "max_frames": 3},
            },
        )
        self.assertTrue(result.ok, result.error)
        self.assertGreaterEqual(len(result.outputs.frames), 1)
        self.assertLessEqual(len(result.outputs.frames), 3)
        for frame in result.outputs.frames:
            self.assertTrue(os.path.exists(frame))

    def test_transcriber_extension_point(self) -> None:
        calls = {}

        class DummyTranscriber:
            def transcribe(self, audio_path, *, language=None):
                calls["audio"] = audio_path
                return {"model": "dummy", "sentences": [{"begin_time": 0, "end_time": 900, "text": "测试"}]}

        result = parse_video(
            self.clips[".mp4"],
            config={"output_dir": os.path.join(self.tmp, "out_asr"), "audio": {"enabled": True}},
            transcriber=DummyTranscriber(),
        )
        self.assertTrue(result.ok, result.error)
        self.assertEqual(result.outputs.transcript["model"], "dummy")
        self.assertTrue(calls["audio"].endswith(".mp3"))

    # ------------------------------------------------------------------
    def test_error_missing_file(self) -> None:
        result = parse_video(os.path.join(self.tmp, "missing.mp4"))
        self.assertFalse(result.ok)
        self.assertEqual(result.error.code, ErrorCode.VIDEO_FILE_NOT_FOUND)

    def test_error_unsupported_format(self) -> None:
        path = os.path.join(self.tmp, "a.xyz")
        with open(path, "wb") as fp:
            fp.write(b"data")
        result = parse_video(path)
        self.assertFalse(result.ok)
        self.assertEqual(result.error.code, ErrorCode.UNSUPPORTED_VIDEO_FORMAT)

    def test_error_empty_file(self) -> None:
        path = os.path.join(self.tmp, "empty.mp4")
        open(path, "wb").close()
        result = parse_video(path)
        self.assertFalse(result.ok)
        self.assertEqual(result.error.code, ErrorCode.VIDEO_FILE_EMPTY)

    def test_error_corrupt_file(self) -> None:
        path = os.path.join(self.tmp, "bad.mp4")
        with open(path, "wb") as fp:
            fp.write(b"this is not a real video")
        result = parse_video(path)
        self.assertFalse(result.ok)
        self.assertIn(result.error.code, {ErrorCode.VIDEO_INVALID, ErrorCode.VIDEO_PROBE_FAILED})

    def test_allowed_formats_filter(self) -> None:
        result = parse_video(self.clips[".ts"], config={"allowed_formats": [".mp4"]})
        self.assertFalse(result.ok)
        self.assertEqual(result.error.code, ErrorCode.UNSUPPORTED_VIDEO_FORMAT)

    def test_strict_raises(self) -> None:
        with self.assertRaises(ParserError) as ctx:
            parse_video(os.path.join(self.tmp, "none.mp4"), strict=True)
        self.assertEqual(ctx.exception.code, ErrorCode.VIDEO_FILE_NOT_FOUND)

    def test_invalid_config(self) -> None:
        with self.assertRaises(ParserError) as ctx:
            VideoConfig.from_dict({"frames": {"interval_sec": 0}})
        self.assertEqual(ctx.exception.code, ErrorCode.INVALID_CONFIG)

    def test_result_serializable(self) -> None:
        result = parse_video(self.clips[".mp4"], config={"output_dir": os.path.join(self.tmp, "out_json")})
        payload = result.to_dict()
        json.dumps(payload, ensure_ascii=False)
        self.assertIn("duration_sec", payload["metadata"])

    def test_facade(self) -> None:
        kit = MediaKit(video_config={"output_dir": os.path.join(self.tmp, "out_facade")})
        result = kit.parse_video(self.clips[".mkv"])
        self.assertTrue(result.ok, result.error)


if __name__ == "__main__":
    unittest.main()
