#!/usr/bin/env bash
# Build a portable binary with PyInstaller (macOS / Linux).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT"

if [[ ! -x "$ROOT/.venv/bin/python" ]]; then
  python3 "$ROOT/desktop_install.py" setup
fi

"$ROOT/.venv/bin/python" -m pip install -q -r requirements.txt pyinstaller
"$ROOT/.venv/bin/python" -m PyInstaller --noconfirm DeepslateLauncher.spec

echo
if [[ "$(uname -s)" == "Darwin" ]]; then
  echo "Built: dist/DeepslateLauncher (or .app if configured)"
else
  echo "Built: dist/DeepslateLauncher"
fi
