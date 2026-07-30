@echo off
setlocal
cd /d "%~dp0"

if not exist ".venv\Scripts\python.exe" (
  echo Create the venv first: python -m venv .venv
  exit /b 1
)

.\.venv\Scripts\python.exe -m pip install -r requirements.txt pyinstaller
.\.venv\Scripts\python.exe -m PyInstaller --noconfirm DeepslateLauncher.spec

echo.
echo Built: dist\DeepslateLauncher.exe
echo On macOS/Linux use: ./build_app.sh
endlocal
