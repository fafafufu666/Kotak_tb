# com.fallbracelet — app source

## Purpose

All Kotlin source for the Android app. One app serves both the patient's phone (medicine box,
reminders, dose record, ESKALASI and the daily summary to the relay) and the PMO's phone (gets
ESKALASI from the relay, like the relay's web page).

## Contents

| Path | What it does |
|---|---|
| `FallBraceletApp.kt` | `Application`: deletes the bracelet app's leftovers (notification channels, `bracelet.db`, recordings), creates `SettingsRepository`, `HpManager`, `PasienManager`, `KotakManager` and `CaregiverManager`, and starts them. Adds `Context.manager` / `.pasien` / `.kotak` / `.caregiver` |
| `HpManager.kt` | Phone settings and the relay link as the patient's phone: registration, nickname, heartbeat every 5 min (the relay tells the PMO when the phone goes quiet), relay discovery, box pairing address, mode switch |
| `PasienManager.kt` | Patient-mode owner: reads `assets/aturan_program.json` once at start, records box openings (`catatPembukaanKotak`, the one path for the simulated and the real box) and manual confirmations, runs demo mode, works out `KeadaanPasien` every 15 s with `adherence/`, sends ESKALASI and STATUS_HARIAN |
| [`adherence/`](adherence/README.md) | Android-free medication rules (PRD section 7) and the rules file reader |
| [`kotak/`](kotak/README.md) | The medicine box: protocol v2, the BLE link to the real box, the simulated ESP32, `KotakManager`, `KotakService` |
| [`pengingat/`](pengingat/) | Reminder alarms and notifications (P5) |
| [`ble/`](ble/README.md) | BLE scanner for box pairing (P3) and `LinkState` |
| [`data/`](data/README.md) | DataStore settings, the patient database `pasien.db` |
| [`relay/`](relay/README.md) | HTTP client for the relay and relay discovery on the local WiFi |
| [`caregiver/`](caregiver/README.md) | PMO mode: live stream, push, ESKALASI notifications and alert screen, "Tandai dilihat" |
| [`ui/`](ui/README.md) | `MainActivity`, `AppViewModel` and all Compose screens: patient mode in `ui/pasien/`, PMO mode in `ui/pmo/` |
| `service/BootReceiver.kt` | After a reboot or an update: starts the app and sets the next reminder alarm again |

## How it works

```
box (BLE, or simulated) ─protocol v2─▶ KotakManager ─▶ PasienManager ─ESKALASI / STATUS_HARIAN─▶ WearerRelay ─▶ relay
relay ─SSE (FCM later)─▶ CaregiverManager ─▶ NotifikasiPmo / AlarmActivity / ui/pmo/PmoApp
```

- The managers live in `FallBraceletApp`, not in an activity, so they keep running while the
  services hold the process.
- Only one mode is active: the patient side runs when `mode == WEARER`, and `CaregiverManager`
  streams only when a PMO token is set and `mode == CAREGIVER`.

## Dependencies

See [`app/README.md`](../../../../../README.md) for libraries. Tests for this code are in
`app/src/test/java/com/fallbracelet/`.
