# Hasil uji penerimaan (PRD bagian 12)

## Uji awal dengan kotak tiruan — 24-11-2026, 01.05–01.20 WIB

Ini **uji awal** sebelum kotak asli jadi. Semua skenario dijalankan ulang dengan kotak asli
(Tahap 7 sesungguhnya) sebelum hari demo, dan hasil itu ditambahkan di bawah.

**Kondisi uji**

| | |
|---|---|
| HP pasien | Tablet Xiaomi (HyperOS 2, Android 15), aplikasi dari cabang `tahap-7` (isi sama dengan `main` 066fb0d) |
| Kotak | ESP32 tiruan di dalam aplikasi (`kotak/KotakSimulasi.kt`), lewat protokol v2 yang sama dengan kotak asli |
| Relay | Laptop di WiFi yang sama (10.133.58.92:8787), tanpa Firebase |
| PMO | Halaman web relay di Chrome **di tablet yang sama** (belum HP kedua); PMO "Ani" dan "Sari" |
| Waktu | Mode demo (jam virtual), kecuali disebut lain |
| Cara ukur | Cap waktu `pasien.db` di tablet dan `relay.db` di laptop; selisih jam tablet–laptop (+1,68 s) dikoreksi. Ketukan lewat adb punya jeda beberapa ratus milidetik, termasuk dalam angka |

**Ringkasan**

| # | Skenario | Hasil | Terukur |
|---|---|---|---|
| 1 | Kotak terhubung di Beranda | Lulus (tiruan) | "Terhubung · Simulasi" |
| 2 | Jam minum: kotak berbunyi, HP memberi notifikasi; buka kotak → "Sudah diminum (kotak)" ≤ 5 s | Lulus (tiruan) | Tersimpan 0,06 s sesudah ketukan |
| 3 | Bluetooth mati, kotak dibuka, Bluetooth nyala → kejadian masuk dengan jam aslinya | Lulus (tiruan) | Dibuka 07.11 (jam aplikasi sudah 08.11); tersimpan 0,67 s sesudah tersambung, waktu tetap 07.11 |
| 4 | Hari 58, 2 dosis terlewat berturut-turut → pesan khusus di HP pasien, PMO menerima ≤ 60 s | Lulus | ESKALASI di relay 0,18 s sesudah batas terlewat hari ke-2 |
| 5 | PMO "Tandai dilihat" → pasien melihat "Dilihat oleh"; relay berhenti mengirim ulang | Lulus | Sampai ke aplikasi pasien ≤ 6 s; relay: 0 kirim ulang |
| 6 | Notifikasi layar kunci tanpa TB, tuberkulosis, atau nama obat | Lulus bersyarat | Teks masih penanda `[DIISI ABYAN]`; uji ulang setelah teks final |
| 7 | `min_terlewat_berturut` = 1 → eskalasi setelah 1 dosis terlewat | Lulus | ESKALASI "terlewatBerturut 1" di relay 2,8 s sesudah ketukan |
| 8 | Pasien menghapus PMO → PMO tidak menerima notifikasi lagi | Lulus | ESKALASI baru tidak sampai ke halaman PMO yang dihapus |

**Rincian**

1. Beranda: "Kotak obat · Terhubung · Simulasi". Pemasangan (bonding) BLE kotak asli belum bisa diuji.
2. Mode demo hari 30 (bukan hari rawan), jam virtual dimajukan dari 06.54 ke 07.09: HP memasang
   notifikasi pengingat biasa (judul `[NAMA APLIKASI]`, teks `[Teks netral - DIISI ABYAN]`) dan
   Beranda menampilkan "Kotak sedang berbunyi". "Buka kotak (simulasi)" → bunyi berhenti, Beranda
   "Sudah diminum (kotak) · Tercatat 07.09".
3. Hari 31: "Kotak dalam jangkauan" dimatikan (Beranda "Terputus"), kotak dibuka pukul 07.11 jam
   kotak, jam aplikasi dimajukan ke 08.11, kotak didekatkan lagi → kejadian tersimpan 0,67 s
   kemudian dengan waktu 07.11 (termasuk jeda sambung tiruan 0,6 s).
