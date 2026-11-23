# PRD: Prototipe Kotak Obat Terhubung + Aplikasi Pasien/PMO

Versi 0.1 · 23-11-2026 · Pemilik produk: Abyan (mahasiswa FK Unpad, Esai Gagasan skema Prototype)

> **Cara membaca dokumen ini**
> - **[DIISI ABYAN]**: nilai atau teks yang ditentukan Abyan. Di kode, bagian ini **harus** dibaca dari file konfigurasi (bagian 8), bukan ditulis langsung di kode.
> - **[CONTOH]**: nilai sementara agar aplikasi bisa dibangun dan didemokan. Ini bukan keputusan akhir.
> - **[CEK]**: fakta teknis atau klinis yang belum diverifikasi. Jangan dianggap benar sebelum dicek.
> - Basis kode: **fork repo `fall_bracelet`** milik saudara Abyan (yang mengizinkan pemakaiannya). Repo itu memuat aplikasi Android Kotlin/Compose, firmware ESP32-C3, dan relay Node.js.

---

## 1. Latar belakang singkat

Analisis SKI 2023 (Yudhanti dkk., 2025) menemukan bahwa "merasa sudah sehat" adalah salah satu dari dua alasan terbesar penderita TB paru tidak rutin minum obat. SKI mendefinisikannya sebagai merasa sehat **di awal-awal pengobatan**. Kotak obat berpengingat terbukti mengurangi dosis terlewat. Namun teknologi pemantauan saja tidak memperbaiki hasil pengobatan (uji ASCENT, 2025).

Gagasan prototipe ini adalah **gabungan** empat hal:
1. kotak obat murah yang mencatat kapan dibuka, termasuk saat tidak ada HP di dekatnya;
2. aplikasi yang mengenali **hari rawan** dari waktu pengobatan dan pola dosis yang mulai bolong;
3. pada hari rawan, pesan personal "belum sembuh" yang isinya ditulis Abyan;
4. eskalasi ke **PMO pilihan pasien** saat dosis terlewat.

Notifikasi ke PMO **bukan** hal baru bila berdiri sendiri. Nilai prototipe ada pada pemicu berbasis risiko dan gabungannya.

## 2. Tujuan prototipe

| Tujuan | Ukuran berhasil (untuk Bab 3 esai) |
|---|---|
| Integrasi kotak ↔ aplikasi bekerja | Membuka kotak tercatat di aplikasi dalam ≤ 5 detik saat HP dekat |
| Data tidak hilang tanpa HP | Pembukaan saat Bluetooth mati terkirim setelah tersambung lagi, dengan waktu asli |
| Eskalasi sampai ke PMO | Notifikasi muncul di HP PMO ≤ 60 detik setelah aturan eskalasi terpenuhi (jaringan lokal) |
| Pesan hari rawan muncul tepat | Pesan muncul hanya bila aturan hari rawan terpenuhi |
| Aturan bisa diubah tanpa ubah kode | Mengganti nilai di file konfigurasi langsung mengubah perilaku setelah aplikasi dibuka ulang |
| Bisa didemokan dalam ± 5 menit | Mode demo bisa melompat ke "hari 58" dan mensimulasikan dosis terlewat |

**Bukan tujuan:** membuktikan efek pada kepatuhan atau kesembuhan, rilis ke Play Store, dan dukungan iOS.

## 3. Pengguna

| Peran | Siapa | Perangkat |
|---|---|---|
| **Pasien** | Pasien TB sensitif obat dewasa yang punya smartphone Android | HP Android + kotak obat |
| **PMO** | Orang pilihan pasien: keluarga, kader, atau petugas | HP Android (aplikasi yang sama, mode PMO) atau halaman web relay |

Aplikasinya **satu**, dengan dua mode. Ini mengikuti pola pemakai/pendamping di repo saudara (wearer = pasien, caregiver = PMO).

## 4. Arsitektur (memakai ulang repo saudara)

