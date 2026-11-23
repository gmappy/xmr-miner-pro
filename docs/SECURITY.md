# Security and safety

## Non-negotiable rules

- No hidden mining, stealth install, unauthorised persistence, security bypasses or concealed CPU usage.
- Mining only after an explicit start action; visible foreground notification with a Stop action at all times.
- No BootCompleted receiver, no background restart after user stop, no silent reinstall of the engine.
- Never request, transmit or store private keys or seed phrases. Reject input that looks like one (64+ word
  mnemonic or `keys` file content) with a clear message.
- Local HTTP API binds to `127.0.0.1` only and uses a freshly generated random bearer token per session.
- No remote control of mining from the management platform in the MVP. Later fleet settings are limited, audited
  and overridable on the device.

## Mobile

- Use `android:requestLegacyExternalStorage=false`; no external storage access.
- App-private `SharedPreferences` for settings. No logs containing wallet addresses beyond the local UI.
- Validate all pool strings against a strict `host:port` pattern before passing them to the engine.
- Limit thread count to a safe fraction of available cores; never use all cores by default.
- Thermal monitoring via `PowerManager.getCurrentThermalStatus()` (API 29+) and battery/charging state; stop at
  configured limits.
- Release build must use a dedicated signing key stored outside the repository; the dev key in this prototype is
  not for production.

## Backend and dashboard (planned)

- HTTPS everywhere (HSTS), Argon2id password hashing, rate limiting, email verification.
- Device registration tokens generated server-side, rotatable and revocable.
- Strict input validation, parameterised SQL, least-privilege DB accounts, encrypted backups.
- API never stores private keys. Public wallet addresses are optional user data.
- Audit log for device registration, removal and configuration changes.
- Dependency scanning and SAST in CI; signed release artifacts with published SHA-256 checksums.

## Safety limits in the app

| Limit | Default | Behaviour |
|---|---|---|
| CPU threads | min(2, cores-1) | User may reduce; never exceeds cores-1. |
| Temperature | 65 °C where reported | Pause mining, notify user. |
| Battery | stop below 20% unless charging | Pause mining, notify user. |
| Charging only | off by default | User can enable; mining pauses when unplugged. |
| Session length | none enforced | User controls; notification always visible. |

## Reporting vulnerabilities

Until a security policy is published, report issues privately to the repository owner. Do not open public issues
for exploitable bugs.
