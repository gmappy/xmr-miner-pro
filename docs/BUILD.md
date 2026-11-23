# Build instructions (Android prototype)

These commands run inside Buddy's Linux on the phone. No computer or Gradle is required.

## 1. One-time toolchain

```sh
bash ~/.buddy/work/.claude/skills/android-app/setup-toolchain.sh
```

## 2. Build the real mining engine

```sh
cd ~/.buddy/work/xmr-miner-pro
bash scripts/build-engine-android.sh
```

This compiles XMRig 6.27.0 from the pinned source in `vendor/xmrig` into a static ARM64 binary and copies it to
`android/assets/xmrig`. Check the output:

```sh
file vendor/xmrig-build/xmrig-notls
# ELF 64-bit LSB executable, ARM aarch64, statically linked
vendor/xmrig-build/xmrig-notls --version
# XMRig 6.27.0 ...
```

## 3. Build the signed APK

```sh
bash scripts/build-apk.sh
```

Output: `android/build/app.apk`.

The prototype is signed with an automatically generated development key. For production, replace it with a
dedicated release keystore kept outside the repository.

## 4. Install

The APK must be installed by the device owner. Open it from Downloads or use Buddy's `install_apk` tool and tap
Install/Confirm yourself. Android may ask you to allow installation from the source first.

## 5. Verify

1. Launch the app.
2. Enter a valid public XMR address (the hint turns green) and a genuine `pool:port`.
3. Set threads to 1, temperature limit to 65, optionally enable charging-only.
4. Tap **Start mining**. A persistent notification appears with a Stop action.
5. Watch **Live statistics** — values are read from XMRig's own HTTP API on `127.0.0.1:33445`.
6. Check the pool's website for an accepted share. This is the acceptance test.

## Troubleshooting the build

- `libuv.a` missing → `apk add libuv-static`.
- CMake cannot find UV → install `libuv-dev`.
- Java errors → run the toolchain setup script again.
- `versionCode` must be bumped in `android/app.properties` for every update.
