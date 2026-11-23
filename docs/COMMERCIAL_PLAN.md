# Commercial launch plan (outline)

Not implemented. This is a planning document, not a claim of readiness.

## Editions

| Edition | Intended entitlement | Notes |
|---|---|---|
| Free | 1 registered device, basic stats, documentation | Unlimited time; no enforced mining limits. |
| Pro | Multiple devices, history, advanced controls, priority support | Billed monthly/yearly. |
| Business | Larger fleets, teams, central admin, reporting | Contract-based, invoicing. |

Subscriptions never affect hash rate, share acceptance or reward amounts. Entitlements gate dashboard/API features
only.

## Payment provider

Use a established payment provider (e.g. Stripe or Paddle) with webhooks, refunds and tax handling. Never store
card details. Webhook handlers must verify signatures and be idempotent.

## Launch sequence

1. Android MVP verified with real accepted shares (blocking milestone).
2. Publish source under GPL-3.0 with build instructions and license notices.
3. Direct APK distribution with disclosure page and checksums.
4. Desktop ports (Windows first, then Linux, then macOS) using the same XMRig local-API pattern.
5. Management dashboard + backend with registration and monitoring only.
6. Subscriptions, support desk, documentation site.
7. Public beta, then production launch.

## Marketing constraints

- No guaranteed earnings claims. No "passive income" guarantees.
- State hardware, electricity and pool-fee costs explicitly.
- State that on-device mining can reduce battery health and may be inefficient versus desktop hardware.
- State that Google Play does not permit on-device mining; this is a direct-download product.

## Support obligations

- Clear uninstall instructions and data-removal steps.
- Troubleshooting for JIT, memory, thermal and pool connection issues.
- Transparent changelog and known-issues list.
