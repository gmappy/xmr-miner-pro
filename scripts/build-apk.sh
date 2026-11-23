#!/bin/bash
# Package the signed Android APK using Buddy's on-phone Android toolchain.
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
SKILL="$HOME/.buddy/work/.claude/skills/android-app"

if [ ! -f "$ROOT/android/assets/xmrig" ]; then
  echo "Missing engine. Run scripts/build-engine-android.sh first." >&2
  exit 1
fi
bash "$SKILL/build-apk.sh" "$ROOT/android"
ls -lh "$ROOT/android/build/app.apk"
