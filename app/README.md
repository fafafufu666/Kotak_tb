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
| `src/main/AndroidManifest.xml` | Permissions (BLE, notifications, foreground services, boot, full-screen intent, battery optimisation, internet), `MainActivity`, `AlarmActivity`, `BraceletService`, `CaregiverService`, `FcmService`, `AckReceiver`, `BootReceiver`, `FileProvider` |
| `src/main/res/` | App name, empty `default_relay_url`, theme and colours, launcher and status-bar icons, `xml/file_paths.xml` (shares `files/recordings/`) |
| `src/debug/AndroidManifest.xml` | Debug only: allows cleartext HTTP to the relay |
| `src/debug/res/values/relay.xml` | Debug only: `default_relay_url` = the home Pi, `http://192.168.0.104:8787` |
| `src/test/java/com/fallbracelet/` | JVM unit tests (below) |

### Tests (`src/test/java/com/fallbracelet/`)

| File | Covers |
|---|---|
| `BleProtocolTest.kt` | Byte layouts against `firmware/fall_bracelet/ble_protocol.h` |
| `AlertPathTest.kt` | Which events alarm whom, the offline warning, the upload queue |
| `ForegroundStartGateTest.kt` | Stopping `CaregiverService` during startup doesn't crash |
| `HistoryViewTest.kt` | Caregiver Day / Week / All periods |

## Dependencies

From `gradle/libs.versions.toml`: Compose BOM, Material 3, Activity Compose, Lifecycle
(ViewModel, runtime, service), kotlinx-coroutines (+ play-services), Room (KSP), DataStore
Preferences, Firebase BOM / Messaging, JUnit 4. Talks to the bracelet over BLE
([`PROTOCOL.md`](../PROTOCOL.md)) and to the Pi relay over HTTP ([`relay/`](../relay/README.md)).

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
