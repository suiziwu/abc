# media_kit · 通用视频 / 文档解析组件

从「课程视频转写 + 教材扫描件解析」两条业务流水线中抽出的可复用解析层，
**不依赖任何具体业务、不内置任何平台密钥**，可直接被其它项目引入。

- 视频：格式校验 → 元数据探测 → 转码/转封装 → 抽音轨 → 抽帧 →（可选）语音转写
- 文档：多格式兼容 → 文本提取（PDF 文本层/扫描件 OCR）→ 结构解析 → 附件提取
- 统一入参/出参、配置化、标准错误码、插件式格式扩展、开箱即用的测试与示例

---

## 1. 目录结构

```text
media_kit/
├── __init__.py              # 公共 API：parse_video / parse_document / MediaKit
├── errors.py                # ErrorCode（E1001…）+ ErrorInfo + ParserError
├── types.py                 # VideoResult / DocumentResult 等 dataclass + Transcriber 协议
├── config.py                # VideoConfig / DocumentConfig（支持 dict / JSON / YAML 读入）
├── registry.py              # 插件注册表 + 内置处理器自动发现
├── video/
│   ├── parser.py            # 校验、路由、输出目录、异常收敛
│   ├── ffmpeg.py            # ffmpeg 定位、探测、转码、抽音轨、抽帧
│   └── handlers/
│       ├── base.py          # BaseVideoHandler（固定流程）
│       └── ffmpeg_video.py  # 14 种容器格式
├── document/
│   ├── parser.py            # 文档校验、路由、附件目录、异常收敛
│   ├── structure.py         # 标题层级识别（章节/编号/Markdown/HTML）
│   └── handlers/
│       ├── base.py          # BaseDocumentHandler
│       ├── pdf_handler.py   # 文本层 + 扫描件 OCR + 书签结构 + 图片附件
│       ├── docx_handler.py  # 段落/表格/标题样式/word/media
│       ├── pptx_handler.py  # 幻灯片文本/标题/ppt/media
│       ├── spreadsheet_handler.py  # xlsx/xlsm/csv
│       └── text_handler.py  # md/txt/html
├── tests/                   # unittest 用例（34 个）
├── examples/                # 视频、文档、插件、转写扩展示例
└── pyproject.toml           # 可选依赖分组：video / pdf / office / ocr / all
```

## 2. 安装

```bash
# 最小：视频需要 ffmpeg 可执行文件；文档按需装对应的库
pip install imageio-ffmpeg          # 或自行安装 ffmpeg 并加入 PATH
pip install pymupdf                 # PDF
pip install python-docx python-pptx openpyxl   # Office
pip install rapidocr-onnxruntime    # 扫描件 OCR（可选）
```

也可以按 extras 安装（在 `media_kit/` 目录执行）：

```bash
pip install -e ".[all]"
```

> 表格里的 `xls` 未注册（需 xlrd），请先另存为 `xlsx`；`.doc` 同理先转 `.docx`。

## 3. 快速开始

```python
from media_kit import parse_video, parse_document

video = parse_video("demo.ts", config={
    "audio": {"enabled": True, "sample_rate": 16000},
    "transcode": {"enabled": True, "mode": "copy"},
})
print(video.ok, video.metadata.duration_sec, video.metadata.video.width)
print(video.outputs.audio_path, video.outputs.normalized_path)

doc = parse_document("book.pdf", config={
    "pdf": {"ocr_fallback": True, "ocr_dpi": 200},
    "attachments": {"enabled": True},
})
print(doc.ok, doc.metadata.page_count, len(doc.pages))
print(doc.text[:300])
print([(h.level, h.title, h.page) for h in doc.structure][:10])
```

长期复用建议使用门面：

```python
from media_kit import MediaKit

kit = MediaKit(
    video_config={"audio": {"enabled": True}},
    document_config={"pdf": {"ocr_dpi": 150}},
)
result = kit.parse_document("a.docx")
```

### 语音转写扩展点

核心包不绑定任何 ASR。实现 `media_kit.types.Transcriber` 协议即可：

```python
class MyTranscriber:
    def transcribe(self, audio_path: str, *, language: str | None = None) -> dict:
        return {"model": "my-asr", "sentences": [{"begin_time": 0, "end_time": 900, "text": "…"}]}

video = parse_video("demo.mp4", transcriber=MyTranscriber())
print(video.outputs.transcript)
```

转写失败不会让整个视频解析失败，会出现在 `result.warnings`（`E1010`）。

## 4. 统一出参