```
Kotak (ESP32-C3 + reed switch/Hall + buzzer + LED)
   │ BLE (protokol repo saudara: STATUS, EVENT/EVENT_ACK, TIME, CONFIG, COMMAND)
   ▼
HP pasien (mode Pasien): jadwal, status dosis, hari rawan, pesan, antrean unggah
   │ HTTP (relay saudara, Node 22)
   ▼
Relay (laptop saat demo, atau Raspberry Pi)
   │ FCM push (bila ada internet + kunci Firebase) / Server-Sent Events (jaringan lokal)
   ▼
HP PMO (mode PMO) atau halaman web relay
```

| Bagian | Dipakai ulang | Diubah |
|---|---|---|
| BLE (`ble/`, `PROTOCOL.md`) | Koneksi, bonding, antrean kejadian + ACK, TIME, CONFIG, COMMAND | Jenis kejadian, isi CONFIG, nama perangkat |
| Data (`data/`) | Room, antrean unggah, DataStore | Tabel dosis harian, status pengobatan |
| Relay app (`relay/`) + server (`relay/server.mjs`) | Registrasi, kode undangan 6 digit, tautan, unggah kejadian, heartbeat, ACK "dilihat", push, SSE, pencarian relay di Wi-Fi | Jenis kejadian baru; timer ulang-kirim untuk ESKALASI |
| Mode PMO (`caregiver/`) | Tautan, push, stream, dasbor, riwayat | Tampilan sesuai bagian 6 |
| Firmware | Struktur `ble_link`, `storage` (antrean flash), `battery` (opsional) | Hapus sensor jatuh (MPU6050), detektor, dan motor; tambah sensor tutup dan buzzer |
| `ble_msg_test` | Alat pengganti untuk uji aplikasi tanpa kotak | Perintah baru: `buka`, `tutup` |

**Penamaan:** ganti `applicationId` (misalnya `id.esgas.kotakobat`) agar bisa terpasang bersama aplikasi saudara. Nama package internal Kotlin **boleh tetap** untuk menghemat waktu. Nama aplikasi dan perangkat BLE harus **netral**, tanpa kata "TB" atau "tuberkulosis": aplikasi `[NAMA APLIKASI]`, perangkat `Kotak-XXXX`.

## 5. Perangkat keras dan firmware

### 5.1 Komponen
- ESP32-C3 Super Mini. **[CEK]** Repo saudara hanya punya peta pin untuk XIAO ESP32-C3 dan ESP32 DevKit. Peta pin Super Mini harus ditambahkan, dan pin yang dipakai dikonfirmasi ke Abyan.
- Reed switch NO + magnet neodymium (alternatif: sensor Hall A3144).
- Buzzer aktif 3 V dan LED status (LED bawaan papan, atau LED terpisah).
- Kotak obat putar bulat 7 sekat.
- Daya USB untuk demo. Baterai LiPo + TP4056 opsional, sehingga `PROTOTYPE_NO_BATTERY` boleh aktif.

### 5.2 Perilaku firmware
| Kebutuhan | Rincian |
|---|---|
| F1 Deteksi tutup | Perubahan status sensor diberi debounce (≥ 50 ms, **[CONTOH]**). Setiap kali tutup dibuka, kotak mencatat kejadian `LID_OPEN` dengan waktu Unix. Penempatan magnet dan sensor pada kotak putar **ditentukan saat perakitan [CEK]**. Firmware hanya peduli pada perubahan status sensor |
| F2 Antrean offline | Kejadian disimpan di flash dan dikirim satu per satu sampai di-ACK HP. Mekanisme ini sudah ada di repo |
| F3 Jam | HP menyetel jam lewat TIME setiap kali tersambung, sehingga tidak perlu RTC. Bila jam belum disetel, waktu = 0 dan HP memakai waktu terima |
| F4 Pengingat di kotak | Jam minum disimpan di CONFIG. Pada jam itu, buzzer berbunyi pendek dan LED berkedip sampai tutup dibuka atau sampai `durasi_alarm_kotak_menit` lewat |
| F5 Perintah | `FIND` (bunyi), `TEST` (kejadian uji), `DEMO_ALARM` (picu alarm kotak sekarang, untuk demo) |
| F6 Status | STATUS memuat flag jam sudah disetel, jumlah antrean, dan baterai bila ada |

