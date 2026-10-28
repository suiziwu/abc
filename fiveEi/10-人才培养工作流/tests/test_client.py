# -*- coding: utf-8 -*-
"""LLM 客户端自测：用本地 HTTP 假接口验证重试与 JSON 解析，不消耗真实额度。"""
import json
import os
import sys
import threading
import unittest
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from unittest import mock

BASE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, BASE)

from talent_workflow.client import LLMClient, LLMError, parse_json_object  # noqa: E402


class _Handler(BaseHTTPRequestHandler):
    def do_POST(self):
        length = int(self.headers.get("Content-Length") or 0)
        self.rfile.read(length)
        self.server.calls += 1
        queue = self.server.queue
        index = min(self.server.calls - 1, len(queue) - 1)
        status, content = queue[index]
        data = json.dumps({
            "choices": [{"message": {"content": content}}],
            "usage": {"prompt_tokens": 12, "completion_tokens": 6},
        }, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def log_message(self, *args):  # 静音
        pass


class ClientTest(unittest.TestCase):
    def _serve(self, queue):
        server = ThreadingHTTPServer(("127.0.0.1", 0), _Handler)
        server.queue = queue
        server.calls = 0
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        self.addCleanup(server.server_close)
        self.addCleanup(server.shutdown)
        return server

    def _client(self, server, retries=3):
        return LLMClient({
            "base_url": "http://127.0.0.1:%d" % server.server_address[1],
            "model": "fake-model",
            "api_key": "test-key",
            "api_key_env": "TALENT_TEST_KEY_UNSET",
            "max_retries": retries,
            "timeout": 5,
        })

    def test_parse_code_fence(self):
        self.assertEqual(parse_json_object('```json\n{"a": 1}\n```'), {"a": 1})

    def test_parse_with_chatter(self):
        self.assertEqual(parse_json_object('好的，结果如下：\n{"a": {"b": "}"}} 以上。'), {"a": {"b": "}"}})

    def test_chat_json_ok(self):
        server = self._serve([(200, '{"ok": true, "n": 3}')])
        client = self._client(server)
        self.assertEqual(client.chat_json("s", "u"), {"ok": True, "n": 3})
        self.assertEqual(server.calls, 1)
        self.assertEqual(client.stats["输入tokens"], 12)

    def test_retry_on_500_then_success(self):
        server = self._serve([(500, "boom"), (200, '{"ok": true}')])
        client = self._client(server)
        with mock.patch("talent_workflow.client.time.sleep"):
            self.assertEqual(client.chat_json("s", "u"), {"ok": True})
        self.assertEqual(server.calls, 2)

    def test_no_retry_on_401(self):
        server = self._serve([(401, "unauthorized")])
        client = self._client(server)
        with self.assertRaises(LLMError):
            client.chat_json("s", "u")
        self.assertEqual(server.calls, 1)

    def test_invalid_json_retried(self):
        server = self._serve([(200, "不是 JSON"), (200, '{"ok": true}')])
        client = self._client(server)
        self.assertEqual(client.chat_json("s", "u"), {"ok": True})
        self.assertEqual(server.calls, 2)


if __name__ == "__main__":
    unittest.main()
