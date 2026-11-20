# Fall-detection bracelet

Android app, ESP32-C3 firmware and a Raspberry Pi relay for a fall-detection bracelet.
The bracelet detects a fall (or an SOS hold), buzzes a cancel window (20 s by default), then
sends the event over Bluetooth LE to the wearer's phone. The phone stores it and passes it to
the relay, which alarms the caregivers by push notification on their phones or in a web page.

```
bracelet ──BLE──▶ wearer's phone ──HTTP──▶ relay (Pi) ──push / live stream──▶ caregivers
```

- [`app/`](app) — Android app (Kotlin, Jetpack Compose, min SDK 26), one app for both wearer and caregiver
- [`firmware/`](firmware) — Arduino sketches for the XIAO ESP32-C3 and an ESP32 DevKit ([build notes](firmware/README.md))
- [`relay/`](relay) — Node relay for the Raspberry Pi, with a caregiver web page ([setup and API](relay/README.md))
- [`PROTOCOL.md`](PROTOCOL.md) — the BLE contract between bracelet and app

## The app

On first start the app asks who the phone is for. Either mode can switch to the other later.

### Wearer mode

The phone stays near the wearer, keeps the BLE link up in a foreground service and
reconnects automatically, including after Bluetooth is turned back on.

| Tab | |
|---|---|
| Home | Bluetooth link, state, battery, last daily check-in, recent events; a red "I'm OK" card during a fall countdown; Find (buzz) and Send test alert. A banner lists anything that would stop alerts getting through (permissions, notifications, battery optimisation, full-screen alarms, internet, relay, no caregiver linked) |
| Events | Every event, with "Seen by …" once a caregiver has acknowledged it, and a note when it was delivered late |
| Live | Streams the 100 Hz accelerometer, plots total acceleration against the thresholds, records labelled CSV trials for tuning |
| Settings | Out-of-range warning; wearer name, cancel window and check-in time (written to the bracelet); switch off, forget, clear history; linked caregivers and invite codes; relay address |

Events are acknowledged to the bracelet only after they're stored, so nothing is lost if the
phone is out of range: the bracelet keeps them in flash and resends. The phone queues uploads
until the relay answers and sends a heartbeat every 5 minutes and whenever the bracelet
connects or drops.

### Caregiver mode

Link to a wearer with the 6-digit code from the wearer's Settings → Invite a caregiver. A
fall or SOS opens a full-screen alarm, even on a locked phone (Firebase Cloud Messaging). The
dashboard shows each wearer's online state, bracelet, battery, last check-in and a day / week /
all history, with Acknowledge on alarms. A caregiver can follow several wearers.

The relay also serves the same caregiver view as a web page at `http://<pi address>:8787/`.

## Build

```bash
./gradlew :app:assembleDebug
```

Needs `app/google-services.json` from the Firebase project (not in git). Debug builds point at
the relay address in `app/src/debug/res/values/relay.xml`; the app also finds the relay by
itself on the local WiFi if its address changes.

The UI uses the dark palette from the project's web theme (`ui/theme/Theme.kt`).
