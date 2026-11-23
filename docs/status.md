# Status pengembangan

## 1. Tahap saat ini
Tahap 1 (PRD bagian 11): logika inti bagian 7, file aturan bagian 8, dan tes unit.
Posisi: kode dan tes selesai, semua tes lulus di GitHub Actions. Menunggu pull request Tahap 1 digabungkan dan persetujuan untuk Tahap 2.

## 2. Sudah jadi dan sudah diuji
- Tahap 0 (digabung ke `main` 23-11-2026): build APK lewat GitHub Actions, `applicationId` `id.esgas.kotakobat`, nama `[NAMA APLIKASI]`. APK terpasang dan terbuka di HP Abyan.
- Tahap 1 (GitHub Actions run 45142654237, 23-11-2026): 56 tes unit lulus, 0 gagal (30 tes baru: 8 pembaca aturan, 22 logika kepatuhan). APK tetap ter-build.
  - Pembaca aturan: file `aturan_program.json` dan `aturan_bawaan.json` valid; bagian yang rusak diganti dari cadangan dengan peringatan.
  - Logika: hari ke-X dan fase, 6 status dosis termasuk batas jendela dan lewat tengah malam, pembukaan di luar jadwal, pengingat ulang, hari rawan (WAKTU/POLA, 4 mode gabungan), batas pesan per hari, giliran pesan, isi variabel, eskalasi.

## 3. Sudah dibuat tetapi belum diuji
- Logika Tahap 1 belum dipakai oleh layar mana pun; baru diuji lewat tes unit. Dipakai mulai Tahap 2.

## 4. Belum dikerjakan
- Tahap 2: layar pasien P1–P8, kotak simulasi, mode demo, peringatan aturan dan pita "Nilai contoh" di Pengaturan.
- Tahap 3 sampai 7.

## 5. Keputusan saya
- 23-11-2026: dosis yang lewat tengah malam dihitung ke tanggal jam minum.
- 23-11-2026: build APK lewat GitHub Actions.
- 23-11-2026: `applicationId` = `id.esgas.kotakobat`; nama aplikasi sementara `[NAMA APLIKASI]`.
- 23-11-2026: Firebase belum dipakai; PMO lewat stream SSE dulu, proyek Firebase sendiri dibuat nanti (sekitar Tahap 3).
- 23-11-2026: cadangan aturan dari file `aturan_bawaan.json`, diganti per bagian dengan peringatan.
- 23-11-2026: beberapa pesan hari rawan tampil bergiliran per hari pengobatan.
- 23-11-2026: eskalasi dikirim setiap hari terlewat selama rangkaian terlewat ≥ `min_terlewat_berturut`.

Asumsi Claude di Tahap 1 (belum kamu konfirmasi; bisa diubah):
- Pola "dalam N hari terakhir" ikut menghitung hari ini.
- Pembukaan kotak pertama dalam jendela yang dicatat; kotak lebih diutamakan daripada konfirmasi manual.
- "Terlambat" dan "terlewat" mulai sesudah batasnya (tepat di menit batas belum terlambat); ujung jendela ikut dihitung.
- Pengingat ulang pertama saat dosis menjadi terlambat, lalu tiap `jarak_menit`, tidak ada sesudah batas terlewat.
- Pesan hari rawan hanya tampil bila dosis hari itu belum tercatat.
- Di luar masa pengobatan (sebelum hari 1 atau sesudah hari terakhir) tidak pernah hari rawan.
- Jumlah jendela (`jendela_awal_menit` + `batas_terlewat_menit`) harus kurang dari 24 jam; bila tidak, bagian `dosis` ditolak.

## 6. Menunggu dari saya
- Menggabungkan pull request Tahap 1.
- Persetujuan untuk mulai merencanakan Tahap 2.
- Nama aplikasi final, nilai dan teks [DIISI ABYAN] di `aturan_program.json`, teks persetujuan (kapan saja).

## 7. Kendala dan risiko
- Cloud ini tidak bisa mengunduh Android SDK atau file hasil build, jadi build dan tes hanya berjalan di GitHub Actions.
- Banyak teks layar masih menyebut gelang (bawaan repo saudara); diganti di Tahap 2 dan 6.
- Alamat relay bawaan build debug (`192.168.0.104`) adalah alamat Pi di rumah saudara; perlu "Cari relay" atau diganti saat demo.
- Tahap 3: timer ulang-kirim relay ditulis langsung di `relay/server.mjs` (2 menit, 5 kali, hanya FALL/SOS), sedangkan PRD meminta nilainya dari konfigurasi aplikasi. Relay tidak bisa membaca file aset aplikasi, jadi perlu diputuskan saat Tahap 3 (misalnya nilainya ikut dikirim dalam kejadian ESKALASI).
- Lama pengobatan dan fase di konfigurasi masih [CONTOH]; perlu dicek ke PNPK TB [CEK].

## 8. Terakhir diperbarui
23-11-2026, 16.05 WIB
