# kotak_obat — medicine box firmware

## Purpose

The firmware for the medicine box (PRD section 5) on an **ESP32 DevKit** (ESP32-WROOM-32 with a
CP2102 USB-serial chip). It records every lid opening with the clock the phone sets, keeps the
events in flash until the phone has stored them, rings at the dose time until the lid is opened,
and answers the phone's commands. BLE protocol v2, see [`PROTOCOL.md`](../../PROTOCOL.md).

It follows the same rules as the app's simulated box (`app/src/main/java/com/fallbracelet/kotak/KotakSimulasi.kt`);
the simulator's unit tests (`KotakSimulasiTest`) describe what this firmware should do.

Status (25-11-2026): runs on the DevKit `Kotak-D1BE` with nothing wired to it yet, the lid
driven by the serial commands below. Tested with the app on a Samsung SM-G990E: pairing and
bonding, TIME and CONFIG, lid events stored within about 0.5 s, events queued while the phone's
Bluetooth was off arriving with their original time, TEST, the dose reminder ringing at the dose
time and stopping when the lid opened. The buzzer, LED and lid sensor themselves are not wired
or tested yet.

## Contents

| File | What it does |
|---|---|
| `kotak_obat.ino` | Main loop: clock from TIME, CONFIG, commands, lid events, dose reminder (F4), low battery, STATUS, serial bench commands |
| `config.h` | **Everything hardware-specific: pins, sensor and LED polarity, battery, timings.** DevKit defaults; compiling for an ESP32-C3 picks Super Mini defaults instead. Each pin can also be set at compile time |
| `ble_protocol.h` | Protocol v2 constants and packet structs, mirrored by the app's `kotak/KotakProtokol.kt` (a unit test checks they match) |
| `ble_link.cpp/.h` | NimBLE peripheral "Kotak-XXXX": the six characteristics, bonding, event delivery and resend until ACK |
| `storage.cpp/.h` | NVS: event queue (up to 64), event counter (random start, never reused), dose settings |
| `hardware.cpp/.h` | Debounced lid sensor (or the serial override), buzzer and LED patterns (non-blocking), optional battery ADC |

## Wiring (defaults, ESP32 DevKit)

| Part | Pin (default) | `config.h` | Notes |
|---|---|---|---|
| Reed switch (NO) or Hall A3144 | GPIO27 | `PIN_LID` | Switch between the pin and GND; internal pull-up on (`LID_PULLUP`). Reads HIGH while the lid is open (`LID_OPEN_LEVEL`). With nothing connected the pin floats HIGH, so the box reads "open" |
| Active buzzer 3 V | GPIO26 | `PIN_BUZZER` | + to the pin, − to GND. Through an NPN transistor (e.g. 2N2222 with 1 kΩ to the base) if it draws over ~20 mA. `BUZZER_ACTIVE_LEVEL` |
| LED | GPIO2 | `PIN_LED` | The DevKit's on-board blue LED (lights on HIGH: `LED_ACTIVE_LEVEL`). An external LED + 220 Ω on another pin works too |
| Battery sense (optional) | none | `PIN_BATTERY` | GPIO34 on a 1:2 divider from the LiPo; -1 = USB power (`BATTERY_NONE` in STATUS) |

GND and 3V3 come from the DevKit's own pins. Any pin set to `-1` is not used. On the classic
ESP32 avoid GPIO6–11 (flash), GPIO1/3 (the USB serial log), the strapping pins GPIO0, 2, 5, 12 and
15 for the lid sensor, and GPIO34–39 for outputs (input only, no pull-up). The magnet's position on
the rotating box decides `LID_OPEN_LEVEL`: if the magnet sits next to the sensor only while the
lid is open, set it to `LOW` [CEK at assembly, PRD 14].

## Build and upload

Board package **esp32 by Espressif 3.x**, library **NimBLE-Arduino 2.x** (tested with core
3.3.12 and NimBLE-Arduino 2.5.2). The DevKit shows up as `/dev/ttyUSB0` (CP2102).

```bash
arduino-cli compile --fqbn esp32:esp32:esp32 firmware/kotak_obat
arduino-cli upload  --fqbn esp32:esp32:esp32 -p /dev/ttyUSB0 firmware/kotak_obat
arduino-cli monitor -p /dev/ttyUSB0 -c baudrate=115200
```

Other pins without editing `config.h` (all on one line):

```bash
arduino-cli compile --fqbn esp32:esp32:esp32 --build-property "build.extra_flags=-DPIN_LID=25 -DPIN_BUZZER=33 -DPIN_LED=-1" firmware/kotak_obat
```

The log (115200 baud) shows the pins and the lid state at start-up, each event queued, the clock
and dose time the phone sets, commands, and the reminder. Opening the serial port resets the
DevKit, and every start queues a `BOX_ON` event.

### Testing without a lid sensor

Type on the serial monitor (`SERIAL_COMMANDS`, on by default):

| Command | |
|---|---|
| `buka` | Lid open, as if the sensor said so (a `LID_OPEN` event) |
| `tutup` | Lid closed (`LID_CLOSE`) |
| `sensor` | Back to reading `PIN_LID` |

A jumper from GPIO27 to GND does the same as a closed lid; pulling it out opens the lid.

### Pairing

The box only talks over an encrypted, bonded link, so Android asks to pair on the first
connection. If the board was paired with the phone before under other firmware (it shows up as
another name in the phone's Bluetooth settings), the app clears Android's cached service list by
itself. If the box's flash was erased (`arduino-cli upload` keeps it, a full erase doesn't), the
phone's old bond no longer matches: remove the box in the phone's Bluetooth settings and pair again.

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
