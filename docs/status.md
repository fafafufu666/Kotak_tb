# Status pengembangan

## 1. Tahap saat ini
Tahap 2 (PRD bagian 11): layar pasien, kotak simulasi, mode demo. Dikerjakan dalam dua bagian.
Posisi: 2a sudah di `main` (PR #3). Bagian 2b (P1, P5 + notifikasi, P7, banner izin) selesai di cabang `tahap-2b`, build dan tes lulus di GitHub Actions, menunggu uji di HP.

## 2. Sudah jadi dan sudah diuji
- Tahap 0 (di `main`): build APK lewat GitHub Actions, `applicationId` `id.esgas.kotakobat`, nama `[NAMA APLIKASI]`. APK terpasang dan terbuka di HP Abyan.
- Tahap 1 (di `main`): logika PRD bagian 7 dan file aturan bagian 8.
- Tahap 2a (di `main`): 65 tes unit lulus. Diuji di HP Abyan 23-11-2026 sekitar 17.05 WIB: Beranda, mode demo (lompat ke hari 58, majukan hari), atur ulang demo, dan kotak simulasi berjalan sesuai.
- Tahap 2b: GitHub Actions run 45155539006 (23-11-2026): 83 tes unit lulus, 0 gagal (15 tes baru untuk jadwal pengingat, 3 untuk pembacaan teks persetujuan); APK ter-build. Ini hanya membuktikan logika dan kompilasi, belum perilaku di HP.
- Tahap 2b diuji di tablet Xiaomi (HyperOS 2, Android 15) lewat adb, 23-11-2026 22.15–23.10 WIB: kartu peringatan izin dan tombolnya, mode demo (lompat hari, majukan jam), P5 muncul sendiri pada hari rawan, "Ingatkan 30 menit lagi" muncul lagi tepat 30 menit kemudian, "Saya sudah minum obat" tercatat di Beranda dan Riwayat, alarm nyata berbunyi tepat 22.30.00 saat layar terkunci, notifikasi layar kunci tanpa kata TB.
- Perbaikan sesudah uji itu (build lokal, 88 tes lulus, dipasang dan dicek di tablet): peringatan Xiaomi "Tampil di Layar kunci" di kartu izin dengan tombol ke Perizinan lainnya; pesan galat relay berbahasa Indonesia (tanpa teks teknis) dan galat PMO hanya tampil di tab PMO; tanda kutip di teks yang sebelumnya hilang.

## 3. Sudah dibuat tetapi belum diuji
- Semua layar dan perilaku 2b belum pernah dijalankan di HP (tidak ada emulator di cloud): P1 Persetujuan, P5 Pengingat khusus (layar penuh, tombol, catatan tombol di Riwayat), notifikasi jam minum, terlambat, dan pengingat ulang lewat alarm Android, P7 PMO berbahasa Indonesia, kartu Relay di Pengaturan, peringatan izin di Beranda.
- Migrasi `pasien.db` versi 1 ke 2 (tabel `log_pengingat` baru): APK 2b di tablet uji dipasang baru, bukan di atas 2a, jadi migrasi belum terbukti di HP. SQL migrasi sudah dicocokkan dengan tabel buatan Room dan sama persis.
- P1 Persetujuan (sudah disetujui sebelum uji), notifikasi biasa pada hari bukan hari rawan, dan pengingat TERLAMBAT belum dilihat di HP.
- P5 di atas layar kunci sesudah izin Xiaomi "Tampil di Layar kunci" dinyalakan belum diuji ulang.
- Dari 2a: riwayat dan pita "Nilai contoh" belum terlihat di tangkapan layar.

## 4. Belum dikerjakan
- Tahap 3 sampai 7 (relay ESKALASI dan STATUS_HARIAN, mode PMO, protokol kotak, firmware, mode bepergian, uji skenario).
- Mode PMO (HP pendamping) masih tampilan lama berbahasa Inggris; diganti di Tahap 3.

## 5. Keputusan saya
- 23-11-2026: dosis yang lewat tengah malam dihitung ke tanggal jam minum.
- 23-11-2026: build APK lewat GitHub Actions.
- 23-11-2026: `applicationId` = `id.esgas.kotakobat`; nama aplikasi sementara `[NAMA APLIKASI]`.
- 23-11-2026: Firebase belum dipakai; PMO lewat stream SSE dulu, proyek Firebase sendiri dibuat nanti (sekitar Tahap 3).
- 23-11-2026: cadangan aturan dari file `aturan_bawaan.json`, diganti per bagian dengan peringatan.
- 23-11-2026: beberapa pesan hari rawan tampil bergiliran per hari pengobatan.
- 23-11-2026: eskalasi dikirim setiap hari terlewat selama rangkaian terlewat ≥ `min_terlewat_berturut`.
- 23-11-2026: Tahap 2 dibagi 2a dan 2b; kunci debug tetap disimpan di repo (`app/kotakobat-debug.keystore`); mockup dikirim di Tahap 6; "Ingatkan x menit lagi" memakai `pengingat_ulang.jarak_menit`.
- 23-11-2026: rencana 2b disetujui (P1 dari `persetujuan.md` berisi penanda, P5 + notifikasi AlarmManager, P7 Indonesia, banner izin). PR #3 digabung 17.10 WIB.
- 23-11-2026: dosis yang jendelanya sudah tutup sebelum aplikasi dipasang tidak dihitung terlewat (status baru "Belum dipantau"); relay Tahap 3 dijalankan di laptop; sisi PMO memakai halaman web relay; nilai ulang-kirim eskalasi ikut dikirim aplikasi di setiap kejadian ESKALASI.

Asumsi Claude di Tahap 1 (belum kamu konfirmasi; bisa diubah):
- Pola "dalam N hari terakhir" ikut menghitung hari ini.
- Pembukaan kotak pertama dalam jendela yang dicatat; kotak lebih diutamakan daripada konfirmasi manual.
- "Terlambat" dan "terlewat" mulai sesudah batasnya (tepat di menit batas belum terlambat); ujung jendela ikut dihitung.
- Pengingat ulang pertama saat dosis menjadi terlambat, lalu tiap `jarak_menit`, tidak ada sesudah batas terlewat.
- Pesan hari rawan hanya tampil bila dosis hari itu belum tercatat.
- Di luar masa pengobatan (sebelum hari 1 atau sesudah hari terakhir) tidak pernah hari rawan.
- Jumlah jendela (`jendela_awal_menit` + `batas_terlewat_menit`) harus kurang dari 24 jam; bila tidak, bagian `dosis` ditolak.

Asumsi Claude di Tahap 2a:
- Status dosis dihitung langsung dari catatan pembukaan kotak dan konfirmasi manual, tidak disimpan sebagai tabel dosis harian. Perubahan jam minum berlaku mulai hari diubah.
- Tombol "Buka kotak (simulasi)" ada di Beranda selama belum ada kotak asli; pembukaan di luar mode demo dihitung sebagai data nyata.
- Data pasien di database terpisah (`pasien.db`); layar gelang lama tidak dipakai tetapi kodenya belum dihapus.

Asumsi Claude untuk "Belum dipantau":
- Batasnya waktu pasien menyelesaikan P2; untuk pemasangan lama yang belum menyimpan waktu itu, waktu persetujuan P1.
- Dosis yang jendelanya masih terbuka saat P2 tetap dihitung. Catatan kotak atau konfirmasi sebelum batas tetap dihitung diminum.
- Di mode demo, "Lompat ke hari" memulai hitungan dari jam virtual saat itu, dan "Isi riwayat contoh" memundurkannya ke hari contoh tertua agar 2 hari terlewat contoh tetap terhitung.

Asumsi Claude di Tahap 2b:
- Saat jam minum dan saat menjadi terlambat: bila hari rawan dan batas `maks_per_hari` belum tercapai, yang tampil pengingat khusus (P5); bila tidak, notifikasi biasa. Pengingat ulang berikutnya selalu notifikasi biasa.
- "Ingatkan x menit lagi" menampilkan P5 lagi dan tidak dihitung dalam `maks_per_hari`.
- Bila HP mati atau jam demo dimajukan jauh, hanya pengingat terakhir yang tampil; yang terlewat dicatat "dilewati" agar tidak muncul beruntun.
- Notifikasi pengingat ulang memakai teks `notifikasi.pasien_jam_minum` (PRD tidak punya kunci teks khusus untuk pengingat ulang). Judul notifikasi = nama aplikasi.
- Pengingat baru aktif setelah pasien menyetujui P1. Pengguna 2a akan melihat P1 sekali saat membuka APK 2b.
- Label kotak centang P1 ("Saya sudah membaca dan menyetujui isi di atas") adalah label tombol, bukan teks persetujuan; bisa kamu ganti di `strings.xml`.
- Alamat relay dipindah dari tab PMO ke Pengaturan (sesuai PRD P8). Peringatan relay dan internet di Beranda menunggu Tahap 3.

## 6. Menunggu dari saya
- Pasang APK 2b di atas APK 2a (tanpa menghapus), jalankan langkah uji di pull request 2b, dan laporkan hasilnya.
- Menggabungkan pull request 2b bila uji lancar.
- Nama aplikasi final, nilai dan teks [DIISI ABYAN] di `aturan_program.json` (termasuk teks notifikasi), dan teks persetujuan di `app/src/main/assets/persetujuan.md` (kapan saja).

## 7. Kendala dan risiko
- Cloud ini tidak bisa mengunduh Android SDK atau file hasil build, jadi build dan tes aplikasi hanya berjalan di GitHub Actions.
- Android 12 ke atas meminta izin "Alarm & pengingat" untuk alarm tepat waktu; tanpa izin itu pengingat bisa terlambat beberapa menit. Beberapa merek HP (misalnya Xiaomi, Oppo, Vivo) juga menahan aplikasi di latar walau izin sudah diberikan.
- Xiaomi (HyperOS): tanpa izin "Tampil di Layar kunci" P5 tidak bisa tampil di atas layar kunci (hanya notifikasi). Di tablet uji, izin layar penuh Android juga kembali mati beberapa detik sesudah setiap pembaruan APK; kartu peringatan menampilkannya lagi dan harus dinyalakan ulang.
- Mode PMO di HP pendamping masih berbahasa Inggris dan menyebut gelang; diganti di Tahap 3.
- Kunci debug di repo publik: siapa pun bisa membuat APK yang ditandatangani dengan kunci yang sama. Aman selama hanya APK dari GitHub Actions repo ini yang dipasang.
- Alamat relay bawaan build debug (`192.168.0.104`) adalah alamat Pi di rumah saudara; perlu "Cari relay" atau diganti saat demo.
- Tahap 3: timer ulang-kirim relay ditulis langsung di `relay/server.mjs` (2 menit, 5 kali, hanya FALL/SOS), sedangkan PRD meminta nilainya dari konfigurasi aplikasi. Perlu diputuskan saat Tahap 3 (misalnya nilainya ikut dikirim dalam kejadian ESKALASI).
- Lama pengobatan dan fase di konfigurasi masih [CONTOH]; perlu dicek ke PNPK TB [CEK].

## 8. Terakhir diperbarui
23-11-2026, 23.15 WIB