### VideoResult

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `ok` | bool | 是否成功 |
| `metadata` | `VideoMetadata` | 时长、bitrate、容器、视频流（编码/分辨率/fps）、音轨（编码/采样率/声道） |
| `outputs.normalized_path` | str? | 转码/转封装产物 |
| `outputs.audio_path` | str? | 抽出的音轨 |
| `outputs.frames` | list[str] | 抽帧图片 |
| `outputs.transcript` | dict? | 语音转写结果 |
| `error` | `ErrorInfo?` | 失败时的标准错误 |
| `warnings` | list[ErrorInfo] | 非致命问题（如转写失败） |
| `to_dict()` | dict | 可直接 JSON 序列化 |

### DocumentResult

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `ok` / `error` / `warnings` | — | 同上 |
| `metadata` | `DocumentMetadata` | 页/工作表/幻灯片数、标题、作者、格式、大小 |
| `text` | str | 全文（已按配置归一化/截断） |
| `pages` | list[DocumentPage] | 分页文本；`ocr=True` 表示该页来自 OCR |
| `structure` | list[Heading] | 标题层级（level/title/page），来自书签、样式、Markdown/HTML 标题或正文规则 |
| `attachments` | list[AttachmentInfo] | 内嵌图片/媒体（开启 `attachments.enabled` 时） |

## 5. 配置

### VideoConfig

| 键 | 默认 | 说明 |
| --- | --- | --- |
| `allowed_formats` | 全部已注册 | 白名单，如 `[".mp4", ".ts"]` |
| `max_size_mb` | 4096 | 超过返回 `E1011` |
| `timeout_sec` | 600 | 单步 ffmpeg 超时 |
| `strict` | False | True 时错误抛 `ParserError` |
| `output_dir` | `<源目录>/<文件名>_parsed` | 产物目录 |
| `transcode.enabled/mode/container` | False / copy / mp4 | `mode=copy` 无损换容器 |
| `audio.enabled/format/sample_rate/channels` | True / mp3 / 16000 / 1 | 音轨提取 |
| `frames.enabled/interval_sec/max_frames` | False / 10 / 50 | 抽帧 |

### DocumentConfig

| 键 | 默认 | 说明 |
| --- | --- | --- |
| `allowed_formats` | 全部已注册 | 白名单 |
| `max_size_mb` | 1024 | 超过返回 `E2009` |
| `output_dir` | `<源目录>/<文件名>_parsed` | 附件目录的父目录 |
| `pdf.ocr_fallback` | True | 无文本层时回退 OCR |
| `pdf.ocr_dpi` | 200 | 50–600 |
| `pdf.ocr_max_pages` | 0 | 0 = 不限 |
| `text.normalize_whitespace` | True | 去行尾空白、压缩空行 |
| `text.max_chars` | 0 | 0 = 不截断 |
| `structure.enabled/max_level/heading_regex` | True / 4 / 内置 | 标题识别 |
| `attachments.enabled/min_size_bytes` | False / 0 | 提取内嵌图片/媒体 |

配置可直接从 dict 构造：`VideoConfig.from_dict({...})`、`DocumentConfig.from_dict({...})`；
未知键会被忽略，非法值抛 `E3003`。

## 6. 标准错误码

| 码 | 枚举 | 场景 |
| --- | --- | --- |
| E1001 | UNSUPPORTED_VIDEO_FORMAT | 视频格式未注册/不在白名单 |
| E1002 | VIDEO_FILE_NOT_FOUND | 视频不存在/不可读 |
| E1003 | VIDEO_FILE_EMPTY | 视频大小为 0 |
| E1004 | VIDEO_PERMISSION_DENIED | 无读权限 |
| E1005 | VIDEO_INVALID | 不是合法媒体 |
| E1006 | VIDEO_PROBE_FAILED | 元数据解析失败 |
| E1007 | VIDEO_TRANSCODE_FAILED | 转码失败 |
| E1008 | VIDEO_AUDIO_EXTRACT_FAILED | 抽音轨失败 |
| E1009 | VIDEO_FRAME_EXTRACT_FAILED | 抽帧失败 |
| E1010 | VIDEO_TRANSCRIBE_FAILED | 转写失败（warning） |
| E1011 | VIDEO_TOO_LARGE | 超过 `max_size_mb` |
| E2001 | UNSUPPORTED_DOCUMENT_FORMAT | 文档格式未注册/不在白名单 |
| E2002 | DOCUMENT_FILE_NOT_FOUND | 文档不存在/不可读 |
| E2003 | DOCUMENT_FILE_EMPTY | 文档大小为 0 |
| E2004 | DOCUMENT_TEXT_EXTRACT_FAILED | 打开/读取失败（损坏等） |
| E2005 | DOCUMENT_STRUCTURE_PARSE_FAILED | 结构解析失败（保留） |
| E2006 | DOCUMENT_ATTACHMENT_EXTRACT_FAILED | 附件提取失败（保留） |
| E2007 | DOCUMENT_OCR_UNAVAILABLE | 未安装 OCR 依赖 |
| E2008 | DOCUMENT_OCR_FAILED | 单页 OCR 失败（warning） |
| E2009 | DOCUMENT_TOO_LARGE | 超过 `max_size_mb` |
| E2010 | DOCUMENT_NO_TEXT_LAYER | 扫描件且未开启 OCR（warning，文本为空） |
| E3001 | DEPENDENCY_MISSING | 缺少 ffmpeg / PyMuPDF 等 |
| E3002 | TIMEOUT | ffmpeg 执行超时 |
| E3003 | INVALID_CONFIG | 配置非法 |
| E9000 | INTERNAL_ERROR | 未预期错误 |

