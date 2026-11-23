package com.fallbracelet.adherence

import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** One treatment day as the screens show it (Beranda, Riwayat). */
data class CatatanHari(
    val tanggal: LocalDate,
    val hariKe: Int,
    val jamMinum: LocalTime,
    val jadwal: JadwalDosis,
    val status: StatusDosis,
    /** When the counted box opening or confirmation happened. */
    val waktuTercatat: Instant?,
)

/** A `jam_minum` that applies from [berlakuMulai] on, until a later entry replaces it. */
data class JamMinumBerlaku(val berlakuMulai: LocalDate, val jam: LocalTime)

/** Demo-mode settings (PRD section 9). All shifts are zero when demo mode is off. */
data class PengaturanDemo(
    val aktif: Boolean = false,
    /** Added to the real clock ("Jam virtual"). */
    val geserJam: Duration = Duration.ZERO,
    /** Replaces the real start date ("Lompat ke hari"); null = keep the real one. */
    val tanggalMulai: LocalDate? = null,
    /**
     * Doses whose window closed before this (virtual) time are TANPA_DATA in demo mode; null =
     * the real setup time. Set by "Lompat ke hari" and moved back by "Isi riwayat contoh".
     */
    val dipantauSejak: Instant? = null,
    /** This demo run's id on the relay; null until its first ESKALASI. */
    val sesi: String? = null,
)

/**
 * Builds the per-day picture from the raw records (box openings, manual confirmations and the
 * `jam_minum` history), plus demo mode's virtual clock. Android-free, like [LogikaKepatuhan].
 */
object RingkasanHarian {

    // --- Demo clock -----------------------------------------------------------------------

    /** The time the app works with: the real time, shifted while demo mode is on. */
    fun sekarang(nyata: Instant, demo: PengaturanDemo): Instant =
        if (demo.aktif) nyata.plus(demo.geserJam) else nyata

    fun tanggalMulai(nyata: LocalDate, demo: PengaturanDemo): LocalDate =
        if (demo.aktif) demo.tanggalMulai ?: nyata else nyata

    /** From when doses count (see [LogikaKepatuhan.statusDosis]): the setup time, or demo mode's own. */
    fun dipantauSejak(nyata: Instant?, demo: PengaturanDemo): Instant? =
        if (demo.aktif) demo.dipantauSejak ?: nyata else nyata

    /** The start date that makes [hariIni] treatment day [hariKe] ("Lompat ke hari 58"). */
    fun tanggalMulaiUntukHariKe(hariIni: LocalDate, hariKe: Int): LocalDate =
        hariIni.minusDays(hariKe.toLong() - 1)

    /**
     * How far to move the virtual clock so today's dose is just past its missed limit
     * (one second after `batas_terlewat_menit`). Zero if it already is.
     */
    fun geserKeBatasTerlewat(jadwal: JadwalDosis, sekarang: Instant): Duration {
        val target = jadwal.terlewatMulai.plusSeconds(1)
        return if (sekarang.isBefore(target)) Duration.between(sekarang, target) else Duration.ZERO
    }

    // --- jam_minum history ----------------------------------------------------------------

    /** The `jam_minum` that applied on [tanggal]: the latest entry starting on or before it. */
    fun jamMinumPada(tanggal: LocalDate, riwayat: List<JamMinumBerlaku>): LocalTime? {
        val urut = riwayat.sortedBy { it.berlakuMulai }
        return urut.lastOrNull { !it.berlakuMulai.isAfter(tanggal) }?.jam ?: urut.firstOrNull()?.jam
    }

    // --- Days ------------------------------------------------------------------------------

