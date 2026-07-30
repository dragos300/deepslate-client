@echo off
cd /d "%~dp0"
REM Prefer the cross-platform installer (delegates to PowerShell shortcuts on Windows)
if exist ".venv\Scripts\python.exe" (
  ".venv\Scripts\python.exe" desktop_install.py install
) else (
  where python >nul 2>&1 && python desktop_install.py install || (
    powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0install_app.ps1"
  )
)
pause
