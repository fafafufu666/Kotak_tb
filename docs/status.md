# Status pengembangan

## 1. Tahap saat ini
Semua pekerjaan ada di `main` (dan di GitHub); tidak ada cabang lain. Tahap 0 sampai 6 (P9), uji awal Tahap 7 dengan kotak tiruan, "Rapikan PMO", dan (25-11-2026) kotak memakai **ESP32 DevKit** (bukan lagi ESP32-C3 Super Mini), firmware kotak berjalan di papan itu, dan aplikasi punya tautan BLE ke kotak asli; diuji dengan papan saja (tutup lewat perintah serial), lihat `docs/hasil_uji.md`.
Berikutnya: merakit sensor tutup, buzzer, dan LED ke DevKit (pin di `firmware/kotak_obat/README.md`), lalu uji Tahap 7 sesungguhnya. Syarat skenario 6 uji awal (teks notifikasi dan saluran gelang) sudah dikerjakan, tinggal dicek ulang.

## 2. Sudah jadi dan sudah diuji
- Tahap 0 (di `main`): build APK lewat GitHub Actions, `applicationId` `id.esgas.kotakobat`, nama `[NAMA APLIKASI]` (sejak 24-11-2026 "Tuntas"). APK terpasang dan terbuka di HP Abyan.
- Tahap 1 (di `main`): logika PRD bagian 7 dan file aturan bagian 8.
- Tahap 2a (di `main`): 65 tes unit lulus. Diuji di HP Abyan 23-11-2026 sekitar 17.05 WIB: Beranda, mode demo (lompat ke hari 58, majukan hari), atur ulang demo, dan kotak simulasi berjalan sesuai.
- Tahap 2b: GitHub Actions run 45155539006 (23-11-2026): 83 tes unit lulus, 0 gagal (15 tes baru untuk jadwal pengingat, 3 untuk pembacaan teks persetujuan); APK ter-build. Ini hanya membuktikan logika dan kompilasi, belum perilaku di HP.
- Tahap 2b diuji di tablet Xiaomi (HyperOS 2, Android 15) lewat adb, 23-11-2026 22.15–23.10 WIB: kartu peringatan izin dan tombolnya, mode demo (lompat hari, majukan jam), P5 muncul sendiri pada hari rawan, "Ingatkan 30 menit lagi" muncul lagi tepat 30 menit kemudian, "Saya sudah minum obat" tercatat di Beranda dan Riwayat, alarm nyata berbunyi tepat 22.30.00 saat layar terkunci, notifikasi layar kunci tanpa kata TB.
- Perbaikan sesudah uji itu (build lokal, 88 tes lulus, dipasang dan dicek di tablet): peringatan Xiaomi "Tampil di Layar kunci" di kartu izin dengan tombol ke Perizinan lainnya; pesan galat relay berbahasa Indonesia (tanpa teks teknis) dan galat PMO hanya tampil di tab PMO; tanda kutip di teks yang sebelumnya hilang.
- "Belum dipantau" (2b): hari sebelum aplikasi dipasang tampil "Belum dipantau" di Riwayat dan "–" di deret minggu; 95 tes lulus.
- Tahap 3 (build lokal: 103 tes unit lulus; tes relay lulus): diuji 23-11-2026 23.30–23.45 WIB di tablet, relay di laptop lewat `adb reverse`, halaman PMO di Chrome tablet:
  - migrasi `pasien.db` 2 ke 3 berjalan di tablet, data lama tetap ada;
  - ringkasan 7 hari sampai ke relay dan tampil di halaman PMO (DEMO, hari ke-, ✓/✗/!/–);
  - PMO terhubung dengan kode undangan dari tab PMO;
  - 2 dosis terlewat berturut-turut (mode demo) → ESKALASI sampai di relay dalam ±5 detik, dengan teks `pmo_eskalasi` dan nilai ulang-kirim dari file aturan (30 menit, 3 kali); halaman PMO menampilkan kartu "2 dosis terlewat berturut-turut", "Kotak belum pernah dibuka", "Hari rawan";
  - "Tandai dilihat" di halaman PMO → Riwayat pasien "Kabar ke PMO: dilihat oleh Ani" dalam 9 detik;
  - hari terlewat ke-3 dan ke-4 masing-masing mengirim satu ESKALASI;
  - ulang-kirim sampai `maks_ulang` dan berhenti setelah "Tandai dilihat" diuji di `relay/test.mjs` (selang dipersingkat), belum ditunggu 30 menit di HP.
