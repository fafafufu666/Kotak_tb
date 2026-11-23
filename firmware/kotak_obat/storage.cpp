#include "storage.h"

#include <Arduino.h>
#include <Preferences.h>
#include <esp_random.h>
#include <string.h>

#include "config.h"

namespace {
Preferences prefs;
DoseSettings current;
EventPacket queue[EVENT_QUEUE_MAX];
uint16_t count = 0;
uint32_t nextSeq = 0;

void saveQueue() {
  if (count) prefs.putBytes("queue", queue, count * sizeof(EventPacket));
  else prefs.remove("queue");
  prefs.putUShort("queued", count);
}
}  // namespace

namespace storage {

void begin() {
  prefs.begin("kotak", false);
  current.hour = prefs.getUChar("hour", DOSE_HOUR_UNSET);
  current.minute = prefs.getUChar("minute", 0);
  current.alarmMinutes = prefs.getUChar("alarm", ALARM_MINUTES_DEFAULT);
  current.earlyMinutes = prefs.getUChar("early", EARLY_MINUTES_DEFAULT);
  nextSeq = prefs.getUInt("seq", 0);  // 0: fresh storage, push() picks the start
  count = prefs.getUShort("queued", 0);
  if (count > EVENT_QUEUE_MAX) count = 0;
  if (count) prefs.getBytes("queue", queue, count * sizeof(EventPacket));
}

DoseSettings& dose() { return current; }

void saveDose() {
  prefs.putUChar("hour", current.hour);
  prefs.putUChar("minute", current.minute);
  prefs.putUChar("alarm", current.alarmMinutes);
  prefs.putUChar("early", current.earlyMinutes);
}

uint32_t push(uint8_t type, uint8_t batteryPct, uint32_t unixTime, uint16_t minutesOpen, bool duringAlarm) {
  // The phone stores each (box, seq) once, so a wiped box must not reuse old numbers: fresh
  // storage starts at a random point, never 1. Picked here because esp_random() is only truly
  // random once the radio is on.
  if (nextSeq == 0) nextSeq = 1000 + esp_random() % 1000000000UL;
  if (count == EVENT_QUEUE_MAX) {  // full: the oldest goes (the phone has been away for weeks)
    memmove(&queue[0], &queue[1], (count - 1) * sizeof(EventPacket));
    count--;
  }
  EventPacket& e = queue[count++];
  memset(&e, 0, sizeof(e));
  e.seq = nextSeq++;
  e.type = type;
  e.batteryPct = batteryPct;
  e.unixTime = unixTime;
  e.minutesOpen = minutesOpen;
  e.duringAlarm = duringAlarm ? 1 : 0;
  prefs.putUInt("seq", nextSeq);
  saveQueue();
  return e.seq;
}

const EventPacket* front() { return count ? &queue[0] : nullptr; }

bool ack(uint32_t seq) {
  if (!count || queue[0].seq != seq) return false;
  memmove(&queue[0], &queue[1], (count - 1) * sizeof(EventPacket));
  count--;
  saveQueue();
  return true;
}

uint16_t queued() { return count; }

}  // namespace storage
