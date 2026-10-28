# -*- coding: utf-8 -*-
"""
DeepSeek API 客户端（OpenAI 兼容格式，直接用 requests 调用，无需额外安装 SDK）
文档：https://api-docs.deepseek.com/
"""
import os

import requests

# ================== DeepSeek API 配置 ==================
# Key 只从环境变量读取，不要写进代码：$env:DEEPSEEK_API_KEY = "sk-..."
DEEPSEEK_API_KEY = os.environ.get("DEEPSEEK_API_KEY", "").strip()
DEEPSEEK_BASE_URL = "https://api.deepseek.com"
DEEPSEEK_MODEL = "deepseek-chat"   # 备选: "deepseek-reasoner"（深度思考，更慢更贵）
# =======================================================


def deepseek_chat(prompt, system="你是一个乐于助人的助手。", temperature=1.0, max_tokens=2048, timeout=60):
    """调用 DeepSeek 对话接口，返回回复文本；失败时抛出异常"""
    if not DEEPSEEK_API_KEY:
        raise RuntimeError("未配置 DEEPSEEK_API_KEY 环境变量，请先设置后再运行")
    resp = requests.post(
        f"{DEEPSEEK_BASE_URL}/chat/completions",
        headers={
            "Content-Type": "application/json",
            "Authorization": f"Bearer {DEEPSEEK_API_KEY}",
        },
        json={
            "model": DEEPSEEK_MODEL,
            "messages": [
                {"role": "system", "content": system},
                {"role": "user", "content": prompt},
            ],
            "temperature": temperature,
            "max_tokens": max_tokens,
            "stream": False,
        },
        timeout=timeout,
    )
    if resp.status_code != 200:
        raise RuntimeError(f"DeepSeek API 调用失败 (HTTP {resp.status_code}): {resp.text}")
    data = resp.json()
    return data["choices"][0]["message"]["content"]


def deepseek_chat_json(prompt, system="你是一个乐于助人的助手。", timeout=120):
    """调用 DeepSeek 并解析 JSON 格式的回复（用于结构化输出，如技能提取）"""
    import json
    text = deepseek_chat(
        prompt,
        system=system + " 请只输出合法的 JSON，不要输出任何其他内容。",
        temperature=0.0,
        timeout=timeout,
    )
    # 兼容模型可能用 ```json ... ``` 包裹的情况
    text = text.strip()
    if text.startswith("```"):
        text = text.strip("`")
        if text.startswith("json"):
            text = text[4:]
    return json.loads(text)


if __name__ == "__main__":
    print("===== DeepSeek API 连通性测试 =====")
    try:
        reply = deepseek_chat("你好，请用一句话自我介绍。", max_tokens=100)
        print(f"✅ 调用成功！模型回复：\n{reply}")
    except Exception as e:
        print(f"❌ 调用失败：{e}")
