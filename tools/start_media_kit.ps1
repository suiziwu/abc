# 启动 media_kit 本地解析服务（供若依后端 /project/media 代理调用）
# 用法：在 PowerShell 中执行  .\tools\start_media_kit.ps1
# 可选环境变量：MEDIA_KIT_PORT（默认 8130）、MEDIA_KIT_TOKEN（启用鉴权）

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$env:PYTHONPATH = Join-Path $root "tools"
$env:PYTHONIOENCODING = "utf-8"
if (-not $env:MEDIA_KIT_PORT) { $env:MEDIA_KIT_PORT = "8130" }

Write-Host "启动 media_kit：http://127.0.0.1:$env:MEDIA_KIT_PORT" -ForegroundColor Cyan
Write-Host "项目根目录：$root"
python -m media_kit.server
