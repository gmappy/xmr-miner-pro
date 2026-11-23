# Legal and licensing

This document records constraints that shape the product. It is engineering guidance, not legal advice.

## XMRig / GPL-3.0

XMRig v6.27.0 is licensed GPL-3.0 (`vendor/xmrig/LICENSE`). Consequences:

- The complete corresponding source of the exact XMRig version shipped must be made available to every user.
  This repository pins the version and includes the build recipe, satisfying that obligation for source builds.
- The unmodified upstream source must remain unmodified here. Any patches must be documented and published.
- Bundling a GPL binary into a single distributed product means the combined work must be distributed under
  GPL-3.0-compatible terms. A closed-source proprietary wrapper that links/ships XMRig as one product is not
  compatible. Recommended resolution: distribute the whole mining application under GPL-3.0; keep any separate
  subscription/website service independent of the GPL code.
- Preserve copyright notices and provide the license text in every release archive.
- XMRig includes a default 1% donation. Do not disable it without understanding and complying with upstream's
  conditions.

## Google Play / app stores

Google Play's policy prohibits apps that mine cryptocurrency on user devices. Play Protect may also warn about
sideloaded mining APKs. Therefore:

- Distribute by direct APK download from the project website with full disclosure.
- Do not attempt to disguise mining, evade Play Protect, or use reflection/native loading tricks to hide it.
- The installer and app must clearly state: what is mined, which pool is used, CPU/battery impact and how to stop.

## Consumer protection and consent

- Mining must be started by an explicit user action every session. No boot-start, no silent restart.
- A persistent notification with a Stop control must remain visible while mining.
- Uninstalling the app must remove the bundled engine and configuration; no residual persistence.
- No private keys or recovery phrases. Only a public wallet address is collected.
- Clearly separate: estimated rewards (not a balance), pool-confirmed balance, and actual wallet payout.
- Never imply that subscriptions increase mining rewards.

## Device owner permission

Business fleet features must require the device owner's informed installation. Remote configuration is limited to
non-destructive mining settings (pool, thread count, temperature limit). The device owner can always override or
stop locally.

## Jurisdictions

Cryptocurrency mining, exchange and tax rules differ by country. Before public launch, obtain jurisdiction-specific
advice on: mining as a business, consumer subscriptions, tax reporting of mined XMR, and marketing claims.

## Third-party libraries

Keep an SBOM (software bill of materials) for each release. Run dependency audits in CI. Record versions of
XMRig, libuv, OpenSSL (if enabled later) and every frontend/backend package.
