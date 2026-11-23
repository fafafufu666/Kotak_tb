// Medicine box <-> phone BLE contract, protocol version 2. See PROTOCOL.md.
// Mirrored by app/src/main/java/com/fallbracelet/kotak/KotakProtokol.kt; the app's unit test
// KotakProtokolTest reads this file and checks the numbers match, so change both together.
// All integers little-endian.
#pragma once
#include <stdint.h>

#define PROTOCOL_VERSION 2
#define DEVICE_NAME_PREFIX "Kotak"   // advertised as "Kotak-XXXX" (last two MAC bytes)

// Service and characteristics: 5b0a000X-1f3c-4d2e-8a71-6b6f74616b00
#define UUID_SERVICE   "5b0a0001-1f3c-4d2e-8a71-6b6f74616b00"
#define UUID_STATUS    "5b0a0002-1f3c-4d2e-8a71-6b6f74616b00"  // read, notify
#define UUID_EVENT     "5b0a0003-1f3c-4d2e-8a71-6b6f74616b00"  // read, indicate
#define UUID_EVENT_ACK "5b0a0004-1f3c-4d2e-8a71-6b6f74616b00"  // write
#define UUID_TIME      "5b0a0005-1f3c-4d2e-8a71-6b6f74616b00"  // write
#define UUID_CONFIG    "5b0a0006-1f3c-4d2e-8a71-6b6f74616b00"  // read, write
#define UUID_COMMAND   "5b0a0007-1f3c-4d2e-8a71-6b6f74616b00"  // write

#define STATUS_SIZE 12
#define EVENT_SIZE 16
#define EVENT_ACK_SIZE 4
#define TIME_SIZE 6
#define CONFIG_SIZE 24
#define NAME_BYTES 19

// STATUS.state
#define STATE_IDLE  0   // waiting
#define STATE_ALARM 1   // dose reminder ringing (buzzer + LED)
#define STATE_FIND  2   // FIND command buzzing

#define BATTERY_NONE 0xFF   // STATUS / EVENT battery %: powered by USB, no battery fitted

// STATUS.flags
#define FLAG_CLOCK_SET  0x01
#define FLAG_LOW_BATT   0x02
#define FLAG_CONFIG_SET 0x04   // a dose time has been written

// EVENT.type
#define EVT_LID_OPEN    1
#define EVT_LID_CLOSE   2   // optional; carries minutes the lid stayed open
#define EVT_BOX_ON      3
#define EVT_LOW_BATTERY 4
#define EVT_TEST        5

// CONFIG: dose time not set
#define DOSE_HOUR_UNSET 0xFF
#define ALARM_MIN_MIN 1
#define ALARM_MIN_MAX 60

// COMMAND
#define CMD_FIND       0x01   // buzz so the box can be found
#define CMD_TEST       0x02   // queue a TEST event (exercises the whole path)
#define CMD_DEMO_ALARM 0x03   // start the dose reminder now, for the demo
#define CMD_STOP_ALARM 0x04   // stop the reminder without opening the lid

#pragma pack(push, 1)
struct StatusPacket {     // 12 bytes
  uint8_t state;          // STATE_*
  uint8_t batteryPct;     // 0-100, BATTERY_NONE without a battery
  uint16_t batteryMv;     // 0 without a battery
  uint8_t lidOpen;        // 1 = open
  uint8_t flags;          // FLAG_*
  uint16_t pendingEvents; // events waiting in the flash queue
  uint32_t uptimeS;
};

struct EventPacket {      // 16 bytes
  uint32_t seq;           // monotonic, kept in NVS; fresh storage starts at a random number, never 1
  uint8_t type;           // EVT_*
  uint8_t batteryPct;     // BATTERY_NONE without a battery
  uint32_t unixTime;      // 0 = clock not set yet; the phone then uses the receive time
  uint16_t minutesOpen;   // EVT_LID_CLOSE only
  uint8_t duringAlarm;    // EVT_LID_OPEN: 1 if the dose reminder was ringing
  uint8_t reserved[3];
};

struct TimePacket {       // 6 bytes, written by the phone on every connection
  uint32_t unixTime;
  int16_t utcOffsetMin;
};

struct ConfigPacket {     // 24 bytes
  uint8_t doseHour;       // 0-23, DOSE_HOUR_UNSET until the phone writes one
  uint8_t doseMinute;     // 0-59
  uint8_t alarmMinutes;   // how long the reminder rings, ALARM_MIN_MIN-ALARM_MIN_MAX
  uint8_t protocolVersion;// read-only: PROTOCOL_VERSION
  uint8_t earlyMinutes;   // the dose window opens this long before the dose time (0-255); a lid
                          // opening from then on means the dose is taken, so no reminder that day
  char deviceName[NAME_BYTES]; // read-only: "Kotak-XXXX", zero-padded
};
#pragma pack(pop)

static_assert(sizeof(StatusPacket) == STATUS_SIZE, "STATUS size");
static_assert(sizeof(EventPacket) == EVENT_SIZE, "EVENT size");
static_assert(sizeof(TimePacket) == TIME_SIZE, "TIME size");
static_assert(sizeof(ConfigPacket) == CONFIG_SIZE, "CONFIG size");
