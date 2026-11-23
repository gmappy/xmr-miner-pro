# Fleet dashboard (working, self-hosted)

The management platform monitors devices that you own and operate. It displays only statistics the mining
app chooses to send: hashrate, accepted/rejected shares, uptime, pool, battery and device status.
It never receives wallet addresses, seed phrases or private keys.

## Run it

Requires Node.js 22+ (Node 24's built-in `node:sqlite` is used; no npm install needed).

```sh
bash scripts/run-dashboard.sh
# open http://127.0.0.1:8787
```

Environment variables: `PORT` (default 8787), `HOST` (default 127.0.0.1),
`DB_PATH` (default `backend/data/xmrminerpro.sqlite`), `SECRET_FILE` (default `backend/data/secret.key`)
or `AUTH_SECRET`. The database and signing key are created automatically and are gitignored.
Back up both files together; losing the signing key invalidates all signed-in sessions.

## Connect a device

1. Open the dashboard, create an account, then **Add device**. Give it a name and platform.
2. Copy the generated **ID** and **token**. The token is shown only once; rotate it later if exposed.
3. In the Android app, fill **Fleet management (optional)** with the server URL, ID and token, then Start.
   * For a server on the same phone use `http://127.0.0.1:8787`; for a LAN server use its `http://192.168.x.x:8787` address.
   * For remote access, put the server behind HTTPS with a reverse proxy and use an `https://` URL.
     Don't expose the plain HTTP port to the internet: device tokens would travel in clear text.
4. The device posts to `/api/devices/{id}/telemetry` every 10 seconds. The dashboard refreshes every 5 seconds.
   A device is shown **online** if its last report is under 3 minutes old and its status isn't `stopped`.

## What the dashboard shows

- **Overview**: device count against your plan limit, online count, sum of reported hashrates, accepted shares.
- **Devices**: per-device cards, latest real statistics, an SVG hashrate history, token rotation and removal.
- **Setup**: how telemetry works and where to verify balances (the pool and your own wallet, not the dashboard).

Numbers come from XMRig's `/1/summary` through the app. If the miner cannot reach the pool, the app sends
`connecting` with zero statistics rather than inventing values.

## Plans and limits

Stored in `backend/db.js`: `free` = 1 device, `pro` = 10, `business` = 100.
Registration currently defaults to `free`; payment/subscription integration is not implemented yet.
There is no billing endpoint and no way to self-upgrade — don't treat plan labels as purchased entitlements.

## API

| Method | Path | Auth | Purpose |
|---|---|---|---|
| POST | `/api/auth/register` | — | Create account, returns user token |
| POST | `/api/auth/login` | — | Sign in, returns user token |
| GET | `/api/me` | user token | Account, plan, device limit |
| POST | `/api/devices` | user token | Register device; returns ID + one-time token |
| GET | `/api/devices` | user token | Fleet list + summary |
| GET | `/api/devices/{id}` | user token | Device detail + up to 120 telemetry samples |
| DELETE | `/api/devices/{id}` | user token | Remove device |
| POST | `/api/devices/{id}/rotate` | user token | Replace device token |
| POST | `/api/devices/{id}/telemetry` | device token | Submit one telemetry sample |

Passwords are hashed with scrypt and a per-user salt. User tokens are HMAC-SHA256 signed and expire after 7 days.
Device tokens are random, stored only as SHA-256 hashes. Auth endpoints are rate-limited per IP.

## Test it

```sh
bash scripts/run-dashboard.sh     # in one terminal
node backend/test.js              # in another
```

The smoke test registers a temporary account, enforces the free limit, submits telemetry, checks the fleet
summary and history, rotates and invalidates the device token, and removes the device.

## Honest limitations

- Local/dev-grade server: no email verification, password reset, audit log, backups or TLS termination built in.
- No user-facing admin panel to change plans; no payment provider integration.
- The browser stores the user token in `localStorage` (fine for a self-hosted single-user tool; review before
  exposing it to untrusted users).
- No remote start/stop command channel — this is monitoring only. Adding one would require explicit owner
  consent controls and stronger authorization.
