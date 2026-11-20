# data — storage on the phone

## Purpose

Everything the app keeps on the phone: the event history and upload queue (Room), the
phone-side settings (DataStore), raw accelerometer recordings (CSV), and the loop that pushes
queued events to the relay.

## Contents

| File | What it does |
|---|---|
| `EventStore.kt` | Room database `AppDatabase` (version 3) with `EventEntity` and `EventDao`. `(deviceAddress, seq)` is unique, so a resent event is ignored (`insert` returns -1). Holds the migrations `SMS_TO_UPLOAD_QUEUE` (v1→2) and `ADD_ACKS` (v2→3) |
| `SettingsRepository.kt` | `AppMode` (WEARER / CAREGIVER), `AppSettings` and `SettingsRepository`, a Preferences DataStore named `settings`: paired bracelet, cached wearer name, offline warning, relay URL and id, wearer / caregiver tokens, ack and stream cursors |
| `Uploader.kt` | `EventRelay` and `UploadQueue` interfaces and `Uploader.flush()`, which sends queued events oldest first and stops at the first failure so order is kept. Does nothing until the relay is `configured` (address set and phone registered) |
| `Recorder.kt` | Writes the 100 Hz STREAM to `files/recordings/<time>_<label>.csv`, one file per labelled trial, for tuning the detector |

## How it works

- **No event is lost.** `BraceletManager` stores an event, then ACKs it to the bracelet, then
  calls `Uploader.flush()`. `uploadedAt` is null while the event waits for the relay.
- **Acks from caregivers** come back in the heartbeat reply and are written with
  `EventDao.setAck` ("Seen by …").
- **Relay address changes:** `setRelay` keeps the phone's tokens when the same relay (same
  `relayId`) turns up at a new address, and starts over only for a different relay.
- **Old SMS build:** migration v1→2 and three leftover preference keys clean up after the
  removed SMS feature.

## Dependencies

- Room (with KSP), DataStore Preferences, kotlinx-coroutines.
- `ble/` for `AccelSample`; `relay/WearerRelay` implements `EventRelay`.
- Used by `BraceletManager`, `caregiver/`, `ui/` and `FallBraceletApp`.
- `R.string.default_relay_url` (empty in `main`, the home Pi in `debug`) seeds `relayUrl`.

## Usage notes

Changing `EventEntity` needs a new database version and a `Migration` in `AppDatabase`.
`AlertPathTest` covers the upload queue.
