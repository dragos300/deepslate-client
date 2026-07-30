#!/usr/bin/env bash
# Install Deepslate Launcher shortcuts (Linux desktop entry / macOS .app).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT"
exec python3 "$ROOT/desktop_install.py" install
