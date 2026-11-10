@echo off
setlocal
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\dev-ports.ps1" -Mode status
set "RESULT=%ERRORLEVEL%"
echo.
pause
exit /b %RESULT%
