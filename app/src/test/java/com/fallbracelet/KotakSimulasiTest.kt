package com.fallbracelet

import com.fallbracelet.kotak.ConfigKotak
import com.fallbracelet.kotak.FlashKotak
import com.fallbracelet.kotak.JenisKejadianKotak
import com.fallbracelet.kotak.KeadaanKotak
import com.fallbracelet.kotak.KejadianKotak
import com.fallbracelet.kotak.KotakProtokol as P
import com.fallbracelet.kotak.KotakSimulasi
import com.fallbracelet.kotak.PerintahKotak
import com.fallbracelet.kotak.PolaBunyi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.random.Random

/** The simulated ESP32 behaves like the box firmware should (PRD 5.2 F1–F6), over protocol v2 bytes. */
class KotakSimulasiTest {
    private class Flash : FlashKotak {
        var isi: String? = null
        override fun muat() = isi
        override fun simpan(isi: String) { this.isi = isi }
    }

    /** The phone's side, at byte level. */
    private inner class Hp(val k: KotakSimulasi) {
        val kejadian = ArrayList<KejadianKotak>()
        val status = ArrayList<com.fallbracelet.kotak.StatusKotak>()
        val bunyi = ArrayList<PolaBunyi>()
        init {
            k.keluar = { uuid, b ->
                when (uuid) {
                    P.EVENT -> kejadian += P.bacaKejadian(b)!!
                    P.STATUS -> status += P.bacaStatus(b)!!
                }
            }
            k.bunyi = { bunyi += it }
        }
        /** Connect, set the clock to [epochS] (WIB), subscribe, like the app does. */
        fun sambung(epochS: Long, t: Long) {
            k.sambung()
            k.tulis(P.TIME, P.tulisWaktu(epochS, 420), t)
            k.langganan(P.STATUS, true, t)
            k.langganan(P.EVENT, true, t)
        }
        fun ack(seq: Long, t: Long) = k.tulis(P.EVENT_ACK, P.tulisAck(seq), t)
        /** ACK everything that comes, the way the app does. */
        fun ackSemua(t: Long) { while (true) { val e = kejadian.lastOrNull() ?: return; val n = kejadian.size; ack(e.seq, t); if (kejadian.size == n) return } }
    }

    private val wib = ZoneOffset.ofHours(7)
    private fun epoch(y: Int, mo: Int, d: Int, h: Int, mi: Int) = LocalDateTime.of(y, mo, d, h, mi).toEpochSecond(wib)
    private val t0 = 1_000_000L // real clock, ms

    private fun kotakBaru(flash: Flash = Flash()) = KotakSimulasi(flash, Random(7)).apply { hidup(t0) }

    @Test fun freshBoxStartsAtARandomSeqAndSaysItIsOn() {
        val k = kotakBaru()
        val hp = Hp(k)
        hp.sambung(epoch(2026, 11, 24, 6, 0), t0)
        val on = hp.kejadian.single()
        assertEquals(JenisKejadianKotak.BOX_ON, on.jenis)
        assertEquals(0L, on.epochDetik) // queued before the clock was set (F3)
        assertNotEquals(1L, on.seq)
        assertTrue(k.nama.startsWith("Kotak-"))
    }

    @Test fun lidOpeningGoesOutWithTheBoxTimeAndLeavesOnlyAfterTheAck() {
        val k = kotakBaru()
        val hp = Hp(k)
        val e0 = epoch(2026, 11, 24, 7, 5)
        hp.sambung(e0, t0)
        hp.ackSemua(t0)
        k.buka(t0 + 1_000)
        val buka = hp.kejadian.last()
        assertEquals(JenisKejadianKotak.LID_OPEN, buka.jenis)
        assertEquals(e0 + 1, buka.epochDetik)
        assertEquals(1, P.bacaStatus(k.baca(P.STATUS, t0 + 1_000)!!)!!.antrean)
        hp.ack(buka.seq + 1, t0 + 1_100) // wrong seq: ignored
        assertEquals(1, P.bacaStatus(k.baca(P.STATUS, t0 + 1_100)!!)!!.antrean)
        hp.ack(buka.seq, t0 + 1_200)
        assertEquals(0, P.bacaStatus(k.baca(P.STATUS, t0 + 1_200)!!)!!.antrean)
    }

    @Test fun noAckMeansTheSameEventAgain() {
        val k = kotakBaru()
        val hp = Hp(k)
        hp.sambung(epoch(2026, 11, 24, 7, 0), t0)
        val pertama = hp.kejadian.single()
        k.detak(t0 + KotakSimulasi.ULANG_MS - 1)
        assertEquals(1, hp.kejadian.size)
        k.detak(t0 + KotakSimulasi.ULANG_MS)
        assertEquals(listOf(pertama, pertama), hp.kejadian)
    }

