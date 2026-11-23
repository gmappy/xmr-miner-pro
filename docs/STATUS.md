# Project status (honest, component by component)

Last updated: 2026-11-23

Legend: **Working** = implemented and tested · **Built, untested** = compiled but not verified end to end ·
**Scaffold** = code/design exists but is not functional · **Planned** = no implementation yet.

| Component | State | Notes |
|---|---|---|
| Repository structure and documentation | Working | This repo. |
| XMRig v6.27.0 source pinned | Working | `vendor/xmrig`, GPL-3.0, unmodified. |
| ARM64 static XMRig engine build | Built, untested | Built with musl static, RandomX JIT, secure JIT, HTTP API; no GPU/CUDA/OpenCL/TLS. |
| Android APK (UI, service, config, controls) | In progress | Plain Java, foreground service, XMRig process supervision. |
| Genuine pool connection and accepted shares | Planned | Requires a real wallet address and a live pool test. |
| Windows / Linux / macOS applications | Planned | No code yet. |
| Web management dashboard | Planned | No code yet. |
| Backend API + database | Planned | No code yet. |
| Subscription / payment integration | Planned | No code yet. |
| Signed releases, dependency auditing | Planned | Debug keystore only in the prototype build. |

## What has actually been verified

- aarch64 host with clang, CMake, libuv-static and the Android APK toolchain.
- XMRig CMake configure succeeds with ARMv8 RandomX JIT, secure JIT and HTTP API enabled.

## Known blockers / risks

1. **On-device mining is banned on Google Play.** Direct APK distribution only; users must be told what the app does.
2. **W^X / JIT memory.** Android restricts writable+executable mappings. `WITH_SECURE_JIT=ON` is used; this still needs an on-device test.
3. **Memory.** RandomX light mode needs roughly 256 MB of RAM for the cache; low-end phones may not sustain it.
4. **Thermal and battery limits.** Sustained CPU mining will throttle and degrade batteries. Thermal and charging-only limits are mandatory, not optional.
5. **Share latency.** At phone-class hashrates (typically a few hundred H/s), a share can take a long time on a normal pool. Test on a low-difficulty/testnet pool first.
6. **GPL-3.0.** Any distribution that bundles XMRig must provide complete corresponding source and license notices.

## Next milestone

Android MVP: install APK → configure public wallet + genuine pool → start → observe real hashrate from XMRig API → verify the pool reports an accepted share. Only then begin desktop ports.