```python
from media_kit import ErrorCode

if not result.ok:
    print(result.error.code)          # ErrorCode 枚举
    print(result.error.code.value)    # "E1001"
    print(result.error.to_dict())     # {code,name,message,detail,stage,cause}
```

## 7. 插件扩展（新增格式不改核心）

```python
from media_kit import register_document_handler
from media_kit.document.handlers.base import BaseDocumentHandler, DocumentExtract

@register_document_handler(".log")
class LogHandler(BaseDocumentHandler):
    def extract(self, source, config, attachment_dir):
        ...
        return DocumentExtract(metadata=..., pages=[...], structure=[])
```

- 文档：继承 `BaseDocumentHandler`，实现 `extract()`，用 `@register_document_handler(".xxx")` 注册。
- 视频：继承 `BaseVideoHandler`，实现 `probe()`；或直接复用 `FFmpegVideoHandler` 注册新扩展名。
- 内置处理器在 `media_kit` 首次导入时自动发现；第三方插件只需 import 一次注册模块。
- 运行 `python media_kit/examples/custom_plugin.py` 可见完整示例。

## 8. 示例

```bash
python media_kit/examples/parse_video.py D:/path/demo.ts --out D:/tmp/v --transcode --frames
python media_kit/examples/parse_document.py D:/path/book.pdf --out D:/tmp/d --attachments
python media_kit/examples/custom_plugin.py
python media_kit/examples/custom_transcriber.py D:/path/demo.mp4
```

## 9. 测试

```bash
python -m unittest media_kit.tests.test_video media_kit.tests.test_document -v
```

覆盖：

- **视频**：mp4 / ts / mkv / webm 元数据、mp3 抽音轨、ts→mp4 转封装、抽帧、转写扩展点、
  文件不存在/空文件/损坏文件/格式不支持、白名单、strict、配置校验、`to_dict()`、门面调用。
- **文档**：文本层 PDF + 书签结构、扫描件 OCR、DOCX（段落/表格/标题样式/图片附件）、
  PPTX（多页）、XLSX（多工作表）、CSV、Markdown、TXT、HTML（含脚本过滤）、`max_chars`、
  各类错误码、strict、配置校验、插件注册。

> OCR 用例在没有 `rapidocr-onnxruntime` 或无中文字体时自动跳过。

## 10. 设计说明与约定

- **业务解耦**：核心只认扩展名、配置和协议；不读取任何平台配置、不内置密钥。
- **错误不混入业务**：统一 `ErrorInfo`/`ErrorCode`；需要异常时传 `strict=True`。
- **产物目录**：默认写在源文件旁 `<文件名>_parsed/`，可用 `output_dir` 指定；
  视频 `audio.*`、`normalized.*`、`frames/`；文档 `attachments/`。
- **页数语义**：PDF=页、PPTX=幻灯片、XLSX=工作表、DOCX=1（Word 不固定分页，`page_count=0`）。
- **OCR**：仅当 PDF 页没有文本层才触发；失败只产生 warning，不会中断整篇解析。
  若整篇都没有文本层且未开启 OCR，会返回 `ok=True` + `E2010` warning，避免静默空文本。
- **依赖**：ffmpeg 是外部二进制（`MEDIA_KIT_FFMPEG` 可指定路径）；OCR 模型路径可用
  `MEDIA_KIT_RAPIDOCR_CONFIG` 指定 RapidOCR 配置文件。

现有 `work_cn/`、`work_asr/` 下的专用脚本仍可独立运行；后续接入时把它们改成调用
`media_kit.parse_video(..., transcriber=...)` 与 `parse_document(...)` 即可，无需改动解析逻辑。
