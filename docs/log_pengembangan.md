# Log pengembangan (catatan penggunaan AI)

Satu baris per tahap atau perubahan besar. Baris lama tidak dihapus.

| Tanggal | Tahap | Yang dikerjakan AI (Claude) | File utama | Hasil tes |
|---|---|---|---|---|
| 23-11-2026 | 0 (persiapan) | Membaca PRD dan dokumentasi repo, memeriksa lingkungan cloud (JDK, Android SDK, Node, akses unduh), menyusun rencana Tahap 0, membuat file status dan log ini | `docs/status.md`, `docs/log_pengembangan.md` | Belum ada build atau tes; Android SDK tidak tersedia di cloud |
| 23-11-2026 | 0 | Membuat workflow GitHub Actions (tes unit + build APK debug), mengganti `applicationId` ke `id.esgas.kotakobat` dan nama aplikasi ke penanda `[NAMA APLIKASI]`, membuat Firebase opsional saat build, memperbarui README | `.github/workflows/build-apk.yml`, `app/build.gradle.kts`, `app/src/main/res/values/strings.xml`, `README.md`, `app/README.md` | Menunggu hasil GitHub Actions |