- Tahap 3 diuji ulang lewat WiFi saja (tanpa USB), 23-11-2026 23.45–00.00 WIB: laptop dan tablet di WiFi yang sama (10.133.58.x). Aplikasi menemukan relay sendiri saat alamat lama tidak menjawab, dan "Cari relay di WiFi ini" juga menemukannya; halaman PMO dibuka di `http://10.133.58.92:8787/`, PMO "Sari" terhubung dengan kode baru; "Atur ulang demo" mengembalikan halaman PMO ke data nyata (hari 1, tanpa tanda DEMO); putaran demo baru: 2 dosis terlewat → ESKALASI di relay ±2 detik → kartu di halaman PMO → "Tandai dilihat" → Riwayat pasien "Kabar ke PMO: dilihat oleh Sari" dalam beberapa detik.
- Empat bug ditemukan dan diperbaiki saat uji WiFi: (1) "Atur ulang demo" bisa bertabrakan dengan pemeriksaan 15 detik sehingga ESKALASI demo tercatat ulang dan terkirim sesudah reset; sekarang reset memakai kunci yang sama dan mematikan demo lebih dulu, dan hanya catatan putaran demo saat ini yang dihitung. (2) Relay menyimpan ESKALASI demo yang belum dilihat sesudah demo selesai dan terus mengirim ulang; sekarang data demo dihapus begitu HP kembali ke data nyata. (3) Halaman PMO memunculkan kabar dari mode lain dan tidak membuang kabar yang sudah hilang dari relay saat memuat ulang. (4) Id kejadian relay bisa dipakai ulang sesudah data demo dihapus; tabel `events` sekarang AUTOINCREMENT (relay.db lama dibangun ulang sekali saat start, isinya tetap).

- Tahap 4 (build lokal: 120 tes unit lulus, 17 baru; header firmware dikompilasi dengan g++, ukuran dan offset paket cocok): protokol v2 (`PROTOCOL.md`, `firmware/kotak_obat/ble_protocol.h`, `kotak/KotakProtokol.kt`, tes yang membandingkan header dengan Kotlin), ESP32 tiruan di aplikasi (`kotak/KotakSimulasi.kt`) yang hanya diajak bicara lewat byte protokol v2. Diuji di tablet 24-11-2026 00.30–00.45 WIB:
  - migrasi `pasien.db` 3 ke 4 berjalan;
  - kotak simulasi tersambung otomatis, jam minum 07.00 dan alarm 10 menit terkirim lewat CONFIG dan terbaca kembali;
  - "Kirim kejadian uji": TEST bolak-balik sampai "Kejadian uji diterima";
  - skenario 2 (mode demo): pada jam minum kotak berbunyi ("Kotak sedang berbunyi"); "Buka kotak (simulasi)" → bunyi berhenti, Beranda "Sudah diminum (kotak) · Tercatat 07.04", Riwayat "dari kotak, 07.04";
  - skenario 3: kotak di luar jangkauan, dibuka pukul 07.07 (jam kotak), jam aplikasi dimajukan ke 08.07, kotak didekatkan lagi → kejadian tersimpan 2,3 detik kemudian dengan waktu aslinya 07.07, Riwayat "dari kotak, 07.07".

- Tahap 5 (firmware kotak, `firmware/kotak_obat`): dikompilasi untuk ESP32-C3 (esp32 core 3.3.12, NimBLE-Arduino 2.5.2) tanpa peringatan; 45% flash, 7% RAM. Semua pin dan polaritas di `config.h`, bisa juga diubah lewat `-D` saat kompilasi (dicoba: tutup di GPIO5, tanpa LED, buzzer di GPIO6, sensor terbalik, tanpa bonding → tetap terkompilasi).

