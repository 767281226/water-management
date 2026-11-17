@echo off
rem Hongyu Water Management - service management
rem   manage.bat                 interactive menu
rem   manage.bat status ^| start ^| stop ^| restart ^| logs ^| backup ^| restore [dir] ^| backup-task [HH:mm] ^| run ^| uninstall
setlocal
if /i "%~1"=="status" goto run
if /i "%~1"=="logs" goto run
net session >nul 2>&1
if %errorlevel% neq 0 (
  echo Requesting administrator privileges...
  if "%~1"=="" (
    powershell -NoProfile -Command "Start-Process -FilePath '%~f0' -Verb RunAs"
  ) else (
    powershell -NoProfile -Command "Start-Process -FilePath '%~f0' -ArgumentList '%*' -Verb RunAs"
  )
  exit /b
)
:run
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0manage.ps1" %*
pause
