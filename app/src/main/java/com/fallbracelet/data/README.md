# data — storage on the phone

## Purpose

Everything the app keeps on the phone: the phone-side settings (DataStore) and the patient's
data (`pasien.db` and the `pasien` DataStore).

## Contents

| File | What it does |
|---|---|
| `SettingsRepository.kt` | `AppMode` (WEARER / CAREGIVER), `AppSettings` and `SettingsRepository`, a Preferences DataStore named `settings`: paired box, the patient's nickname, relay URL and id, patient / PMO tokens, the PMO stream cursor |
| `PasienStore.kt` | Separate Room database `pasien.db` (version 1): `kejadian_pasien` (box openings and manual confirmations, with source and a demo flag) and `jam_minum` (dose time history, each entry applies from its date) |
| `PasienRepository.kt` | Preferences DataStore `pasien`: nickname, real start date, "box skipped", demo mode (on, virtual clock shift, virtual start date) |

## How it works

- **Relay address changes:** `setRelay` keeps the phone's tokens when the same relay (same
  `relayId`) turns up at a new address, and starts over only for a different relay.
- **Bracelet leftovers:** six old preference keys (SMS build, offline warning, ack cursor) are
  removed on the next write; `FallBraceletApp` deletes the old `bracelet.db`.

## Dependencies

- Room (with KSP), DataStore Preferences, kotlinx-coroutines.
- Used by `HpManager`, `PasienManager`, `caregiver/`, `ui/` and `FallBraceletApp`.
- `R.string.default_relay_url` (empty in `main`, the home Pi in `debug`) seeds `relayUrl`.

## Usage notes

Changing a `pasien.db` table needs a new database version and a migration in `PasienStore.kt`.