- Tahap 6, P9 mode bepergian (build lokal: 126 tes lulus, 6 baru; tes relay lulus): kartu di Pengaturan (pilih 1 sampai `maks_hari` hari, nyalakan/matikan), pemberitahuan di Beranda, hari dengan konfirmasi manual tercatat BEPERGIAN, eskalasi mengikuti `mode_bepergian.eskalasi_aktif`, mati sendiri. Diuji di tablet (mode demo) 24-11-2026 01.00–01.10 WIB: nyalakan 3 hari → "Aktif sampai Kamis, 26 November 2026 (sisa 3 hari)", relay langsung menerima tanggal akhir, halaman PMO menampilkan "Mode bepergian sampai Kamis, 26 Nov"; "Saya sudah minum obat" → "Bepergian (dikonfirmasi)" dan ✓ di deret PMO; dimajukan ke Jumat → mode mati sendiri, tanggal akhir di relay kosong, Selasa tetap BEPERGIAN.
- Perbaikan dari uji itu: deret 7 hari PMO baru diperbarui tiap 5 menit sesudah dosis diminum; sekarang ringkasan dikirim begitu ada hari yang berubah.

- Tahap 7 uji awal (24-11-2026 01.05–01.20 WIB, kotak tiruan, relay laptop lewat WiFi): skenario 1–5, 7, 8 lulus; skenario 6 lulus bersyarat. Rincian dan angka di `docs/hasil_uji.md`.

- Kotak asli di ESP32 DevKit (cabang `tautan-ble`, digabung ke `main` 25-11-2026; build lokal 113 tes unit lulus, firmware dikompilasi tanpa peringatan untuk DevKit dan C3). Firmware: pin bawaan DevKit (tutup GPIO27, buzzer GPIO26, LED GPIO2), perintah serial `buka`/`tutup`/`sensor` untuk uji tanpa sensor. Aplikasi: `kotak/TautanBle.kt` (tautan GATT ke kotak asli), `KotakManager` memilih kotak asli bila ada kotak dipasangkan dan kotak simulasi sesudah "Lewati", `KotakService` (layanan latar `connectedDevice`) menjaga sambungan. Diuji 20.40–20.57 WIB di HP Samsung SM-G990E dengan papan `Kotak-D1BE`: pindai menemukan kotak; sambungan terenkripsi; kejadian tutup sampai ±0,5 s; antrean saat Bluetooth HP mati masuk dengan jam aslinya; kejadian sampai saat aplikasi di latar dan layar mati; jam minum dan lama alarm terkirim lewat CONFIG; kotak berbunyi tepat pada jam minum dan berhenti saat dibuka; TEST dan FIND sampai ke kotak.
- Perbaikan dari uji itu: (1) pemindai masih mencari UUID gelang sehingga kotak tidak pernah ditemukan; (2) Android memakai daftar layanan lama dari cache untuk papan yang dulu berpasangan sebagai gelang, sekarang aplikasi menyegarkan cache sendiri; (3) kejadian pertama sesudah tersambung baru sampai 10 s kemudian, sekarang ±0,4 s.

## 3. Sudah dibuat tetapi belum diuji
- Migrasi `pasien.db` versi 1 ke 2 (tabel `log_pengingat` baru): APK 2b di tablet uji dipasang baru, bukan di atas 2a, jadi migrasi belum terbukti di HP. SQL migrasi sudah dicocokkan dengan tabel buatan Room dan sama persis.
- P1 Persetujuan (sudah disetujui sebelum uji), notifikasi biasa pada hari bukan hari rawan, dan pengingat TERLAMBAT belum dilihat di HP.
- P5 di atas layar kunci sesudah izin Xiaomi "Tampil di Layar kunci" dinyalakan belum diuji ulang.
- Tahap 3: ulang-kirim ESKALASI setelah 30 menit nyata; push FCM ke HP PMO (Firebase belum dipakai); halaman PMO di HP lain lewat WiFi saja (uji di HP lain baru lewat USB).
- Dari 2a: riwayat dan pita "Nilai contoh" belum terlihat di tangkapan layar.

- Tahap 4: bunyi buzzer tiruan belum terdengar di uji (tablet dalam mode senyap; sekarang dipindah ke aliran alarm).

- Kotak asli: sensor tutup, buzzer, dan LED sungguhan belum dipasang; antrean flash sesudah kotak mati listrik; notifikasi HP pada jam minum bersamaan dengan bunyi kotak; dialog pasangan Android di HP yang belum pernah berpasangan dengan papan ini; tautan BLE di HP lain (tablet Xiaomi) dengan penghemat baterai ketatnya.

