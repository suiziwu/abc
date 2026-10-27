# -*- coding: utf-8 -*-
"""media_kit 本地 HTTP 服务（供 Java / 前端 / 其它进程调用）。

端点：
    GET  /health         服务状态与已注册格式
    POST /api/video      body: {"path": "...", "config": {...}}
    POST /api/document   body: {"path": "...", "config": {...}}

说明：
    - 仅监听 127.0.0.1（可用 MEDIA_KIT_HOST/PORT 覆盖）；
    - 解析结果与错误码和 Python API 完全一致（Result.to_dict()）；
    - 若设置 MEDIA_KIT_TOKEN，则请求需带请求头 X-Media-Kit-Token。

运行：
    python -m media_kit.server            # 需把包所在目录加入 PYTHONPATH
    python media_kit/server.py            # 也可以直接运行
"""

from __future__ import annotations

import os
import sys

# 直接把本文件当脚本运行时，sys.path[0] 是 media_kit/ 目录，会导致包内 types.py
# 遮蔽标准库 types。这里先把它换成包所在目录，再导入其余模块。
if __package__ in (None, ""):
    _here = os.path.dirname(os.path.abspath(__file__))
    _root = os.path.dirname(_here)
    sys.path = [p for p in sys.path if os.path.abspath(p or os.getcwd()) != _here]
    sys.path.insert(0, _root)

import json  # noqa: E402
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer  # noqa: E402

from media_kit import __version__, parse_document, parse_video  # noqa: E402
from media_kit.errors import ErrorCode, ParserError, error_info  # noqa: E402
from media_kit.registry import supported_document_formats, supported_video_formats  # noqa: E402

HOST = os.environ.get("MEDIA_KIT_HOST", "127.0.0.1")
PORT = int(os.environ.get("MEDIA_KIT_PORT", "8130"))
TOKEN = os.environ.get("MEDIA_KIT_TOKEN", "").strip()
MAX_BODY = 2 * 1024 * 1024  # 2MB JSON


class Handler(BaseHTTPRequestHandler):
    server_version = "MediaKitService/1.0"

    def log_message(self, fmt: str, *args) -> None:
        print("[media-kit]", fmt % args, flush=True)

    # ------------------------------------------------------------------
    def _send_json(self, status: int, payload: dict) -> None:
        body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def _authorized(self) -> bool:
        if not TOKEN:
            return True
        return self.headers.get("X-Media-Kit-Token", "") == TOKEN

    def _health(self) -> dict:
        return {
            "ok": True,
            "version": __version__,
            "video_formats": supported_video_formats(),
            "document_formats": supported_document_formats(),
        }

    # ------------------------------------------------------------------
    def do_GET(self) -> None:
        path = self.path.split("?", 1)[0]
        if path in ("/", "/health"):
            self._send_json(200, self._health())
        else:
            self._send_json(404, {"ok": False, "error": {"code": "E9000", "message": "not found"}})

    def do_POST(self) -> None:
        path = self.path.split("?", 1)[0]
        if path not in ("/api/video", "/api/document"):
            self._send_json(404, {"ok": False, "error": {"code": "E9000", "message": "not found"}})
            return
        if not self._authorized():
            self._send_json(401, {"ok": False, "error": {"code": "E9000", "message": "unauthorized"}})
            return

        try:
            length = int(self.headers.get("Content-Length") or 0)
            if length <= 0 or length > MAX_BODY:
                raise ValueError("请求体为空或超过 2MB")
            payload = json.loads(self.rfile.read(length) or b"{}")
            source = (payload.get("path") or "").strip()
            if not source:
                raise ValueError("缺少 path")
            config = payload.get("config") or {}
            if not isinstance(config, dict):
                raise ValueError("config 必须是对象")
        except Exception as exc:
            self._send_json(400, {"ok": False, "error": error_info(ErrorCode.INVALID_CONFIG, str(exc), stage="request").to_dict()})
            return

        try:
            if path == "/api/video":
                result = parse_video(source, config=config)
            else:
                result = parse_document(source, config=config)
            self._send_json(200, result.to_dict())
        except ParserError as exc:  # 配置非法等
            self._send_json(400, {"ok": False, "error": exc.info.to_dict()})
        except Exception as exc:
            self._send_json(
                500,
                {
                    "ok": False,
                    "error": error_info(ErrorCode.INTERNAL_ERROR, "解析服务内部错误", stage="internal", cause=exc).to_dict(),
                },
            )


def main() -> None:
    server = ThreadingHTTPServer((HOST, PORT), Handler)
    print(f"media_kit {__version__} 服务: http://{HOST}:{PORT}", flush=True)
    print(f"视频格式: {', '.join(supported_video_formats())}", flush=True)
    print(f"文档格式: {', '.join(supported_document_formats())}", flush=True)
    if TOKEN:
        print("已启用 X-Media-Kit-Token 鉴权", flush=True)
    server.serve_forever()


if __name__ == "__main__":
    main()