    /** PRD scenario 3: Bluetooth off, open the box, back on: the event arrives with its real time. */
    @Test fun openedWhileOutOfRangeArrivesLaterWithItsOwnTime() {
        val k = kotakBaru()
        val hp = Hp(k)
        val e0 = epoch(2026, 11, 24, 6, 50)
        hp.sambung(e0, t0)
        hp.ackSemua(t0)
        k.putus()
        k.buka(t0 + 10 * 60_000) // 07.00, phone away
        k.tutup(t0 + 11 * 60_000)
        val sebelum = hp.kejadian.size
        k.detak(t0 + 30 * 60_000)
        assertEquals(sebelum, hp.kejadian.size) // nothing goes out while disconnected
        hp.sambung(e0 + 40 * 60, t0 + 40 * 60_000) // 07.30, back in range
        val buka = hp.kejadian.last()
        assertEquals(JenisKejadianKotak.LID_OPEN, buka.jenis)
        assertEquals(e0 + 10 * 60, buka.epochDetik)
        hp.ack(buka.seq, t0 + 40 * 60_000)
        val tutup = hp.kejadian.last()
        assertEquals(JenisKejadianKotak.LID_CLOSE, tutup.jenis)
        assertEquals(1, tutup.menitTerbuka)
    }

    @Test fun ringsAtTheDoseTimeUntilTheLidOpens() {
        val k = kotakBaru()
        val hp = Hp(k)
        hp.sambung(epoch(2026, 11, 24, 6, 59), t0)
        k.tulis(P.CONFIG, P.tulisConfig(ConfigKotak(7, 0, 10, nama = "dari HP")), t0)
        assertEquals(k.nama, P.bacaConfig(k.baca(P.CONFIG, t0)!!)!!.nama) // the box keeps its own name
        k.detak(t0 + 30_000)
        assertFalse(hp.bunyi.contains(PolaBunyi.ALARM))
        k.detak(t0 + 60_000) // 07.00
        assertEquals(KeadaanKotak.ALARM, P.bacaStatus(k.baca(P.STATUS, t0 + 60_000)!!)!!.keadaan)
        assertTrue(hp.bunyi.contains(PolaBunyi.ALARM))
        hp.ackSemua(t0 + 60_000)
        k.buka(t0 + 120_000)
        assertTrue(hp.kejadian.last().saatAlarm)
        assertEquals(KeadaanKotak.SIAGA, P.bacaStatus(k.baca(P.STATUS, t0 + 120_000)!!)!!.keadaan)
        k.detak(t0 + 180_000)
        assertEquals(KeadaanKotak.SIAGA, P.bacaStatus(k.baca(P.STATUS, t0 + 180_000)!!)!!.keadaan) // not again today
    }

    @Test fun stopsRingingAfterTheAlarmLength() {
        val k = kotakBaru()
        val hp = Hp(k)
        hp.sambung(epoch(2026, 11, 24, 7, 0), t0)
        k.tulis(P.CONFIG, P.tulisConfig(ConfigKotak(7, 0, 10)), t0)
        k.detak(t0)
        assertEquals(KeadaanKotak.ALARM, P.bacaStatus(k.baca(P.STATUS, t0)!!)!!.keadaan)
        k.detak(t0 + 10 * 60_000)
        assertEquals(KeadaanKotak.SIAGA, P.bacaStatus(k.baca(P.STATUS, t0 + 10 * 60_000)!!)!!.keadaan)
    }

    @Test fun clockMovedIntoTheWindowStillRings() {
        val k = kotakBaru()
        val hp = Hp(k)
        // The phone moves the clock to 07.05 (e.g. demo): inside the alarm time, lid not opened → rings
        hp.sambung(epoch(2026, 11, 24, 6, 0), t0)
        k.tulis(P.CONFIG, P.tulisConfig(ConfigKotak(7, 0, 10, jendelaAwalMenit = 120)), t0)
        k.tulis(P.TIME, P.tulisWaktu(epoch(2026, 11, 24, 7, 5), 420), t0 + 1)
        k.detak(t0 + 2)
        assertEquals(KeadaanKotak.ALARM, P.bacaStatus(k.baca(P.STATUS, t0 + 2)!!)!!.keadaan)
    }

