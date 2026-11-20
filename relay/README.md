# relay — Raspberry Pi relay

## Purpose

The server between the wearer's phone and the caregivers. The wearer's phone uploads events and a
heartbeat every 5 minutes, and as soon as the bracelet connects or drops. Caregiver phones get
alarms by Firebase Cloud Messaging push (`push.mjs`), which wakes a locked phone, and over a live
Server-Sent Events stream while the app or web page is open. Node 22 with its built-in SQLite; the
one package is `firebase-admin`. Without it, or without the key, the relay runs stream-only.

## Contents

| File | What it does |
|---|---|
| `server.mjs` | The relay: HTTP routes, SQLite schema (wearers, caregivers, links, invites, events, push tokens, meta), SSE fan-out, offline and escalation timers. `PORT` (default 8787), `DB_PATH` and `LAN_ONLY` (default on) env vars |
| `push.mjs` | FCM push: high-priority data messages for FALL/SOS and re-pushes, normal for TEST, LOW_BATTERY, OFF; drops tokens FCM rejects for good |
| `web/index.html` | Caregiver mode in a browser, served at `/` and `/caregiver` (see below); a single self-contained page |
| `test.mjs` | End-to-end test: starts `server.mjs` on port 18787 with a throwaway database and drives the API |
| `package.json` / `package-lock.json` | Node ≥ 22, `npm start` / `npm test`, the `firebase-admin` dependency |
| `fall-bracelet-relay.service` | systemd unit for the Pi (replace `YOUR_PI_USER`) |

Not in git: `relay.db*` (the database) and `firebase-service-account.json` (the push key).

## Dependencies

- Node 22 (`node:sqlite`, `node:http`), optional `firebase-admin` for push.
- Clients: the Android app's [`relay/`](../app/src/main/java/com/fallbracelet/relay/README.md)
  (wearer) and [`caregiver/`](../app/src/main/java/com/fallbracelet/caregiver/README.md)
  packages, and `web/index.html`.

## Usage notes

```bash
npm ci && npm test
```

## Run on the Pi

```bash
mkdir -p ~/fall-bracelet-relay/web && cp server.mjs push.mjs test.mjs package*.json ~/fall-bracelet-relay/ && cp web/index.html ~/fall-bracelet-relay/web/
(cd ~/fall-bracelet-relay && npm ci --omit=dev)
node --disable-warning=ExperimentalWarning ~/fall-bracelet-relay/test.mjs
sed "s/YOUR_PI_USER/$USER/g" fall-bracelet-relay.service | sudo tee /etc/systemd/system/fall-bracelet-relay.service >/dev/null
sudo systemctl daemon-reload && sudo systemctl enable --now fall-bracelet-relay
```

For now the relay only answers phones on the home network: requests from public addresses get
`403`. Set `LAN_ONLY=0` once it sits behind HTTPS.

**Changing WiFi (e.g. a phone hotspot at a demo):** join the Pi to the new network. The app finds
the relay by itself: when its address stops answering, each phone asks every address on its own
WiFi for `/v1/health` and switches to the one that replies with this relay's `relayId`, keeping its
links. Settings and the caregiver link screen also have "Find relay on this WiFi".

Debug builds of the app point at `http://192.168.0.104:8787` (`app/src/debug/res/values/relay.xml`)
and allow plain HTTP. Anything outside the home network needs HTTPS, e.g. a Cloudflare Tunnel.

## Caregiver mode in a browser

Open `http://<pi address>:8787/` on any phone or laptop on the same WiFi. It does what the app's
Caregiver mode does: link with the wearer's 6-digit invite code, a live stream with a full-screen
alarm and Acknowledge, and a dashboard per wearer (online, bracelet, state, battery, last check-in,
history). The token stays in that browser's storage, so a reload keeps the link.

A browser can't wake a locked phone the way the app's full-screen alarm does. The page rings
(after one tap, which browsers require before playing sound), vibrates on Android, flashes and
keeps the screen on only while it is open in the foreground. Notifications and the screen wake
lock need HTTPS, so on plain `http://` on the LAN they're off; the page says what's missing.
The page is read on every request, so copying a new `web/index.html` needs no restart.

## Push (Firebase Cloud Messaging)

Put the Firebase service-account key on the Pi as `firebase-service-account.json` next to
`server.mjs` (`chmod 600`, never committed; `FIREBASE_KEY` overrides the path). The log says
`push: Firebase Cloud Messaging on` at start. The app needs `app/google-services.json` (also kept
out of git) to build.

The caregiver app sends its FCM token to `PUT /v1/push-token` after linking and whenever Firebase
rotates it. Every message the relay fans out also goes by push: FALL and SOS (and their re-pushes)
as high-priority data messages, which open the full-screen alarm; TEST, LOW_BATTERY, OFF and
offline/online as normal priority. Tokens FCM rejects for good are deleted. Once its token is
registered the app drops the background live connection and its foreground service, and keeps the
stream only while the dashboard is open. A phone without push keeps the stream running in the
background as before.

## API

Every phone gets a random bearer token; only its SHA-256 is stored.

| Call | Who | What |
|---|---|---|
| `GET /v1/health` | anyone | `{ok, service, relayId, time}`; the app's relay discovery looks for this |
| `POST /v1/wearers` | wearer phone | Register, returns `token` |
| `PUT /v1/wearers/me` | wearer | Rename (the wearer name caregivers see) |
| `POST /v1/invites` | wearer | 6-digit code, single use, 15 min |
| `POST /v1/link` | caregiver | `{code, name}`; returns a caregiver token (send an existing one to follow another wearer) |
| `GET /v1/caregivers`, `DELETE /v1/caregivers/:id` | wearer | List / remove linked caregivers |
| `POST /v1/events` | wearer | Idempotent on (wearer, deviceAddress, seq) |
| `POST /v1/heartbeat` | wearer | Latest status; reply carries acknowledgements and the caregiver count |
| `GET /v1/dashboard` | caregiver | Each followed wearer with status and the last 100 events (`?limit=` up to 1000) |
| `POST /v1/events/:id/ack` | caregiver | "Seen by …" |
| `PUT /v1/push-token` | caregiver | `{token}`: this phone's FCM token |
| `GET /v1/stream` | caregiver | SSE: `hello`, `event`, `status`, `ack`, `offline`, `online`, `unlinked`. `Last-Event-ID` replays the last 24 h |

Timers: a wearer with no heartbeat for 15 min is pushed to caregivers as `offline`. A FALL or SOS
nobody acknowledged is pushed again every 2 min, up to 5 times.
