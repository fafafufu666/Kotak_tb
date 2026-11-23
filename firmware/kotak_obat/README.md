# kotak_obat — medicine box firmware (to come, stage 5)

## Purpose

The ESP32-C3 Super Mini firmware for the medicine box (PRD 5): lid sensor (reed switch or Hall),
buzzer, LED, flash queue. For now only its BLE contract is here.

## Contents

| File | What it does |
|---|---|
| `ble_protocol.h` | BLE protocol v2: UUIDs, sizes, codes and packet structs (see [`PROTOCOL.md`](../../PROTOCOL.md)). The app mirrors it in `kotak/KotakProtokol.kt`; `KotakProtokolTest` checks the two match |

## Usage notes

Until the box is built, the app's simulated ESP32 (`app/src/main/java/com/fallbracelet/kotak/KotakSimulasi.kt`)
implements this side of the protocol; the firmware should behave the same way (its unit tests,
`KotakSimulasiTest`, describe the behaviour). The pin map for the Super Mini is still open (PRD 14).
