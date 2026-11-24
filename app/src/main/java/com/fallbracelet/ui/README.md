# ui — Jetpack Compose screens

## Purpose

The app's only launcher activity and every screen, for both modes. Screens read state from
`AppViewModel`, which forwards actions to `HpManager` and `CaregiverManager`.

## Contents

| File | What it does |
|---|---|
| `MainActivity.kt` | Launcher activity (shows over the lock screen). Requests Bluetooth / notification permissions, hosts `App`, and tells `CaregiverManager` when it's visible |
| `AppViewModel.kt` | Phone-wide `AndroidViewModel`: mode, box scan and pairing (P3), relay URL / discovery, invites and linked PMOs (patient side), PMO linking, alerts and "Tandai dilihat" (PMO side) |
| `App.kt` | Top-level routing: no mode → `PilihModeScreen`; PMO → `pmo/PmoApp`; patient → `pasien/PasienApp` |
| `pmo/PmoApp.kt` | PMO mode (PRD 6.2), the same screen as `relay/web/index.html`: link card (code, name, relay address, "Cari relay"), per patient "Pendamping untuk", day, DEMO and phone-connection badges, travel-mode card, alert card for the newest unseen ESKALASI, the 7-day row (✓ ✗ ! • –), past alerts with "Dilihat oleh …"; the alert box with "Tandai dilihat" / "Nanti"; Indonesian warnings for notifications, full-screen, battery and network |
| `pasien/` | Patient mode (PRD 6.1), Indonesian text from `strings.xml`, teal–white theme (`TemaPasien.kt`): `PasienApp.kt` (routing, tabs, DEMO ribbon, mode choice, P3 pairing with "skip", temporary PMO tab), `MulaiScreen.kt` (P2), `BerandaScreen.kt` (P4), `RiwayatScreen.kt` (P6), `PengaturanScreen.kt` (P8 + demo mode), `Pemilih.kt` (date and time dialogs), `Format.kt` (Indonesian dates, status and phase labels), `PasienViewModel.kt` |
| `SetupChecks.kt` | `RelayHealth` and the helpers that open the phone's settings (notifications, full-screen, battery optimisation, app details) and check the network |
| `theme/Theme.kt` | `Tokens` (colours matching the web page's CSS variables), `ExtraColors`, `FallBraceletTheme`, `AppTheme` |

## How it works

- One activity, no navigation library: `App` switches on `AppSettings.mode` and the patient tabs.
- The PMO screen follows `relay/web/index.html`, so both show the same thing in the same words.

## Dependencies

- Compose Material 3, Activity Compose, Lifecycle ViewModel / runtime Compose.
- `HpManager` and `CaregiverManager` (via the `Context.manager` / `Context.caregiver`
  extensions in `FallBraceletApp.kt`), `ble/BleScanner`, `data/`, `relay/`, `caregiver/`.
