# app — Android app

## Purpose

The Gradle module for the Android app: Kotlin, Jetpack Compose, min SDK 26, target SDK 36,
compile SDK 37, Java 17. `applicationId` is `id.esgas.kotakobat` (so it installs next to the
original FallBracelet app); the Kotlin package stays `com.fallbracelet`.

## Contents

| Path | What it does |
|---|---|
| `build.gradle.kts` | Module config: Compose, KSP (Room); the Google Services plugin is applied only when `google-services.json` exists; release builds are minified and shrunk |
| `proguard-rules.pro` | Keeps source file / line numbers in release stack traces |
| `google-services.json` | Firebase config for FCM. **Not in git**, optional: without it the app builds with no push (caregivers use the relay's live stream). CI writes it from the `GOOGLE_SERVICES_JSON` secret when set |
| [`src/main/java/com/fallbracelet/`](src/main/java/com/fallbracelet/README.md) | All app source |
| `src/main/AndroidManifest.xml` | Permissions (BLE, notifications, foreground services, boot, full-screen intent, battery optimisation, internet), `MainActivity`, `AlarmActivity` (PMO alert), `CaregiverService`, `FcmService`, `AckReceiver`, `PengingatKhususActivity`, `PengingatReceiver`, `BootReceiver` |
| `src/main/assets/aturan_program.json` | Program rules and message texts (PRD section 8). Values are Claude's proposal (`status_nilai` USULAN, 24-11-2026) until Abyan confirms them; treatment length checked against the 2025 Kemenkes juknis |
| `src/main/assets/aturan_bawaan.json` | Fallback copy of the rules, used section by section when `aturan_program.json` has a broken section. Not edited by hand |
| `src/main/res/` | App name, empty `default_relay_url`, theme and colours, launcher and status-bar icons, Indonesian texts (`strings.xml`; PMO mode uses the `pd_` keys) |
| `src/debug/AndroidManifest.xml` | Debug only: allows cleartext HTTP to the relay |
| `src/debug/res/values/relay.xml` | Debug only: `default_relay_url` = the home Pi, `http://192.168.0.104:8787` |
| `src/test/java/com/fallbracelet/` | JVM unit tests (below) |

### Tests (`src/test/java/com/fallbracelet/`)

| File | Covers |
|---|---|
| `KotakProtokolTest.kt` | Protocol v2 byte layouts, and `firmware/kotak_obat/ble_protocol.h` against the app's mirror |
| `KotakSimulasiTest.kt` | The simulated box behaves like the firmware should (PRD 5.2 F1–F6) |
| `BleProtocolTest.kt` | Legacy: bracelet protocol v1 byte layouts against `firmware/fall_bracelet/ble_protocol.h` |
| `ForegroundStartGateTest.kt` | Stopping `CaregiverService` during startup doesn't crash |
| `TampilanPmoTest.kt` | PMO mode reads the relay like the web page: dashboard, demo/real filter, which ESKALASI ring, the 7-day row, merging updates |
| `AturanProgramTest.kt` | Both rules files are valid; a broken section falls back alone with a warning |
| `LogikaKepatuhanTest.kt` | PRD section 7: day and phase, dose status and window edges, midnight, vulnerable days, messages, escalation |

## Dependencies

From `gradle/libs.versions.toml`: Compose BOM, Material 3, Activity Compose, Lifecycle
(ViewModel, runtime, service), kotlinx-coroutines (+ play-services), Room (KSP), DataStore
Preferences, Firebase BOM / Messaging, JUnit 4, and `org.json` for unit tests only (Android's own
copy is stubbed in JVM tests). Talks to the medicine box over BLE protocol v2
([`PROTOCOL.md`](../PROTOCOL.md); a simulated box until the real link is added) and to the Pi relay over HTTP ([`relay/`](../relay/README.md)).

## Usage notes

No local Android SDK? Every push runs `.github/workflows/build-apk.yml` on GitHub Actions:
unit tests, then `assembleDebug`; the APK is the `apk-debug` artifact of that run.

From the repo root:

```bash
./gradlew :app:assembleDebug
```

```bash
./gradlew :app:testDebugUnitTest
```

- Release builds are signed with the **debug** key (`signingConfig = debug` in
  `build.gradle.kts`), although `.gitignore` reserves `keystore.properties` / `*.jks` for a
  real key.
- Debug builds point at a fixed LAN address; change `src/debug/res/values/relay.xml` or set
  the address in the app (Settings, or "Find relay on this WiFi").
