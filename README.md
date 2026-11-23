# XMR Miner Pro

Commercial, cross-platform Monero (RandomX) mining software.

## Important: read this first

This repository currently contains a **prototype foundation**, not a finished commercial product. Only the
items marked *Working* in [docs/STATUS.md](docs/STATUS.md) have been implemented and tested. Everything else is a
design document or a planned component. No simulated mining data is used anywhere: the Android app reads
hashrate and share counts from XMRig's own local HTTP API.

## What is here

| Path | Purpose |
|---|---|
| `android/` | Native Android mining application (Kotlin-free, plain Java + XMRig engine) |
| `vendor/xmrig` | XMRig v6.27.0 source (GPL-3.0), pinned as the mining engine |
| `vendor/xmrig-build` | Local build output for the ARM64 engine (not committed) |
| `scripts/` | Reproducible build scripts |
| `backend/` | Management API (planned) |
| `dashboard/` | Web dashboard (planned) |
| `docs/` | Architecture, licensing, security, configuration and launch documentation |

## Design commitments

1. **Genuine mining only.** RandomX via XMRig; all statistics come from the miner.
2. **No private keys.** Only a public Monero wallet address is ever requested.
3. **No hidden mining.** Mining runs in a foreground service with a persistent notification and a Stop action.
4. **No fabricated balances.** Estimated rewards, confirmed pool balances and wallet payouts are distinct concepts.
5. **Owner consent.** Every device is installed and operated by its owner, with visible controls.
6. **GPL compliance.** XMRig is GPL-3.0; its source and build recipe ship with the product.

## Distribution reality

On-device cryptocurrency mining is prohibited by Google Play's policy. Distribution must therefore be by
direct APK download, with clear disclosure and user consent. See [docs/LEGAL_AND_LICENSING.md](docs/LEGAL_AND_LICENSING.md).

## Quick start (development)

```sh
bash scripts/build-engine-android.sh      # build ARM64 XMRig engine
bash scripts/build-apk.sh                 # build the signed APK
```

Then install the APK on the phone and follow [docs/MINING_CONFIGURATION.md](docs/MINING_CONFIGURATION.md).

## Status

See [docs/STATUS.md](docs/STATUS.md) for the honest, component-by-component state of the project.