    /**
     * Every day from [tanggalMulai] to [hariIni] (both included), oldest first. Empty before
     * the start. [bepergianPada] tells whether travel mode was on that day. Doses that closed
     * before [dipantauSejak] with nothing recorded are TANPA_DATA.
     */
    fun hari(
        tanggalMulai: LocalDate,
        hariIni: LocalDate,
        riwayatJam: List<JamMinumBerlaku>,
        zona: ZoneId,
        sekarang: Instant,
        pembukaanKotak: List<Instant>,
        konfirmasiManual: List<Instant>,
        dosis: AturanDosis,
        bepergianPada: (LocalDate) -> Boolean = { false },
        dipantauSejak: Instant? = null,
    ): List<CatatanHari> {
        if (hariIni.isBefore(tanggalMulai) || riwayatJam.isEmpty()) return emptyList()
        val hasil = ArrayList<CatatanHari>()
        var tanggal = tanggalMulai
        while (!tanggal.isAfter(hariIni)) {
            val jam = jamMinumPada(tanggal, riwayatJam)!!
            val jadwal = LogikaKepatuhan.jadwal(tanggal, jam, zona, dosis)
            val status = LogikaKepatuhan.statusDosis(
                jadwal, sekarang, pembukaanKotak, konfirmasiManual, bepergianPada(tanggal), dipantauSejak,
            )
            hasil += CatatanHari(
                tanggal = tanggal,
                hariKe = LogikaKepatuhan.hariKe(tanggalMulai, tanggal),
                jamMinum = jam,
                jadwal = jadwal,
                status = status.status,
                waktuTercatat = status.waktuTercatat,
            )
            tanggal = tanggal.plusDays(1)
        }
        return hasil
    }

    /**
     * Box openings that fall in no dose window ("dibuka di luar jadwal"), newest first. Only
     * openings at or before [sekarang] and from the start date on are listed.
     */
    fun dibukaDiLuarJadwal(
        pembukaanKotak: List<Instant>,
        tanggalMulai: LocalDate,
        riwayatJam: List<JamMinumBerlaku>,
        zona: ZoneId,
        sekarang: Instant,
        dosis: AturanDosis,
    ): List<Instant> = pembukaanKotak
        .filter { !it.isAfter(sekarang) && !it.atZone(zona).toLocalDate().isBefore(tanggalMulai) }
        .filter { waktu ->
            val tanggalLokal = waktu.atZone(zona).toLocalDate()
            listOf(tanggalLokal.minusDays(1), tanggalLokal, tanggalLokal.plusDays(1)).none { t ->
                val jam = jamMinumPada(t, riwayatJam) ?: return@none false
                !t.isBefore(tanggalMulai) && LogikaKepatuhan.jadwal(t, jam, zona, dosis).berisi(waktu)
            }
        }
        .sortedDescending()

    /** The week containing [hariIni], Monday to Sunday; null where there is no treatment day. */
    fun mingguIni(hariIni: LocalDate, hari: List<CatatanHari>): List<Pair<LocalDate, CatatanHari?>> {
        val senin = hariIni.with(DayOfWeek.MONDAY)
        val perTanggal = hari.associateBy { it.tanggal }
        return (0L until 7L).map { senin.plusDays(it) }.map { it to perTanggal[it] }
    }

    /** Doses counted so far (the `{dosis_tercatat}` variable). */
    fun dosisTercatat(hari: List<CatatanHari>): Int = hari.count { it.status.tercatat }

    // --- Demo history ----------------------------------------------------------------------

    /** The start of the oldest day [riwayatContoh] fills for [pola]: demo counting starts there. */
    fun awalRiwayatContoh(hariIni: LocalDate, pola: List<Boolean>, zona: ZoneId): Instant =
        hariIni.minusDays(pola.size.toLong()).atStartOfDay(zona).toInstant()

    /**
     * Box-opening times for a sample history over the days before [hariIni]: one opening,
     * [menitSetelahJamMinum] after `jam_minum`, for each `true` in [pola] (oldest first, ending
     * yesterday); none for `false`. Days before [tanggalMulai] are skipped.
     */
    fun riwayatContoh(
        hariIni: LocalDate,
        tanggalMulai: LocalDate,
        pola: List<Boolean>,
        riwayatJam: List<JamMinumBerlaku>,
        zona: ZoneId,
        menitSetelahJamMinum: Long = 5,
    ): List<Instant> = pola.mapIndexedNotNull { i, diminum ->
        val tanggal = hariIni.minusDays((pola.size - i).toLong())
        if (!diminum || tanggal.isBefore(tanggalMulai)) return@mapIndexedNotNull null
        val jam = jamMinumPada(tanggal, riwayatJam) ?: return@mapIndexedNotNull null
        tanggal.atTime(jam).atZone(zona).toInstant().plusSeconds(menitSetelahJamMinum * 60)
    }
}
