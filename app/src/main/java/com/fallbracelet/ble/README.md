# ble — Bluetooth LE scanner and the old bracelet GATT client

## Purpose

`BleScanner` finds the medicine box for pairing (P3) and `LinkState` is the link state the box
code uses. The rest is the phone side of the old bracelet's GATT protocol (v1), which nothing calls
any more: it is kept as the starting point for the real box link (`kotak/TautanKotak`).

## Contents

| File | What it does |
|---|---|
| `BleProtocol.kt` | `BleProtocol` object: service/characteristic UUIDs, packet sizes, parse/encode for STATUS, EVENT, ACK, TIME, CONFIG and STREAM. Also the shared types `BraceletState`, `EventType`, `Command`, `BraceletStatus`, `BraceletEvent`, `BraceletConfig`, `AccelSample` |
| `BleScanner.kt` | `BleScanner.scan()`: a `Flow<FoundDevice>` of nearby medicine boxes, filtered by the protocol v2 service UUID in the advertisement (`KotakProtokol.LAYANAN`) |
| `BraceletConnection.kt` | `BraceletConnection`: GATT client for one bracelet, exposing `link`, `status`, `config`, `events` and `stream` flows, plus `sendCommand`, `ack` and `writeConfig` |

## How it works

- **One GATT operation at a time.** Android allows only one outstanding read/write, so every
  operation goes through `op()`, which holds a mutex and suspends until the matching callback
  (10 s timeout).
- **Setup order after connecting:** discover services, request the 185-byte MTU, write TIME
  (the phone sets the bracelet's clock; 45 s timeout because it may trigger the pairing
  dialog), read CONFIG and STATUS, subscribe to STATUS and STREAM, and subscribe to EVENT last,
  because the bracelet starts sending queued events as soon as that subscription is on.
- **Reconnects:** after a drop it reopens with `autoConnect`, which waits until the bracelet is
  back in range. A receiver on Bluetooth adapter state reopens the link when Bluetooth is
  switched back on.
- **API 33 split:** read/write calls have both the old and the API 33+ signatures.

## Dependencies

- Mirrors `firmware/fall_bracelet/ble_protocol.h`; the contract is in
  [`PROTOCOL.md`](../../../../../../../PROTOCOL.md). Change all three together and bump `VERSION`.
- `BleScanner` is used by `ui/AppViewModel` (P3 box pairing); `LinkState` by `kotak/` and
  `ui/pasien/BerandaScreen`. The rest of `BraceletConnection.kt` and all of `BleProtocol` are not used.
- Android Bluetooth APIs and kotlinx-coroutines only.

## Usage notes

`BleProtocolTest` in `app/src/test` checks the byte layouts against the firmware's packet
structs. Add a case there whenever a packet changes.
