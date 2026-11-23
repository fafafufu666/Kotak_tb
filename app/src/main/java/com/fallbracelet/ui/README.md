# ui — Jetpack Compose screens

## Purpose

The app's only launcher activity and every screen, for both modes. Screens read state from
`AppViewModel`, which forwards actions to `BraceletManager` and `CaregiverManager`.

## Contents

| File | What it does |
|---|---|
| `MainActivity.kt` | Launcher activity (shows over the lock screen). Requests Bluetooth / notification permissions, hosts `App`, and tells `CaregiverManager` when it's visible |
| `AppViewModel.kt` | Single `AndroidViewModel` for all screens: scanning and pairing, bracelet commands and config, relay URL / discovery, invites and caregivers, caregiver linking and acks, streaming and recordings |
| `App.kt` | Top-level routing: no mode → `PilihModeScreen`; caregiver → `CaregiverScreen`; wearer (patient) → `pasien/PasienApp`. The old bracelet tabs are kept as the unused `GelangApp`. Also `ScreenTitle` |
| `pasien/` | Patient mode (PRD 6.1), Indonesian text from `strings.xml`, teal–white theme (`TemaPasien.kt`): `PasienApp.kt` (routing, tabs, DEMO ribbon, mode choice, P3 pairing with "skip", temporary PMO tab), `MulaiScreen.kt` (P2), `BerandaScreen.kt` (P4), `RiwayatScreen.kt` (P6), `PengaturanScreen.kt` (P8 + demo mode), `Pemilih.kt` (date and time dialogs), `Format.kt` (Indonesian dates, status and phase labels), `PasienViewModel.kt` |
| `ModeScreens.kt` | First-launch mode choice and the whole caregiver UI: link with a 6-digit code, dashboard per wearer with stat tiles, Day / Week / All history, Acknowledge |
| `HomeScreen.kt` | Wearer Home tab: Bluetooth link, state, fall-countdown "I'm OK" card, battery, last check-in, recent events, Find and Send test alert |
| `EventsScreen.kt` | Wearer Events tab: full history with "Seen by" and late-delivery notes; `relativeTime` |
| `LiveScreen.kt` | Wearer Live tab: streams the accelerometer, plots total acceleration against the thresholds, records / shares / deletes labelled CSV trials |
| `SettingsScreen.kt` | Wearer Settings tab: out-of-range warning, bracelet settings (name, cancel window, check-in time), device actions, relay address, invites and linked caregivers |
| `SetupChecks.kt` | `setupProblems()` and `SetupWarnings`: lists what would stop alerts getting through (permissions, notifications, battery optimisation, full-screen alarms, internet, relay, no caregiver), with a fix button where possible |
| `Components.kt` | Shared `Card`, `SectionLabel`, `Pill` |
| `theme/Theme.kt` | `Tokens` (colours matching the web page's CSS variables), `ExtraColors`, `FallBraceletTheme`, `AppTheme` |

## How it works

- One activity, no navigation library: `App` switches on `AppSettings.mode` and a tab enum.
- The caregiver dashboard follows `relay/web/index.html`, so both look and filter the same.

## Dependencies

- Compose Material 3, Activity Compose, Lifecycle ViewModel / runtime Compose.
- `BraceletManager` and `CaregiverManager` (via the `Context.manager` / `Context.caregiver`
  extensions in `FallBraceletApp.kt`), `ble/`, `data/`, `relay/`, `caregiver/HistoryView`.
- Recordings are shared through the `FileProvider` declared in the manifest
  (`res/xml/file_paths.xml`).
