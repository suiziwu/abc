# -*- coding: utf-8 -*-
"""LLM 客户端：OpenAI 兼容协议（DeepSeek 官方接口）。

设计要点：
- 不写死任何密钥：优先读环境变量（默认 DEEPSEEK_API_KEY），其次读 config.json 的 api.api_key。
- 自带重试（429 / 5xx / 网络异常 / JSON 解析失败），指数退避。
- 从模型回复中稳健解析 JSON：兼容 ```json 围栏、前后寒暄、字符串里带大括号。
- 统计调用次数、失败次数与 token 用量，供 meta 层回传。

本模块只做“调用”，不含业务逻辑，业务编排见 workflow.py。
"""
import json
import os
import random
import re
import time

import requests


class LLMError(RuntimeError):
    """API 调用失败或响应无法解析。"""


class LLMConfigError(LLMError):
    """配置缺失，例如没有 API Key。"""


def resolve_api_key(api_cfg):
    """按“环境变量 > 配置文件”的顺序取 Key，取不到就明确报错。"""
    env_name = (api_cfg.get("api_key_env") or "DEEPSEEK_API_KEY").strip()
    key = (os.environ.get(env_name) or "").strip()
    if not key:
        key = (api_cfg.get("api_key") or "").strip()
    if not key:
        raise LLMConfigError(
            "未配置 API Key：请设置环境变量 %s，或填写 config.json 中的 api.api_key。" % env_name
        )
    return key


def parse_json_object(text):
    """从模型回复中解析出一个 JSON 对象。"""
    if not text or not text.strip():
        raise LLMError("模型返回为空")
    raw = text.strip()
    if raw.startswith("```"):
        raw = re.sub(r"^```[a-zA-Z0-9_-]*\s*", "", raw)
        raw = re.sub(r"\s*```$", "", raw).strip()
    try:
        value = json.loads(raw)
        if isinstance(value, dict):
            return value
    except ValueError:
        pass

    start = raw.find("{")
    if start < 0:
        raise LLMError("模型回复中找不到 JSON 对象：%s" % raw[:120])

    depth = 0
    in_str = False
    escape = False
    for i in range(start, len(raw)):
        ch = raw[i]
        if in_str:
            if escape:
                escape = False
            elif ch == "\\":
                escape = True
            elif ch == '"':
                in_str = False
            continue
        if ch == '"':
            in_str = True
        elif ch == "{":
            depth += 1
        elif ch == "}":
            depth -= 1
            if depth == 0:
                try:
                    value = json.loads(raw[start:i + 1])
                except ValueError as exc:
                    raise LLMError("JSON 解析失败：%s" % exc)
                if not isinstance(value, dict):
                    raise LLMError("模型输出的 JSON 不是对象")
                return value
    raise LLMError("模型输出的 JSON 不完整")


def _short(text, limit=200):
    text = re.sub(r"\s+", " ", str(text or "")).strip()
    return text[:limit]


def _looks_degenerate(value):
    """识别模型把 response_format 原样吐回来之类的占位输出。"""
    if not isinstance(value, dict) or not value:
        return True
    keys = set(value.keys())
    if keys <= {"type", "json_object"}:
        return True
    return False


