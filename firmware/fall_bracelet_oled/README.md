# fall_bracelet_oled — DevKit firmware with an OLED

## Purpose

A copy of [`../fall_bracelet/`](../fall_bracelet/README.md) for an ESP32 DevKit on a
breadboard, with a 0.96" 128×64 SSD1306 status display. Same BLE protocol, so it works with the
app unchanged.

## Contents

| File | What it does |
|---|---|
| `fall_bracelet_oled.ino` | The main sketch plus display updates |
| `display.h/.cpp` | Draws the header (BLE link, time, battery or "USB") and the body: scrolling acceleration graph while monitoring, the countdown after a fall, or alert delivery status. Optional: the bracelet runs without the panel |
| `config.h` | Same as the main sketch plus an `OLED` section (`OLED_ADDR` 0x3C, refresh rate, graph scale) |
| `battery`, `ble_link`, `ble_protocol`, `button`, `detector`, `motor`, `sensor`, `storage` `.h/.cpp` | Identical copies of the main sketch's files |

## Dependencies

Everything the main sketch needs, plus **Adafruit SSD1306** and **Adafruit GFX**. The OLED
shares SDA/SCL (D4/D5) with the MPU6050.

## Usage notes

```bash
arduino-cli compile --fqbn esp32:esp32:esp32 firmware/fall_bracelet_oled
```

Arduino sketches can't include files outside their folder, so the shared files are copied.
**A fix to a shared file must be made in both folders.** If the screen stays blank and serial
says `OLED not found`, try `OLED_ADDR` 0x3D. Wiring is in [`../README.md`](../README.md).
