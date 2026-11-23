# kotak — the medicine box (stage 4)

## Purpose

The phone's side of the medicine box (PRD section 5) and, until the hardware exists, a simulated
ESP32 that plays the box's side. Both speak BLE protocol v2 ([`PROTOCOL.md`](../../../../../../../PROTOCOL.md)),
byte for byte, so the patient screens and records can't tell the simulated box from the real one.

## Contents

| File | What it does |
|---|---|
| `KotakProtokol.kt` | Protocol v2 bytes, mirror of `firmware/kotak_obat/ble_protocol.h`: the phone side (parse STATUS, EVENT, CONFIG; build ACK, TIME, CONFIG, COMMAND) and the box side used by the simulator |
| `KotakSimulasi.kt` | The simulated ESP32 firmware, Android-free: lid events with the box clock, flash queue until ACK and resend after 10 s, clock from TIME, dose reminder at the dose time (not when the lid was already opened in the dose window), FIND / TEST / DEMO_ALARM / STOP_ALARM, STATUS on change. Flash (queue, seq, config, name) survives a restart |
| `TautanKotak.kt` | `TautanKotak`, the link to a box (connection state, STATUS, CONFIG, EVENT, writes), and `TautanSimulasi`, which reaches `KotakSimulasi` with the same steps as the BLE setup: TIME, read CONFIG and STATUS, subscribe |
| `KotakManager.kt` | Runs the simulated box in the app process, connects while the patient uses it, keeps the box clock (the virtual one in demo mode) and dose time in step, stores each LID_OPEN through `PasienManager.catatDariKotak` before ACKing it, plays the box buzzer on the alarm stream. "Kotak dalam jangkauan" (Pengaturan) cuts the link to try the offline queue |

## Dependencies

- `PasienManager` (records, the app clock, the rules file), `ble/LinkState`.
- Tests: `KotakProtokolTest` (layouts, and the firmware header against this mirror),
  `KotakSimulasiTest` (the firmware behaviour, PRD 5.2 F1–F6 and scenario 3).

## Usage notes

- The real box over BLE is the next step once an ESP32 is attached: another `TautanKotak` built on
  the GATT code in `ble/BraceletConnection.kt`, with the v2 service UUID. Nothing above
  `TautanKotak` changes.
- Each box event is stored once per (box name, `seq`): `kejadian_pasien.perangkat` and
  `seqKotak`, unique together (pasien.db version 4).
