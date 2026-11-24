package com.fallbracelet

import com.fallbracelet.caregiver.JenisSel
import com.fallbracelet.caregiver.PasienPmo
import com.fallbracelet.caregiver.RingkasanPmo
import com.fallbracelet.caregiver.TampilanPmo
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** The PMO screen reads the relay the same way as relay/web/index.html. */
class TampilanPmoTest {
    private val sekarang = 1_800_000_000_000L

    /** One `/v1/dashboard` wearer, as the relay sends it. */
    private fun pasien(demo: Boolean = false, events: String = "[]", days: String = "[]", ringkasan: String? = null) = PasienPmo.dari(
        JSONObject(
            """{"id": 7, "name": "Ani", "lastHeartbeat": $sekarang, "offline": false, "status": {},
               "ringkasan": ${ringkasan ?: """{"tanggal": "2026-11-24", "demo": $demo, "hariKe": 58, "totalHari": 168,
                 "terakhirDibuka": null, "hariRawan": true, "terlewatBerturut": 2, "bepergianSampai": null, "at": $sekarang}"""},
               "days": $days, "events": $events}"""
        )
    )

    private fun eskalasi(id: Long, demo: Boolean = false, acked: Boolean = false, receivedAt: Long = sekarang) =
        """{"id": $id, "wearerId": 7, "type": "ESKALASI", "message": "Coba sapa", "receivedAt": $receivedAt,
           "occurredAt": $receivedAt, "ackedBy": ${if (acked) "\"Sari\"" else "null"}, "ackedAt": ${if (acked) sekarang else "null"},
           "escalations": 1, "demo": $demo,
           "detail": {"tanggal": "2026-11-23", "hariKe": 57, "terlewatBerturut": 2, "terakhirDibuka": null, "hariRawan": true}}"""

    @Test
    fun readsTheDashboard() {
        val p = pasien(events = "[${eskalasi(1)}, ${eskalasi(2, acked = true)}]")
        assertEquals("Ani", p.nama)
        assertEquals(58, p.ringkasan!!.hariKe)
        assertEquals(168, p.ringkasan!!.totalHari)
        assertEquals(2, p.eskalasi.size)
        assertEquals(listOf(1L), p.belumDilihat.map { it.id })
        val e = p.eskalasi.first { it.id == 1L }
        assertEquals(2, e.detail!!.terlewatBerturut)
        assertNull(e.detail!!.terakhirDibuka) // null = box never opened
        assertEquals(LocalDate.parse("2026-11-23"), e.detail!!.tanggal)
        assertEquals("Sari", p.eskalasi.first { it.id == 2L }.ackedBy)
    }

    @Test
    fun onlyTheCurrentModesEskalasiShow() {
        val p = pasien(demo = true, events = "[${eskalasi(1, demo = false)}, ${eskalasi(2, demo = true)}]")
        assertEquals(listOf(2L), p.eskalasi.map { it.id })
    }

    @Test
    fun ringsForRecentUnseenEskalasiOnly() {
        val p = pasien(events = "[${eskalasi(1)}, ${eskalasi(2, acked = true)}, ${eskalasi(3, demo = true)}, " +
            "${eskalasi(4, receivedAt = sekarang - 13 * 3600_000L)}]")
        val ring = p.kabar.filter { TampilanPmo.perluBerdering(it, p, sekarang) }.map { it.id }
        assertEquals(listOf(1L), ring)
    }

    @Test
    fun weekRowUsesTheWebPagesSymbols() {
        val days = """[{"tanggal": "2026-11-20", "status": "TANPA_DATA"}, {"tanggal": "2026-11-21", "status": "DIMINUM_KOTAK"},
            {"tanggal": "2026-11-22", "status": "TERLAMBAT"}, {"tanggal": "2026-11-23", "status": "TERLEWAT"},
            {"tanggal": "2026-11-24", "status": "BELUM"}]"""
        val minggu = TampilanPmo.minggu(pasien(days = days))!!
        assertEquals(7, minggu.size)
        assertEquals(LocalDate.parse("2026-11-18"), minggu.first().tanggal)
        assertEquals(listOf("", "", "–", "✓", "!", "✗", "•"), minggu.map { it.simbol })
        assertEquals(JenisSel.BURUK, minggu[5].jenis)
        assertEquals("✓", TampilanPmo.simbol("BEPERGIAN").first)
    }

    @Test
    fun noWeekRowBeforeTheFirstSummary() {
        assertNull(TampilanPmo.minggu(pasien(ringkasan = "null")))
    }

    @Test
    fun aReSendKeepsTheKnownAck() {
        val p = pasien(events = "[${eskalasi(2, acked = true)}]")
        val ulang = p.kabar.first().copy(ackedBy = null, ackedAt = null, escalations = 2)
        val baru = TampilanPmo.denganKabar(p, ulang)
        assertEquals("Sari", baru.kabar.single().ackedBy)
        assertEquals(2, baru.kabar.single().escalations)
    }

    @Test
    fun demoEndingDropsDemoEskalasi() {
        val p = pasien(demo = true, events = "[${eskalasi(1, demo = true)}]")
        val nyata = RingkasanPmo(LocalDate.parse("2026-11-24"), demo = false, hariKe = 3, totalHari = 168, at = sekarang, bepergianSampai = null)
        val baru = TampilanPmo.denganRingkasan(p, nyata, emptyList())
        assertTrue(baru.kabar.isEmpty())
        assertFalse(baru.ringkasan!!.demo)
    }

    @Test
    fun markSeenOnlyOnce() {
        val p = pasien(events = "[${eskalasi(1)}]")
        val a = TampilanPmo.denganDilihat(p, 1, "Sari", sekarang)
        val b = TampilanPmo.denganDilihat(a, 1, "Budi", sekarang + 1)
        assertEquals("Sari", b.kabar.single().ackedBy)
        assertTrue(b.belumDilihat.isEmpty())
    }
}
