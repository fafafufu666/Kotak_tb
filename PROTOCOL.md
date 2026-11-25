# Medicine box ↔ phone BLE protocol (version 2)

This is the contract between the medicine box's ESP32 firmware
(`firmware/kotak_obat/ble_protocol.h`) and the Android app
(`app/src/main/java/com/fallbracelet/kotak/KotakProtokol.kt`). Change both together and bump
`PROTOCOL_VERSION`; the unit test `KotakProtokolTest` reads the header and fails if the numbers
differ. The app's BLE link is `kotak/TautanBle.kt`; its simulated ESP32 (`kotak/KotakSimulasi.kt`)
implements the box side of the same contract, and the app talks to it only in these bytes.

The box records each lid opening with its own clock and keeps it in flash until the phone has
stored it (EVENT, then EVENT_ACK), so openings while the phone is away arrive later with their
real time (PRD 5.2 F1–F3). At the dose time it rings until the lid opens (F4).

All integers are **little-endian**. The box advertises as `Kotak-XXXX` (last two bytes of the
MAC) with the service UUID in the advertisement. The name says nothing about the medicine or
the illness.

## Service `5b0a0001-1f3c-4d2e-8a71-6b6f74616b00`

| Char | UUID | Props | Size | Purpose |
|---|---|---|---|---|
| STATUS | `5b0a0002-…` | read, notify | 12 B | State, lid, clock-set flag, queue length; pushed on change |
| EVENT | `5b0a0003-…` | read, indicate | 16 B | Queued events, oldest first, one at a time |
| EVENT_ACK | `5b0a0004-…` | write | 4 B | Phone confirms an event `seq`; the box deletes it and sends the next |
| TIME | `5b0a0005-…` | write | 6 B | Phone sets the clock (no RTC on the box) |
| CONFIG | `5b0a0006-…` | read, write | 24 B | Dose time, alarm length, dose window |
| COMMAND | `5b0a0007-…` | write | 1 B | One-byte commands |

Full UUIDs are `5b0a000X-1f3c-4d2e-8a71-6b6f74616b00`.

### STATUS (12 bytes)

| Offset | Type | Field |
|---|---|---|
| 0 | u8 | state: 0 IDLE, 1 ALARM (dose reminder ringing), 2 FIND (buzzing for FIND) |
| 1 | u8 | battery % (0–100), `0xFF` = no battery (USB power) |
| 2 | u16 | battery mV, 0 without a battery |
| 4 | u8 | lid: 0 closed, 1 open |
| 5 | u8 | flags: bit0 clock set, bit1 low battery, bit2 dose time set |
| 6 | u16 | events waiting in the flash queue |
| 8 | u32 | uptime, seconds |

### EVENT (16 bytes)

| Offset | Type | Field |
|---|---|---|
| 0 | u32 | seq — monotonic, survives reboots (NVS); fresh storage starts at a random number, never 1 |
| 4 | u8 | type: 1 LID_OPEN, 2 LID_CLOSE, 3 BOX_ON, 4 LOW_BATTERY, 5 TEST |
| 5 | u8 | battery % when it happened, `0xFF` without a battery |
| 6 | u32 | Unix time when it happened (0 = clock not set; the phone uses the receive time) |
| 10 | u16 | LID_CLOSE: minutes the lid stayed open, else 0 |
| 12 | u8 | LID_OPEN: 1 if the dose reminder was ringing |
| 13 | 3 B | reserved, 0 |

Delivery: once the phone subscribes to EVENT, the box indicates the oldest queued event and
waits for EVENT_ACK with the same `seq`. With no ACK after 10 s it sends the same event again.
The phone stores each (box name, `seq`) once, so a resent event never counts twice, and ACKs
only after storing it. The lid is debounced (≥ 50 ms) before an event is queued.

### TIME (6 bytes)

`u32` Unix time, `i16` local UTC offset in minutes. Written by the phone on every connection,
before it subscribes to EVENT, and again when the phone's clock moves (demo mode's virtual clock),
so the box's events and alarm follow the app's timeline.

### CONFIG (24 bytes)

| Offset | Type | Field |
|---|---|---|
| 0 | u8 | dose hour (0–23), `0xFF` until the phone writes one |
| 1 | u8 | dose minute (0–59) |
| 2 | u8 | alarm length, minutes (1–60): `dosis.durasi_alarm_kotak_menit` |
| 3 | u8 | protocol version (read-only, 2) |
| 4 | u8 | dose window opens this many minutes before the dose time (0–255): `dosis.jendela_awal_menit`. A lid opening from then on means the dose is taken, so the box doesn't ring that day |
| 5 | 19 B | device name, UTF-8, zero-padded (read-only, `Kotak-XXXX`) |

The phone writes the patient's dose time and the rules-file values whenever they change; the
box keeps its own name and version whatever the phone writes there.

### Dose reminder on the box (F4)

From the dose time, for the alarm length, the box buzzes and blinks its LED (state ALARM), unless
the lid was already opened inside the dose window that day. Opening the lid stops it (that
LID_OPEN has byte 12 = 1), as do the end of the alarm length and STOP_ALARM. It rings at most once
a day, plus whenever DEMO_ALARM asks.

### COMMAND (1 byte)

| Value | Command |
|---|---|
| 0x01 | FIND — buzz so the box can be found (P3 "Uji bunyi") |
| 0x02 | TEST — queue a TEST event (exercises the whole path) |
| 0x03 | DEMO_ALARM — start the dose reminder now, for the demo |
| 0x04 | STOP_ALARM — stop the reminder without opening the lid |

## Security

