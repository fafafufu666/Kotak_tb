// Flash (NVS): the event queue, the event counter and the dose settings survive a power cut
// (PRD F2). Same rules as the app's simulated box (kotak/KotakSimulasi.kt).
#pragma once
#include <stdint.h>

#include "ble_protocol.h"

struct DoseSettings {
  uint8_t hour;          // DOSE_HOUR_UNSET until the phone writes CONFIG
  uint8_t minute;
  uint8_t alarmMinutes;
  uint8_t earlyMinutes;
};

namespace storage {
void begin();
DoseSettings& dose();
void saveDose();
// Queues an event stamped with [unixTime] (0 = clock not set); returns its seq.
uint32_t push(uint8_t type, uint8_t batteryPct, uint32_t unixTime, uint16_t minutesOpen, bool duringAlarm);
const EventPacket* front();     // oldest waiting event, or nullptr
bool ack(uint32_t seq);         // drops the oldest if it has this seq
uint16_t queued();
}  // namespace storage
