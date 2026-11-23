# Status pengembangan

## 1. Tahap saat ini
Tahap 0 (PRD bagian 11): fork siap, build APK debug apa adanya, ganti `applicationId` dan nama aplikasi.
Posisi: build dan tes di GitHub Actions berhasil (pull request #1). Menunggu kamu memasang APK di HP dan menggabungkan pull request.

## 2. Sudah jadi dan sudah diuji
- Pemeriksaan lingkungan cloud (23-11-2026): JDK 21 ada, Node 22 ada, Android SDK tidak ada, unduhan dari `dl.google.com` dan `maven.google.com` diblokir jaringan. Kesimpulan: APK tidak bisa di-build di cloud ini.

- 23-11-2026, GitHub Actions run 45132401074: tes unit yang ada (4 file) lulus, APK debug ter-build tanpa Firebase (artifact `apk-debug`, 13,6 MB).

## 3. Sudah dibuat tetapi belum diuji
- `applicationId` `id.esgas.kotakobat` dan nama `[NAMA APLIKASI]` di APK: build sukses, tetapi isi APK belum bisa saya periksa (unduhan artifact diblokir dari cloud).
- Pemasangan dan pembukaan APK di HP (langkahmu).
- Build dengan Firebase (secret `GOOGLE_SERVICES_JSON` belum dibuat).

## 4. Belum dikerjakan
- Tahap 1 sampai 7.

## 5. Keputusan saya
- 23-11-2026: dosis yang lewat tengah malam dihitung ke tanggal jam minum (pilihan bawaan yang disetujui di utas sebelumnya).

- 23-11-2026: build APK lewat GitHub Actions.
- 23-11-2026: `applicationId` = `id.esgas.kotakobat`; nama aplikasi sementara `[NAMA APLIKASI]`.
- 23-11-2026: Firebase belum dipakai; PMO lewat stream SSE dulu, proyek Firebase sendiri dibuat nanti (sekitar Tahap 3).

## 6. Menunggu dari saya
- Nama aplikasi final (kapan saja).
- Setelah build hijau: unduh dan pasang APK di HP, lalu laporkan apakah aplikasi terbuka.
- Meninjau dan menggabungkan (merge) pull request Tahap 0.

## 7. Kendala dan risiko
- Banyak teks layar masih menyebut "bracelet"/gelang (bawaan repo saudara); diganti di Tahap 2 dan 6.
- Cloud ini tidak bisa mengunduh Android SDK dan pustaka Google, jadi build dan tes Android hanya bisa dijalankan di GitHub Actions.
- `google-services.json` milik saudara terikat ke paket `com.fallbracelet`; setelah `applicationId` diganti, file itu tidak cocok lagi.
- Versi AGP 9.1.1, compileSdk 37, dan Gradle 9.3.1 belum bisa dicek dari sini; baru terbukti saat build pertama di GitHub Actions.
- Alamat relay bawaan build debug (`192.168.0.104`) adalah alamat Pi di rumah saudara; perlu diganti atau dicari ulang lewat "Cari relay" saat demo.
- Tahap 3: timer ulang-kirim relay ditulis langsung di `relay/server.mjs` (2 menit, 5 kali, hanya FALL/SOS), sedangkan PRD meminta nilainya dari konfigurasi (30 menit, 3 kali, [CONTOH]). Perlu diputuskan saat Tahap 3.

## 8. Terakhir diperbarui
23-11-2026, 14.55 WIB