    @Test fun openedInsideTheDoseWindowMeansNoRing() {
        val k = kotakBaru()
        val hp = Hp(k)
        // Window opens 05.00 for a 07.00 dose: opened at 06.40 counts as taken, so no ring at 07.00
        hp.sambung(epoch(2026, 11, 24, 6, 40), t0)
        k.tulis(P.CONFIG, P.tulisConfig(ConfigKotak(7, 0, 10, jendelaAwalMenit = 120)), t0)
        k.buka(t0 + 1); k.tutup(t0 + 2)
        k.detak(t0 + 20 * 60_000)
        assertEquals(KeadaanKotak.SIAGA, P.bacaStatus(k.baca(P.STATUS, t0 + 20 * 60_000)!!)!!.keadaan)
        // Next day, opened at 04.30 (before the window): it still rings at 07.00
        k.tulis(P.TIME, P.tulisWaktu(epoch(2026, 11, 25, 4, 30), 420), t0 + 21 * 60_000)
        k.buka(t0 + 21 * 60_000 + 1); k.tutup(t0 + 21 * 60_000 + 2)
        k.tulis(P.TIME, P.tulisWaktu(epoch(2026, 11, 25, 7, 0), 420), t0 + 22 * 60_000)
        k.detak(t0 + 22 * 60_000 + 1)
        assertEquals(KeadaanKotak.ALARM, P.bacaStatus(k.baca(P.STATUS, t0 + 22 * 60_000 + 1)!!)!!.keadaan)
    }

    @Test fun commands() {
        val k = kotakBaru()
        val hp = Hp(k)
        hp.sambung(epoch(2026, 11, 24, 12, 0), t0)
        hp.ackSemua(t0)
        k.tulis(P.COMMAND, byteArrayOf(PerintahKotak.FIND), t0)
        assertEquals(PolaBunyi.CARI, hp.bunyi.last())
        assertEquals(KeadaanKotak.CARI, P.bacaStatus(k.baca(P.STATUS, t0)!!)!!.keadaan)
        k.tulis(P.COMMAND, byteArrayOf(PerintahKotak.TEST), t0 + 10)
        assertEquals(JenisKejadianKotak.TEST, hp.kejadian.last().jenis)
        hp.ackSemua(t0 + 10)
        k.tulis(P.COMMAND, byteArrayOf(PerintahKotak.DEMO_ALARM), t0 + 20)
        assertEquals(KeadaanKotak.ALARM, P.bacaStatus(k.baca(P.STATUS, t0 + 20)!!)!!.keadaan)
        k.tulis(P.COMMAND, byteArrayOf(PerintahKotak.STOP_ALARM), t0 + 30)
        assertEquals(KeadaanKotak.SIAGA, P.bacaStatus(k.baca(P.STATUS, t0 + 4_000)!!)!!.keadaan)
        assertFalse(k.tulis(P.COMMAND, byteArrayOf(0x7F), t0 + 40))
    }

    @Test fun flashKeepsTheQueueCounterAndDoseTimeOverARestart() {
        val flash = Flash()
        val k = kotakBaru(flash)
        val hp = Hp(k)
        hp.sambung(epoch(2026, 11, 24, 7, 0), t0)
        k.tulis(P.CONFIG, P.tulisConfig(ConfigKotak(7, 30, 15)), t0)
        k.putus()
        k.buka(t0 + 1_000)
        val nama = k.nama
        val lagi = KotakSimulasi(flash, Random(99)).apply { hidup(t0 + 5_000) } // reboot
        val hp2 = Hp(lagi)
        hp2.sambung(epoch(2026, 11, 24, 7, 10), t0 + 6_000)
        assertEquals(nama, lagi.nama)
        assertEquals(ConfigKotak(7, 30, 15, nama = nama), P.bacaConfig(lagi.baca(P.CONFIG, t0)!!)!!)
        // BOX_ON (first boot), LID_OPEN, BOX_ON (reboot), in order, with increasing seq
        val seqs = ArrayList<Long>()
        while (true) {
            val e = hp2.kejadian.lastOrNull() ?: break
            if (seqs.lastOrNull() == e.seq) break
            seqs += e.seq
            hp2.ack(e.seq, t0 + 6_000)
        }
        assertEquals(listOf(JenisKejadianKotak.BOX_ON, JenisKejadianKotak.LID_OPEN, JenisKejadianKotak.BOX_ON), hp2.kejadian.map { it.jenis })
        assertEquals(seqs.sorted(), seqs)
    }

    @Test fun statusIsPushedWhenSomethingChanges() {
        val k = kotakBaru()
        val hp = Hp(k)
        hp.sambung(epoch(2026, 11, 24, 12, 0), t0)
        hp.ackSemua(t0)
        val n = hp.status.size
        k.detak(t0 + 5_000) // only uptime changed
        assertEquals(n, hp.status.size)
        k.buka(t0 + 6_000)
        assertTrue(hp.status.last().tutupTerbuka)
    }
}
