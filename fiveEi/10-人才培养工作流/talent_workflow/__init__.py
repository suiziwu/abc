# -*- coding: utf-8 -*-
"""人才培养方案智能数据工作流。"""

from .workflow import generate, load_config, WorkflowError
from .client import LLMClient, LLMError, LLMConfigError
from .render import render_table, print_report

__all__ = [
    "generate",
    "load_config",
    "WorkflowError",
    "LLMClient",
    "LLMError",
    "LLMConfigError",
    "render_table",
    "print_report",
]
__version__ = "1.0.0"
