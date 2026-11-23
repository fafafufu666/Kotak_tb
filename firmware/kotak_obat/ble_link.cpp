#include "ble_link.h"

#include <Arduino.h>
#include <NimBLEDevice.h>
#include <esp_mac.h>
#include <freertos/FreeRTOS.h>
#include <freertos/queue.h>

#include "config.h"
#include "storage.h"

namespace {
NimBLEServer* server = nullptr;
NimBLECharacteristic* chStatus = nullptr;
NimBLECharacteristic* chEvent = nullptr;
NimBLECharacteristic* chConfig = nullptr;
char deviceName[NAME_BYTES + 1] = {0};

QueueHandle_t inbox = nullptr;
volatile bool isConnected = false;
volatile uint16_t connHandle = BLE_HS_CONN_HANDLE_NONE;
volatile uint32_t connectedAt = 0;
volatile bool eventsSubscribed = false;
volatile uint32_t ackedSeq = 0;  // written on the BLE task, handled in deliver()
volatile bool ackPending = false;

// Delivery state, loop() only.
uint32_t inFlightSeq = 0;
bool inFlight = false;
uint32_t sentAt = 0;

#if BLE_REQUIRE_BONDING
constexpr uint32_t PROP_READ = NIMBLE_PROPERTY::READ | NIMBLE_PROPERTY::READ_ENC;
constexpr uint32_t PROP_WRITE = NIMBLE_PROPERTY::WRITE | NIMBLE_PROPERTY::WRITE_ENC;
#else
constexpr uint32_t PROP_READ = NIMBLE_PROPERTY::READ;
constexpr uint32_t PROP_WRITE = NIMBLE_PROPERTY::WRITE;
#endif

void send(const BleMessage& m) { xQueueSend(inbox, &m, 0); }

class ServerCallbacks : public NimBLEServerCallbacks {
  void onConnect(NimBLEServer* s, NimBLEConnInfo& info) override {
    isConnected = true;
    connHandle = info.getConnHandle();
    connectedAt = millis();
    s->updateConnParams(info.getConnHandle(), 24, 48, 0, 400);  // 30-60 ms, 4 s timeout
  }
  void onDisconnect(NimBLEServer*, NimBLEConnInfo&, int) override {
    isConnected = false;
    eventsSubscribed = false;
    NimBLEDevice::startAdvertising();
  }
};

class WriteCallbacks : public NimBLECharacteristicCallbacks {
  void onWrite(NimBLECharacteristic* c, NimBLEConnInfo&) override {
    NimBLEAttValue v = c->getValue();
    BleMessage m{};
    const NimBLEUUID& id = c->getUUID();
    if (id == NimBLEUUID(UUID_COMMAND) && v.size() >= 1) {
      m.kind = BleMessage::CommandMsg;
      m.command = v.data()[0];
      send(m);
    } else if (id == NimBLEUUID(UUID_TIME) && v.size() >= sizeof(TimePacket)) {
      m.kind = BleMessage::TimeMsg;
      memcpy(&m.time, v.data(), sizeof(TimePacket));
      send(m);
    } else if (id == NimBLEUUID(UUID_CONFIG) && v.size() >= sizeof(ConfigPacket)) {
      m.kind = BleMessage::ConfigMsg;
      memcpy(&m.config, v.data(), sizeof(ConfigPacket));
      send(m);
    } else if (id == NimBLEUUID(UUID_EVENT_ACK) && v.size() >= EVENT_ACK_SIZE) {
      uint32_t seq;
      memcpy(&seq, v.data(), 4);
      ackedSeq = seq;
      ackPending = true;
    }
  }

  void onSubscribe(NimBLECharacteristic* c, NimBLEConnInfo&, uint16_t subValue) override {
    if (c == chEvent) eventsSubscribed = (subValue & 0x02) != 0;  // indications
  }
};

ServerCallbacks serverCallbacks;
WriteCallbacks writeCallbacks;
}  // namespace