### 5.3 Perubahan protokol (naikkan `PROTOCOL_VERSION` ke 2)
- **Jenis EVENT baru:** `LID_OPEN`, `LID_CLOSE` (opsional), `BOX_ON`, `LOW_BATTERY`, `TEST`. Kode jenis lama yang tidak dipakai dihapus dari kedua sisi.
- **CONFIG:** jam minum (jam, menit), durasi alarm kotak (menit), versi protokol, nama perangkat. Isi lama (`COUNTDOWN_S`, `CHECKIN_TIME`) diganti.
- `PROTOCOL.md`, `ble_protocol.h` (semua salinan), dan `BleProtocol.kt` diubah bersama, lalu tes `BleProtocolTest` diperbarui.

## 6. Aplikasi: layar dan perilaku

Rujukan visual: mockup tiga layar (beranda pasien, pengingat khusus, tampilan pendamping) yang dibuat Abyan. Gaya Material 3, warna teal–putih, teks besar. **Semua teks UI berbahasa Indonesia** dan disimpan di `strings.xml`. Teks pesan diambil dari file konfigurasi.

### 6.1 Mode Pasien

| ID | Layar | Isi dan perilaku | Prioritas |
|---|---|---|---|
| P1 | Persetujuan | Ditampilkan sebelum memakai aplikasi. Isi dari `assets/persetujuan.md` (**teks dibuat terpisah**; sementara berisi penanda). Pasien wajib mencentang setuju; waktu persetujuan disimpan. Ada tautan "Berhenti ikut program" di Pengaturan | P0 |
| P2 | Mulai pengobatan | Isi nama panggilan, tanggal mulai pengobatan, jam minum obat. Lama pengobatan dan fase diambil dari konfigurasi | P0 |
| P3 | Pasangkan kotak | Pindai, pasangkan (bonding), uji bunyi. Dipakai ulang dari repo | P0 |
| P4 | Beranda | Nama aplikasi, "Halo, [nama]"; cincin kemajuan **"Hari X dari N"**; label fase; kartu status kotak (terhubung/terputus, terakhir dibuka: hari/jam); kartu dosis hari ini (belum/sudah dari kotak/sudah dikonfirmasi/terlambat/terlewat) dengan tombol **"Saya sudah minum obat"** (konfirmasi manual, simulasi bukti); deret 7 hari Sen–Min; navigasi Beranda, Riwayat, PMO, Pengaturan | P0 |
| P5 | Pengingat khusus (hari rawan) | Muncul bila aturan hari rawan (bagian 7.3) terpenuhi: notifikasi + layar penuh di aplikasi. Isinya teks pesan dari konfigurasi **[DIISI ABYAN]**. Tampilkan juga **status kotak hari ini** agar terlihat pemicunya dari data kotak. Tombol "Saya sudah minum obat" dan "Ingatkan [x] menit lagi". Pesan dicatat (kapan tampil, tombol apa yang ditekan) | P0 |
| P6 | Riwayat | Daftar per hari: status dosis, sumber (kotak/manual), jam | P0 |
| P7 | PMO | Daftar PMO terhubung; buat kode undangan 6 digit (dari repo); hapus/ganti PMO kapan saja | P0 |
| P8 | Pengaturan | Jam minum (dikirim ke kotak), lupakan kotak, alamat relay / cari relay, mode demo, berhenti ikut program (hapus data lokal setelah konfirmasi) | P0 |
| P9 | Mode bepergian | Pasien menyalakan dan memilih lama (hari). Selama aktif, konfirmasi manual dianggap cukup dan aturan eskalasi mengikuti konfigurasi `mode_bepergian`. PMO diberi tahu mode aktif. Mati otomatis | P1 |

### 6.2 Mode PMO

