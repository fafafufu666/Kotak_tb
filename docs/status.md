# Status pengembangan

## 1. Tahap saat ini
Tahap 2 (PRD bagian 11): layar pasien, kotak simulasi, mode demo. Dikerjakan dalam dua bagian.
Posisi: bagian 2a selesai di cabang `tahap-2a` (pull request #3), build dan tes lulus, menunggu uji di HP. Bagian 2b (P1, P5, pengingat, P7) belum dimulai.

## 2. Sudah jadi dan sudah diuji
- Tahap 0 (di `main`): build APK lewat GitHub Actions, `applicationId` `id.esgas.kotakobat`, nama `[NAMA APLIKASI]`. APK terpasang dan terbuka di HP Abyan.
- Tahap 1 (di `main`): logika PRD bagian 7 dan file aturan bagian 8; 56 tes lulus.
- Tahap 2a (GitHub Actions run 45145404668, 23-11-2026): 65 tes unit lulus, 0 gagal (9 tes baru untuk ringkasan dosis harian, riwayat jam minum, dan jam virtual demo); APK ter-build dengan kunci debug tetap.

## 3. Sudah dibuat tetapi belum diuji
- Semua layar 2a belum pernah dijalankan (tidak ada emulator di cloud): pilih mode, P2 Mulai, P3 Pasangkan (bisa dilewati), P4 Beranda, P6 Riwayat, P8 Pengaturan dengan mode demo, tab PMO sementara. Perlu diuji di HP dengan langkah di pull request #3.
- Kunci debug tetap: APK berikutnya seharusnya bisa dipasang di atas APK 2a tanpa menghapus data; baru terbukti saat pembaruan pertama.

## 4. Belum dikerjakan
- Tahap 2b: P1 Persetujuan, P5 Pengingat khusus, notifikasi jam minum dan terlambat (alarm Android), P7 PMO versi Indonesia, banner izin di layar pasien.
- Tahap 3 sampai 7.

## 5. Keputusan saya
- 23-11-2026: dosis yang lewat tengah malam dihitung ke tanggal jam minum.
- 23-11-2026: build APK lewat GitHub Actions.
- 23-11-2026: `applicationId` = `id.esgas.kotakobat`; nama aplikasi sementara `[NAMA APLIKASI]`.
- 23-11-2026: Firebase belum dipakai; PMO lewat stream SSE dulu, proyek Firebase sendiri dibuat nanti (sekitar Tahap 3).
- 23-11-2026: cadangan aturan dari file `aturan_bawaan.json`, diganti per bagian dengan peringatan.
- 23-11-2026: beberapa pesan hari rawan tampil bergiliran per hari pengobatan.
- 23-11-2026: eskalasi dikirim setiap hari terlewat selama rangkaian terlewat ≥ `min_terlewat_berturut`.
- 23-11-2026: Tahap 2 dibagi 2a dan 2b; kunci debug tetap disimpan di repo (`app/kotakobat-debug.keystore`); mockup dikirim di Tahap 6; "Ingatkan x menit lagi" memakai `pengingat_ulang.jarak_menit`.

Asumsi Claude di Tahap 1 (belum kamu konfirmasi; bisa diubah):
- Pola "dalam N hari terakhir" ikut menghitung hari ini.
- Pembukaan kotak pertama dalam jendela yang dicatat; kotak lebih diutamakan daripada konfirmasi manual.
- "Terlambat" dan "terlewat" mulai sesudah batasnya (tepat di menit batas belum terlambat); ujung jendela ikut dihitung.
- Pengingat ulang pertama saat dosis menjadi terlambat, lalu tiap `jarak_menit`, tidak ada sesudah batas terlewat.
- Pesan hari rawan hanya tampil bila dosis hari itu belum tercatat.
- Di luar masa pengobatan (sebelum hari 1 atau sesudah hari terakhir) tidak pernah hari rawan.
- Jumlah jendela (`jendela_awal_menit` + `batas_terlewat_menit`) harus kurang dari 24 jam; bila tidak, bagian `dosis` ditolak.

Asumsi Claude di Tahap 2a:
- Status dosis dihitung langsung dari catatan pembukaan kotak dan konfirmasi manual, tidak disimpan sebagai tabel dosis harian. Perubahan jam minum disimpan sebagai riwayat dan berlaku mulai hari diubah.
- Tombol "Buka kotak (simulasi)" ada di Beranda selama belum ada kotak asli; pembukaan di luar mode demo dihitung sebagai data nyata.
- Data pasien disimpan di database terpisah (`pasien.db`), sehingga database gelang lama tidak diubah.
- Layar gelang lama (Home, Events, Live) tidak dipakai lagi tetapi kodenya belum dihapus.

## 6. Menunggu dari saya
- Hapus aplikasi lama sekali, pasang APK 2a, jalankan langkah uji di pull request #3, dan laporkan hasilnya (tangkapan layar bila ada yang aneh).
- Menggabungkan pull request #3 bila uji lancar.
- Nama aplikasi final, nilai dan teks [DIISI ABYAN] di `aturan_program.json`, teks persetujuan (kapan saja).

## 7. Kendala dan risiko
- Cloud ini tidak bisa mengunduh Android SDK atau file hasil build, jadi build dan tes hanya berjalan di GitHub Actions.
- Mode PMO dan tab PMO sementara masih berbahasa Inggris dan menyebut gelang; diganti di 2b dan Tahap 3.
- Kunci debug di repo publik: siapa pun bisa membuat APK yang ditandatangani dengan kunci yang sama. Aman selama hanya APK dari GitHub Actions repo ini yang dipasang.
- Alamat relay bawaan build debug (`192.168.0.104`) adalah alamat Pi di rumah saudara; perlu "Cari relay" atau diganti saat demo.
- Tahap 3: timer ulang-kirim relay ditulis langsung di `relay/server.mjs` (2 menit, 5 kali, hanya FALL/SOS), sedangkan PRD meminta nilainya dari konfigurasi aplikasi. Relay tidak bisa membaca file aset aplikasi, jadi perlu diputuskan saat Tahap 3 (misalnya nilainya ikut dikirim dalam kejadian ESKALASI).
- Lama pengobatan dan fase di konfigurasi masih [CONTOH]; perlu dicek ke PNPK TB [CEK].

## 8. Terakhir diperbarui
23-11-2026, 16.40 WIB
