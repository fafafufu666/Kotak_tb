package com.fallbracelet

import com.fallbracelet.adherence.AturanDosis
import com.fallbracelet.adherence.AturanEskalasi
import com.fallbracelet.adherence.AturanModeBepergian
import com.fallbracelet.adherence.CatatanHari
import com.fallbracelet.adherence.JamMinumBerlaku
import com.fallbracelet.adherence.LogikaKepatuhan
import com.fallbracelet.adherence.PengingatUlang
import com.fallbracelet.adherence.RencanaEskalasi
import com.fallbracelet.adherence.RingkasanHarian
import com.fallbracelet.adherence.StatusDosis
import com.fallbracelet.adherence.StatusDosis.BELUM
import com.fallbracelet.adherence.StatusDosis.DIMINUM_KOTAK
import com.fallbracelet.adherence.StatusDosis.TANPA_DATA
import com.fallbracelet.adherence.StatusDosis.TERLEWAT
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** Which missed days send an ESKALASI to the PMO (PRD 7.4) and when the app checks. */
class RencanaEskalasiTest {
    private val zona = ZoneId.of("Asia/Jakarta")
    private val dosis = AturanDosis(120, 60, 240, PengingatUlang(2, 30), 10)
    private val eskalasi = AturanEskalasi(minTerlewatBerturut = 2, ulangKirimMenit = 30, maksUlang = 3)
    private val bepergian = AturanModeBepergian(7, eskalasiAktif = true)
    private val mulai = LocalDate.of(2026, 11, 16)

    private fun hari(vararg status: StatusDosis): List<CatatanHari> = status.mapIndexed { i, s ->
        val t = mulai.plusDays(i.toLong())
        CatatanHari(t, i + 1, LocalTime.of(7, 0), LogikaKepatuhan.jadwal(t, LocalTime.of(7, 0), zona, dosis), s, null)
    }
    private fun rencana(h: List<CatatanHari>, sudah: Set<LocalDate> = emptySet()) =
        RencanaEskalasi.rencanakan(h, sudah, eskalasi, bepergian)

    @Test fun secondMissedDayInARowSends() {
        assertNull(rencana(hari(DIMINUM_KOTAK, TERLEWAT)).kirim)
        assertEquals(mulai.plusDays(2), rencana(hari(DIMINUM_KOTAK, TERLEWAT, TERLEWAT)).kirim)
    }

    @Test fun eachFurtherMissedDaySendsOnce() {
        val h = hari(TERLEWAT, TERLEWAT, TERLEWAT)
        val sudah = setOf(mulai.plusDays(1))
        val r = rencana(h, sudah)
        assertEquals(mulai.plusDays(2), r.kirim)
        assertEquals(emptyList<LocalDate>(), r.dilewati)
        assertNull(rencana(h, sudah + mulai.plusDays(2)).kirim)
    }

    @Test fun phoneOffForDaysSendsOnlyTheLatest() {
        val r = rencana(hari(TERLEWAT, TERLEWAT, TERLEWAT, TERLEWAT))
        assertEquals(mulai.plusDays(3), r.kirim)
        assertEquals(listOf(mulai.plusDays(1), mulai.plusDays(2)), r.dilewati)
    }

    @Test fun todayStillOpenDoesNotBreakOrAdd() {
        val r = rencana(hari(TERLEWAT, TERLEWAT, BELUM))
        assertEquals(mulai.plusDays(1), r.kirim)
    }

    @Test fun daysBeforeSetupNeverEscalate() {
        assertNull(rencana(hari(TANPA_DATA, TANPA_DATA, TERLEWAT)).kirim)
        assertEquals(mulai.plusDays(3), rencana(hari(TANPA_DATA, TANPA_DATA, TERLEWAT, TERLEWAT)).kirim)
    }

    @Test fun travelModeCanSilenceIt() {
        val h = hari(TERLEWAT, TERLEWAT)
        val diam = RencanaEskalasi.rencanakan(h, emptySet(), eskalasi, bepergian.copy(eskalasiAktif = false)) { true }
        assertNull(diam.kirim)
    }

    @Test fun missedCountIsTheRunEndingThatDay() {
        val h = hari(DIMINUM_KOTAK, TERLEWAT, TERLEWAT, TERLEWAT)
        assertEquals(2, RencanaEskalasi.terlewatBerturutPada(h, mulai.plusDays(2)))
        assertEquals(3, RencanaEskalasi.terlewatBerturutPada(h, mulai.plusDays(3)))
        assertEquals(0, RencanaEskalasi.terlewatBerturutPada(h, mulai.plusDays(9)))
    }

    @Test fun checksOneSecondAfterTheNextMissedLimit() {
        val jam7 = listOf(JamMinumBerlaku(mulai, LocalTime.of(7, 0)))
        val jadwal = listOf(mulai, mulai.plusDays(1)).map { LogikaKepatuhan.jadwal(it, RingkasanHarian.jamMinumPada(it, jam7)!!, zona, dosis) }
        val pagi = LocalDateTime.of(mulai, LocalTime.of(9, 0)).atZone(zona).toInstant()
        assertEquals(LocalDateTime.of(mulai, LocalTime.of(11, 0, 1)).atZone(zona).toInstant(), RencanaEskalasi.cekBerikutnya(jadwal, pagi))
        val siang = LocalDateTime.of(mulai, LocalTime.of(12, 0)).atZone(zona).toInstant()
        assertEquals(LocalDateTime.of(mulai.plusDays(1), LocalTime.of(11, 0, 1)).atZone(zona).toInstant(), RencanaEskalasi.cekBerikutnya(jadwal, siang))
    }
}
