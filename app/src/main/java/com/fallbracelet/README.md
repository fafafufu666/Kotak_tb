# com.fallbracelet — app source

## Purpose

All Kotlin source for the Android app. One app serves both the wearer's phone (talks to the
bracelet over BLE and forwards events to the relay) and caregivers' phones (get alarms from
the relay).

## Contents

| Path | What it does |
|---|---|
| `FallBraceletApp.kt` | `Application`: creates `AlertDispatcher`, `SettingsRepository`, the Room database, `BraceletManager` and `CaregiverManager`, and starts both. Adds `Context.manager` / `Context.caregiver` |
| `BraceletManager.kt` | Wearer-mode owner for the life of the process: connects to the paired bracelet, stores → alerts → ACKs → uploads each event, runs the relay loop (upload, heartbeat), reports link changes and the out-of-range warning, records trials |
| [`ble/`](ble/README.md) | BLE protocol, scanner and GATT connection to the bracelet |
| [`data/`](data/README.md) | Room event store and upload queue, DataStore settings, CSV recorder |
| [`relay/`](relay/README.md) | HTTP client for the Pi relay and relay discovery on the local WiFi |
| [`caregiver/`](caregiver/README.md) | Caregiver mode: push, live stream, full-screen alarm, acknowledgements |
| [`ui/`](ui/README.md) | `MainActivity`, `AppViewModel` and all Compose screens |
| `alert/AlertDispatcher.kt` | Notification channels (alarm, info, connection) and every notification: wearer-phone alerts, caregiver alarms (insistent, full-screen, Acknowledge action), offline notices. Also the event message wording |
| `alert/AlertRules.kt` | Android-free rules: what a caregiver's phone does per event type (`CaregiverAlert`), whether the wearer's phone notifies, when to warn the bracelet is out of range |
| `service/BraceletService.kt` | Foreground service (`connectedDevice`) that keeps the process and BLE link alive with a status notification; `BootReceiver` reconnects after boot or an app update |

## How it works

```
bracelet ─BLE─▶ BraceletConnection ─events─▶ BraceletManager.handle()
                                               ├─ EventDao.insert (dedupe by deviceAddress+seq)
                                               ├─ AlertDispatcher.notify (this phone)
                                               ├─ BraceletConnection.ack(seq)  → bracelet drops it
                                               └─ Uploader.flush → WearerRelay → Pi relay
Pi relay ─FCM / SSE─▶ CaregiverManager ─▶ AlertDispatcher.caregiverAlarm / AlarmActivity
```

- The two managers live in `FallBraceletApp`, not in an activity, so they keep running while
  the services hold the process.
- Only one mode is active: `BraceletManager` connects only when `mode == WEARER`, and
  `CaregiverManager` streams only when a caregiver token is set and `mode == CAREGIVER`.

## Dependencies

See [`app/README.md`](../../../../../README.md) for libraries. Tests for this code are in
`app/src/test/java/com/fallbracelet/`.
