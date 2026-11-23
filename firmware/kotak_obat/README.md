# kotak_obat — medicine box firmware

## Purpose

The ESP32-C3 firmware for the medicine box (PRD section 5). It records every lid opening with
the clock the phone sets, keeps the events in flash until the phone has stored them, rings at
the dose time until the lid is opened, and answers the phone's commands. BLE protocol v2, see
[`PROTOCOL.md`](../../PROTOCOL.md).

It follows the same rules as the app's simulated box (`app/src/main/java/com/fallbracelet/kotak/KotakSimulasi.kt`);
the simulator's unit tests (`KotakSimulasiTest`) describe what this firmware should do, and are
the checklist when it first runs on the real box.

Status: compiles for the ESP32-C3 (esp32 core 3.3.12, NimBLE-Arduino 2.5.2) with no warnings;
not yet run on hardware. The app talks to it once its BLE link to the box is added (next step,
with an ESP32 attached); until then the app uses the simulated box.

## Contents

| File | What it does |
|---|---|
| `kotak_obat.ino` | Main loop: clock from TIME, CONFIG, commands, lid events, dose reminder (F4), low battery, STATUS |
| `config.h` | **Everything hardware-specific: pins, sensor and LED polarity, battery, timings.** Each pin can also be set at compile time |
| `ble_protocol.h` | Protocol v2 constants and packet structs, mirrored by the app's `kotak/KotakProtokol.kt` (a unit test checks they match) |
| `ble_link.cpp/.h` | NimBLE peripheral "Kotak-XXXX": the six characteristics, bonding, event delivery and resend until ACK |
| `storage.cpp/.h` | NVS: event queue (up to 64), event counter (random start, never reused), dose settings |
| `hardware.cpp/.h` | Debounced lid sensor, buzzer and LED patterns (non-blocking), optional battery ADC |

## Wiring (defaults, ESP32-C3 Super Mini)

| Part | Pin (default) | `config.h` | Notes |
|---|---|---|---|
| Reed switch (NO) or Hall A3144 | GPIO3 | `PIN_LID` | Switch between the pin and GND; internal pull-up on (`LID_PULLUP`). Reads HIGH while the lid is open (`LID_OPEN_LEVEL`) |
| Active buzzer 3 V | GPIO4 | `PIN_BUZZER` | Through an NPN transistor if it draws over ~20 mA. `BUZZER_ACTIVE_LEVEL` |
| LED | GPIO8 | `PIN_LED` | The Super Mini's on-board LED (lights on LOW: `LED_ACTIVE_LEVEL`). An external LED + resistor on another pin works too |
| Battery sense (optional) | none | `PIN_BATTERY` | ADC pin on a 1:2 divider from the LiPo; -1 = USB power (`BATTERY_NONE` in STATUS) |

Any pin set to `-1` is not used. Avoid GPIO2/8/9 for the lid sensor (strapping pins), GPIO18/19
(USB) and GPIO20/21 (UART). The magnet's position on the rotating box decides `LID_OPEN_LEVEL`:
if the magnet sits next to the sensor only while the lid is open, set it to `LOW`
[CEK at assembly, PRD 14].

## Build and upload

Board package **esp32 by Espressif 3.x**, library **NimBLE-Arduino 2.x**.

```bash
arduino-cli compile --fqbn esp32:esp32:esp32c3:CDCOnBoot=cdc firmware/kotak_obat
arduino-cli upload  --fqbn esp32:esp32:esp32c3:CDCOnBoot=cdc -p /dev/ttyACM0 firmware/kotak_obat
arduino-cli monitor -p /dev/ttyACM0 -c baudrate=115200
```

Other pins without editing `config.h` (all on one line):

```bash
arduino-cli compile --fqbn esp32:esp32:esp32c3:CDCOnBoot=cdc --build-property "build.extra_flags=-DPIN_LID=5 -DPIN_BUZZER=6 -DPIN_LED=-1" firmware/kotak_obat
```

`CDCOnBoot=cdc` puts the serial log on the Super Mini's USB port. The log shows the pins in use
at start-up, each event queued, the clock and dose time the phone sets, and the reminder.

## Behaviour (PRD 5.2)

| | |
|---|---|
| F1 | Lid opening (debounced 50 ms) → `LID_OPEN` with the clock, and whether the reminder was ringing; closing → `LID_CLOSE` with minutes open (`SEND_LID_CLOSE`) |
| F2 | Events wait in NVS and go to the phone oldest first, one at a time; no ACK in 10 s → the same event again. They survive a power cut |
| F3 | The phone's TIME sets the clock on every connection; before that events carry time 0 and the phone uses the receive time |
| F4 | From the dose time, for `alarmMinutes`: three beeps every 4 s and a blinking LED, until the lid opens, the time runs out or STOP_ALARM. Not if the lid was already opened in the dose window (`earlyMinutes` before the dose time). Once a day, plus DEMO_ALARM |
| F5 | FIND (two long beeps), TEST (queues a TEST event, one blip), DEMO_ALARM, STOP_ALARM |
| F6 | STATUS: state, lid, clock-set flag, dose-time flag, queue length, battery (or none); notified on change and every 30 s |
| Power on | Queues `BOX_ON` |
