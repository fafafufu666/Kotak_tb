package com.fallbracelet

import com.fallbracelet.adherence.AturanDosis
import com.fallbracelet.adherence.AturanEskalasi
import com.fallbracelet.adherence.AturanHariRawan
import com.fallbracelet.adherence.AturanModeBepergian
import com.fallbracelet.adherence.AturanPengobatan
import com.fallbracelet.adherence.AturanPola
import com.fallbracelet.adherence.Fase
import com.fallbracelet.adherence.Gabungan
import com.fallbracelet.adherence.LogikaKepatuhan as L
import com.fallbracelet.adherence.PengingatUlang
import com.fallbracelet.adherence.Pesan
import com.fallbracelet.adherence.StatusDosis
import com.fallbracelet.adherence.StatusDosis.BELUM
import com.fallbracelet.adherence.StatusDosis.BEPERGIAN
import com.fallbracelet.adherence.StatusDosis.DIKONFIRMASI_MANUAL
import com.fallbracelet.adherence.StatusDosis.DIMINUM_KOTAK
import com.fallbracelet.adherence.StatusDosis.TERLAMBAT
import com.fallbracelet.adherence.StatusDosis.TERLEWAT
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * The adherence rules of PRD section 7. The rule values here are fixed test values, so the
 * tests keep passing whatever Abyan puts in `aturan_program.json`.
 */
class LogikaKepatuhanTest {

    private val zona = ZoneId.of("Asia/Jakarta")
    private val pengobatan = AturanPengobatan(totalHari = 180, faseIntensifHari = 56)
    private val dosis = AturanDosis(
        jendelaAwalMenit = 120, batasTerlambatMenit = 60, batasTerlewatMenit = 240,
        pengingatUlang = PengingatUlang(jumlah = 2, jarakMenit = 30), durasiAlarmKotakMenit = 10,
    )
    private val tanggal = LocalDate.of(2026, 11, 23)
    private val jam7 = LocalTime.of(7, 0)
    private val jadwal = L.jadwal(tanggal, jam7, zona, dosis)

    /** An instant on [tanggal] (or [hari] days later) at local time [jam]:[menit]:[detik]. */
    private fun t(jam: Int, menit: Int = 0, detik: Int = 0, hari: Long = 0): Instant =
        LocalDateTime.of(tanggal.plusDays(hari), LocalTime.of(jam, menit, detik)).atZone(zona).toInstant()

    private fun status(
        sekarang: Instant,
        kotak: List<Instant> = emptyList(),
        manual: List<Instant> = emptyList(),
        bepergian: Boolean = false,
    ) = L.statusDosis(jadwal, sekarang, kotak, manual, bepergian).status

    // --- 7.1 ------------------------------------------------------------------------------