| ID | Layar | Isi dan perilaku | Prioritas |
|---|---|---|---|
| M1 | Tautkan | Masukkan kode 6 digit dari pasien (dari repo) | P0 |
| M2 | Dasbor pendamping | "Pendamping: [nama pasien]"; kartu peringatan bila ada eskalasi (mis. "2 dosis terlewat", **"Terakhir dibuka: Minggu"**); riwayat 7 hari dengan ✓/✗; tombol **"Hubungi"** (membuka panggilan telepon atau WhatsApp ke nomor yang diisi pasien, opsional) dan **"Tandai dilihat"** (memakai ACK relay; pasien melihat "Dilihat oleh …") | P0 |
| M3 | Notifikasi eskalasi | Push FCM bila tersedia, stream SSE bila tidak. Teks notifikasi netral **[DIISI ABYAN]** | P0 |

### 6.3 Anti-stigma dan privasi (P0)
- Teks notifikasi di layar kunci, nama aplikasi, nama perangkat BLE, dan isi yang dikirim ke relay **tidak menyebut TB, tuberkulosis, atau nama obat**.
- Relay hanya menyimpan nama panggilan, status dosis, dan kejadian; tidak ada diagnosis.
- Relay berjalan di jaringan lokal (`LAN_ONLY` bawaan repo). Data hanya untuk demo.
- PMO bisa dihapus pasien kapan saja; pasien bisa berhenti ikut kapan saja.

## 7. Logika inti (fungsi murni, tanpa tipe Android, wajib diuji unit)

Letakkan di paket baru, misalnya `adherence/`, mengikuti gaya `alert/AlertRules.kt`. Semua angka dibaca dari konfigurasi (bagian 8).

### 7.1 Hari pengobatan
- `hari_ke = (tanggal hari ini − tanggal mulai) + 1`; total hari = `pengobatan.total_hari`.
- Fase: hari ≤ `pengobatan.fase_intensif_hari` → "Fase intensif", selebihnya "Fase lanjutan". Angka lama pengobatan dan fase **[CEK: PNPK TB Kemenkes]**; nilai di konfigurasi hanya **[CONTOH]**.

### 7.2 Status dosis harian
Untuk setiap hari, terhadap `jam_minum`:
1. **DIMINUM_KOTAK:** ada `LID_OPEN` dalam jendela `[jam_minum − jendela_awal_menit, jam_minum + batas_terlewat_menit]`.
2. **DIKONFIRMASI_MANUAL:** pasien menekan "Saya sudah minum obat" dalam jendela yang sama.
3. **TERLAMBAT:** sudah lewat `batas_terlambat_menit`, tetapi belum lewat `batas_terlewat_menit`. Pengingat ulang dikirim sesuai `pengingat_ulang`.
4. **TERLEWAT:** sudah lewat `batas_terlewat_menit` tanpa 1 atau 2.
5. **BEPERGIAN:** mode bepergian aktif dan pasien sudah konfirmasi manual.
6. **BELUM:** jendela belum dimulai atau masih berjalan.

`LID_OPEN` di luar jendela tetap dicatat di riwayat ("dibuka di luar jadwal"), tetapi tidak dihitung sebagai dosis.

### 7.3 Hari rawan (pemicu pesan P5)
Kondisi yang bisa dipakai, semuanya dari konfigurasi **[DIISI ABYAN]**:
- **WAKTU:** `hari_ke` berada dalam salah satu rentang `hari_rawan.rentang_hari`, atau tanggal hari ini ada di `hari_rawan.tanggal_libur`.
- **POLA:** jumlah TERLEWAT dalam `hari_rawan.pola.dalam_hari` hari terakhir ≥ `hari_rawan.pola.min_terlewat`.
- **Penggabungan:** `hari_rawan.gabungan` = `WAKTU_SAJA` | `POLA_SAJA` | `SALAH_SATU` | `KEDUANYA`.

Pesan ditampilkan paling banyak `hari_rawan.maks_per_hari` kali per hari, pada `jam_minum` dan saat status berubah menjadi TERLAMBAT. Pesan yang dipilih ditentukan aturan di `pesan` (bagian 8).

