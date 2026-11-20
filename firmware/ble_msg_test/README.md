# ble_msg_test — stand-in bracelet for testing the app

## Purpose

Firmware for a plain ESP32 DevKit (no sensor, motor or button) that speaks the real bracelet
protocol, so the app's whole alert path can be tested by typing commands.

## Contents

| File | What it does |
|---|---|
| `ble_msg_test.ino` | Bracelet GATT service plus a Nordic UART service; queues events from typed commands and delivers them like the real firmware (indicate, resend every 10 s until ACK) |
| `ble_protocol.h` | Copy of `../fall_bracelet/ble_protocol.h` (same content plus a "COPY" header) |
| `bt_cmd.py` | Sends commands over USB serial and prints the replies, e.g. `python3 bt_cmd.py fall` |

## How it works

Commands (on USB serial at 115200 baud, or a BLE terminal app through the Nordic UART service):
`fall [g]`, `sos`, `lowbatt`, `checkin`, `cancelled`, `on`, `off`, `test`,
`state <monitoring|idle|countdown [s]|sending|off>`, `batt <pct>`, `info`, `help`.
`[ACK] seq N` means the app stored the event. Output goes to both serial and the BLE terminal.

## Dependencies

NimBLE-Arduino 2.x. `bt_cmd.py` needs Python 3 and `pyserial`; default port `/dev/ttyUSB0`.

## Usage notes

```bash
arduino-cli compile --fqbn esp32:esp32:esp32 firmware/ble_msg_test
```

```bash
python3 firmware/ble_msg_test/bt_cmd.py -t 30 sos info
```

Phone-side setup and the Service Changed note are in [`../README.md`](../README.md).
