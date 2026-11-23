package com.fallbracelet.adherence

import java.time.Instant
import java.time.LocalDate

/** The ESKALASI to send now, and the earlier qualifying days that are only recorded. */
data class HasilRencanaEskalasi(
    /** The latest missed day that needs an ESKALASI and has none yet; null if none. */
    val kirim: LocalDate?,
    /** Older qualifying days without one (e.g. the phone was off): recorded, not sent. */
    val dilewati: List<LocalDate>,
)

/**
 * Which missed days send an ESKALASI to the PMO (PRD 7.4), on top of
 * [LogikaKepatuhan.perluEskalasi]. Android-free; the caller stores the result and uploads it.
 */
object RencanaEskalasi {

    /**
     * [hari] is every treatment day so far, oldest first (see [RingkasanHarian.hari]); [sudah]
     * holds the dates that already have an ESKALASI record. Like reminders, when several days
     * qualify at once only the latest is sent, so a phone that was off for days gives the PMO
     * one message, not a burst.
     */
    fun rencanakan(
        hari: List<CatatanHari>,
        sudah: Set<LocalDate>,
        eskalasi: AturanEskalasi,
        bepergian: AturanModeBepergian,
        bepergianPada: (LocalDate) -> Boolean = { false },
    ): HasilRencanaEskalasi {
        val status = hari.map { it.status }
        val calon = hari.indices
            .filter { i -> LogikaKepatuhan.perluEskalasi(status.subList(0, i + 1), bepergianPada(hari[i].tanggal), eskalasi, bepergian) }
            .map { hari[it].tanggal }
            .filter { it !in sudah }
        return HasilRencanaEskalasi(kirim = calon.lastOrNull(), dilewati = calon.dropLast(1))
    }

    /** The missed-day count to report for [tanggal]: the run of TERLEWAT ending that day. */
    fun terlewatBerturutPada(hari: List<CatatanHari>, tanggal: LocalDate): Int {
        val sampai = hari.indexOfFirst { it.tanggal == tanggal }
        if (sampai < 0) return 0
        return LogikaKepatuhan.terlewatBerturut(hari.subList(0, sampai + 1).map { it.status })
    }

    /**
     * When to wake the app next to check for an ESKALASI: one second after the next missed limit
     * still ahead (a dose only turns TERLEWAT then, and no reminder is due at that moment).
     */
    fun cekBerikutnya(jadwal: List<JadwalDosis>, sekarang: Instant): Instant? =
        jadwal.map { it.terlewatMulai.plusSeconds(1) }.filter { it.isAfter(sekarang) }.minOrNull()
}
