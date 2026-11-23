// BLE peripheral for protocol v2 (PROTOCOL.md): advertising as "Kotak-XXXX", the six
// characteristics, and event delivery (indicate the oldest queued event, resend until ACKed).
#pragma once
#include <stdint.h>

#include "ble_protocol.h"

// A write from the phone, handed from the BLE task to loop().
struct BleMessage {
  enum Kind : uint8_t { TimeMsg, ConfigMsg, CommandMsg } kind;
  union {
    TimePacket time;
    ConfigPacket config;
    uint8_t command;
  };
};

namespace ble {
void begin();
const char* name();
bool connected();
bool poll(BleMessage& out);
void publishStatus(const StatusPacket& s, bool notify);
void publishConfig(const ConfigPacket& c);
// Call every loop: handles ACKs and sends / resends the oldest queued event.
void deliver(uint32_t now);
}  // namespace ble
