package com.fallbracelet

import com.fallbracelet.adherence.AturanDosis
import com.fallbracelet.adherence.AturanEskalasi
import com.fallbracelet.adherence.AturanModeBepergian
import com.fallbracelet.adherence.JamMinumBerlaku
import com.fallbracelet.adherence.ModeBepergian
import com.fallbracelet.adherence.PengingatUlang
import com.fallbracelet.adherence.PeriodeBepergian
import com.fallbracelet.adherence.RencanaEskalasi
import com.fallbracelet.adherence.RingkasanHarian
import com.fallbracelet.adherence.StatusDosis.BEPERGIAN
import com.fallbracelet.adherence.StatusDosis.DIKONFIRMASI_MANUAL
import com.fallbracelet.adherence.StatusDosis.TERLEWAT
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** Travel mode (PRD P9): on for N days, off by itself, manual confirmation counts, escalation per the rules file. */
class ModeBepergianTest {
    private val aturan = AturanModeBepergian(maksHari = 7, eskalasiAktif = true)
    private val senin = LocalDate.of(2026, 11, 23)

    @Test fun onForNDaysThenOffByItself() {
        val p = ModeBepergian.nyalakan(senin, 3, aturan, emptyList())
        assertEquals(listOf(PeriodeBepergian(senin, senin.plusDays(2))), p)
        assertTrue(ModeBepergian.aktifPada(senin.plusDays(2), p))
        assertFalse(ModeBepergian.aktifPada(senin.plusDays(3), p)) // switched itself off
        assertEquals(3, ModeBepergian.sisaHari(senin, p))
        assertEquals(1, ModeBepergian.sisaHari(senin.plusDays(2), p))
        assertEquals(0, ModeBepergian.sisaHari(senin.plusDays(3), p))
    }

    @Test fun neverLongerThanTheRulesAllow() {
        assertEquals(senin.plusDays(6), ModeBepergian.nyalakan(senin, 30, aturan, emptyList()).single().sampai)
        assertEquals(senin, ModeBepergian.nyalakan(senin, 0, aturan, emptyList()).single().sampai)
    }

    @Test fun switchingOffKeepsThePastDays() {
        val p = ModeBepergian.nyalakan(senin, 5, aturan, emptyList())
        val rabu = senin.plusDays(2)
        val mati = ModeBepergian.matikan(rabu, p)
        assertEquals(listOf(PeriodeBepergian(senin, senin.plusDays(1))), mati)
        assertNull(ModeBepergian.aktif(rabu, mati))
        assertTrue(ModeBepergian.aktifPada(senin.plusDays(1), mati))
        assertEquals(emptyList<PeriodeBepergian>(), ModeBepergian.matikan(senin, p)) // started today: gone
    }

    @Test fun switchingOnAgainReplacesTodaysStretch() {
        val p = ModeBepergian.nyalakan(senin, 5, aturan, emptyList())
        val lagi = ModeBepergian.nyalakan(senin.plusDays(1), 2, aturan, p)
        assertEquals(listOf(PeriodeBepergian(senin, senin), PeriodeBepergian(senin.plusDays(1), senin.plusDays(2))), lagi)
    }

    @Test fun confirmedByHandWhileTravellingIsBepergian() {
        val zona = ZoneId.of("Asia/Jakarta")
        val dosis = AturanDosis(120, 60, 240, PengingatUlang(2, 30), 10)
        val jam7 = listOf(JamMinumBerlaku(senin, LocalTime.of(7, 0)))
        val p = ModeBepergian.nyalakan(senin.plusDays(1), 1, aturan, emptyList())
        val t = { d: LocalDate -> LocalDateTime.of(d, LocalTime.of(7, 10)).atZone(zona).toInstant() }
        val hari = RingkasanHarian.hari(
            senin, senin.plusDays(2), jam7, zona, t(senin.plusDays(3)), emptyList(), listOf(senin, senin.plusDays(1)).map(t), dosis,
            bepergianPada = { ModeBepergian.aktifPada(it, p) },
        )
        assertEquals(listOf(DIKONFIRMASI_MANUAL, BEPERGIAN, TERLEWAT), hari.map { it.status })
    }

    @Test fun escalationWhileTravellingFollowsTheRulesFile() {
        val zona = ZoneId.of("Asia/Jakarta")
        val dosis = AturanDosis(120, 60, 240, PengingatUlang(2, 30), 10)
        val jam7 = listOf(JamMinumBerlaku(senin, LocalTime.of(7, 0)))
        val p = ModeBepergian.nyalakan(senin, 2, aturan, emptyList())
        val sekarang = LocalDateTime.of(senin.plusDays(1), LocalTime.of(12, 0)).atZone(zona).toInstant()
        val hari = RingkasanHarian.hari(
            senin, senin.plusDays(1), jam7, zona, sekarang, emptyList(), emptyList(), dosis,
            bepergianPada = { ModeBepergian.aktifPada(it, p) },
        )
        val esk = AturanEskalasi(2, 30, 3)
        // Missed twice while travelling: sent when eskalasi_aktif is true, not when false
        assertEquals(senin.plusDays(1), RencanaEskalasi.rencanakan(hari, emptySet(), esk, aturan) { ModeBepergian.aktifPada(it, p) }.kirim)
        assertNull(RencanaEskalasi.rencanakan(hari, emptySet(), esk, aturan.copy(eskalasiAktif = false)) { ModeBepergian.aktifPada(it, p) }.kirim)
    }
}
