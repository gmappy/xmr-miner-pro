# Project status (honest, component by component)

Last updated: 2026-11-23

Legend: **Working** = implemented and tested · **Built, untested on device** = compiled and verified locally but not
yet proven on the phone · **Planned** = no implementation yet.

| Component | State | Notes |
|---|---|---|
| Repository structure and documentation | Working | https://github.com/gmappy/xmr-miner-pro |
| XMRig v6.27.0 source pinned | Working | `vendor/xmrig`, GPL-3.0, unmodified, full source in repo. |
| ARM64 static XMRig engine | Built, untested on device | 2.6 MB static aarch64 ELF, RandomX JIT, secure JIT, HTTP API. `xmrig --version` verified. |
| Wallet address validation | Working | Monero block-base58 + Keccak-256 checksum verified against XMRig's published donation address. |
| Android APK (UI, service, config, controls) | Built, untested on device | Signed APK built; foreground service, persistent notification with Stop, local API polling. |
| GitHub repository | Working | Public repo created and pushed, GPL-3.0 at root. |
| Genuine pool connection and accepted shares | Planned | Needs a public wallet address and a live pool test. |
| Windows / Linux / macOS applications | Planned | No code yet. |
| Web management dashboard | Working (self-hosted) | Account, fleet overview, device detail, history chart, token rotation/removal. `dashboard/`. |
| Backend API + database | Working (self-hosted) | Node 24 built-in SQLite, scrypt passwords, HMAC user tokens, hashed device tokens, plan limits. `backend/`, 15/15 smoke checks passed. |
| Android → dashboard telemetry | Built, untested on device | Optional endpoint, ID and token in the app; sends real XMRig samples every 10 s plus a final stopped report. Needs a live run to verify. |
| Subscription / payment integration | Planned | No code yet. |
| Reproducible CI releases, signed release key | Planned | Prototype uses a generated dev key. |

## What has actually been verified

- aarch64 Alpine Linux with clang 22, CMake 4.2, libuv-static 1.52.1.
- XMRig CMake configure and full build with `ARM_V8=ON`, `WITH_SECURE_JIT=ON`, static linking.
- `xmrig-notls --version` reports XMRig 6.27.0, 64-bit AES, libuv/1.52.1.
- Keccak-256 empty-input digest matches the canonical value.
- Known-good Monero address decodes to 69 bytes with version 0x12 and a matching 4-byte checksum.
- Java compilation, dexing, packaging and APK signing all succeed.
- Dashboard API smoke test: 15/15 checks (auth, plan limits, telemetry, history, token rotation, removal).

## Known blockers / risks

1. **On-device mining is banned on Google Play.** Direct APK distribution only; users must be told what the app does.
2. **W^X / JIT memory.** `WITH_SECURE_JIT=ON` is used; still needs an on-device test.
3. **Memory.** RandomX light mode needs roughly 256 MB of cache; low-end phones may not sustain it.
4. **Thermal and battery limits.** Enforced in the service, but must be verified on a real device.
5. **Share latency.** At phone-class hashrates a share can take a long time on a normal pool. Use a low-difficulty
   or testnet pool for the first end-to-end test.
6. **Static musl DNS.** If pool hostnames fail, resolve in Java and pass an IP:port to XMRig.
7. **GPL-3.0.** Source is included in the repository; release archives must include the license and notices.
8. **Dashboard transport.** Use HTTPS (reverse proxy) before exposing it beyond localhost/LAN; device tokens must not cross the public internet in clear text.

## Next milestone

Android MVP: install APK → configure public wallet + genuine pool → start → observe real hashrate from XMRig API →
verify the pool reports an accepted share. Only then begin desktop ports.
