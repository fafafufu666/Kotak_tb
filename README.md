# Tuntas — connected TB medicine box

Android app, ESP32 firmware and a Node relay for a TB medicine box prototype (see
[`PRD.md`](PRD.md)). The box records every lid opening, even with no phone nearby, and rings at
the dose time. The patient's phone stores each opening, works out each dose's status, the
vulnerable days and missed doses from the rules file, and sends an ESKALASI (missed doses) and a
daily summary to the relay. The relay alerts the patient's PMO in the app or in a web page.

```
medicine box ──BLE (protocol v2)──▶ patient's phone ──HTTP──▶ relay (laptop / Pi) ──live stream / push──▶ PMO
```

The repo is a fork of a fall-detection bracelet project. The bracelet code has been removed from
the app; the old bracelet firmware and the v1 protocol are still in the repo for reference (see
[Legacy bracelet code](#legacy-bracelet-code)).

## Tech stack

| Part | Stack |
|---|---|
| Medicine box | ESP32 DevKit (ESP32-WROOM-32), reed switch or Hall sensor, active buzzer, on-board LED; Arduino (esp32 core 3.x), NimBLE-Arduino 2.x |
| Phone app | Kotlin, Jetpack Compose / Material 3, Room, DataStore, Firebase Cloud Messaging (optional); Gradle 9 wrapper, AGP 9.1, min SDK 26, target 36 |
| Relay | Node 22 (built-in SQLite, HTTP, Server-Sent Events), optional `firebase-admin`; runs on a laptop for the demo or under systemd on a Raspberry Pi |

## Directory map

| Path | What's there |
|---|---|
| [`PRD.md`](PRD.md) | Product requirements (Indonesian): goals, box, app screens, rules, test scenarios |
| [`app/`](app/README.md) | Android app module, one app for both the patient and the PMO |
| ↳ [`app/src/main/java/com/fallbracelet/`](app/src/main/java/com/fallbracelet/README.md) | App source: managers and packages below |
| ↳ [`adherence/`](app/src/main/java/com/fallbracelet/adherence/README.md) | Medication rules (PRD section 7), rules file reader |
| ↳ [`kotak/`](app/src/main/java/com/fallbracelet/kotak/README.md) | The medicine box: protocol v2, the BLE link, the simulated ESP32 |
| ↳ [`ble/`](app/src/main/java/com/fallbracelet/ble/README.md) | BLE scanner for box pairing |
| ↳ [`data/`](app/src/main/java/com/fallbracelet/data/README.md) | Settings and the patient database `pasien.db` |
| ↳ [`relay/`](app/src/main/java/com/fallbracelet/relay/README.md) | HTTP client for the relay, relay discovery |
| ↳ [`caregiver/`](app/src/main/java/com/fallbracelet/caregiver/README.md) | PMO mode: live stream, push, ESKALASI alerts |
| ↳ [`ui/`](app/src/main/java/com/fallbracelet/ui/README.md) | Activity, view model, Compose screens (`pasien/`, `pmo/`) |
| [`firmware/`](firmware/README.md) | Arduino sketches |
| ↳ [`kotak_obat/`](firmware/kotak_obat/README.md) | The medicine box firmware (ESP32 DevKit, protocol v2); pins in `config.h`, wiring in its README |
| [`relay/`](relay/README.md) | Node relay, the PMO web page, API |
| [`PROTOCOL.md`](PROTOCOL.md) | The BLE contract between the box and the app (v2), and the old bracelet one (v1) |
| [`docs/`](docs/) | Status, development log and test results (Indonesian) |
| `gradle/` | Gradle wrapper and `libs.versions.toml` (all library and plugin versions) |
| `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties` | Root Gradle setup: plugins, repositories, the single `:app` module, 4 GB JVM heap |

## The app

The app is called **Tuntas**; all texts are Indonesian. On first start it asks who the phone is
for. Either mode can switch to the other later.

### Patient mode (PRD 6.1)

Consent, start date and dose time, then box pairing (can be skipped), then four tabs:

| Tab | |
|---|---|
| Beranda | Day of treatment, today's dose and its window, "Saya sudah minum obat", the box (connection, last opening, queued events), the week, travel mode banner |
| Riwayat | Each day's dose status and source (box or manual), the special reminders shown, and each ESKALASI with "dilihat oleh …" |
| PMO | Invite code for the PMO, linked PMOs, the last alert sent to the PMO |
| Pengaturan | Dose time, travel mode, box pairing and test, relay address, rules-file warnings, demo mode (virtual clock), stop |

On vulnerable days the app shows a special reminder (`PengingatKhususActivity`) with the message
from the rules file.

Box openings are acknowledged to the box only after they're stored, so nothing is lost if the
phone is out of range: the box keeps them in flash and resends with the original time. While a
box is paired, a quiet notification ("Tersambung ke kotak obat") keeps the link up in the
background. "Lewati, pakai kotak simulasi" on the pairing screen uses a simulated box inside the
app that speaks the same protocol ([`kotak/`](app/src/main/java/com/fallbracelet/kotak/README.md)).

### PMO mode (PRD 6.2)

Link to a patient with the 6-digit code from the patient's PMO tab. Each patient shows the day
of treatment, the 7-day row and the alert card for the newest unseen ESKALASI. A new or re-sent
ESKALASI rings and opens a screen over the lock screen with "Tandai dilihat" / "Nanti"; the
patient then sees "Dilihat oleh …". The relay serves the same view as a web page at
`http://<relay address>:8787/`.

## Build and run

**App** (needs JDK 17+ and the Android SDK; `app/google-services.json` from the Firebase
project is optional and not in git; without it there is no push and PMO phones use the live
stream). GitHub Actions builds the debug APK on every push (`.github/workflows/build-apk.yml`,
artifact `apk-debug`):

```bash
./gradlew :app:assembleDebug
```

```bash
./gradlew :app:testDebugUnitTest
```

Debug builds point at the relay address in `app/src/debug/res/values/relay.xml`; the app also
finds the relay by itself on the local WiFi if its address changes.

**Firmware** (`arduino-cli` with the esp32 core 3.x and NimBLE-Arduino 2.x, see
[`firmware/kotak_obat/README.md`](firmware/kotak_obat/README.md)):

```bash
arduino-cli compile --fqbn esp32:esp32:esp32 firmware/kotak_obat
```

**Relay** (Node 22; laptop and Pi steps in [`relay/README.md`](relay/README.md)):

```bash
cd relay && npm ci && npm test && npm start
```

## Legacy bracelet code

Left over from the fork and not used by the app:

- `firmware/fall_bracelet/`, `firmware/fall_bracelet_oled/`, `firmware/ble_msg_test/`: the
  bracelet firmware, its OLED DevKit copy and a stand-in bracelet ([`firmware/README.md`](firmware/README.md)).
- In the relay: the FALL / SOS / LOW_BATTERY event types and their re-push timer, and the
  `wearers` naming in the API and database.
- The Kotlin package `com.fallbracelet` and the class names `FallBraceletApp` /
  `FallBraceletTheme`.
