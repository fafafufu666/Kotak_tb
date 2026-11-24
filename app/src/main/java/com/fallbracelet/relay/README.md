# relay — talking to the Raspberry Pi relay

## Purpose

The app's HTTP client for [`relay/server.mjs`](../../../../../../../relay/README.md): the wearer
phone's calls (register, upload, heartbeat, invites) and finding the Pi on the local WiFi.
The caregiver side of the relay lives in [`caregiver/`](../caregiver/README.md).

## Contents

| File | What it does |
|---|---|
| `RelayClient.kt` | Minimal JSON-over-`HttpURLConnection` client with a bearer token. Non-2xx replies throw `RelayException` (`status` 0 = unreachable) |
| `WearerRelay.kt` | Wearer-phone API: `ensureRegistered`, `rename`, `upload` (implements `data.EventRelay`), `heartbeat`, `invite`, `caregivers`, `removeCaregiver`. Also `LinkedCaregiver`, `Invite`, `Ack`, `HeartbeatReply` |
| `RelayDiscovery.kt` | `RelayDiscovery` probes `/v1/health` on port 8787 across every address of the phone's private /24 networks (48 at a time). `RelayFinder` re-runs that scan after a failure, at most once a minute, and saves the new address |

## How it works

- The relay identifies itself with a `relayId` in `/v1/health`. When the Pi moves to another
  WiFi (for example a phone hotspot), discovery prefers the same `relayId`, so the phone's
  tokens and links survive the address change.
- `HpManager.relayLoop()` drives this package every minute: check the relay, re-find it if
  needed, register, and heartbeat every 5 minutes. ESKALASI and STATUS_HARIAN go out from
  `PasienManager`.

## Dependencies

- `org.json` and `java.net` from the platform; no HTTP library.
- `data/SettingsRepository` for the URL and tokens.
- Used by `HpManager`, `PasienManager`, `caregiver/CaregiverManager` (`RelayClient`, `RelayFinder`) and `ui/`.
- Server side and API table: [`relay/README.md`](../../../../../../../relay/README.md).

## Usage notes

Debug builds allow cleartext HTTP (`app/src/debug/AndroidManifest.xml`); release builds need
an `https://` relay.
