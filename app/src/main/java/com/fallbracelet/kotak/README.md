# kotak — the medicine box

## Purpose

The phone's side of the medicine box (PRD section 5): the real box (`firmware/kotak_obat` on an
ESP32 DevKit) over Bluetooth LE, and a simulated ESP32 inside the app for when there is no box
("Lewati, pakai kotak simulasi" on P3, and the demo). Both speak BLE protocol v2
([`PROTOCOL.md`](../../../../../../../PROTOCOL.md)) byte for byte, so the patient screens and
records can't tell them apart.

## Contents

| File | What it does |
|---|---|
| `KotakProtokol.kt` | Protocol v2 bytes, mirror of `firmware/kotak_obat/ble_protocol.h`: the phone side (parse STATUS, EVENT, CONFIG; build ACK, TIME, CONFIG, COMMAND) and the box side used by the simulator |
| `KotakSimulasi.kt` | The simulated ESP32 firmware, Android-free: lid events with the box clock, flash queue until ACK and resend after 10 s, clock from TIME, dose reminder at the dose time (not when the lid was already opened in the dose window), FIND / TEST / DEMO_ALARM / STOP_ALARM, STATUS on change. Flash (queue, seq, config, name) survives a restart |
| `TautanKotak.kt` | `TautanKotak`, the link to a box (connection state, STATUS, CONFIG, EVENT, writes); `TautanSimulasi`, which reaches `KotakSimulasi` with the same steps as the BLE setup; `TautanAktif`, whichever of the two is in use, for the screens |
| `TautanBle.kt` | The real box over BLE: GATT client with one operation at a time, setup (discover, MTU 64, TIME, read CONFIG and STATUS, subscribe to STATUS, then EVENT last), reconnect with autoConnect after a drop and when Bluetooth comes back on, refreshes Android's GATT cache when a board's old service list is cached |
| `KotakManager.kt` | Picks the box: the paired one (`AppSettings.deviceAddress`) over BLE, else the simulated one after "Lewati". Connects while patient mode is set up, keeps the box clock (the virtual one in demo mode) and dose time in step, stores each LID_OPEN through `PasienManager.catatDariKotak` (source KOTAK or SIMULASI) before ACKing it, plays the simulated buzzer. "Kotak dalam jangkauan" (Pengaturan, simulated box only) cuts the link to try the offline queue |
| `KotakService.kt` | Foreground service (`connectedDevice`) with a quiet "Tersambung ke kotak obat" notification while the real box is in use, so the link survives in the background. `cobaLagi()` starts it again when the app is opened, if Android refused it from the background |

## Dependencies

- `PasienManager` (records, the app clock, the rules file), `ble/LinkState`, `caregiver/ForegroundStartGate`.
- Needs `BLUETOOTH_CONNECT` (asked by `MainActivity`) for the real box.
- Tests: `KotakProtokolTest` (layouts, and the firmware header against this mirror),
  `KotakSimulasiTest` (the firmware behaviour, PRD 5.2 F1–F6 and scenario 3).

## Usage notes

- The box needs a bonded, encrypted link: the first TIME write brings up Android's pairing
  prompt (45 s to answer). A bonded box restores the EVENT subscription as soon as the link is
  encrypted, so `TautanBle` registers EVENT and STATUS locally before its own CCCD writes;
  otherwise Android drops the first event and the box resends it only after 10 s.
- "Lupakan kotak" forgets the address only; the Android bond stays (removing it needs a hidden API).
- Each box event is stored once per (box name, `seq`): `kejadian_pasien.perangkat` and
  `seqKotak`, unique together (pasien.db version 4).
