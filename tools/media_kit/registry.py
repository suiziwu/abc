# -*- coding: utf-8 -*-
"""插件注册表。

核心流程只依赖注册表，不直接 import 具体格式处理器；
新增格式时只需实现处理器并用 ``@register_video_handler(".xxx")`` /
``@register_document_handler(".yyy")`` 注册，无需修改解析主流程。

内置处理器位于 ``media_kit.video.handlers`` / ``media_kit.document.handlers``，
包首次导入时会自动发现并注册。
"""

from __future__ import annotations

import importlib
import pkgutil
from typing import Any, Callable, TypeVar

T = TypeVar("T", bound=type)

VIDEO_HANDLERS: dict[str, type] = {}
DOCUMENT_HANDLERS: dict[str, type] = {}
_loaded = False


def normalize_format(fmt: str) -> str:
    fmt = str(fmt).strip().lower()
    if not fmt:
        raise ValueError("格式不能为空")
    return fmt if fmt.startswith(".") else f".{fmt}"


def register_video_handler(*formats: str) -> Callable[[T], T]:
    """注册视频处理器类。

    用法::

        @register_video_handler(".m4v", "mp4")
        class MyVideoHandler(FFmpegVideoHandler):
            ...
    """

    def decorator(cls: T) -> T:
        for fmt in formats:
            VIDEO_HANDLERS[normalize_format(fmt)] = cls
        existing = getattr(cls, "formats", ())
        cls.formats = tuple(dict.fromkeys(tuple(existing) + tuple(normalize_format(f) for f in formats)))
        return cls

    return decorator


def register_document_handler(*formats: str) -> Callable[[T], T]:
    """注册文档处理器类。"""

    def decorator(cls: T) -> T:
        for fmt in formats:
            DOCUMENT_HANDLERS[normalize_format(fmt)] = cls
        existing = getattr(cls, "formats", ())
        cls.formats = tuple(dict.fromkeys(tuple(existing) + tuple(normalize_format(f) for f in formats)))
        return cls

    return decorator


def _format_of(path: str) -> str:
    name = str(path).replace("\\", "/").rsplit("/", 1)[-1]
    if "." not in name:
        return ""
    return "." + name.rsplit(".", 1)[-1].lower()


def get_video_handler(path: str) -> type | None:
    return VIDEO_HANDLERS.get(_format_of(path))


def get_document_handler(path: str) -> type | None:
    return DOCUMENT_HANDLERS.get(_format_of(path))


def supported_video_formats() -> list[str]:
    return sorted(VIDEO_HANDLERS)


def supported_document_formats() -> list[str]:
    return sorted(DOCUMENT_HANDLERS)


def _discover(package_name: str) -> None:
    package = importlib.import_module(package_name)
    for module in pkgutil.iter_modules(getattr(package, "__path__", [])):
        if module.name.startswith("_") or module.name == "base":
            continue
        importlib.import_module(f"{package_name}.{module.name}")


def load_builtin_plugins(force: bool = False) -> None:
    """导入内置处理器模块，触发装饰器注册（幂等）。"""
    global _loaded
    if _loaded and not force:
        return
    _discover("media_kit.video.handlers")
    _discover("media_kit.document.handlers")
    _loaded = True


def registry_snapshot() -> dict[str, Any]:
    """便于调试/健康检查。"""
    return {
        "video": {
            fmt: f"{cls.__module__}.{cls.__name__}" for fmt, cls in sorted(VIDEO_HANDLERS.items())
        },
        "document": {
            fmt: f"{cls.__module__}.{cls.__name__}" for fmt, cls in sorted(DOCUMENT_HANDLERS.items())
        },
    }
