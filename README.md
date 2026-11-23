# Fall-detection bracelet

Android app, ESP32-C3 firmware and a Raspberry Pi relay for a fall-detection bracelet.
The bracelet detects a fall (or an SOS hold), buzzes a cancel window (20 s by default), then
sends the event over Bluetooth LE to the wearer's phone. The phone stores it and passes it to
the relay, which alarms the caregivers by push notification on their phones or in a web page.

```
bracelet ──BLE──▶ wearer's phone ──HTTP──▶ relay (Pi) ──push / live stream──▶ caregivers
```

## Tech stack

| Part | Stack |
|---|---|
| Bracelet | Seeed XIAO ESP32-C3, MPU6050, Arduino (esp32 core 3.x), NimBLE-Arduino 2.x |
| Phone app | Kotlin, Jetpack Compose / Material 3, Room, DataStore, Firebase Cloud Messaging; Gradle 9 wrapper, AGP 9.1, min SDK 26, target 36 |
| Relay | Node 22 (built-in SQLite, HTTP, Server-Sent Events), `firebase-admin`, systemd on a Raspberry Pi |

## Directory map

| Path | What's there |
|---|---|
| [`app/`](app/README.md) | Android app module, one app for both wearer and caregiver |
| ↳ [`app/src/main/java/com/fallbracelet/`](app/src/main/java/com/fallbracelet/README.md) | App source: managers, alerts, foreground service |
| ↳ [`ble/`](app/src/main/java/com/fallbracelet/ble/README.md) | BLE protocol and connection to the bracelet |
| ↳ [`data/`](app/src/main/java/com/fallbracelet/data/README.md) | Event store, upload queue, settings, recordings |
| ↳ [`relay/`](app/src/main/java/com/fallbracelet/relay/README.md) | HTTP client for the Pi relay, relay discovery |
| ↳ [`caregiver/`](app/src/main/java/com/fallbracelet/caregiver/README.md) | Caregiver mode: push, live stream, alarm |
| ↳ [`ui/`](app/src/main/java/com/fallbracelet/ui/README.md) | Activity, view model, Compose screens |
| [`firmware/`](firmware/README.md) | Arduino sketches, DevKit wiring, power and pre-release notes |
| ↳ [`fall_bracelet/`](firmware/fall_bracelet/README.md) | The bracelet firmware |
| ↳ [`fall_bracelet_oled/`](firmware/fall_bracelet_oled/README.md) | DevKit copy with an OLED status display |
| ↳ [`ble_msg_test/`](firmware/ble_msg_test/README.md) | Stand-in bracelet for testing the app by hand |
| [`relay/`](relay/README.md) | Node relay for the Raspberry Pi, caregiver web page, API |
| [`PROTOCOL.md`](PROTOCOL.md) | The BLE contract between bracelet and app |
| `gradle/` | Gradle wrapper and `libs.versions.toml` (all library and plugin versions) |
| `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties` | Root Gradle setup: plugins, repositories, the single `:app` module, 4 GB JVM heap |

The PCB (KiCad) and the 3D-printed case are kept outside this repo.

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

## Build and run

**App** (needs JDK 17+ and the Android SDK; `app/google-services.json` from the Firebase
project is optional and not in git; without it there is no push). GitHub Actions builds the
debug APK on every push (`.github/workflows/build-apk.yml`, artifact `apk-debug`):

```bash
./gradlew :app:assembleDebug
```

```bash
./gradlew :app:testDebugUnitTest
```

Debug builds point at the relay address in `app/src/debug/res/values/relay.xml`; the app also
finds the relay by itself on the local WiFi if its address changes.

**Firmware** (`arduino-cli` with the esp32 core 3.x and the libraries in
[`firmware/README.md`](firmware/README.md)):

```bash
arduino-cli compile --fqbn esp32:esp32:XIAO_ESP32C3 firmware/fall_bracelet
```

**Relay** (Node 22; deploy steps for the Pi in [`relay/README.md`](relay/README.md)):

```bash
cd relay && npm ci && npm test && npm start
```

The UI uses the dark palette from the project's web theme (`ui/theme/Theme.kt`).
