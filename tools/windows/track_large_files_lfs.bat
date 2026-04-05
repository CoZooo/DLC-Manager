@echo off
setlocal

chcp 65001 >nul

set "SCRIPT_DIR=%~dp0"
set "PS_SCRIPT=%SCRIPT_DIR%track_large_files_lfs.ps1"

if not exist "%PS_SCRIPT%" (
  echo PowerShell script not found: %PS_SCRIPT%
  exit /b 1
)

powershell -NoProfile -ExecutionPolicy Bypass -Command "& $env:PS_SCRIPT @args" %*
exit /b %ERRORLEVEL%
