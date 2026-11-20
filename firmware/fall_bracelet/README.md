# fall_bracelet — bracelet firmware

## Purpose

The Arduino sketch that runs on the bracelet (Seeed XIAO ESP32-C3 + MPU6050): detects falls,
handles the button and motor, manages power, and delivers events to the phone over BLE. Also
builds for a classic ESP32 DevKit for breadboard prototyping.

## Contents

| File | What it does |
|---|---|
| `fall_bracelet.ino` | `setup()` / `loop()` and the state machine (Off, Monitoring, Idle, Countdown, SendingAlert): countdown, alarms, button handling, BLE messages, battery checks, daily check-in, deep sleep |
| `config.h` | Every pin and number: pins per board, detection thresholds, button timings, power, alert retries, BLE settings |
| `ble_protocol.h` | UUIDs, enums and packed packet structs shared with the app (see [`PROTOCOL.md`](../../PROTOCOL.md)) |
| `ble_link.h/.cpp` | NimBLE peripheral: advertising, characteristics, messages from the phone (`poll`), STATUS / CONFIG / STREAM publishing, event delivery with resends |
| `detector.h/.cpp` | `FallDetector`: free fall → impact → stillness, then an orientation-change or strong-impact check, one sample at a time |
| `sensor.h/.cpp` | MPU6050 at 100 Hz into its FIFO (accelerometer only, gyro in standby), motion-wake cycle mode for Idle, full sleep for Off |
| `button.h/.cpp` | Short press, 2–4 s SOS, 5 s off, with the 2 s tick; ignores the press that woke the chip |
| `motor.h/.cpp` | Non-blocking vibration patterns; latches the motor pin low through deep sleep |
| `battery.h/.cpp` | 16-sample average through the divider, 3.3–4.2 V → % (fake 4000 mV on the DevKit build) |
| `storage.h/.cpp` | NVS: setup values, the event queue and its sequence counter; events stay until the phone ACKs |

## How it works

- **Loop:** read the FIFO → run the detector → handle the button → drive the motor → deliver
  queued events over BLE → sleep when nothing is happening.
- **A fall** starts a countdown (default 20 s, buzzing); the button or the app's CANCEL stops
  it, otherwise a FALL event is queued. Holding the button 2–4 s queues SOS.
- **Idle and Off are deep sleep**, so BLE drops. The button wakes it; in Idle, motion on the
  MPU6050 INT line wakes it too.
- **Delivery:** the oldest queued event is indicated and resent until the phone writes its
  `seq` to EVENT_ACK; after `ALERT_RETRIES` it buzzes "failed" and retries later.

## Dependencies

- esp32 Arduino core 3.x, NimBLE-Arduino 2.x, Adafruit MPU6050 (+ Unified Sensor, BusIO).
- `ble_protocol.h` must match `app/.../ble/BleProtocol.kt`; copies live in
  [`../fall_bracelet_oled/`](../fall_bracelet_oled/README.md) and
  [`../ble_msg_test/`](../ble_msg_test/README.md).

## Usage notes

```bash
arduino-cli compile --fqbn esp32:esp32:XIAO_ESP32C3 firmware/fall_bracelet
```

Pick **ESP32 Dev Module** (`esp32:esp32:esp32`) for the DevKit pins. Wiring, power notes and
the pre-release checklist are in [`../README.md`](../README.md). Thresholds are untuned; record
trials in the app's Live tab first.
