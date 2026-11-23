package com.fallbracelet

import com.fallbracelet.adherence.AturanDosis
import com.fallbracelet.adherence.JamMinumBerlaku
import com.fallbracelet.adherence.LogikaKepatuhan
import com.fallbracelet.adherence.PengaturanDemo
import com.fallbracelet.adherence.PengingatUlang
import com.fallbracelet.adherence.RingkasanHarian as R
import com.fallbracelet.adherence.StatusDosis.BELUM
import com.fallbracelet.adherence.StatusDosis.DIKONFIRMASI_MANUAL
import com.fallbracelet.adherence.StatusDosis.DIMINUM_KOTAK
import com.fallbracelet.adherence.StatusDosis.TERLAMBAT
import com.fallbracelet.adherence.StatusDosis.TERLEWAT
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** Per-day summary, `jam_minum` history and demo mode (PRD 6.1 P4/P6, section 9). */
class RingkasanHarianTest {

    private val zona = ZoneId.of("Asia/Jakarta")
    private val dosis = AturanDosis(120, 60, 240, PengingatUlang(2, 30), 10)
    private val mulai = LocalDate.of(2026, 11, 16) // a Monday
    private val jam7 = listOf(JamMinumBerlaku(mulai, LocalTime.of(7, 0)))

    private fun t(tanggal: LocalDate, jam: Int, menit: Int = 0): Instant =
        LocalDateTime.of(tanggal, LocalTime.of(jam, menit)).atZone(zona).toInstant()

    @Test fun demoClockOnlyShiftsWhileDemoIsOn() {
        val nyata = Instant.parse("2026-11-23T00:00:00Z")
        val geser = PengaturanDemo(aktif = true, geserJam = Duration.ofHours(2), tanggalMulai = LocalDate.of(2026, 9, 27))
        assertEquals(nyata.plusSeconds(7200), R.sekarang(nyata, geser))
        assertEquals(nyata, R.sekarang(nyata, geser.copy(aktif = false)))
        assertEquals(LocalDate.of(2026, 9, 27), R.tanggalMulai(mulai, geser))
        assertEquals(mulai, R.tanggalMulai(mulai, geser.copy(aktif = false)))
        assertEquals(mulai, R.tanggalMulai(mulai, PengaturanDemo(aktif = true)))
    }

    @Test fun jumpToDay58() {
        val hariIni = LocalDate.of(2026, 11, 23)
        val start = R.tanggalMulaiUntukHariKe(hariIni, 58)
        assertEquals(58, LogikaKepatuhan.hariKe(start, hariIni))
        assertEquals(1, LogikaKepatuhan.hariKe(R.tanggalMulaiUntukHariKe(hariIni, 1), hariIni))
    }

    @Test fun moveTheClockJustPastTheMissedLimit() {
        val jadwal = LogikaKepatuhan.jadwal(mulai, LocalTime.of(7, 0), zona, dosis)
        val jam9 = t(mulai, 9)
        val geser = R.geserKeBatasTerlewat(jadwal, jam9)
        assertEquals(Duration.ofHours(2).plusSeconds(1), geser)
        val sesudah = jam9.plus(geser)
        assertEquals(TERLEWAT, LogikaKepatuhan.statusDosis(jadwal, sesudah, emptyList(), emptyList(), false).status)
        assertEquals(Duration.ZERO, R.geserKeBatasTerlewat(jadwal, t(mulai, 12)))
    }

    @Test fun jamMinumHistoryAppliesFromItsDate() {
        val riwayat = listOf(
            JamMinumBerlaku(mulai.plusDays(3), LocalTime.of(20, 0)),
            JamMinumBerlaku(mulai, LocalTime.of(7, 0)),
        )
        assertEquals(LocalTime.of(7, 0), R.jamMinumPada(mulai.plusDays(2), riwayat))
        assertEquals(LocalTime.of(20, 0), R.jamMinumPada(mulai.plusDays(3), riwayat))
        assertEquals(LocalTime.of(20, 0), R.jamMinumPada(mulai.plusDays(30), riwayat))
        assertEquals("before the first entry (demo start moved back)", LocalTime.of(7, 0), R.jamMinumPada(mulai.minusDays(9), riwayat))
        assertNull(R.jamMinumPada(mulai, emptyList()))
    }