    @Test fun dayNumberCountsTheStartDateAsDayOne() {
        assertEquals(1, L.hariKe(tanggal, tanggal))
        assertEquals(58, L.hariKe(tanggal, tanggal.plusDays(57)))
        assertEquals(0, L.hariKe(tanggal, tanggal.minusDays(1)))
        assertEquals(31, L.hariKe(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 3, 3))) // across February
    }

    @Test fun phaseFollowsTheConfiguredDays() {
        assertEquals(Fase.BELUM_MULAI, L.fase(0, pengobatan))
        assertEquals(Fase.INTENSIF, L.fase(1, pengobatan))
        assertEquals(Fase.INTENSIF, L.fase(56, pengobatan))
        assertEquals(Fase.LANJUTAN, L.fase(57, pengobatan))
        assertEquals(Fase.LANJUTAN, L.fase(180, pengobatan))
        assertEquals(Fase.SELESAI, L.fase(181, pengobatan))
        assertEquals(122, L.sisaHari(58, pengobatan))
        assertEquals(0, L.sisaHari(200, pengobatan))
    }

    // --- 7.2 ------------------------------------------------------------------------------

    @Test fun scheduleTimes() {
        assertEquals(t(5), jadwal.mulaiJendela)
        assertEquals(t(7), jadwal.jamMinum)
        assertEquals(t(8), jadwal.terlambatMulai)
        assertEquals(t(11), jadwal.terlewatMulai)
    }

    @Test fun statusWithoutAnythingRecordedMovesThroughBelumTerlambatTerlewat() {
        assertEquals(BELUM, status(t(4)))
        assertEquals(BELUM, status(t(7, 30)))
        assertEquals("exactly at the late limit is not late yet", BELUM, status(t(8)))
        assertEquals(TERLAMBAT, status(t(8, 0, 1)))
        assertEquals("exactly at the missed limit is not missed yet", TERLAMBAT, status(t(11)))
        assertEquals(TERLEWAT, status(t(11, 0, 1)))
    }

    @Test fun boxOpeningCountsOnlyInsideTheWindowEndsIncluded() {
        assertEquals(DIMINUM_KOTAK, status(t(12), kotak = listOf(t(5))))
        assertEquals(DIMINUM_KOTAK, status(t(12), kotak = listOf(t(11))))
        assertEquals(TERLEWAT, status(t(12), kotak = listOf(t(4, 59, 59))))
        assertEquals(TERLEWAT, status(t(12), kotak = listOf(t(11, 0, 1))))
        assertEquals("yesterday's opening", TERLEWAT, status(t(12), kotak = listOf(t(7, hari = -1))))
    }

    @Test fun openingsAfterNowDontCountYet() {
        // Demo mode can run a virtual clock behind real events.
        assertEquals(BELUM, status(t(6), kotak = listOf(t(7))))
    }

    @Test fun theFirstOpeningIsTheOneRecorded() {
        val hasil = L.statusDosis(jadwal, t(12), listOf(t(9), t(6, 30), t(10)), emptyList(), false)
        assertEquals(DIMINUM_KOTAK, hasil.status)
        assertEquals(t(6, 30), hasil.waktuTercatat)
    }

    @Test fun manualConfirmationAndTravelMode() {
        assertEquals(DIKONFIRMASI_MANUAL, status(t(12), manual = listOf(t(9))))
        assertEquals(BEPERGIAN, status(t(12), manual = listOf(t(9)), bepergian = true))
        assertEquals("the box wins over a manual tap", DIMINUM_KOTAK, status(t(12), kotak = listOf(t(10)), manual = listOf(t(9))))
        assertEquals("a box opening on a travel day is still the box", DIMINUM_KOTAK, status(t(12), kotak = listOf(t(9)), bepergian = true))
        assertEquals("a tap after the missed limit changes nothing", TERLEWAT, status(t(12), manual = listOf(t(11, 30))))
        assertEquals("travel mode alone isn't a dose", TERLEWAT, status(t(12), bepergian = true))
    }

    @Test fun aWindowCrossingMidnightBelongsToTheDateOfTheDose() {
        val jam23 = LocalTime.of(23, 0)
        val malam = L.jadwal(tanggal, jam23, zona, dosis)
        val setengahDuaPagi = t(1, 30, hari = 1)
        assertEquals(DIMINUM_KOTAK, L.statusDosis(malam, t(4, hari = 1), listOf(setengahDuaPagi), emptyList(), false).status)
        assertEquals(tanggal, L.tanggalDosisUntuk(setengahDuaPagi, jam23, zona, dosis))
        assertEquals(TERLEWAT, L.statusDosis(malam, t(3, 0, 1, hari = 1), emptyList(), emptyList(), false).status)
    }

    @Test fun findsTheDoseDateOfAnOpeningOrNoneOutsideTheSchedule() {
        assertEquals(tanggal, L.tanggalDosisUntuk(t(6), jam7, zona, dosis))
        assertEquals(tanggal.plusDays(1), L.tanggalDosisUntuk(t(5, hari = 1), jam7, zona, dosis))
        assertNull("opened outside the schedule", L.tanggalDosisUntuk(t(15), jam7, zona, dosis))
        assertNull(L.tanggalDosisUntuk(t(4, 59), jam7, zona, dosis))
    }

    @Test fun repeatRemindersStartWhenLateAndStopAtTheMissedLimit() {
        assertEquals(listOf(t(8), t(8, 30)), L.jadwalPengingatUlang(jadwal, dosis))
        val banyak = dosis.copy(pengingatUlang = PengingatUlang(jumlah = 10, jarakMenit = 60))
        assertEquals(listOf(t(8), t(9), t(10), t(11)), L.jadwalPengingatUlang(jadwal, banyak))
        assertEquals(emptyList<Instant>(), L.jadwalPengingatUlang(jadwal, dosis.copy(pengingatUlang = PengingatUlang(0, 30))))
    }

    // --- 7.3 ------------------------------------------------------------------------------

    private val rawan = AturanHariRawan(
        rentangHari = listOf(14..28, 50..70),
        tanggalLibur = listOf(LocalDate.of(2026, 12, 25)),
        pola = AturanPola(dalamHari = 7, minTerlewat = 1),
        gabungan = Gabungan.SALAH_SATU,
        maksPerHari = 2,
    )
    private val baik = List(10) { DIMINUM_KOTAK }

    private fun cekRawan(
        hariKe: Int,
        status: List<StatusDosis> = baik + BELUM,
        hari: LocalDate = tanggal,
        aturan: AturanHariRawan = rawan,
    ) = L.hariRawan(hariKe, hari, status, aturan, pengobatan)

    @Test fun timeRuleUsesTheDayRangesEndsIncludedAndHolidays() {
        assertFalse(cekRawan(13).olehWaktu)
        assertTrue(cekRawan(14).olehWaktu)
        assertTrue(cekRawan(28).olehWaktu)
        assertFalse(cekRawan(29).olehWaktu)
        assertTrue(cekRawan(58).olehWaktu)
        assertTrue("holiday", cekRawan(40, hari = LocalDate.of(2026, 12, 25)).olehWaktu)
    }

    @Test fun patternRuleCountsMissedDosesInTheLastDaysIncludingToday() {
        val terlewat8HariLalu = listOf(TERLEWAT) + List(7) { DIMINUM_KOTAK }
        assertFalse(cekRawan(40, terlewat8HariLalu).olehPola)
        val terlewat7HariLalu = listOf(TERLEWAT) + List(6) { DIMINUM_KOTAK }
        assertTrue(cekRawan(40, terlewat7HariLalu).olehPola)
        val duaKali = cekRawan(40, listOf(TERLEWAT, DIMINUM_KOTAK, TERLEWAT), aturan = rawan.copy(pola = AturanPola(7, 2)))
        assertTrue(duaKali.olehPola)
        assertEquals(2, duaKali.jumlahTerlewat)
        assertFalse(cekRawan(40, listOf(TERLEWAT), aturan = rawan.copy(pola = AturanPola(7, 2))).olehPola)
    }

    @Test fun combinationModes() {
        val terlewat = baik + TERLEWAT
        // (hari 20 = time rule on, hari 40 = off) × (pattern on, off)
        val harapan = mapOf(
            Gabungan.WAKTU_SAJA to listOf(true, true, false, false),
            Gabungan.POLA_SAJA to listOf(true, false, true, false),
            Gabungan.SALAH_SATU to listOf(true, true, true, false),
            Gabungan.KEDUANYA to listOf(true, false, false, false),
        )
        for ((g, h) in harapan) {
            val a = rawan.copy(gabungan = g)
            val hasil = listOf(
                cekRawan(20, terlewat, aturan = a), cekRawan(20, baik, aturan = a),
                cekRawan(40, terlewat, aturan = a), cekRawan(40, baik, aturan = a),
            ).map { it.rawan }
            assertEquals(g.name, h, hasil)
        }
    }

    @Test fun neverVulnerableOutsideTheTreatment() {
        assertFalse(cekRawan(0, baik + TERLEWAT).rawan)
        assertFalse(cekRawan(181, baik + TERLEWAT, hari = LocalDate.of(2026, 12, 25)).rawan)
    }

    @Test fun specialReminderShowsWhileTheDoseIsOpenUpToTheDailyLimit() {
        val ya = cekRawan(20)
        val tidak = cekRawan(40)
        assertTrue(L.perluTampilkanPesan(ya, BELUM, sudahTampilHariIni = 0, aturan = rawan))
        assertTrue(L.perluTampilkanPesan(ya, TERLAMBAT, sudahTampilHariIni = 1, aturan = rawan))
        assertFalse("daily limit", L.perluTampilkanPesan(ya, TERLAMBAT, sudahTampilHariIni = 2, aturan = rawan))
        assertFalse("already taken", L.perluTampilkanPesan(ya, DIMINUM_KOTAK, sudahTampilHariIni = 0, aturan = rawan))
        assertFalse("not vulnerable", L.perluTampilkanPesan(tidak, BELUM, sudahTampilHariIni = 0, aturan = rawan))
        assertFalse("limit 0", L.perluTampilkanPesan(ya, BELUM, 0, rawan.copy(maksPerHari = 0)))
    }

    @Test fun messagesTakeTurnsByTreatmentDay() {
        fun p(id: String, kapan: String = "HARI_RAWAN") = Pesan(id, kapan, "j", "i", emptyList())
        val daftar = listOf(p("a"), p("lain", kapan = "LAIN"), p("b"))
        assertEquals(listOf("a", "b", "a", "b"), (1..4).map { L.pilihPesan(daftar, "HARI_RAWAN", it)?.id })
        assertEquals("day 0 still picks one", "b", L.pilihPesan(daftar, "HARI_RAWAN", 0)?.id)
        assertEquals("lain", L.pilihPesan(daftar, "LAIN", 7)?.id)
        assertNull(L.pilihPesan(daftar, "TIDAK_ADA", 1))
    }

    @Test fun fillsTheMessageVariables() {
        val teks = "{nama}: hari {hari_ke}/{total_hari}, sisa {sisa_hari}, tercatat {dosis_tercatat}, {nama}, {lain}"
        assertEquals("Ani: hari 58/180, sisa 122, tercatat 55, Ani, {lain}", L.isiPesan(teks, "Ani", 58, pengobatan, 55))
    }

    // --- 7.4 ------------------------------------------------------------------------------

    @Test fun consecutiveMissesSkipTodayWhileItIsStillOpen() {
        assertEquals(2, L.terlewatBerturut(listOf(DIMINUM_KOTAK, TERLEWAT, TERLEWAT, BELUM)))
        assertEquals(2, L.terlewatBerturut(listOf(TERLEWAT, TERLEWAT, TERLAMBAT)))
        assertEquals(1, L.terlewatBerturut(listOf(TERLEWAT, DIMINUM_KOTAK, TERLEWAT)))
        assertEquals(0, L.terlewatBerturut(listOf(TERLEWAT, BEPERGIAN)))
        assertEquals(0, L.terlewatBerturut(emptyList()))
    }

    private val eskalasi = AturanEskalasi(minTerlewatBerturut = 2, ulangKirimMenit = 30, maksUlang = 3)
    private val bepergian = AturanModeBepergian(maksHari = 7, eskalasiAktif = true)

    @Test fun everyMissedDayEscalatesOnceTheRunReachesTheMinimum() {
        val hari = listOf(DIMINUM_KOTAK, TERLEWAT, TERLEWAT, TERLEWAT, DIMINUM_KOTAK, TERLEWAT)
        val kirim = hari.indices.map { L.perluEskalasi(hari.subList(0, it + 1), false, eskalasi, bepergian) }
        assertEquals(listOf(false, false, true, true, false, false), kirim)
    }

    @Test fun minimumOfOneEscalatesOnTheFirstMiss() {
        // PRD scenario 7: min_terlewat_berturut = 1.
        assertTrue(L.perluEskalasi(listOf(DIMINUM_KOTAK, TERLEWAT), false, eskalasi.copy(minTerlewatBerturut = 1), bepergian))
    }

    @Test fun travelModeCanTurnEscalationOff() {
        val dua = listOf(TERLEWAT, TERLEWAT)
        assertTrue(L.perluEskalasi(dua, bepergianAktif = true, eskalasi, bepergian))
        assertFalse(L.perluEskalasi(dua, bepergianAktif = true, eskalasi, bepergian.copy(eskalasiAktif = false)))
        assertTrue(L.perluEskalasi(dua, bepergianAktif = false, eskalasi, bepergian.copy(eskalasiAktif = false)))
    }
}
