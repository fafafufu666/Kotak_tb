// Lid sensor, buzzer, LED and battery, all on the pins in config.h (-1 = not fitted).
#pragma once
#include <stdint.h>

enum class Pattern : uint8_t { Off, Find, Alarm, Ack };

namespace hw {
void begin();
// Debounced lid state; true = open. Call every loop.
bool lidOpen(uint32_t now);
// Bench test without a sensor: -1 = read the sensor, 0 = lid closed, 1 = lid open.
void setLidOverride(int8_t o);
int8_t lidOverride();
// Buzzer + LED pattern, non-blocking; update() drives it.
void play(Pattern p);
void update(uint32_t now);
// Battery %, BATTERY_NONE without a battery; millivolts in [mv].
uint8_t batteryPct(uint16_t* mv);
}  // namespace hw
