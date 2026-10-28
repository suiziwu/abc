@echo off
setlocal
cd /d %~dp0
if not exist logs mkdir logs
set "JAVA_EXE=%USERPROFILE%\.jdks\corretto-1.8.0_492\bin\java.exe"
if not exist "%JAVA_EXE%" set "JAVA_EXE=java.exe"
set "SERVER__PORT="
"%JAVA_EXE%" -Dfile.encoding=UTF-8 -jar ruoyi-admin\target\ruoyi-admin.jar --server.port=8081 >> logs\app.log 2>&1
endlocal
