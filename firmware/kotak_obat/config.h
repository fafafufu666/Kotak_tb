// Medicine box firmware: everything that depends on the hardware is here.
//
// Pins and options can be changed in two ways:
//   1. edit the defaults below, or
//   2. leave this file alone and pass them when compiling, e.g. (one line)
//        arduino-cli compile --fqbn esp32:esp32:esp32 --build-property
//          "build.extra_flags=-DPIN_LID=25 -DPIN_BUZZER=33" firmware/kotak_obat
// Any pin set to -1 is not used (e.g. no LED fitted).
//
// The box is an ESP32 DevKit (ESP32-WROOM-32, CP2102 USB-serial, 30 or 38 pins). Its defaults are
// below; compiling for an ESP32-C3 (--fqbn esp32:esp32:esp32c3) picks the Super Mini defaults
// instead. On the classic ESP32 avoid GPIO6-11 (the flash chip), GPIO1/3 (the USB serial log),
// the strapping pins GPIO0, 2, 5, 12 and 15 for the lid sensor, and GPIO34-39 for outputs (input
// only, no internal pull-up).
#pragma once
#include <sdkconfig.h>  // CONFIG_IDF_TARGET_*

// --- Pins --------------------------------------------------------------------------------------

#if CONFIG_IDF_TARGET_ESP32C3
// ESP32-C3 Super Mini (not used for the box any more; kept so the sketch still builds for it)
#ifndef PIN_LID
#define PIN_LID 3
#endif
#ifndef PIN_BUZZER
#define PIN_BUZZER 4
#endif
#ifndef PIN_LED
#define PIN_LED 8            // on-board blue LED, lights on LOW
#endif
#ifndef LED_ACTIVE_LEVEL
#define LED_ACTIVE_LEVEL LOW
#endif
#else
// ESP32 DevKit
#ifndef PIN_LID
#define PIN_LID 27           // reed switch (NO) to GND, or an A3144 Hall sensor output
#endif
#ifndef PIN_BUZZER
#define PIN_BUZZER 26        // active 3 V buzzer (+ to the pin, - to GND; through an NPN if it draws > 20 mA)
#endif
#ifndef PIN_LED
#define PIN_LED 2            // the DevKit's on-board blue LED, lights on HIGH
#endif
#ifndef LED_ACTIVE_LEVEL
#define LED_ACTIVE_LEVEL HIGH
#endif
#endif
#ifndef PIN_BATTERY
#define PIN_BATTERY -1       // ADC pin on a 1:2 divider from the LiPo (GPIO34 on the DevKit), -1 = USB power only
#endif

// --- Polarity ------------------------------------------------------------------------------------

// The lid sensor reads this level while the lid is OPEN. A reed switch to GND with the pull-up
// below, or an A3144 Hall sensor, pulls the pin LOW while the magnet is near (lid closed), so
// "open" is HIGH. Flip it if the magnet sits so that it is near only while the lid is open.
#ifndef LID_OPEN_LEVEL
#define LID_OPEN_LEVEL HIGH
#endif
#ifndef LID_PULLUP
#define LID_PULLUP 1         // 1: internal pull-up on PIN_LID (reed switch / open-collector Hall)
#endif
#ifndef LID_DEBOUNCE_MS
#define LID_DEBOUNCE_MS 50   // PRD F1: >= 50 ms
#endif

#ifndef BUZZER_ACTIVE_LEVEL
#define BUZZER_ACTIVE_LEVEL HIGH
#endif

// --- Battery (optional; PRD 5.1: USB power is enough for the demo) ------------------------------

#ifndef BATTERY_DIVIDER
#define BATTERY_DIVIDER 2.0f // LiPo -> 100k -> PIN_BATTERY -> 100k -> GND
#endif
#define BATTERY_EMPTY_MV 3300
#define BATTERY_FULL_MV 4150
#define BATTERY_LOW_PCT 20

// --- Behaviour ---------------------------------------------------------------------------------

#define ALARM_MINUTES_DEFAULT 10       // until the phone writes CONFIG (rules: durasi_alarm_kotak_menit)
#define EARLY_MINUTES_DEFAULT 120      // until the phone writes CONFIG (rules: jendela_awal_menit)
#define EVENT_RESEND_MS 10000          // no ACK after this long: send the same event again
#define EVENT_START_DELAY_MS 1500      // after connecting, before the first event (let the app subscribe)
#define EVENT_QUEUE_MAX 64             // events kept in flash while the phone is away
#define FIND_MS 3000                   // FIND buzzes this long
#define STATUS_EVERY_MS 30000          // STATUS is also pushed this often while connected
#define SEND_LID_CLOSE 1               // 1: queue LID_CLOSE too (optional in PRD 5.3)

// --- BLE ---------------------------------------------------------------------------------------

#ifndef BLE_REQUIRE_BONDING
#define BLE_REQUIRE_BONDING 1          // every characteristic needs an encrypted, bonded link
#endif
#define BLE_TX_POWER_DBM 3

// --- Serial log and bench commands (115200 baud on the DevKit's USB port) ----------------------

#ifndef LOG_SERIAL
#define LOG_SERIAL 1
#endif
// 1: typing "buka", "tutup" or "sensor" on the serial port opens / closes the lid by hand or goes
// back to the sensor, for testing the box before the lid sensor is wired.
#ifndef SERIAL_COMMANDS
#define SERIAL_COMMANDS 1
#endif
