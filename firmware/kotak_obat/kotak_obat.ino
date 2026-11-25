// Medicine box firmware (PRD section 5): ESP32 DevKit + lid sensor + buzzer + LED.
//
// Records each lid opening with the clock the phone sets (PRD F1, F3), keeps events in flash
// until the phone ACKs them (F2), rings at the dose time until the lid opens (F4), answers FIND,
// TEST, DEMO_ALARM and STOP_ALARM (F5) and reports its state in STATUS (F6). BLE protocol v2,
// see PROTOCOL.md. The app's simulated box (kotak/KotakSimulasi.kt) follows the same rules and its
// unit tests (KotakSimulasiTest) describe what this sketch should do.
//
// Pins and options: config.h (or -D flags when compiling).

#include <sys/time.h>
#include <time.h>

#include "ble_link.h"
#include "ble_protocol.h"
#include "config.h"
#include "hardware.h"
#include "storage.h"

#if LOG_SERIAL
#define LOG(...) Serial.printf(__VA_ARGS__)
#else
#define LOG(...)
#endif

namespace {
bool clockSet = false;
int16_t utcOffsetMin = 0;

bool lidWasOpen = false;
uint32_t openedAtUnix = 0;
uint32_t lastOpenedUnix = 0;     // 0 = not since power-on

bool alarmOn = false;
uint32_t alarmUntilMs = 0;
int32_t alarmDoneDay = -1;       // local day number the reminder already ran (or wasn't needed)
uint32_t findUntilMs = 0;

bool lowBatteryReported = false;
StatusPacket lastStatus{};
uint32_t statusAt = 0;

uint32_t unixNow() { return clockSet ? (uint32_t)time(nullptr) : 0; }
int32_t localDay(uint32_t unix) { return (int32_t)((int64_t)unix + utcOffsetMin * 60) / 86400; }

uint32_t queueEvent(uint8_t type, uint16_t minutesOpen = 0, bool duringAlarm = false) {
  uint8_t pct = hw::batteryPct(nullptr);
  uint32_t seq = storage::push(type, pct, unixNow(), minutesOpen, duringAlarm);
  LOG("event %lu type %u time %lu (%u waiting)\n", (unsigned long)seq, type, (unsigned long)unixNow(), storage::queued());
  return seq;
}

void startAlarm(uint32_t now, uint32_t forMs) {
  alarmOn = true;
  alarmUntilMs = now + forMs;
  hw::play(Pattern::Alarm);
  LOG("reminder ringing for %lu s\n", (unsigned long)(forMs / 1000));
}

void stopAlarm() {
  if (!alarmOn) return;
  alarmOn = false;
  if (clockSet) alarmDoneDay = localDay(unixNow());
  hw::play(Pattern::Off);
  LOG("reminder stopped\n");
}

void publishConfig() {
  ConfigPacket c{};
  DoseSettings& d = storage::dose();
  c.doseHour = d.hour;
  c.doseMinute = d.minute;
  c.alarmMinutes = d.alarmMinutes;
  c.protocolVersion = PROTOCOL_VERSION;
  c.earlyMinutes = d.earlyMinutes;
  strncpy(c.deviceName, ble::name(), NAME_BYTES);
  ble::publishConfig(c);
}

void handle(const BleMessage& m, uint32_t now) {
  switch (m.kind) {
    case BleMessage::TimeMsg: {
      timeval tv{(time_t)m.time.unixTime, 0};
      settimeofday(&tv, nullptr);
      utcOffsetMin = m.time.utcOffsetMin;
      clockSet = true;
      LOG("clock set: %lu, UTC%+d min\n", (unsigned long)m.time.unixTime, utcOffsetMin);
      break;
    }
    case BleMessage::ConfigMsg: {
      // Only the dose time, alarm length and early window are the phone's; name and version stay.
      DoseSettings& d = storage::dose();
      d.hour = m.config.doseHour <= 23 ? m.config.doseHour : DOSE_HOUR_UNSET;
      d.minute = m.config.doseMinute <= 59 ? m.config.doseMinute : 0;
      d.alarmMinutes = constrain(m.config.alarmMinutes, ALARM_MIN_MIN, ALARM_MIN_MAX);
      d.earlyMinutes = m.config.earlyMinutes;
      storage::saveDose();
      publishConfig();
      LOG("dose time %02u:%02u, alarm %u min, window opens %u min before\n", d.hour, d.minute, d.alarmMinutes, d.earlyMinutes);
      break;
    }
    case BleMessage::CommandMsg:
      LOG("command %u\n", m.command);
      switch (m.command) {
        case CMD_FIND:
          findUntilMs = now + FIND_MS;
          if (!alarmOn) hw::play(Pattern::Find);
          break;
        case CMD_TEST:
          queueEvent(EVT_TEST);
          if (!alarmOn) hw::play(Pattern::Ack);
          break;
        case CMD_DEMO_ALARM:
          startAlarm(now, storage::dose().alarmMinutes * 60000UL);
          break;
        case CMD_STOP_ALARM:
          stopAlarm();
          break;
      }
      break;
  }
}

/** F4: ring from the dose time for the alarm length, unless the lid was opened in the dose window. */
void checkReminder(uint32_t now) {
  if (alarmOn) {
    if ((int32_t)(now - alarmUntilMs) >= 0) stopAlarm();
    return;
  }
  DoseSettings& d = storage::dose();
  if (!clockSet || d.hour == DOSE_HOUR_UNSET) return;
  uint32_t unix = unixNow();
  int32_t day = localDay(unix);
  if (day == alarmDoneDay) return;
  int64_t start = (int64_t)day * 86400 + d.hour * 3600 + d.minute * 60 - utcOffsetMin * 60;
  int64_t end = start + d.alarmMinutes * 60;
  if (unix < start || unix >= end) return;
  if (lastOpenedUnix && (int64_t)lastOpenedUnix >= start - d.earlyMinutes * 60) {
    alarmDoneDay = day;  // already taken inside the dose window
    return;
  }
  startAlarm(now, (uint32_t)(end - unix) * 1000);
}

void checkLid(uint32_t now) {
  bool open = hw::lidOpen(now);
  if (open == lidWasOpen) return;
  lidWasOpen = open;
  if (open) {
    bool during = alarmOn;
    stopAlarm();
    openedAtUnix = unixNow();
    if (clockSet) lastOpenedUnix = openedAtUnix;
    queueEvent(EVT_LID_OPEN, 0, during);
  } else if (SEND_LID_CLOSE) {
    uint32_t minutes = clockSet && openedAtUnix ? (unixNow() - openedAtUnix) / 60 : 0;
    queueEvent(EVT_LID_CLOSE, minutes);
  }
}

void checkBattery() {
  uint8_t pct = hw::batteryPct(nullptr);
  if (pct == BATTERY_NONE) return;
  if (pct <= BATTERY_LOW_PCT && !lowBatteryReported) {
    queueEvent(EVT_LOW_BATTERY);
    lowBatteryReported = true;
  } else if (pct > BATTERY_LOW_PCT + 5) {
    lowBatteryReported = false;
  }
}

void publishStatus(uint32_t now) {
  StatusPacket s{};
  uint16_t mv = 0;
  s.state = alarmOn ? STATE_ALARM : ((int32_t)(now - findUntilMs) < 0 ? STATE_FIND : STATE_IDLE);
  s.batteryPct = hw::batteryPct(&mv);
  s.batteryMv = mv;
  s.lidOpen = lidWasOpen ? 1 : 0;
  s.flags = (clockSet ? FLAG_CLOCK_SET : 0) |
            (s.batteryPct != BATTERY_NONE && s.batteryPct <= BATTERY_LOW_PCT ? FLAG_LOW_BATT : 0) |
            (storage::dose().hour != DOSE_HOUR_UNSET ? FLAG_CONFIG_SET : 0);
  s.pendingEvents = storage::queued();
  s.uptimeS = now / 1000;
  // Notify on a change (uptime alone isn't one) and every STATUS_EVERY_MS.
  bool changed = memcmp(&s, &lastStatus, offsetof(StatusPacket, uptimeS)) != 0;
  bool due = now - statusAt >= STATUS_EVERY_MS;
  ble::publishStatus(s, changed || due);
  if (changed || due) {
    lastStatus = s;
    statusAt = now;
  }
}
#if SERIAL_COMMANDS
/** Bench commands on the serial port: "buka", "tutup", "sensor" (see config.h). */
void checkSerial() {
  static char line[16];
  static uint8_t len = 0;
  while (Serial.available()) {
    char ch = (char)Serial.read();
    if (ch != '\n' && ch != '\r') {
      if (len < sizeof(line) - 1) line[len++] = ch;
      continue;
    }
    if (len == 0) continue;
    line[len] = 0;
    len = 0;
    if (!strcmp(line, "buka")) hw::setLidOverride(1);
    else if (!strcmp(line, "tutup")) hw::setLidOverride(0);
    else if (!strcmp(line, "sensor")) hw::setLidOverride(-1);
    else {
      LOG("commands: buka, tutup, sensor\n");
      continue;
    }
    LOG("lid: %s\n", hw::lidOverride() < 0 ? "sensor" : (hw::lidOverride() ? "open (by hand)" : "closed (by hand)"));
  }
}
#endif
}  // namespace

void setup() {
#if LOG_SERIAL || SERIAL_COMMANDS
  Serial.begin(115200);
#endif
  hw::begin();
  storage::begin();
  ble::begin();
  publishConfig();
  lidWasOpen = hw::lidOpen(millis());
  queueEvent(EVT_BOX_ON);
  LOG("%s ready, protocol v%d, lid pin %d (%s), buzzer pin %d, LED pin %d\n", ble::name(), PROTOCOL_VERSION, PIN_LID,
      lidWasOpen ? "open" : "closed", PIN_BUZZER, PIN_LED);
#if SERIAL_COMMANDS
  LOG("commands: buka, tutup, sensor\n");
#endif
}

void loop() {
  uint32_t now = millis();
  BleMessage m;
  while (ble::poll(m)) handle(m, now);
#if SERIAL_COMMANDS
  checkSerial();
#endif
  checkLid(now);
  checkReminder(now);
  checkBattery();
  publishStatus(now);
  ble::deliver(now);
  hw::update(now);
  delay(10);
}