- Rapikan PMO (24-11-2026, build lokal, 119 tes unit lulus; diuji di HP Samsung SM-G990E lewat adb dengan relay di laptop dan pasien tiruan lewat curl: tautkan dengan kode, ringkasan harian dan deret 7 hari, kotak kabar + notifikasi, "Nanti" lalu muncul lagi saat dikirim ulang, "Tandai dilihat" sampai ke pasien sebagai "Dilihat oleh Sari", layar kabar penuh di atas layar kunci; ikon bilah status kini gelap; notifikasi PMO tidak lagi dibundel Android; layanan latar PMO dicoba lagi saat aplikasi dibuka karena Android menolak memulainya dari latar sesudah pembaruan APK): mode PMO di aplikasi Android ditulis ulang mengikuti halaman web relay (bahasa Indonesia, ESKALASI, deret 7 hari, "Tandai dilihat"/"Nanti", layar kabar di atas layar kunci); sisa kode gelang dihapus (layar Home/Events/Live/Settings, layanan gelang, database dan antrean kejadian gelang, rekaman sensor, tiga saluran notifikasi berbahasa Inggris, yang kini dihapus saat aplikasi dibuka). `ble/BraceletConnection` disimpan sebagai titik awal `TautanKotak` asli.
- Belum diuji dari Rapikan PMO: penghapusan tiga saluran notifikasi lama pada pembaruan di atas versi lama (HP Samsung belum pernah punya saluran itu), push FCM dengan `detail`, dan mode pasien di HP Samsung sesudah kode gelang dihapus (mode pasien diuji ulang hanya lewat tes unit).

## 4. Belum dikerjakan
- Merakit kotak: reed switch atau Hall, buzzer, dan magnet di kotak putar, disambung ke DevKit sesuai `firmware/kotak_obat/README.md`.
- Uji Tahap 7 sesungguhnya (PRD bagian 12) dengan kotak asli dan HP PMO terpisah.
- Tombol "Hubungi" untuk PMO (PRD M2), di halaman web dan di aplikasi: aplikasi belum punya isian nomor HP pasien.
- Push FCM: proyek Firebase sendiri (`google-services.json`) belum dibuat; tanpa itu HP PMO memakai layanan latar dengan pemberitahuan tetap.

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

- 24-11-2026 (atas permintaan): nama aplikasi "Tuntas" (netral, tanpa kata TB, sesuai pesan utama "tuntaskan pengobatan"); `total_hari` 168 dan `fase_intensif_hari` 56 menurut juknis 2025; tiga pesan hari rawan dan tiga teks notifikasi netral ditulis Claude; nilai jendela dosis, hari rawan, eskalasi, dan bepergian dibiarkan seperti contoh PRD; `status_nilai` = "USULAN" (pita "Nilai contoh" tidak tampil lagi).
- 24-11-2026: mode PMO di aplikasi mengikuti halaman web relay; push FCM membawa `detail`, `demo`, dan `receivedAt` ESKALASI.
- 25-11-2026: kotak memakai ESP32 DevKit (ESP32-WROOM-32, CP2102) yang sudah ada, bukan ESP32-C3 Super Mini; peta pin dipilih Claude (permintaan: "pinout is yours").

Asumsi Claude untuk kotak asli (25-11-2026):
- Pin DevKit: tutup GPIO27, buzzer GPIO26, LED bawaan GPIO2, baterai (opsional) GPIO34; bukan pin flash, serial, atau strapping. Kompilasi untuk ESP32-C3 tetap memakai pin Super Mini lama.
- Perintah serial `buka`/`tutup`/`sensor` di firmware kotak menggantikan rencana `ble_msg_test` versi kotak (PRD tabel bagian 4): satu firmware, tanpa papan kedua.
- Layanan latar `KotakService` hanya berjalan selama kotak asli dipasangkan; dengan kotak simulasi tidak ada notifikasi tetap.
- Kode GATT gelang (`ble/BraceletConnection.kt`, `ble/BleProtocol.kt`, `BleProtocolTest`) dihapus; isinya sudah dipindah ke `kotak/TautanBle.kt`.
- "Lupakan kotak" hanya melupakan alamat; ikatan Bluetooth di Android tetap ada (menghapusnya butuh API tersembunyi).

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
- Data pasien di database terpisah (`pasien.db`); layar gelang lama tidak dipakai tetapi kodenya belum dihapus. (Sudah dihapus 24-11-2026, termasuk `bracelet.db` di HP.)

