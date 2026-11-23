package com.fallbracelet

import com.fallbracelet.adherence.AturanDosis
import com.fallbracelet.adherence.AturanEskalasi
import com.fallbracelet.adherence.AturanHariRawan
import com.fallbracelet.adherence.AturanModeBepergian
import com.fallbracelet.adherence.AturanPengobatan
import com.fallbracelet.adherence.AturanPola
import com.fallbracelet.adherence.AturanProgram
import com.fallbracelet.adherence.Gabungan
import com.fallbracelet.adherence.HasilHariRawan
import com.fallbracelet.adherence.JadwalPengingat as J
import com.fallbracelet.adherence.JenisPemicu
import com.fallbracelet.adherence.LogikaKepatuhan
import com.fallbracelet.adherence.Pemicu
import com.fallbracelet.adherence.PengingatUlang
import com.fallbracelet.adherence.StatusDosis.BELUM
import com.fallbracelet.adherence.StatusDosis.DIKONFIRMASI_MANUAL
import com.fallbracelet.adherence.StatusDosis.DIMINUM_KOTAK
import com.fallbracelet.adherence.StatusDosis.TERLAMBAT
import com.fallbracelet.adherence.StatusDosis.TERLEWAT
import com.fallbracelet.adherence.TeksNotifikasi
import com.fallbracelet.adherence.Tindakan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** When reminders fire and what they do (P5, PRD 7.2–7.3). Fixed test values, not the JSON file. */
class JadwalPengingatTest {

    private val zona = ZoneId.of("Asia/Jakarta")
    private val dosis = AturanDosis(
        jendelaAwalMenit = 120, batasTerlambatMenit = 60, batasTerlewatMenit = 240,
        pengingatUlang = PengingatUlang(jumlah = 2, jarakMenit = 30), durasiAlarmKotakMenit = 10,
    )
    private val aturan = AturanProgram(
        statusNilai = "TES",
        pengobatan = AturanPengobatan(180, 56),
        dosis = dosis,
        hariRawan = AturanHariRawan(listOf(14..28), emptyList(), AturanPola(7, 1), Gabungan.SALAH_SATU, maksPerHari = 2),
        eskalasi = AturanEskalasi(2, 30, 3),
        modeBepergian = AturanModeBepergian(7, true),
        pesan = emptyList(),
        notifikasi = TeksNotifikasi("a", "b", "c"),
    )
    private val tanggal = LocalDate.of(2026, 11, 23)
    private val jadwal = LogikaKepatuhan.jadwal(tanggal, LocalTime.of(7, 0), zona, dosis)
    private val besok = LogikaKepatuhan.jadwal(tanggal.plusDays(1), LocalTime.of(7, 0), zona, dosis)
    private val rawan = HasilHariRawan(rawan = true, olehWaktu = true, olehPola = false, jumlahTerlewat = 0)
    private val tidakRawan = HasilHariRawan(rawan = false, olehWaktu = false, olehPola = false, jumlahTerlewat = 0)

    private fun t(jam: Int, menit: Int = 0, hari: Long = 0): Instant =
        LocalDateTime.of(tanggal.plusDays(hari), LocalTime.of(jam, menit)).atZone(zona).toInstant()

    private fun pemicu(jenis: JenisPemicu, urutan: Int = 0, waktu: Instant = t(7)) = Pemicu(tanggal, jenis, urutan, waktu)

    @Test fun oneDoseHasJamMinumLateAndTheLaterRepeats() {
        val p = J.pemicuDosis(jadwal, dosis)
        assertEquals(listOf(JenisPemicu.JAM_MINUM, JenisPemicu.TERLAMBAT, JenisPemicu.PENGINGAT_ULANG), p.map { it.jenis })
        assertEquals(listOf(t(7), t(8), t(8, 30)), p.map { it.waktu })
    }

    @Test fun noRepeatsWhenTheCountIsZero() {
        val tanpa = dosis.copy(pengingatUlang = PengingatUlang(0, 30))
        assertEquals(listOf(JenisPemicu.JAM_MINUM, JenisPemicu.TERLAMBAT), J.pemicuDosis(jadwal, tanpa).map { it.jenis })
    }

    @Test fun nothingDueBeforeJamMinumAndTheNextIsJamMinum() {
        val r = J.rencanakan(listOf(jadwal, besok), emptyList(), emptySet(), t(6, 59), dosis)
        assertNull(r.dijalankan)
        assertEquals(t(7), r.berikutnya)
    }

    @Test fun jamMinumIsDueAtItsMinute() {
        val r = J.rencanakan(listOf(jadwal), emptyList(), emptySet(), t(7), dosis)
        assertEquals(JenisPemicu.JAM_MINUM, r.dijalankan?.jenis)
        assertEquals(t(8), r.berikutnya)
    }

    @Test fun handledMomentsDoNotFireAgain() {
        val sudah = setOf(pemicu(JenisPemicu.JAM_MINUM).kunci)
        val r = J.rencanakan(listOf(jadwal), emptyList(), sudah, t(7, 30), dosis)
        assertNull(r.dijalankan)
        assertEquals(t(8), r.berikutnya)
    }