4. Atur ulang demo, lompat ke hari 57, dosis hari 57 diminum lewat kotak. Hari 58 pukul 07.00:
   layar penuh pengingat khusus ("Hari ke-58 termasuk jadwal hari rawan") muncul; dosis dibiarkan
   lewat batas terlewat. Hari 59 lewat batas terlewat → ESKALASI di relay 0,18 s kemudian
   (tanggal 26-11, hari ke-59, 2 terlewat berturut-turut, terakhir dibuka Selasa 07.13, hari
   rawan). Halaman PMO menampilkan "tes (demo) · 2 dosis terlewat berturut-turut · Terakhir
   dibuka: Selasa 07.13 · Hari rawan". Halaman PMO di tablet yang sama sedang di latar, jadi
   kabar tampil saat halaman dibuka lagi; di HP PMO terpisah yang halamannya terbuka, kabar
   datang lewat aliran langsung tanpa menunggu.
5. "Tandai dilihat" di halaman PMO → `pasien.db` mencatat "dilihat oleh Sari" dalam ≤ 6 s
   (batas atas; pengecekan sendiri butuh ±3 s per putaran). Di relay: dilihat oleh Sari, 0 kirim
   ulang (kirim ulang hanya untuk yang belum dilihat; diuji di `relay/test.mjs`).
6. Notifikasi aplikasi yang tampil: judul `[NAMA APLIKASI]`, teks `[Teks netral - DIISI ABYAN]`,
   saluran "Pengingat". Pencarian kata TB, tuberkulosis, rifampisin, isoniazid, pirazinamid,
   etambutol, streptomisin, OAT, INH di semua teks aplikasi, file aturan, halaman PMO, relay dan
   firmware: tidak ada. Nama perangkat BLE "Kotak-4229". Halaman PMO lewat `http://` tidak bisa
   menampilkan notifikasi di layar kunci sama sekali (butuh HTTPS), jadi bagian "HP PMO" baru
   bisa dinilai bila PMO memakai aplikasi + Firebase. **Temuan:** aplikasi masih membuat tiga
   saluran notifikasi bawaan gelang berbahasa Inggris ("Fall and SOS alarms", "Bracelet updates",
   "Connection") yang terlihat di Pengaturan Android → Notifikasi aplikasi. Bukan kata terlarang,
   tetapi tidak sesuai dan sebaiknya dihapus.
   **Tindak lanjut (24-11-2026, "Rapikan PMO"):** nama aplikasi kini "Tuntas" dan teks notifikasi
   berisi usulan netral (`status_nilai` "USULAN", menunggu Abyan); kode gelang dihapus dan tiga
   saluran lama dihapus saat aplikasi dibuka. Penghapusan saluran di tablet ini belum dicek ulang.
7. APK uji dengan `min_terlewat_berturut: 1` (file di repo tetap 2; APK biasa dipasang lagi
   sesudahnya). Atur ulang demo, lompat ke hari 57, "Majukan ke batas terlewat" → ESKALASI
   dengan "terlewatBerturut 1" di relay 2,8 s kemudian (termasuk ±2 s pembacaan layar alat uji).
8. Tab PMO → Hapus "Sari" → relay hanya menyisakan Ani; halaman Sari langsung berubah menjadi
   "Belum mendampingi siapa pun". ESKALASI berikutnya (id 6) tersimpan di relay tetapi tidak
   tampil di halaman Sari. Sari ditautkan lagi sesudah uji.

**Belum teruji di uji awal ini**

- Kotak asli lewat BLE (pemasangan, bunyi buzzer, sensor tutup, antrean flash setelah mati listrik).
- HP PMO terpisah, dan notifikasi di layar kunci HP PMO (butuh aplikasi PMO + Firebase, atau HTTPS).
- Kirim ulang ESKALASI setelah 30 menit nyata (hanya di `relay/test.mjs` dengan selang dipersingkat).
- Teks notifikasi final (kini usulan Claude, menunggu persetujuan Abyan).

**Catatan lain dari uji**

- Di tablet Xiaomi, izin layar penuh Android kembali mati setelah tiap pembaruan APK
  (notifikasi pengingat khusus tercatat `FSI_REQUESTED_BUT_DENIED`). Kartu peringatan di Beranda
  menampilkannya; sebelum demo, pasang APK final lalu nyalakan izin itu sekali lagi.

## Uji pertama dengan kotak asli (papan saja) — 25-11-2026, 20.40–20.56 WIB

