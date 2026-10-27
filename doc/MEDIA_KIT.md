# media_kit 视频 / 文档解析组件接入说明

本项目已把通用解析组件 [media_kit](../tools/media_kit/README.md) 内置到 `tools/media_kit`，
并通过 `MediaKitController` 暴露为若依接口。整体链路：

```text
前端 / 其它服务
      │  /project/media/**（若依鉴权）
      ▼
MediaKitController（Java，转存上传、代理请求、错误码透传）
      │  HTTP JSON  127.0.0.1:8130
      ▼
media_kit Python 服务（ffmpeg / PyMuPDF / OCR / Office 解析）
```

> 组件与业务无关，可独立复用；本项目的 Java 控制器只是薄代理。

## 1. 安装 Python 依赖

要求 Python 3.10+（本机已验证 3.12）。

```powershell
cd D:\AAAWork\Knowledge\Knowledge
python -m pip install -r tools\media_kit\requirements.txt
```

- 视频解析需要 ffmpeg：`imageio-ffmpeg` 已包含静态二进制；也可自行安装 ffmpeg 并加入 PATH。
- 扫描件 OCR 依赖 `rapidocr-onnxruntime`，不装时扫描 PDF 会返回 `E2010` 警告（文本为空）。
- 不支持的格式：`doc/xls/ppt` 请先另存为 `docx/xlsx/pptx`。

## 2. 启动解析服务

```powershell
cd D:\AAAWork\Knowledge\Knowledge
.\tools\start_media_kit.ps1
```

默认监听 `http://127.0.0.1:8130`。可用环境变量覆盖：

| 变量 | 默认 | 说明 |
| --- | --- | --- |
| `MEDIA_KIT_PORT` | 8130 | 监听端口 |
| `MEDIA_KIT_HOST` | 127.0.0.1 | 监听地址（建议保持本机） |
| `MEDIA_KIT_TOKEN` | 空 | 设置后请求需带 `X-Media-Kit-Token` |

健康检查：

```powershell
curl.exe http://127.0.0.1:8130/health
```

## 3. 若依配置

`ruoyi-admin/src/main/resources/application.yml` 已追加：

```yaml
media-kit:
  base-url: http://127.0.0.1:8130
  token:                # 与 MEDIA_KIT_TOKEN 保持一致；为空不鉴权
  timeout-seconds: 600  # 单次解析超时（长视频/扫描件可调大）
  upload-dir:           # 为空时使用 <ruoyi.profile>/media_kit
```

修改配置后需重启后端；仅启动 Python 服务不需要重启 Java。

## 4. 接口

所有接口走若依登录鉴权，需带 `Authorization: Bearer <token>`。

| 方法 | 路径 | 入参 | 说明 |
| --- | --- | --- | --- |
| GET | `/project/media/health` | - | 服务状态、版本、已注册格式 |
| POST | `/project/media/video` | JSON `{path, config?}` | 解析服务器本地视频 |
| POST | `/project/media/document` | JSON `{path, config?}` | 解析服务器本地文档 |
| POST | `/project/media/video/upload` | multipart `file` + 可选 `config`(JSON 字符串) | 上传并解析视频 |
| POST | `/project/media/document/upload` | multipart `file` + 可选 `config` | 上传并解析文档 |

成功响应：`{"code":200,"msg":"操作成功","data": <media_kit 标准结果>}`，
`data` 结构与 Python API 的 `Result.to_dict()` 一致（见 `tools/media_kit/README.md`）。

失败响应：`{"code":500,"msg":"<错误消息>","errorCode":"E1001","detail":<完整结果>}`。

### 示例：解析本地视频

```powershell
curl.exe -X POST http://127.0.0.1:8081/project/media/video `
  -H "Authorization: Bearer <token>" -H "Content-Type: application/json" `
  -d '{"path":"D:/AAAWork/dataTest/docs/1.1.1 概念和功能.ts","config":{"audio":{"enabled":true}}}'
```

### 示例：上传文档并提取附件

```powershell
curl.exe -X POST http://127.0.0.1:8081/project/media/document/upload `
  -H "Authorization: Bearer <token>" `
  -F "file=@D:/data/book.pdf" `
  -F "config={""pdf"":{""ocr_fallback"":true},""attachments"":{""enabled"":true}}"
```

上传文件默认保存到 `upload/media_kit/video`、`upload/media_kit/document`；
解析产物（`audio.*`、`normalized.*`、`frames/`、`attachments/`）写在源文件旁
`<文件名>_parsed/`，可通过 `config.output_dir` 指定。

## 5. 常用配置

视频：

```json
{
  "transcode": {"enabled": true, "mode": "copy", "container": "mp4"},
  "audio": {"enabled": true, "format": "mp3", "sample_rate": 16000},
  "frames": {"enabled": true, "interval_sec": 10, "max_frames": 20}
}
```

文档：

```json
{
  "pdf": {"ocr_fallback": true, "ocr_dpi": 200, "ocr_max_pages": 0},
  "structure": {"enabled": true, "max_level": 4},
  "text": {"max_chars": 0},
  "attachments": {"enabled": true}
}
```

## 6. 错误码

与 `tools/media_kit/README.md` 一致：

- 视频：`E1001–E1011`（格式、文件、损坏、探测、转码、抽音轨/帧、转写、超限）
- 文档：`E2001–E2010`（格式、文件、文本/结构/附件、OCR、超限、无文本层）
- 公共：`E3001` 依赖缺失、`E3002` 超时、`E3003` 配置非法、`E9000` 内部错误

Java 会把 `errorCode` 放在响应顶层，`detail` 里保留完整错误对象与分页/警告信息。

## 7. 验证

```powershell
# 1) Python 服务
curl.exe http://127.0.0.1:8130/health

# 2) 组件自测（35 个用例）
cd D:\AAAWork\Knowledge\Knowledge
$env:PYTHONPATH = "tools"
python -m unittest media_kit.tests.test_video media_kit.tests.test_document

# 3) 若依代理（需后端已启动并重启过）
curl.exe http://127.0.0.1:8081/project/media/health -H "Authorization: Bearer <token>"
```

已验证：Python 服务可解析 21:46 的 `.ts`（1306.22s / 960×540）与 399 页扫描 PDF；
Java 侧编译通过后即可用上述接口调用。

## 8. 注意事项

- 本组件**不含语音识别**：ASR 属于业务能力，可在 Java 侧接入百炼，或给 Python 服务扩展
  `Transcriber`（见 `tools/media_kit/examples/custom_transcriber.py`）。
- `path` 接口要求文件在**服务器本机**；跨机调用请用上传接口。
- Python 服务仅监听回环地址，不对外暴露；生产环境建议用进程守护/计划任务常驻。
- 更新组件时，直接替换 `tools/media_kit` 目录并重启 Python 服务即可，Java 代理无需改动。