namespace ble {

void begin() {
  inbox = xQueueCreate(8, sizeof(BleMessage));

  uint8_t mac[6];
  esp_read_mac(mac, ESP_MAC_BT);
  snprintf(deviceName, sizeof(deviceName), "%s-%02X%02X", DEVICE_NAME_PREFIX, mac[4], mac[5]);

  NimBLEDevice::init(deviceName);
  NimBLEDevice::setPower(BLE_TX_POWER_DBM);
#if BLE_REQUIRE_BONDING
  // "Just works" pairing (no screen or keypad), bonded, LE Secure Connections.
  NimBLEDevice::setSecurityAuth(true, false, true);
  NimBLEDevice::setSecurityIOCap(BLE_HS_IO_NO_INPUT_OUTPUT);
#endif

  server = NimBLEDevice::createServer();
  server->setCallbacks(&serverCallbacks);
  NimBLEService* svc = server->createService(UUID_SERVICE);

  chStatus = svc->createCharacteristic(UUID_STATUS, PROP_READ | NIMBLE_PROPERTY::NOTIFY);
  chEvent = svc->createCharacteristic(UUID_EVENT, PROP_READ | NIMBLE_PROPERTY::INDICATE);
  chConfig = svc->createCharacteristic(UUID_CONFIG, PROP_READ | PROP_WRITE);
  NimBLECharacteristic* chAck = svc->createCharacteristic(UUID_EVENT_ACK, PROP_WRITE);
  NimBLECharacteristic* chTime = svc->createCharacteristic(UUID_TIME, PROP_WRITE);
  NimBLECharacteristic* chCommand = svc->createCharacteristic(UUID_COMMAND, PROP_WRITE);
  for (NimBLECharacteristic* c : {chEvent, chConfig, chAck, chTime, chCommand}) c->setCallbacks(&writeCallbacks);
  uint8_t empty[EVENT_SIZE] = {0};
  chEvent->setValue(empty, sizeof(empty));

  NimBLEAdvertising* adv = NimBLEDevice::getAdvertising();
  NimBLEAdvertisementData advData;
  advData.setFlags(BLE_HS_ADV_F_DISC_GEN | BLE_HS_ADV_F_BREDR_UNSUP);
  advData.addServiceUUID(NimBLEUUID(UUID_SERVICE));
  NimBLEAdvertisementData scanData;  // the name doesn't fit next to a 128-bit UUID
  scanData.setName(deviceName);
  adv->setAdvertisementData(advData);
  adv->setScanResponseData(scanData);
  adv->start();
}

const char* name() { return deviceName; }
bool connected() { return isConnected; }
bool poll(BleMessage& out) { return inbox && xQueueReceive(inbox, &out, 0) == pdTRUE; }

void publishStatus(const StatusPacket& s, bool notify) {
  chStatus->setValue((const uint8_t*)&s, sizeof(s));
  if (notify && isConnected) chStatus->notify();
}

void publishConfig(const ConfigPacket& c) { chConfig->setValue((const uint8_t*)&c, sizeof(c)); }

void deliver(uint32_t now) {
  if (ackPending) {
    ackPending = false;
    if (storage::ack(ackedSeq)) inFlight = false;  // a wrong seq is ignored
  }
  const EventPacket* f = storage::front();
  if (!f) return;
  if (!isConnected || !eventsSubscribed) {
    inFlight = false;  // send again from scratch on the next connection
    return;
  }
  // A bonded phone's subscription is restored on connect, before the app is listening.
  if (now - connectedAt < EVENT_START_DELAY_MS) return;
  if (inFlight && inFlightSeq == f->seq && now - sentAt < EVENT_RESEND_MS) return;

  chEvent->setValue((const uint8_t*)f, sizeof(EventPacket));
  // Straight to the connection (the queued indicate() could stall on a resend).
  chEvent->indicate((const uint8_t*)f, sizeof(EventPacket), connHandle);
  inFlight = true;
  inFlightSeq = f->seq;
  sentAt = now;
}

}  // namespace ble