Asumsi Claude untuk "Belum dipantau":
- Batasnya waktu pasien menyelesaikan P2; untuk pemasangan lama yang belum menyimpan waktu itu, waktu persetujuan P1.
- Dosis yang jendelanya masih terbuka saat P2 tetap dihitung. Catatan kotak atau konfirmasi sebelum batas tetap dihitung diminum.
- Di mode demo, "Lompat ke hari" memulai hitungan dari jam virtual saat itu, dan "Isi riwayat contoh" memundurkannya ke hari contoh tertua agar 2 hari terlewat contoh tetap terhitung.

Asumsi Claude di Tahap 6 (P9):
- Hari pertama mode bepergian adalah hari dinyalakan; lama dihitung termasuk hari itu.
- Mematikan lebih awal: hari-hari yang sudah lewat tetap BEPERGIAN; bila dimatikan di hari yang sama ia dinyalakan, periodenya dihapus.
- Pengingat tetap berbunyi selama bepergian (obat dibawa); yang berubah hanya status dan aturan eskalasi.
- PMO diberi tahu lewat kartu di halaman PMO (dan notifikasi browser bila tersedia), bukan lewat ESKALASI.

Asumsi Claude di Tahap 4:
- UUID layanan kotak baru (`5b0a000X-…`), berbeda dari gelang, agar aplikasi tidak tertukar dengan gelang saudara.
- CONFIG memuat juga `jendela_awal_menit` (1 byte, nama perangkat jadi 19 byte): bila tutup sudah dibuka dalam jendela dosis, kotak tidak berbunyi hari itu. Tanpa ini kotak tetap berbunyi pukul 07.00 walau dosis sudah tercatat pukul 06.58.
- Kotak memakai jam aplikasi, termasuk jam virtual mode demo (TIME dikirim ulang saat jam demo bergeser), agar kejadian dan alarm kotak cocok dengan linimasa aplikasi.
- Kotak mengirim LID_CLOSE (opsional di PRD) dengan lama tutup terbuka; aplikasi menerimanya tetapi belum memakainya.
- Kejadian dari ESP32 tiruan dicatat dengan sumber SIMULASI.

Asumsi Claude di Tahap 3:
- Bila beberapa hari memenuhi syarat eskalasi sekaligus (misalnya HP mati beberapa hari), hanya hari terbaru yang dikirim; yang lebih lama dicatat tanpa dikirim.
- ESKALASI unik per tanggal dosis; di mode demo tiap putaran (sesudah "Atur ulang demo") memakai id baru di relay agar latihan demo bisa diulang.
- Nama yang dilihat PMO = nama panggilan pasien. Relay hanya menyimpan nama panggilan, status dosis, dan kejadian.
- Halaman PMO memakai jendela kecil berbunyi lembut (bukan sirene) untuk ESKALASI; "Nanti" menundanya sampai dikirim ulang.
- Ringkasan harian dikirim tiap ada perubahan, tiap 5 menit, dan tiap 30 detik selama ada ESKALASI yang belum dilihat (paling lama 24 jam).

Asumsi Claude di Tahap 2b:
- Saat jam minum dan saat menjadi terlambat: bila hari rawan dan batas `maks_per_hari` belum tercapai, yang tampil pengingat khusus (P5); bila tidak, notifikasi biasa. Pengingat ulang berikutnya selalu notifikasi biasa.
- "Ingatkan x menit lagi" menampilkan P5 lagi dan tidak dihitung dalam `maks_per_hari`.
- Bila HP mati atau jam demo dimajukan jauh, hanya pengingat terakhir yang tampil; yang terlewat dicatat "dilewati" agar tidak muncul beruntun.
- Notifikasi pengingat ulang memakai teks `notifikasi.pasien_jam_minum` (PRD tidak punya kunci teks khusus untuk pengingat ulang). Judul notifikasi = nama aplikasi.
- Pengingat baru aktif setelah pasien menyetujui P1. Pengguna 2a akan melihat P1 sekali saat membuka APK 2b.
- Label kotak centang P1 ("Saya sudah membaca dan menyetujui isi di atas") adalah label tombol, bukan teks persetujuan; bisa kamu ganti di `strings.xml`.
- Alamat relay dipindah dari tab PMO ke Pengaturan (sesuai PRD P8). Peringatan relay dan internet di Beranda menunggu Tahap 3.

