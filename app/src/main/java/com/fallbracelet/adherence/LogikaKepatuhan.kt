package com.fallbracelet.adherence

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** A day's dose status (PRD 7.2). */
enum class StatusDosis {
    /** The window hasn't started, or is running and the dose isn't late yet. */
    BELUM,
    /** Past `batas_terlambat_menit`, not yet past `batas_terlewat_menit`. */
    TERLAMBAT,
    /** Past `batas_terlewat_menit` with no box opening or confirmation in the window. */
    TERLEWAT,
    /** The box lid was opened in the window. */
    DIMINUM_KOTAK,
    /** The patient pressed "I've taken my medicine" in the window. */
    DIKONFIRMASI_MANUAL,
    /** Travel mode was on and the patient confirmed by hand in the window. */
    BEPERGIAN;

    /** The dose counts as taken. */
    val tercatat: Boolean get() = this == DIMINUM_KOTAK || this == DIKONFIRMASI_MANUAL || this == BEPERGIAN

    /** The day's outcome can still change. */
    val masihBerjalan: Boolean get() = this == BELUM || this == TERLAMBAT
}

/** Treatment phase (PRD 7.1), plus the days before the start and after the end. */
enum class Fase { BELUM_MULAI, INTENSIF, LANJUTAN, SELESAI }

/** One day's dose times. The window is [mulaiJendela, terlewatMulai], both ends inclusive. */
data class JadwalDosis(
    /** The date the dose belongs to (the date of `jam_minum`, even if the window crosses midnight). */
    val tanggal: LocalDate,
    val mulaiJendela: Instant,
    val jamMinum: Instant,
    val terlambatMulai: Instant,
    val terlewatMulai: Instant,
) {
    fun berisi(waktu: Instant): Boolean = !waktu.isBefore(mulaiJendela) && !waktu.isAfter(terlewatMulai)
}

data class HasilStatusDosis(
    val status: StatusDosis,
    /** When the counted box opening or confirmation happened; null if none. */
    val waktuTercatat: Instant?,
)

data class HasilHariRawan(
    val rawan: Boolean,
    val olehWaktu: Boolean,
    val olehPola: Boolean,
    /** TERLEWAT days counted for the POLA rule. */
    val jumlahTerlewat: Int,
)

/**
 * The adherence rules of PRD section 7, free of Android types so they can be unit tested
 * (same style as `alert/AlertRules.kt`). Every number comes from [AturanProgram]; the current
 * time is always a parameter, so demo mode can pass a virtual clock.
 */
object LogikaKepatuhan {

    // --- 7.1 Treatment day and phase ------------------------------------------------------

    /** Day number: the start date is day 1. Zero or negative before the start. */
    fun hariKe(tanggalMulai: LocalDate, hariIni: LocalDate): Int =
        (ChronoUnit.DAYS.between(tanggalMulai, hariIni) + 1).toInt()

    fun fase(hariKe: Int, pengobatan: AturanPengobatan): Fase = when {
        hariKe < 1 -> Fase.BELUM_MULAI
        hariKe <= pengobatan.faseIntensifHari -> Fase.INTENSIF
        hariKe <= pengobatan.totalHari -> Fase.LANJUTAN
        else -> Fase.SELESAI
    }

    /** Days left after today, never negative (the `{sisa_hari}` variable). */
    fun sisaHari(hariKe: Int, pengobatan: AturanPengobatan): Int = maxOf(pengobatan.totalHari - hariKe, 0)

    // --- 7.2 Daily dose status ------------------------------------------------------------

    fun jadwal(tanggal: LocalDate, jamMinum: LocalTime, zona: ZoneId, dosis: AturanDosis): JadwalDosis {
        val minum = tanggal.atTime(jamMinum).atZone(zona).toInstant()
        return JadwalDosis(
            tanggal = tanggal,
            mulaiJendela = minum.minus(dosis.jendelaAwalMenit.toLong(), ChronoUnit.MINUTES),
            jamMinum = minum,
            terlambatMulai = minum.plus(dosis.batasTerlambatMenit.toLong(), ChronoUnit.MINUTES),
            terlewatMulai = minum.plus(dosis.batasTerlewatMenit.toLong(), ChronoUnit.MINUTES),
        )
    }

    /**
     * Status of one day's dose at [sekarang]. Only box openings and confirmations at or before
     * [sekarang] and inside the window count; the first one counts. A box opening wins over a
     * manual confirmation. [bepergianAktif] is whether travel mode was on for that day.
     */
    fun statusDosis(
        jadwal: JadwalDosis,
        sekarang: Instant,
        pembukaanKotak: Collection<Instant>,
        konfirmasiManual: Collection<Instant>,
        bepergianAktif: Boolean,
    ): HasilStatusDosis {
        fun pertamaDalamJendela(daftar: Collection<Instant>) =
            daftar.filter { !it.isAfter(sekarang) && jadwal.berisi(it) }.minOrNull()

        pertamaDalamJendela(pembukaanKotak)?.let { return HasilStatusDosis(StatusDosis.DIMINUM_KOTAK, it) }
        pertamaDalamJendela(konfirmasiManual)?.let {
            val status = if (bepergianAktif) StatusDosis.BEPERGIAN else StatusDosis.DIKONFIRMASI_MANUAL
            return HasilStatusDosis(status, it)
        }
        val status = when {
            sekarang.isAfter(jadwal.terlewatMulai) -> StatusDosis.TERLEWAT
            sekarang.isAfter(jadwal.terlambatMulai) -> StatusDosis.TERLAMBAT
            else -> StatusDosis.BELUM
        }
        return HasilStatusDosis(status, null)
    }

