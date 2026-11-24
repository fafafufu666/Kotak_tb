# caregiver — PMO mode

## Purpose

Everything a PMO's phone does (PRD 6.2 M1–M3), the same as the relay's web page
(`relay/web/index.html`): link to a patient with the invite code, follow each patient's daily
summary and 7-day row, ring for each new or re-sent ESKALASI, and send "Tandai dilihat" back
(the patient then sees "Dilihat oleh …"). All texts are Indonesian and neutral (no diagnosis, no
medicine names).

## Contents

| File | What it does |
|---|---|
| `TampilanPmo.kt` | Pure model of what the relay sends: `PasienPmo` (dashboard entry), `RingkasanPmo` (STATUS_HARIAN), `HariPmo`, `KabarPmo` + `DetailEskalasi` (an ESKALASI). `TampilanPmo`: the 7-day row with the web page's ✓ ✗ ! • – symbols, which ESKALASI ring (unseen, current demo/real mode, last 12 h), merging stream updates |
| `CaregiverManager.kt` | Process-wide owner of PMO mode. Links, registers the FCM token, runs the Server-Sent Events stream (`event`, `days`, `status`, `online`, `offline`, `ack`, `unlinked`), keeps `berdering` (unseen ESKALASI that rang) and `nanti` (put aside until re-sent), marks seen, signs out |
| `NotifikasiPmo.kt` | The PMO's notification channels ("Kabar dosis terlewat", "Info pendampingan", "Sambungan ke relay"), made only on a PMO phone. Also deletes the bracelet app's old channels on every start |
| `CaregiverService.kt` | Foreground service (`remoteMessaging`) that keeps the stream alive while push isn't set up; `ForegroundStartGate` avoids a crash when a stop arrives before `startForeground()`; `AckReceiver` handles the notification's "Tandai dilihat" |
| `FcmService.kt` | Firebase Cloud Messaging entry point (only with `google-services.json`): passes data messages to `CaregiverManager.handlePush` |
| `AlarmActivity.kt` | An ESKALASI over the lock screen: name, "2 dosis terlewat berturut-turut", last box opening, "Tandai dilihat" / "Nanti" |

## How it works

- **Stream now, push later.** Without a Firebase project (the current plan) the stream and
  `CaregiverService` stay on in the background. Once the relay has this phone's FCM token, the
  stream runs only while the screen is open (`MainActivity.onStart/onStop` call `setVisible`).
- **No double ring.** Push and stream can deliver the same ESKALASI; `bunyikan` remembers
  `eventId:escalations` (last 200) and notifies once per re-send.
- **Demo and real don't mix.** Only ESKALASI of the mode the patient's phone is in now are shown;
  when demo starts or ends (`days` message), the other mode's alerts stop ringing.
- **Reconnect replay.** The stream sends `Last-Event-ID` (`lastStreamEventId` in settings), so
  the relay replays up to 24 h of missed messages. A `401` means the patient removed this PMO:
  the phone signs out and asks for a new code.

## Dependencies

- `relay/` (`RelayClient`, `RelayFinder`, `GalatRelay`), `data/SettingsRepository`.
- Firebase Messaging and `kotlinx-coroutines-play-services`.
- Shown by `ui/pmo/PmoApp.kt`.

## Usage notes

`TampilanPmoTest` and `ForegroundStartGateTest` in `app/src/test` cover the model and the start gate.
