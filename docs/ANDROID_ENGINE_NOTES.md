# Android engine findings

Recorded from the actual build environment (aarch64 Alpine Linux inside Buddy, 2026-11-23).

## Why XMRig can run on Android

XMRig's own CMake files contain an `XMRIG_OS_ANDROID` branch and include an AArch64 JIT compiler for RandomX
(`jit_compiler_a64_static.S`). Native ARM64 mining is therefore supported upstream in principle — the desktop
binary cannot simply be copied, but the source can be compiled for the platform.

## Build recipe used

```
cmake -S vendor/xmrig -B vendor/xmrig-build \
  -DCMAKE_BUILD_TYPE=Release \
  -DCMAKE_C_COMPILER=clang -DCMAKE_CXX_COMPILER=clang++ \
  -DBUILD_STATIC=ON -DWITH_HWLOC=OFF -DWITH_TLS=OFF \
  -DWITH_OPENCL=OFF -DWITH_CUDA=OFF -DWITH_NVML=OFF -DWITH_ADL=OFF \
  -DWITH_DMI=OFF -DWITH_MSR=OFF -DWITH_VAES=OFF \
  -DWITH_SSE4_1=OFF -DWITH_AVX2=OFF -DWITH_GHOSTRIDER=OFF -DWITH_KAWPOW=OFF \
  -DWITH_CN_LITE=OFF -DWITH_CN_HEAVY=OFF -DWITH_CN_PICO=OFF -DWITH_CN_FEMTO=OFF \
  -DWITH_SECURE_JIT=ON -DARM_V8=ON
cmake --build vendor/xmrig-build -j4
```

`libuv-static` is required (`apk add libuv-static`). TLS is disabled to avoid an OpenSSL dependency; pool
connections are plain Stratum in the MVP. The HTTP API is kept enabled so the app can read real statistics.

## Flags explained

- `ARM_V8=ON` – forces the AArch64 RandomX JIT and code paths.
- `WITH_SECURE_JIT=ON` – uses dual-mapping techniques so code pages are not simultaneously writable and
  executable. Android enforces W^X for apps; this matters.
- `WITH_TLS=OFF` and all GPU backends off – smaller static binary, fewer dependencies.
- `WITH_HWLOC=OFF` – hwloc is not available/needed for phone topologies.

## Runtime constraints to test on the device

1. **Executable from app-private storage.** Android may block `Runtime.exec()` on files written to
   `getFilesDir()`. If so, ship the engine in `lib/arm64-v8a/` as a native library and execute from
   `nativeLibraryDir`, or load it through a tiny JNI shim.
2. **W^X.** If RandomX JIT still fails, try the interpreted/`--randomx-mode=light` path or revisit secure JIT.
3. **Memory.** Light mode cache is ~256 MB. Watch for `mmap` failures and OEM per-app memory caps.
4. **DNS.** A static musl binary may not use Android's netd resolver. If pool hostnames fail, resolve the host in
   Java (`InetAddress.getByName`) and pass a numeric IP:port to XMRig.
5. **Thermal.** Use `PowerManager.getCurrentThermalStatus()` (API 29+) where available, plus battery level and
   charging state; stop mining at the configured limit.
6. **Background rules.** A foreground service with a persistent notification is required; Android may still
   throttle CPU in the background depending on OEM battery optimisations.

## Verification checklist (must all pass for MVP)

- [ ] APK installs and launches on the target phone.
- [ ] Bundled engine extracts and starts; version is reported by the API.
- [ ] XMRig API `/1/summary` returns non-zero hashrate.
- [ ] Pool connection shows `connected` and increasing `shares_good`.
- [ ] Stop button terminates the process and notification disappears.
- [ ] Thermal and charging-only limits stop mining.
- [ ] No mining after app force-stop or device reboot.
