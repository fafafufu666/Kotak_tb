# ble — finding the medicine box

## Purpose

Finding medicine boxes over Bluetooth LE for pairing (P3), and `LinkState`, the link state every
box link reports. The link itself is in [`kotak/`](../kotak/README.md) (`TautanBle`).

## Contents

| File | What it does |
|---|---|
| `BleScanner.kt` | `BleScanner.scan()`: a `Flow<FoundDevice>` of nearby medicine boxes, filtered by the protocol v2 service UUID in the advertisement (`KotakProtokol.LAYANAN`); a box without a name in its scan response shows as "Kotak" |
| `LinkState.kt` | `LinkState` (DISCONNECTED, CONNECTING, CONNECTED) |

## Dependencies

- `kotak/KotakProtokol` for the service UUID and name prefix.
- `BleScanner` is used by `ui/AppViewModel` (P3 box pairing); `LinkState` by `kotak/` and
  `ui/pasien/BerandaScreen`.
- Android Bluetooth APIs and kotlinx-coroutines only. The UI asks for `BLUETOOTH_SCAN` before scanning.
