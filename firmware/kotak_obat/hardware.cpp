#include "hardware.h"

#include <Arduino.h>

#include "ble_protocol.h"
#include "config.h"

namespace {
bool stable = false;     // debounced: lid open
bool lastRaw = false;
uint32_t changedAt = 0;

Pattern pattern = Pattern::Off;
uint32_t patternStart = 0;

void buzzer(bool on) {
  if (PIN_BUZZER >= 0) digitalWrite(PIN_BUZZER, on ? BUZZER_ACTIVE_LEVEL : !BUZZER_ACTIVE_LEVEL);
}
void led(bool on) {
  if (PIN_LED >= 0) digitalWrite(PIN_LED, on ? LED_ACTIVE_LEVEL : !LED_ACTIVE_LEVEL);
}
bool readLid() { return PIN_LID >= 0 && digitalRead(PIN_LID) == LID_OPEN_LEVEL; }
}  // namespace

namespace hw {

void begin() {
  if (PIN_LID >= 0) pinMode(PIN_LID, LID_PULLUP ? INPUT_PULLUP : INPUT);
  if (PIN_BUZZER >= 0) pinMode(PIN_BUZZER, OUTPUT);
  if (PIN_LED >= 0) pinMode(PIN_LED, OUTPUT);
  buzzer(false);
  led(false);
  stable = lastRaw = readLid();
}

bool lidOpen(uint32_t now) {
  bool raw = readLid();
  if (raw != lastRaw) {
    lastRaw = raw;
    changedAt = now;
  } else if (raw != stable && now - changedAt >= LID_DEBOUNCE_MS) {
    stable = raw;
  }
  return stable;
}

void play(Pattern p) {
  pattern = p;
  patternStart = millis();
  if (p == Pattern::Off) {
    buzzer(false);
    led(false);
  }
}

void update(uint32_t now) {
  uint32_t t = now - patternStart;
  switch (pattern) {
    case Pattern::Off:
      return;
    case Pattern::Find:  // two long beeps
      buzzer(t < 600 || (t >= 900 && t < 1500));
      led(t < 1500);
      if (t >= FIND_MS) play(Pattern::Off);
      return;
    case Pattern::Alarm: {  // three short beeps every 4 s, LED blinking (PRD F4)
      uint32_t c = t % 4000;
      buzzer(c < 200 || (c >= 400 && c < 600) || (c >= 800 && c < 1000));
      led((t / 250) % 2 == 0);
      return;
    }
    case Pattern::Ack:  // one short blip (e.g. a TEST command)
      buzzer(t < 80);
      led(t < 200);
      if (t >= 200) play(Pattern::Off);
      return;
  }
}

uint8_t batteryPct(uint16_t* mv) {
  if (PIN_BATTERY < 0) {
    if (mv) *mv = 0;
    return BATTERY_NONE;
  }
  uint32_t v = analogReadMilliVolts(PIN_BATTERY) * BATTERY_DIVIDER;
  if (mv) *mv = v;
  if (v <= BATTERY_EMPTY_MV) return 0;
  if (v >= BATTERY_FULL_MV) return 100;
  return (v - BATTERY_EMPTY_MV) * 100 / (BATTERY_FULL_MV - BATTERY_EMPTY_MV);
}

}  // namespace hw
