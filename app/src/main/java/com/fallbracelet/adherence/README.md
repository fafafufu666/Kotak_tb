# adherence — medication rules

## Purpose

The core logic of PRD section 7, written as pure functions with no Android types so it is
covered by JVM unit tests (same style as `alert/AlertRules.kt`). Every number and text comes
from `app/src/main/assets/aturan_program.json` (PRD section 8); none is written in the code.
Nothing in the app calls this package yet; the screens arrive in stage 2.

## Contents

| File | What it does |
|---|---|
| `AturanProgram.kt` | Data classes mirroring the JSON: treatment length, dose window, vulnerable-day rules, escalation, travel mode, messages, notification texts. `nilaiContoh` is true while `status_nilai` says CONTOH |
| `PembacaAturan.kt` | Reads the JSON with `org.json`. Each top-level section that is missing or invalid is taken from `aturan_bawaan.json` instead and reported as a `PeringatanAturan` (section key + technical reason) for the Settings warning |
| `LogikaKepatuhan.kt` | `hariKe` / `fase` / `sisaHari`; `jadwal` and `statusDosis` (BELUM, TERLAMBAT, TERLEWAT, DIMINUM_KOTAK, DIKONFIRMASI_MANUAL, BEPERGIAN); `tanggalDosisUntuk` (which dose an opening belongs to, or none); `jadwalPengingatUlang`; `hariRawan`, `perluTampilkanPesan`, `pilihPesan`, `isiPesan`; `terlewatBerturut`, `perluEskalasi` |

## Rules worth knowing

- A dose window is `[jam_minum − jendela_awal_menit, jam_minum + batas_terlewat_menit]`, both
  ends included; "late" and "missed" start strictly after their limit. The window must be
  shorter than 24 h, so a box opening belongs to at most one dose. A window crossing midnight
  belongs to the date of `jam_minum`.
- Only events at or before "now" count, and the first one in the window is recorded. A box
  opening wins over a manual confirmation. "Now" is always a parameter (demo mode's virtual clock).
- Repeat reminders: the first when the dose turns late, then every `jarak_menit`, `jumlah` in
  all, none after the missed limit.
- The POLA rule counts TERLEWAT in the last `dalam_hari` days, today included. Days outside the
  treatment are never vulnerable. The special reminder shows only while today's dose is still
  open (BELUM / TERLAMBAT), at most `maks_per_hari` times; several messages take turns by day.
- Escalation: every missed day sends one ESKALASI while the run of consecutive misses is at
  least `min_terlewat_berturut`; today, while still open, neither extends nor breaks the run.
  The caller sends at most one per date.

## Dependencies

`org.json` (part of Android; the real library is added for unit tests) and `java.time`
(min SDK 26). Tests: `app/src/test/java/com/fallbracelet/AturanProgramTest.kt` and
`LogikaKepatuhanTest.kt`.
