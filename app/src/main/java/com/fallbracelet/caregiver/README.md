# caregiver — caregiver mode

## Purpose

Everything a caregiver's phone does: link to a wearer through the relay, receive events by
push and by a live stream, ring a full-screen alarm for FALL / SOS, and send "Seen by"
acknowledgements back.

## Contents

| File | What it does |
|---|---|
| `CaregiverManager.kt` | Process-wide owner of caregiver mode. Links with an invite code, registers the FCM token, runs the Server-Sent Events stream, dispatches events to `AlertDispatcher`, acknowledges, signs out. Also `RemoteEvent`, `WatchedWearer`, `StreamState` |
| `CaregiverService.kt` | Foreground service (`remoteMessaging`) that keeps the stream alive when push isn't available; `ForegroundStartGate` avoids a crash when a stop arrives before `startForeground()`; `AckReceiver` handles the notification's Acknowledge button |
| `FcmService.kt` | Firebase Cloud Messaging entry point: passes data messages to `CaregiverManager.handlePush` and new tokens to `onNewPushToken` |
| `AlarmActivity.kt` | Full-screen alarm over the lock screen until acknowledged; a newer alarm replaces the one shown |
| `HistoryView.kt` | Pure functions for the dashboard history: Day / Week / All periods (weeks Monday–Sunday), day names, "5 min ago" |

## How it works

- **Push first, stream as fallback.** Once the relay has this phone's FCM token, the stream
  runs only while the dashboard is on screen (`MainActivity.onStart/onStop` call
  `setVisible`). Without push (no Play services or no internet when linking), the stream and
  `CaregiverService` stay on in the background.
- **No double ring.** Push and stream can deliver the same event; `onEvent` remembers
  `eventId:escalations` (last 200) and alerts once per escalation.
- **Reconnect replay.** The stream sends `Last-Event-ID` (`lastStreamEventId` in settings), so
  the relay replays up to 24 h of missed messages.
- What rings is decided by `alert/AlertRules.caregiverAlert`: FALL / SOS alarm, TEST rings
  once, LOW_BATTERY / OFF notify, the rest is history only.

## Dependencies

- `relay/` (`RelayClient`, `RelayFinder`), `data/SettingsRepository`, `alert/AlertDispatcher`
  and `AlertRules`, `ble/EventType`.
- Firebase Messaging and `kotlinx-coroutines-play-services`; needs `app/google-services.json`.
- Shown by `ui/ModeScreens.kt` (`CaregiverScreen`). The relay's web page
  (`relay/web/index.html`) mirrors this mode in a browser.

## Usage notes

`ForegroundStartGateTest` and `HistoryViewTest` in `app/src/test` cover the start gate and
history periods.