    /**
     * The dose date whose window holds [waktu], or null if it is outside every window
     * ("dibuka di luar jadwal"). Windows are shorter than a day, so at most one matches.
     */
    fun tanggalDosisUntuk(waktu: Instant, jamMinum: LocalTime, zona: ZoneId, dosis: AturanDosis): LocalDate? {
        val tanggalLokal = waktu.atZone(zona).toLocalDate()
        return listOf(tanggalLokal.minusDays(1), tanggalLokal, tanggalLokal.plusDays(1))
            .firstOrNull { jadwal(it, jamMinum, zona, dosis).berisi(waktu) }
    }

    /**
     * When to repeat the reminder for a late dose: the first when the dose turns late, then every
     * `pengingat_ulang.jarak_menit`, `pengingat_ulang.jumlah` times in all, dropping any that
     * would fall after the dose is already missed.
     */
    fun jadwalPengingatUlang(jadwal: JadwalDosis, dosis: AturanDosis): List<Instant> =
        List(dosis.pengingatUlang.jumlah) { i ->
            jadwal.terlambatMulai.plus(i.toLong() * dosis.pengingatUlang.jarakMenit, ChronoUnit.MINUTES)
        }.filter { !it.isAfter(jadwal.terlewatMulai) }

    // --- 7.3 Vulnerable day ---------------------------------------------------------------

    /**
     * Whether today is a vulnerable day (PRD 7.3). [statusHarian] holds one status per day,
     * oldest first, ending with today, and only days from the treatment start. The POLA rule
     * counts TERLEWAT in the last `pola.dalam_hari` entries, today included. Days outside the
     * treatment (before day 1 or after the last day) are never vulnerable.
     */
    fun hariRawan(
        hariKe: Int,
        tanggal: LocalDate,
        statusHarian: List<StatusDosis>,
        aturan: AturanHariRawan,
        pengobatan: AturanPengobatan,
    ): HasilHariRawan {
        val jumlahTerlewat = statusHarian.takeLast(aturan.pola.dalamHari).count { it == StatusDosis.TERLEWAT }
        if (hariKe !in 1..pengobatan.totalHari) return HasilHariRawan(false, false, false, jumlahTerlewat)

        val olehWaktu = aturan.rentangHari.any { hariKe in it } || tanggal in aturan.tanggalLibur
        val olehPola = jumlahTerlewat >= aturan.pola.minTerlewat
        val rawan = when (aturan.gabungan) {
            Gabungan.WAKTU_SAJA -> olehWaktu
            Gabungan.POLA_SAJA -> olehPola
            Gabungan.SALAH_SATU -> olehWaktu || olehPola
            Gabungan.KEDUANYA -> olehWaktu && olehPola
        }
        return HasilHariRawan(rawan, olehWaktu, olehPola, jumlahTerlewat)
    }

    /**
     * Whether to show the special reminder (P5) now. Called at `jam_minum` and when the dose
     * turns TERLAMBAT. Shown only while today's dose is still open, at most `maks_per_hari` times.
     */
    fun perluTampilkanPesan(
        hariRawan: HasilHariRawan,
        statusHariIni: StatusDosis,
        sudahTampilHariIni: Int,
        aturan: AturanHariRawan,
    ): Boolean = hariRawan.rawan && statusHariIni.masihBerjalan && sudahTampilHariIni < aturan.maksPerHari

    /** The message for [kapan] (e.g. "HARI_RAWAN"); several take turns by treatment day. */
    fun pilihPesan(daftar: List<Pesan>, kapan: String, hariKe: Int): Pesan? {
        val cocok = daftar.filter { it.kapan == kapan }
        if (cocok.isEmpty()) return null
        return cocok[Math.floorMod(hariKe - 1, cocok.size)]
    }

    /** Fills `{nama}`, `{hari_ke}`, `{sisa_hari}`, `{total_hari}` and `{dosis_tercatat}` in [teks]. */
    fun isiPesan(teks: String, nama: String, hariKe: Int, pengobatan: AturanPengobatan, dosisTercatat: Int): String =
        teks.replace("{nama}", nama)
            .replace("{hari_ke}", hariKe.toString())
            .replace("{sisa_hari}", sisaHari(hariKe, pengobatan).toString())
            .replace("{total_hari}", pengobatan.totalHari.toString())
            .replace("{dosis_tercatat}", dosisTercatat.toString())

    // --- 7.4 Escalation to the PMO --------------------------------------------------------

    /**
     * Consecutive TERLEWAT days ending at the latest decided day. Undecided days at the end
     * (today still BELUM or TERLAMBAT) are skipped, so they neither add to nor break the run.
     */
    fun terlewatBerturut(statusHarian: List<StatusDosis>): Int =
        statusHarian.dropLastWhile { it.masihBerjalan }.takeLastWhile { it == StatusDosis.TERLEWAT }.size

    /**
     * Whether the day that ends [statusSampaiHariItu] (oldest first) sends an ESKALASI. Every
     * missed day sends one while the run is at least `min_terlewat_berturut` long, so with 2 the
     * 2nd, 3rd, 4th… missed day in a row each send. The caller sends at most once per date.
     * With travel mode on that day and `mode_bepergian.eskalasi_aktif` false, nothing is sent.
     */
    fun perluEskalasi(
        statusSampaiHariItu: List<StatusDosis>,
        bepergianAktif: Boolean,
        eskalasi: AturanEskalasi,
        bepergian: AturanModeBepergian,
    ): Boolean {
        if (statusSampaiHariItu.lastOrNull() != StatusDosis.TERLEWAT) return false
        if (bepergianAktif && !bepergian.eskalasiAktif) return false
        return terlewatBerturut(statusSampaiHariItu) >= eskalasi.minTerlewatBerturut
    }
}
