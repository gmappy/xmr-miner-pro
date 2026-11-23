#!/bin/bash
# Build the genuine ARM64 RandomX mining engine (XMRig) for Android.
# Requires: clang, cmake, make, musl-dev, openssl-dev, libuv-dev, libuv-static.
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
SRC="$ROOT/vendor/xmrig"
BUILD="$ROOT/vendor/xmrig-build"

apk add --no-cache cmake libuv-dev libuv-static >/dev/null 2>&1 || true

cmake -S "$SRC" -B "$BUILD" \
  -DCMAKE_BUILD_TYPE=Release \
  -DCMAKE_C_COMPILER=clang -DCMAKE_CXX_COMPILER=clang++ \
  -DBUILD_STATIC=ON -DWITH_HWLOC=OFF -DWITH_TLS=OFF \
  -DWITH_OPENCL=OFF -DWITH_CUDA=OFF -DWITH_NVML=OFF -DWITH_ADL=OFF \
  -DWITH_DMI=OFF -DWITH_MSR=OFF -DWITH_VAES=OFF \
  -DWITH_SSE4_1=OFF -DWITH_AVX2=OFF -DWITH_GHOSTRIDER=OFF -DWITH_KAWPOW=OFF \
  -DWITH_CN_LITE=OFF -DWITH_CN_HEAVY=OFF -DWITH_CN_PICO=OFF -DWITH_CN_FEMTO=OFF \
  -DWITH_SECURE_JIT=ON -DARM_V8=ON

cmake --build "$BUILD" -j4

mkdir -p "$ROOT/android/assets"
cp "$BUILD/xmrig-notls" "$ROOT/android/assets/xmrig"
chmod 755 "$ROOT/android/assets/xmrig"
sha256sum "$ROOT/android/assets/xmrig" | tee "$ROOT/android/assets/xmrig.sha256"
echo "Engine ready: $ROOT/android/assets/xmrig"