### 7.4 Eskalasi ke PMO
- Bila TERLEWAT berturut-turut ≥ `eskalasi.min_terlewat_berturut`, kirim kejadian `ESKALASI` ke relay. Isinya: jumlah terlewat, waktu terakhir kotak dibuka, dan status hari rawan. Relay meneruskannya ke semua PMO tertaut.
- Bila tidak ada "Tandai dilihat" dalam `eskalasi.ulang_kirim_menit`, relay mengirim ulang sampai `eskalasi.maks_ulang` kali. Ini memakai timer ulang-kirim relay yang sudah ada untuk FALL/SOS.
- Tingkat lanjut (puskesmas) **tidak** ada di prototipe; cukup ditulis di esai sebagai rencana.
- Ringkasan status harian juga diunggah sebagai kejadian `STATUS_HARIAN` agar dasbor PMO bisa menampilkan riwayat 7 hari.

## 8. File konfigurasi aturan (satu sumber kebenaran)

`app/src/main/assets/aturan_program.json`. Dibaca saat aplikasi dibuka. Nilai yang tidak valid → aplikasi memakai nilai bawaan dan menampilkan peringatan di Pengaturan. **Semua nilai di bawah adalah [CONTOH] sampai diganti Abyan.**

```json
{
  "status_nilai": "CONTOH - belum ditetapkan Abyan",
  "pengobatan": { "total_hari": 180, "fase_intensif_hari": 56 },
  "dosis": {
    "jendela_awal_menit": 120,
    "batas_terlambat_menit": 60,
    "batas_terlewat_menit": 240,
    "pengingat_ulang": { "jumlah": 2, "jarak_menit": 30 },
    "durasi_alarm_kotak_menit": 10
  },
  "hari_rawan": {
    "rentang_hari": [[14, 28], [50, 70]],
    "tanggal_libur": [],
    "pola": { "dalam_hari": 7, "min_terlewat": 1 },
    "gabungan": "SALAH_SATU",
    "maks_per_hari": 2
  },
  "eskalasi": {
    "min_terlewat_berturut": 2,
    "ulang_kirim_menit": 30,
    "maks_ulang": 3
  },
  "mode_bepergian": { "maks_hari": 7, "eskalasi_aktif": true },
  "pesan": [
    {
      "id": "pesan_1",
      "kapan": "HARI_RAWAN",
      "judul": "Pengingat khusus hari ini",
      "isi": "[Teks pesan pengingat - DIISI ABYAN]",
      "variabel_tersedia": ["{nama}", "{hari_ke}", "{sisa_hari}", "{total_hari}", "{dosis_tercatat}"]
    }
  ],
  "notifikasi": {
    "pasien_jam_minum": "[Teks netral - DIISI ABYAN]",
    "pasien_hari_rawan": "[Teks netral - DIISI ABYAN]",
    "pmo_eskalasi": "[Teks netral - DIISI ABYAN]"
  }
}
```

- `isi` pesan mendukung variabel personal di atas, yang diganti saat ditampilkan.
- Selama `status_nilai` masih "CONTOH", tampilkan pita kecil **"Nilai contoh"** di Pengaturan agar tidak tertukar dengan versi final.
- Jam minum diatur pasien di aplikasi, bukan di file ini.

## 9. Mode demo (P0, penting untuk hari H)

Tersedia di Pengaturan → Mode demo, dengan penanda "DEMO" di bilah atas selama aktif.
- **Lompat ke hari:** setel `hari_ke` (misalnya 58) dengan menggeser tanggal mulai secara virtual.
- **Jam virtual:** majukan waktu 15 menit / 1 jam / ke batas terlewat, agar status berubah tanpa menunggu.
- **Isi riwayat:** buat riwayat contoh (misalnya 5 hari diminum, 2 terlewat) untuk tampilan PMO.
- **Kotak simulasi:** tombol "Buka kotak (simulasi)" bila kotak asli tidak ada. Kejadian berjalan lewat jalur yang sama seperti `LID_OPEN` dari BLE.
- **Picu alarm kotak:** kirim `DEMO_ALARM` ke kotak asli.
- **Atur ulang demo:** kembali ke data nyata.

Kejadian dari mode demo diberi tanda `demo=true` di relay dan dasbor.

