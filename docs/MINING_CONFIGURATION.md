# Mining configuration guide

## What you need

1. **A public Monero wallet address.** The app asks for this only. Never enter a private key, seed phrase or
   `keys` file anywhere in this product.
2. **A pool.** Choose a genuine Monero pool and confirm its fee, minimum payout and payout schedule on the
   pool's own website before mining.
3. **A worker name.** Any identifier you like; it lets you recognise this device on the pool's website.

Suggested public pools to evaluate (verify current details yourself — this list is not an endorsement):

- `pool.supportxmr.com:3333`
- `xmr.nanopool.org:14444`
- `moneroocean.stream:1000` (pays in XMR, algorithm switching; review their terms)
- P2Pool nodes: run your own or use a community node; payouts are direct via the P2Pool PPLNS model.

For a first end-to-end share test, use a pool with low share difficulty or a Monero testnet/stagenet pool.
Phone hashrates are low, so a normal public pool may take a long time to record the first accepted share.

## Android app settings

| Setting | Meaning |
|---|---|
| Wallet address | Public `4...` or `8...` Monero address. Validated locally (length, base58 alphabet, checksum). |
| Pool | `host:port`. Plain Stratum in the MVP. |
| Worker | Worker/rig name sent to the pool. |
| Threads | Number of CPU threads to mine with. Start at 1–2 on phones. |
| Temperature limit | Stops mining when the device reports a temperature above the limit (where available). |
| Charging only | Mining runs only while the battery is charging. |

## How rewards work

- The pool credits your **account/wallet** directly. This app never holds funds.
- `Accepted shares` is work the pool accepted. It is not XMR.
- `Estimated XMR/day` (if shown later) is a calculation based on current hashrate and network difficulty.
  It is an estimate, not a balance.
- `Pool balance` and `wallet payouts` must be checked on the pool's website and in your own wallet.

## Starting and stopping

1. Open the app, enter wallet, pool and worker.
2. Choose thread count and safety limits.
3. Tap **Start mining**. A notification appears; mining is visible and stoppable.
4. Tap **Stop mining** (in the app or the notification) at any time.

Force-stopping the app, rebooting, or uninstalling also stops mining. There is no autostart.

## Troubleshooting

| Symptom | Likely cause |
|---|---|
| `Engine failed to start` | Unsupported ABI, blocked executable, or JIT restriction — see ANDROID_ENGINE_NOTES.md. |
| `Connecting…` forever | Wrong pool/port, mobile network blocking Stratum, or DNS issue. |
| Hashrate zero | RandomX initialisation still in progress, or memory allocation failed. |
| Rejected shares high | Pool difficulty mismatch, stale shares, or unstable connection. |
| Device hot / battery draining | Lower thread count, enable charging-only, lower temperature limit. |
