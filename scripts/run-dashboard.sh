#!/bin/bash
# Start the management API + web dashboard on localhost:8787.
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
cd "$ROOT"
mkdir -p backend/data
export PORT="${PORT:-8787}"
export HOST="${HOST:-127.0.0.1}"
echo "Open http://${HOST}:${PORT} in a browser"
exec node backend/server.js