## 10. Kebutuhan non-fungsional
- Android min SDK 26, target SDK sesuai repo. Build debug cukup; tidak perlu rilis.
- Bekerja tanpa internet untuk pencatatan. Relay di jaringan lokal (hotspot HP/laptop) cukup untuk demo; FCM butuh internet dan kunci Firebase.
- `app/google-services.json` dibutuhkan untuk build (dari proyek Firebase sendiri atau milik saudara, atas izinnya). Jangan dimasukkan ke git.
- Izin dan pengaturan seperti di repo: notifikasi, Bluetooth, pengecualian optimasi baterai. Banner peringatan dari `SetupChecks` dipakai ulang.
- Tes unit untuk logika bagian 7 dan parser konfigurasi. Tes relay (`relay/test.mjs`) diperbarui untuk `ESKALASI` dan `STATUS_HARIAN`.

## 11. Rencana bertahap (urutan pembangunan)

| Tahap | Hasil yang bisa dicek | Butuh kotak asli? |
|---|---|---|
| 0 | Fork dibuat, build debug berhasil apa adanya, `applicationId` dan nama aplikasi diganti | Tidak |
| 1 | Logika bagian 7 + konfigurasi bagian 8 + tes unit lulus | Tidak |
| 2 | Layar pasien P1–P8 dengan **kotak simulasi** dan mode demo | Tidak |
| 3 | Relay + mode PMO: eskalasi dan STATUS_HARIAN sampai ke HP kedua / web relay | Tidak |
| 4 | Protokol v2 + `ble_msg_test` versi kotak (perintah `buka`) diuji dengan aplikasi | ESP32 apa saja |
| 5 | Firmware kotak asli: reed/Hall, buzzer, LED, antrean offline | Ya |
| 6 | P9 mode bepergian; rapikan UI sesuai mockup | Tidak |
| 7 | Uji skenario bagian 12, rekam video cadangan demo | Ya |

## 12. Skenario uji penerimaan (juga bahan Bab 3)

1. Pasangkan kotak; beranda menunjukkan "Kotak terhubung".
2. Pada jam minum, kotak berbunyi dan HP memberi notifikasi. Buka kotak → status "Sudah diminum (kotak)" dalam ≤ 5 detik.
3. Matikan Bluetooth HP, buka kotak, nyalakan lagi → kejadian masuk dengan jam aslinya.
4. Mode demo: lompat ke hari 58, majukan jam sampai dosis TERLEWAT dua hari berturut-turut → pesan pengingat khusus muncul di HP pasien, dan PMO menerima notifikasi ≤ 60 detik.
5. PMO menekan "Tandai dilihat" → pasien melihat "Dilihat oleh [PMO]"; relay berhenti mengirim ulang.
6. Notifikasi di layar kunci kedua HP tidak memuat kata TB, tuberkulosis, atau nama obat.
7. Ubah `eskalasi.min_terlewat_berturut` menjadi 1 di konfigurasi → setelah aplikasi dibuka ulang, eskalasi terjadi setelah 1 dosis terlewat.
8. Pasien menghapus PMO → PMO tidak lagi menerima notifikasi.

Catat hasil tiap skenario (lulus/gagal, waktu terukur) di `docs/hasil_uji.md`.

## 13. Di luar ruang lingkup prototipe (rencana pengembangan di esai)
Verifikasi minum obat dengan video atau AI; versi kotak GSM tanpa smartphone; dasbor puskesmas dan eskalasi tingkat dua; iOS; dispenser yang menjatuhkan obat; pengujian pada pasien nyata (butuh izin etik).

## 14. Hal terbuka
- Nilai dan teks **[DIISI ABYAN]** di bagian 8.
- Teks persetujuan (`assets/persetujuan.md`), dibuat terpisah.
- Peta pin ESP32-C3 Super Mini dan penempatan magnet/sensor pada kotak putar **[CEK saat perakitan]**.
- Proyek Firebase mana yang dipakai untuk `google-services.json` dan kunci FCM relay.
- Lama pengobatan dan fase dari PNPK TB **[CEK]**.