Ini uji **papan ESP32 dengan firmware kotak dan tautan BLE aplikasi**, sebelum sensor tutup,
buzzer, dan LED luar dipasang. Tutup dibuka/ditutup lewat perintah serial `buka`/`tutup` di
firmware. Tahap 7 sesungguhnya tetap diulang sesudah kotak dirakit.

**Kondisi uji**

| | |
|---|---|
| HP pasien | Samsung SM-G990E (Android 16), aplikasi dari cabang `tautan-ble` |
| Kotak | ESP32 DevKit `Kotak-D1BE` (ESP32-WROOM-32, CP2102), firmware `kotak_obat` dengan pin bawaan DevKit, tanpa komponen terpasang |
| Relay / PMO | Tidak dipakai di uji ini (skenario 4–8 tidak bergantung pada kotak asli) |
| Waktu | Jam nyata, bukan mode demo |
| Cara ukur | Jam laptop saat perintah serial dikirim dibandingkan dengan logcat HP (jam keduanya disetel dari jaringan; selisih tidak diukur) |

**Ringkasan**

| # | Skenario | Hasil | Terukur |
|---|---|---|---|
| 1 | Kotak terhubung di Beranda | Lulus | Pindai menemukan `Kotak-D1BE`; Beranda "Terhubung", tanpa label Simulasi |
| 2 | Jam minum: kotak berbunyi; buka kotak → "Sudah diminum (kotak)" ≤ 5 s | Lulus sebagian | Kotak mulai berbunyi 20.54.59 (jam minum 20.55), Beranda "Kotak sedang berbunyi"; `buka` → bunyi berhenti, kejadian sampai di HP ±0,5 s kemudian. Status "Sudah diminum (kotak)" sudah ada dari pembukaan 20.42; notifikasi HP tidak diuji |
| 3 | Bluetooth mati, kotak dibuka, Bluetooth nyala → kejadian masuk dengan jam aslinya | Lulus | Dibuka 20.43.27 saat Bluetooth HP mati; Bluetooth nyala 20.44.28 → tersambung 1,5 s kemudian, kejadian tersimpan 0,5 s sesudahnya; Riwayat "20.43" |

**Rincian**

- Pemasangan: HP masih punya ikatan (bond) lama dengan papan ini dari firmware gelang, dan
  Android memakai daftar layanan lama dari cache ("box service not found"). Aplikasi sekarang
  menyegarkan cache itu sendiri; sesudahnya sambungan terenkripsi tanpa dialog pasangan baru,
  dan 8 kejadian `BOX_ON` yang menumpuk di kotak terkirim dan di-ACK.
- Kejadian pertama sesudah tersambung semula baru sampai 10 s kemudian (kotak mengirim sebelum
  aplikasi siap, lalu menunggu waktu kirim ulang). Diperbaiki di aplikasi; sesudahnya kejadian
  antrean sampai 0,4 s sesudah tersambung.
- Pembukaan saat terhubung: `buka` dikirim 20.42.57,59 → tersimpan di HP 20.42.58,06.
- Latar: aplikasi di latar dan layar mati 1 menit → `buka` → kejadian tersimpan; layanan
  `KotakService` (connectedDevice) berjalan dengan notifikasi "Tersambung ke kotak obat".
- Pengaturan: "Di kotak: jam minum 07.00, alarm 10 menit" terbaca dari CONFIG kotak; mengubah jam
  minum ke 20.55 langsung terkirim ke kotak; "Kirim kejadian uji" → "Kejadian uji diterima".
- Skenario 2 tidak bersih: pembukaan 20.42–20.46 sudah masuk jendela dosis 20.55, jadi aplikasi
  menganggap dosis sudah diminum dan tidak memberi notifikasi. Kotak tetap berbunyi karena
  catatan pembukaannya hilang saat firmware dipasang ulang 20.48.

**Belum teruji**

- Sensor tutup, buzzer, dan LED sungguhan (belum dipasang); "Uji bunyi" hanya terlihat sebagai
  perintah di log kotak.
- Notifikasi HP pada jam minum bersama bunyi kotak (skenario 2 utuh).
- Antrean flash sesudah kotak mati listrik.
- Pemasangan pertama di HP yang belum pernah berpasangan dengan papan ini (dialog pasangan Android).
