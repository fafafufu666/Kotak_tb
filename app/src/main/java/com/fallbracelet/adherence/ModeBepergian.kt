package com.fallbracelet.adherence

import java.time.LocalDate

/**
 * One stretch of travel mode (PRD P9): from [mulai] to [sampai], both dates included. Kept after it
 * ends, so the days in it stay "Bepergian" in the history.
 */
data class PeriodeBepergian(val mulai: LocalDate, val sampai: LocalDate, val demo: Boolean = false) {
    fun berisi(tanggal: LocalDate): Boolean = !tanggal.isBefore(mulai) && !tanggal.isAfter(sampai)
}

/**
 * Travel mode (PRD P9). While it is on, a manual confirmation is the expected proof and the day
 * shows as BEPERGIAN; escalation follows `mode_bepergian.eskalasi_aktif`
 * ([LogikaKepatuhan.perluEskalasi]); it switches itself off after the chosen number of days.
 * Android-free, like [LogikaKepatuhan].
 */
object ModeBepergian {

    /** Whether travel mode covers [tanggal]. */
    fun aktifPada(tanggal: LocalDate, periode: List<PeriodeBepergian>): Boolean = periode.any { it.berisi(tanggal) }

    /** The stretch covering [hariIni], if travel mode is on today. */
    fun aktif(hariIni: LocalDate, periode: List<PeriodeBepergian>): PeriodeBepergian? = periode.lastOrNull { it.berisi(hariIni) }

    /**
     * Turns travel mode on today for [lamaHari] days (today counts as the first), at most
     * `mode_bepergian.maks_hari`. A stretch already running today is replaced, not stacked.
     */
    fun nyalakan(
        hariIni: LocalDate,
        lamaHari: Int,
        aturan: AturanModeBepergian,
        periode: List<PeriodeBepergian>,
        demo: Boolean = false,
    ): List<PeriodeBepergian> {
        val lama = lamaHari.coerceIn(1, aturan.maksHari)
        val baru = PeriodeBepergian(hariIni, hariIni.plusDays(lama - 1L), demo)
        return matikan(hariIni, periode) + baru
    }

    /**
     * Turns travel mode off from today: a stretch that started before today ends yesterday (its
     * past days keep their status); one that started today is dropped.
     */
    fun matikan(hariIni: LocalDate, periode: List<PeriodeBepergian>): List<PeriodeBepergian> = periode.mapNotNull { p ->
        when {
            !p.berisi(hariIni) -> p
            p.mulai.isBefore(hariIni) -> p.copy(sampai = hariIni.minusDays(1))
            else -> null
        }
    }

    /** Days left including today (1 on the last day), or 0 when travel mode is off today. */
    fun sisaHari(hariIni: LocalDate, periode: List<PeriodeBepergian>): Int =
        aktif(hariIni, periode)?.let { (java.time.temporal.ChronoUnit.DAYS.between(hariIni, it.sampai) + 1).toInt() } ?: 0
}