The box requires an encrypted, bonded link for every characteristic, as the bracelet did
(`BLE_REQUIRE_BONDING`). Android shows the system pairing prompt on first connection.

---

# Legacy: bracelet ↔ phone protocol (version 1)

The rest of this file is the FallBracelet protocol the fork started from. The app no longer has
any of it (its `ble/BleProtocol.kt` and GATT client were replaced by `kotak/TautanBle.kt` on
25-11-2026); only the legacy firmware still follows it (`firmware/fall_bracelet*`,
`firmware/ble_msg_test`). It goes when that firmware is removed; the box uses version 2 above.


This is the contract between the ESP32-C3 firmware (`firmware/fall_bracelet/ble_protocol.h`)
and the original FallBracelet app's `ble/BleProtocol.kt` (no longer in this repo).

The spec leaves the alert channel open; this implements **option B — BLE → phone companion
app**. The bracelet detects and queues events; the phone stores them, raises a notification,
queues them for the relay (which alerts the caregivers, see [`relay/README.md`](relay/README.md)),
and then acknowledges each event so the bracelet can drop it from flash.

All integers are **little-endian**. The device advertises as `FallBracelet-XXXX` (last two
bytes of the MAC) with the service UUID in the advertisement.

## Service `7a1f0001-6b2c-4e3d-9f10-a1b2c3d4e5f6`

| Char | UUID suffix | Props | Size | Purpose |
|---|---|---|---|---|
| STATUS | `…0002…` | read, notify | 12 B | Current state and battery, pushed on change and every 30 s |
| EVENT | `…0003…` | read, indicate | 16 B | Queued events, sent oldest first, one at a time |
| EVENT_ACK | `…0004…` | write | 4 B | Phone confirms an event `seq`; bracelet deletes it and sends the next |
| TIME | `…0005…` | write | 6 B | Phone sets the clock (replaces NTP) |
| CONFIG | `…0006…` | read, write | 24 B | The *setup* values from `config.h` |
| COMMAND | `…0007…` | write | 1 B | One-byte commands |
| STREAM | `…0008…` | notify | 2 + 6·n B | Raw accelerometer for logging/tuning (off by default) |

Full UUIDs are `7a1f000X-6b2c-4e3d-9f10-a1b2c3d4e5f6`.

### STATUS (12 bytes)

| Offset | Type | Field |
|---|---|---|
| 0 | u8 | state: 0 OFF, 1 MONITORING, 2 IDLE, 3 COUNTDOWN, 4 SENDING_ALERT |
| 1 | u8 | battery % (0–100) |
| 2 | u16 | battery mV |
| 4 | u8 | countdown seconds remaining (0 unless COUNTDOWN) |
| 5 | u8 | flags: bit0 clock set, bit1 streaming, bit2 low battery |
| 6 | u16 | events waiting in the flash queue |
| 8 | u32 | uptime, seconds |

### EVENT (16 bytes)

| Offset | Type | Field |
|---|---|---|
| 0 | u32 | seq — monotonic, survives reboots (stored in NVS); fresh storage starts at a random number, never 1, so a wiped bracelet can't reuse old numbers |
| 4 | u8 | type: 1 FALL, 2 SOS, 3 LOW_BATTERY, 4 OFF, 5 CHECKIN, 6 CANCELLED, 7 ON, 8 TEST |
| 5 | u8 | battery % when it happened |
| 6 | u32 | Unix time when it happened (0 = clock was not set; phone uses receive time) |
| 10 | u16 | peak impact, centi-g (FALL / CANCELLED), else 0 |
| 12 | u8 | alarms in the last 24 h (CHECKIN) |
| 13 | u8 | cancelled countdowns in the last 24 h (CHECKIN) |
| 14 | u16 | reserved, 0 |

Delivery: after the phone subscribes to EVENT, the bracelet indicates the oldest queued event
and waits for EVENT_ACK with the same `seq`. With no ACK after 10 s it resends, up to
`ALERT_RETRIES` times; after that it buzzes the "failed" pattern each minute and retries
every `ALERT_RETRY_MIN` minutes or on the next connection. The phone de-duplicates by `seq`,
so a resent event never alerts twice.

### TIME (6 bytes)

`u32` Unix time, `i16` local UTC offset in minutes. Written by the phone on every connection,
before it subscribes to EVENT.

### CONFIG (24 bytes)

| Offset | Type | Field |
|---|---|---|
| 0 | u8 | `COUNTDOWN_S` (5–60) |
| 1 | u8 | `CHECKIN_TIME` hour (0–23) |
| 2 | u8 | `CHECKIN_TIME` minute (0–59) |
| 3 | u8 | protocol version (read-only, currently 1) |
| 4 | 20 B | `WEARER_NAME`, UTF-8, zero-padded |

### COMMAND (1 byte)

| Value | Command |
|---|---|
| 0x01 | FIND — buzz 2 long so the bracelet can be found |
| 0x02 | CANCEL — cancel a running fall countdown (same as a button press) |
| 0x03 | STREAM_ON — start raw accelerometer notifications |
| 0x04 | STREAM_OFF |
| 0x05 | TEST — queue a TEST event (exercises the full alert path) |
| 0x06 | POWER_OFF — soft off (sends an OFF event first) |

### STREAM

`u16` packet counter, then up to 9 samples of `i16 ax, ay, az` in milli-g at 100 Hz.
The phone requests a 185-byte MTU so a full packet fits.

## Security

`BLE_REQUIRE_BONDING` in `config.h` (default on) makes every characteristic require an
encrypted, bonded link. Android shows the system pairing prompt on first connection.