    @Test fun buildsOneEntryPerDayFromTheStart() {
        val hariIni = mulai.plusDays(3)
        val hari = R.hari(
            tanggalMulai = mulai, hariIni = hariIni, riwayatJam = jam7, zona = zona,
            sekarang = t(hariIni, 6),
            pembukaanKotak = listOf(t(mulai, 7, 10), t(mulai.plusDays(2), 15)),
            konfirmasiManual = listOf(t(mulai.plusDays(1), 9)),
            dosis = dosis,
        )
        assertEquals(listOf(1, 2, 3, 4), hari.map { it.hariKe })
        assertEquals(listOf(DIMINUM_KOTAK, DIKONFIRMASI_MANUAL, TERLEWAT, BELUM), hari.map { it.status })
        assertEquals(t(mulai, 7, 10), hari[0].waktuTercatat)
        assertEquals(2, R.dosisTercatat(hari))
        assertEquals(emptyList<Any>(), R.hari(mulai, mulai.minusDays(1), jam7, zona, t(mulai, 6), emptyList(), emptyList(), dosis))
    }

    @Test fun aChangedJamMinumMovesThatDaysWindow() {
        val riwayat = jam7 + JamMinumBerlaku(mulai.plusDays(1), LocalTime.of(20, 0))
        val hari = R.hari(mulai, mulai.plusDays(1), riwayat, zona, t(mulai.plusDays(1), 23, 59),
            pembukaanKotak = listOf(t(mulai.plusDays(1), 7, 5)), konfirmasiManual = emptyList(), dosis = dosis)
        assertEquals(LocalTime.of(20, 0), hari[1].jamMinum)
        assertEquals("a 07:05 opening no longer counts for a 20:00 dose", TERLAMBAT, hari[1].status)
    }

    @Test fun openingsOutsideEveryWindow() {
        val sekarang = t(mulai.plusDays(2), 23)
        val diLuar = R.dibukaDiLuarJadwal(
            listOf(t(mulai, 7), t(mulai, 15), t(mulai.plusDays(1), 4), t(mulai.minusDays(1), 15), t(mulai.plusDays(5), 15)),
            mulai, jam7, zona, sekarang, dosis,
        )
        // 07:00 is in the window; before the start and after "now" are left out.
        assertEquals(listOf(t(mulai.plusDays(1), 4), t(mulai, 15)), diLuar)
    }

    @Test fun weekRunsMondayToSunday() {
        val hariIni = mulai.plusDays(2) // Wednesday
        val hari = R.hari(mulai, hariIni, jam7, zona, t(hariIni, 6), emptyList(), emptyList(), dosis)
        val minggu = R.mingguIni(hariIni, hari)
        assertEquals(7, minggu.size)
        assertEquals(mulai, minggu.first().first)
        assertEquals(listOf(true, true, true, false, false, false, false), minggu.map { it.second != null })
    }

    @Test fun sampleHistoryFiveTakenThenTwoMissed() {
        val hariIni = mulai.plusDays(10)
        val pola = listOf(true, true, true, true, true, false, false)
        val buka = R.riwayatContoh(hariIni, mulai, pola, jam7, zona)
        assertEquals(5, buka.size)
        assertEquals(t(hariIni.minusDays(7), 7, 5), buka.first())
        val hari = R.hari(mulai, hariIni, jam7, zona, t(hariIni, 6), buka, emptyList(), dosis)
        assertEquals(listOf(DIMINUM_KOTAK, DIMINUM_KOTAK, DIMINUM_KOTAK, DIMINUM_KOTAK, DIMINUM_KOTAK, TERLEWAT, TERLEWAT, BELUM),
            hari.takeLast(8).map { it.status })
        assertEquals(2, LogikaKepatuhan.terlewatBerturut(hari.map { it.status }))
        // Days before the start are skipped.
        assertEquals(4, R.riwayatContoh(mulai.plusDays(6), mulai, pola, jam7, zona).size)
    }
}
