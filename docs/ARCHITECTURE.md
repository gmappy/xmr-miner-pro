# Architecture

## Principle

The mining engine is a separate process. The application supervises it, configures it and reads its local API.
It never invents statistics and never accepts wallet private keys.

```
                    ┌──────────────────────────────┐
                    │  Android app (Java, no deps)  │
                    │  MainActivity / MiningService │
                    └──────────────┬───────────────┘
                                   │ start / stop / config
                                   │ HTTP GET 127.0.0.1:<port>/1/summary
                    ┌──────────────▼───────────────┐
                    │  XMRig ARM64 (bundled binary) │
                    │  RandomX · Stratum · API      │
                    └──────────────┬───────────────┘
                                   │ Stratum (TCP)
                    ┌──────────────▼───────────────┐
                    │  Customer-selected XMR pool   │
                    │  Payout → customer's wallet   │
                    └──────────────────────────────┘
```

## Android components

- **MainActivity** – wallet/pool/worker settings, CPU threads, temperature limit, charging-only toggle,
  Start/Stop, live stats and miner log. No hidden settings screen.
- **MiningService** – `START_STICKY` foreground service. Persistent notification shows state, hashrate and a
  Stop `PendingIntent`. Monitors battery level, charging state and (where available) thermal state; stops mining
  when a configured limit is exceeded. No boot receiver: mining never starts without the user.
- **XmrigController** – extracts the bundled ARM64 binary to app-private storage, writes a JSON config with a
  randomly generated API token, launches the process, supervises it, parses `/1/summary`, and kills it on Stop.
- **Preferences** – public wallet address, pool host/port, worker name, thread count, temperature limit,
  charging-only mode. Stored in app-private `SharedPreferences`; no private key material is accepted.

## Statistics (all sourced from XMRig `/1/summary`)

| UI field | Source field |
|---|---|
| Current hash rate | `hashrate.total[0]` |
| Average hash rate | `hashrate.total[2]` |
| Accepted shares | `results.shares_good` |
| Rejected shares | `results.shares_total - results.shares_good` |
| Total hashes | `results.hashes_total` |
| Pool connection uptime | `connection.uptime` |
| Pool / ping / failures | `connection.pool`, `connection.ping`, `connection.failures` |
| CPU model | `cpu.brand` |

Estimated XMR/day is **not** shown in the MVP. It is derivable from hashrate, difficulty and pool fee, but
presenting it as earnings would be misleading. When added, it must be labelled `Estimated only – not a balance`.

## Management platform (planned)

- FastAPI + PostgreSQL, REST over HTTPS, password hashing with Argon2id, device registration tokens.
- Devices report their own hashrate/uptime; the server never controls mining.
- No wallet credentials are stored server-side. A device may store only the public payout address.
- Free/Pro/Business entitlements are enforced in the API, never in the mining reward calculation.

## Packaging

- Android: direct APK, signed with a project release key (the prototype uses a generated dev key).
- Desktop: XMRig-based wrappers with the same local-API approach; shipped installers.
- CI: GitHub Actions builds engine + packages for Android/Windows/Linux/macOS, verifies checksums, publishes releases.