class LLMClient(object):
    """DeepSeek（OpenAI 兼容）对话客户端。"""

    def __init__(self, api_cfg, logger=None):
        self.base_url = (api_cfg.get("base_url") or "https://api.deepseek.com").rstrip("/")
        self.model = api_cfg.get("model") or "deepseek-chat"
        self.timeout = float(api_cfg.get("timeout") or 120)
        self.max_retries = max(1, int(api_cfg.get("max_retries") or 3))
        self.temperature = float(api_cfg.get("temperature", 0.2))
        self.max_tokens = int(api_cfg.get("max_tokens") or 4096)
        self.json_mode = bool(api_cfg.get("json_mode", True))
        self.api_key = resolve_api_key(api_cfg)
        self.logger = logger or (lambda msg: None)
        self.stats = {
            "调用次数": 0,
            "失败次数": 0,
            "输入tokens": 0,
            "输出tokens": 0,
        }

    # ------------------------------------------------------------------ 基础调用
    def _headers(self):
        return {
            "Content-Type": "application/json",
            "Authorization": "Bearer %s" % self.api_key,
        }

    def _request(self, body):
        url = self.base_url + "/chat/completions"
        last_error = None
        for attempt in range(1, self.max_retries + 1):
            self.stats["调用次数"] += 1
            try:
                resp = requests.post(url, headers=self._headers(), json=body, timeout=self.timeout)
            except requests.RequestException as exc:
                last_error = LLMError("网络异常：%s" % exc)
            else:
                if resp.status_code == 200:
                    try:
                        return resp.json()
                    except ValueError:
                        last_error = LLMError("接口返回的不是 JSON：%s" % _short(resp.text))
                elif resp.status_code in (429, 500, 502, 503, 504):
                    last_error = LLMError("HTTP %s（可重试）：%s" % (resp.status_code, _short(resp.text)))
                else:
                    # 401/402/400 这类重试也没用，直接抛出，避免浪费额度
                    raise LLMError("HTTP %s：%s" % (resp.status_code, _short(resp.text)))

            self.stats["失败次数"] += 1
            if attempt < self.max_retries:
                delay = min(2 ** attempt, 10) + random.random()
                self.logger("  第 %d 次调用失败（%s），%.1fs 后重试" % (attempt, last_error, delay))
                time.sleep(delay)

        raise last_error or LLMError("调用失败")

    def _do_chat(self, messages, temperature, max_tokens, json_mode):
        body = {
            "model": self.model,
            "messages": messages,
            "temperature": self.temperature if temperature is None else temperature,
            "max_tokens": self.max_tokens if max_tokens is None else max_tokens,
            "stream": False,
        }
        if json_mode and self.json_mode:
            body["response_format"] = {"type": "json_object"}

        data = self._request(body)
        try:
            content = data["choices"][0]["message"]["content"]
        except (KeyError, IndexError, TypeError):
            raise LLMError("接口响应结构异常：%s" % _short(json.dumps(data, ensure_ascii=False)))

        usage = data.get("usage") or {}
        self.stats["输入tokens"] += int(usage.get("prompt_tokens") or 0)
        self.stats["输出tokens"] += int(usage.get("completion_tokens") or 0)
        return content

    # ------------------------------------------------------------------ 对外接口
    def chat_text(self, system, user, temperature=None, max_tokens=None):
        """普通文本对话（用于连通性测试）。"""
        messages = [
            {"role": "system", "content": system},
            {"role": "user", "content": user},
        ]
        return self._do_chat(messages, temperature, max_tokens, json_mode=False)

    def chat_json(self, system, user, tag="", temperature=None, max_tokens=None):
        """要求模型返回 JSON 对象，并解析成 dict。"""
        messages = [
            {"role": "system", "content": system},
            {"role": "user", "content": user},
        ]
        content = self._do_chat(messages, temperature, max_tokens, json_mode=True)
        reason = ""
        try:
            result = parse_json_object(content)
            if not _looks_degenerate(result):
                return result
            reason = "返回了占位内容"
        except LLMError:
            reason = "返回内容不是合法 JSON"

        # JSON 模式偶发退化：关掉 response_format 再要一次，并明确禁止占位内容
        self.logger("  %s %s，关闭 JSON 模式重试一次" % (tag or "调用", reason))
        retry_messages = messages + [
            {"role": "assistant", "content": content[:2000]},
            {"role": "user", "content": "上面的输出无效（%s）。请只输出题目要求的 JSON 对象，不要输出 {\"type\": \"json_object\"} 之类的占位内容，不要解释。" % reason},
        ]
        content = self._do_chat(retry_messages, 0.0, max_tokens, json_mode=False)
        return parse_json_object(content)
