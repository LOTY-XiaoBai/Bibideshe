@echo off
setlocal
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\start-redis.ps1"
if errorlevel 1 (
  echo.
  echo Redis startup failed. See the message above.
  pause
  exit /b 1
)
echo.
echo Redis is ready. You can refresh the login page now.
timeout /t 5 >nul
exit /b 0