    @Test fun afterALongGapOnlyTheLatestFiresAndTheRestAreSkipped() {
        val r = J.rencanakan(listOf(jadwal, besok), emptyList(), emptySet(), t(9), dosis)
        assertEquals(JenisPemicu.PENGINGAT_ULANG, r.dijalankan?.jenis)
        assertEquals(listOf(JenisPemicu.JAM_MINUM, JenisPemicu.TERLAMBAT), r.dilewati.map { it.jenis })
        assertEquals("tomorrow's jam_minum", t(7, hari = 1), r.berikutnya)
    }

    @Test fun aSnoozeIsPlannedLikeAnyOtherMoment() {
        val tunda = pemicu(JenisPemicu.TUNDA, 1, t(7, 30))
        val sudah = setOf(pemicu(JenisPemicu.JAM_MINUM).kunci)
        assertEquals(t(7, 30), J.rencanakan(listOf(jadwal), listOf(tunda), sudah, t(7, 10), dosis).berikutnya)
        assertEquals(tunda, J.rencanakan(listOf(jadwal), listOf(tunda), sudah, t(7, 30), dosis).dijalankan)
    }

    @Test fun keysDifferByDoseMomentAndCount() {
        val kunci = setOf(
            pemicu(JenisPemicu.JAM_MINUM).kunci, pemicu(JenisPemicu.TERLAMBAT).kunci,
            pemicu(JenisPemicu.TUNDA, 1).kunci, pemicu(JenisPemicu.TUNDA, 2).kunci,
            Pemicu(tanggal.plusDays(1), JenisPemicu.JAM_MINUM, 0, t(7, hari = 1)).kunci,
        )
        assertEquals(5, kunci.size)
    }

    // --- What a moment does -----------------------------------------------------------------

    @Test fun nothingOnceTheDoseIsTakenOrMissed() {
        for (s in listOf(DIMINUM_KOTAK, DIKONFIRMASI_MANUAL, TERLEWAT)) {
            assertEquals(Tindakan.TIDAK_ADA, J.tindakan(pemicu(JenisPemicu.JAM_MINUM), s, rawan, 0, aturan, adaPesan = true))
        }
    }

    @Test fun ordinaryReminderOnANormalDay() {
        assertEquals(Tindakan.NOTIF_JAM_MINUM, J.tindakan(pemicu(JenisPemicu.JAM_MINUM), BELUM, tidakRawan, 0, aturan, true))
        assertEquals(Tindakan.NOTIF_PENGINGAT_ULANG, J.tindakan(pemicu(JenisPemicu.TERLAMBAT), TERLAMBAT, tidakRawan, 0, aturan, true))
        assertEquals(Tindakan.NOTIF_PENGINGAT_ULANG, J.tindakan(pemicu(JenisPemicu.PENGINGAT_ULANG, 1), TERLAMBAT, tidakRawan, 0, aturan, true))
    }

    @Test fun specialReminderAtJamMinumAndWhenLateOnAVulnerableDay() {
        assertEquals(Tindakan.PESAN_KHUSUS, J.tindakan(pemicu(JenisPemicu.JAM_MINUM), BELUM, rawan, 0, aturan, true))
        assertEquals(Tindakan.PESAN_KHUSUS, J.tindakan(pemicu(JenisPemicu.TERLAMBAT), TERLAMBAT, rawan, 1, aturan, true))
        assertEquals(
            "later repeats stay ordinary",
            Tindakan.NOTIF_PENGINGAT_ULANG, J.tindakan(pemicu(JenisPemicu.PENGINGAT_ULANG, 1), TERLAMBAT, rawan, 0, aturan, true),
        )
    }

    @Test fun theDailyLimitFallsBackToTheOrdinaryReminder() {
        assertEquals(Tindakan.NOTIF_PENGINGAT_ULANG, J.tindakan(pemicu(JenisPemicu.TERLAMBAT), TERLAMBAT, rawan, 2, aturan, true))
    }

    @Test fun withoutAMessageTheOrdinaryReminderIsUsed() {
        assertEquals(Tindakan.NOTIF_JAM_MINUM, J.tindakan(pemicu(JenisPemicu.JAM_MINUM), BELUM, rawan, 0, aturan, adaPesan = false))
    }

    @Test fun aSnoozeShowsTheSpecialReminderAgainEvenPastTheLimit() {
        assertEquals(Tindakan.PESAN_KHUSUS, J.tindakan(pemicu(JenisPemicu.TUNDA, 1), BELUM, rawan, 2, aturan, true))
        assertEquals(Tindakan.TIDAK_ADA, J.tindakan(pemicu(JenisPemicu.TUNDA, 1), DIMINUM_KOTAK, rawan, 0, aturan, true))
    }

    @Test fun noLateReminderWhenRepeatsAreOff() {
        val tanpa = aturan.copy(dosis = dosis.copy(pengingatUlang = PengingatUlang(0, 30)))
        assertEquals(Tindakan.TIDAK_ADA, J.tindakan(pemicu(JenisPemicu.TERLAMBAT), TERLAMBAT, tidakRawan, 0, tanpa, true))
    }
}