## 6. Menunggu dari saya
- Untuk demo: laptop, HP pasien, dan HP/laptop PMO di satu WiFi atau hotspot; port 8787 dibuka di firewall laptop.
- Cek usulan Claude (24-11-2026): nama aplikasi "Tuntas", nilai dan teks di `aturan_program.json` (`status_nilai` "USULAN"), lalu ganti `status_nilai` ke FINAL bila setuju. Teks persetujuan di `app/src/main/assets/persetujuan.md` kini usulan Claude (25-11-2026), menunggu dicek; bila aplikasi dipakai pasien sungguhan, teks ini perlu ditinjau komite etik bersama protokol ujinya (PRD bagian 13).
- Memutuskan apakah prototipe perlu lama pengobatan per pasien (paduan 4 bulan atau 9–12 bulan; lihat bagian 7) atau cukup 168 hari untuk semua.
- Cek peta pin DevKit usulan Claude (PRD 5.1) sebelum merakit, dan posisi magnet pada kotak putar.
- Coba penghapusan tiga saluran notifikasi lama di HP yang pernah memasang versi lama aplikasi ini (misalnya tablet Xiaomi); HP Samsung uji belum pernah punya saluran itu.

## 7. Kendala dan risiko
- Di cloud Android SDK tidak bisa diunduh, jadi di sana build dan tes hanya lewat GitHub Actions. Di laptop (sejak uji tablet) build, tes unit, dan pemasangan lewat adb berjalan lokal.
- Android 12 ke atas meminta izin "Alarm & pengingat" untuk alarm tepat waktu; tanpa izin itu pengingat bisa terlambat beberapa menit. Beberapa merek HP (misalnya Xiaomi, Oppo, Vivo) juga menahan aplikasi di latar walau izin sudah diberikan.
- Xiaomi (HyperOS): tanpa izin "Tampil di Layar kunci" P5 tidak bisa tampil di atas layar kunci (hanya notifikasi). Di tablet uji, izin layar penuh Android juga kembali mati beberapa detik sesudah setiap pembaruan APK; kartu peringatan menampilkannya lagi dan harus dinyalakan ulang.
- Kunci debug di repo publik: siapa pun bisa membuat APK yang ditandatangani dengan kunci yang sama. Aman selama hanya APK dari GitHub Actions repo ini yang dipasang.
- Alamat relay bawaan build debug (`192.168.0.104`) adalah alamat Pi di rumah saudara; perlu "Cari relay" atau diganti saat demo.
- Halaman PMO hanya berbunyi selama terbuka di depan dan layar menyala; browser tidak bisa membangunkan HP terkunci, dan notifikasi browser butuh HTTPS (tidak ada di `http://` LAN). Chrome juga membekukan sambungan tab yang tidak tampil; halaman menyambung ulang saat tampil lagi.
- Dosis yang jadi TERLEWAT diperiksa lewat alarm di batas terlewat. Bila Android menahan aplikasi di latar (lihat Xiaomi di atas), ESKALASI baru terkirim saat aplikasi bangun lagi.
- Lama pengobatan sudah dicek (24-11-2026) ke Juknis Penatalaksanaan TB Sensitif Obat (Kepdirjen P2 HK.02.02/C/5401/2025, hlm. 51–53): 2HRZE/4HR setiap hari, 1 bulan pengobatan = 28 hari, jadi 56 + 112 = 168 hari. Pasien dengan paduan 4 bulan (2HPMZ/2HPM) atau TB ekstra paru berat (9–12 bulan) butuh `total_hari` lain; aplikasi hanya punya satu nilai untuk semua pasien.

## 8. Terakhir diperbarui
25-11-2026, sesudah tautan BLE ke kotak asli di ESP32 DevKit diuji dan cabang `tautan-ble` digabung ke `main`
