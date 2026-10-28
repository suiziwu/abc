@echo off
rem Start RuoYi knowledge-base service on Windows.
rem Usage: start-app.bat [port]   (default 8081)
rem javaw.exe has no console, so closing the launcher window will not kill the service.
setlocal
cd /d %~dp0
rem Clear SERVER__PORT injected by some terminals, it would override server.port
set SERVER__PORT=
set PORT=%1
if "%PORT%"=="" set PORT=8081
if not exist logs mkdir logs
start "" /b "C:\Users\liu\.jdks\corretto-1.8.0_492\bin\javaw.exe" -Dfile.encoding=UTF-8 -jar ruoyi-admin\target\ruoyi-admin.jar --server.port=%PORT% >> logs\app-restart.log 2>&1
echo started on port %PORT%
endlocal
