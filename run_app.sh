#!/usr/bin/env bash
# Cross-platform (macOS / Linux) launcher — no terminal UI chrome when possible.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT"

if [[ ! -x "$ROOT/.venv/bin/python" ]]; then
  python3 "$ROOT/desktop_install.py" setup
fi

exec "$ROOT/.venv/bin/python" "$ROOT/app.py"
