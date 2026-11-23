package com.fallbracelet.adherence

import java.time.LocalDate

/**
 * Program rules read from `assets/aturan_program.json` (PRD section 8). Every number and text
 * that drives the adherence logic lives here; nothing is hard-coded in the logic itself.
 * Field names follow the JSON keys.
 */
data class AturanProgram(
    val statusNilai: String,
    val pengobatan: AturanPengobatan,
    val dosis: AturanDosis,
    val hariRawan: AturanHariRawan,
    val eskalasi: AturanEskalasi,
    val modeBepergian: AturanModeBepergian,
    val pesan: List<Pesan>,
    val notifikasi: TeksNotifikasi,
) {
    /** True while the values are still the placeholders ("Nilai contoh" ribbon in Settings). */
    val nilaiContoh: Boolean get() = statusNilai.contains("CONTOH", ignoreCase = true)
}

data class AturanPengobatan(val totalHari: Int, val faseIntensifHari: Int)

data class AturanDosis(
    val jendelaAwalMenit: Int,
    val batasTerlambatMenit: Int,
    val batasTerlewatMenit: Int,
    val pengingatUlang: PengingatUlang,
    val durasiAlarmKotakMenit: Int,
)

data class PengingatUlang(val jumlah: Int, val jarakMenit: Int)

data class AturanHariRawan(
    /** Inclusive day ranges, e.g. 14..28. */
    val rentangHari: List<IntRange>,
    val tanggalLibur: List<LocalDate>,
    val pola: AturanPola,
    val gabungan: Gabungan,
    val maksPerHari: Int,
)

data class AturanPola(val dalamHari: Int, val minTerlewat: Int)

/** How the WAKTU (time) and POLA (pattern) conditions combine (PRD 7.3). */
enum class Gabungan { WAKTU_SAJA, POLA_SAJA, SALAH_SATU, KEDUANYA }

data class AturanEskalasi(val minTerlewatBerturut: Int, val ulangKirimMenit: Int, val maksUlang: Int)

data class AturanModeBepergian(val maksHari: Int, val eskalasiAktif: Boolean)

data class Pesan(
    val id: String,
    /** When the message is used, e.g. "HARI_RAWAN". */
    val kapan: String,
    val judul: String,
    /** Text with variables such as `{nama}`; see [LogikaKepatuhan.isiPesan]. */
    val isi: String,
    val variabelTersedia: List<String>,
)

data class TeksNotifikasi(
    val pasienJamMinum: String,
    val pasienHariRawan: String,
    val pmoEskalasi: String,
)
