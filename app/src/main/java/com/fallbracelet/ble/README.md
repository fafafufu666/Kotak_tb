# ble — Bluetooth LE link to the bracelet

## Purpose

The phone side of the bracelet's GATT protocol: finding bracelets, keeping one connected, and
turning its bytes into Kotlin types. Used only in wearer mode.

## Contents

| File | What it does |
|---|---|
| `BleProtocol.kt` | `BleProtocol` object: service/characteristic UUIDs, packet sizes, parse/encode for STATUS, EVENT, ACK, TIME, CONFIG and STREAM. Also the shared types `BraceletState`, `EventType`, `Command`, `BraceletStatus`, `BraceletEvent`, `BraceletConfig`, `AccelSample` |
| `BleScanner.kt` | `BleScanner.scan()`: a `Flow<FoundDevice>` of nearby bracelets, filtered by the service UUID in the advertisement |
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
- Used by `BraceletManager` (owns the connection), `ui/` (scanner, commands) and `data/Recorder`
  (`AccelSample`). `EventType` is also used by `alert/` and `caregiver/`.
- Android Bluetooth APIs and kotlinx-coroutines only.

## Usage notes

`BleProtocolTest` in `app/src/test` checks the byte layouts against the firmware's packet
structs. Add a case there whenever a packet changes.
