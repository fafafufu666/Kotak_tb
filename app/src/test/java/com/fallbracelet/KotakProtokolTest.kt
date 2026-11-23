package com.fallbracelet

import com.fallbracelet.kotak.ConfigKotak
import com.fallbracelet.kotak.JenisKejadianKotak
import com.fallbracelet.kotak.KeadaanKotak
import com.fallbracelet.kotak.KejadianKotak
import com.fallbracelet.kotak.KotakProtokol
import com.fallbracelet.kotak.PerintahKotak
import com.fallbracelet.kotak.StatusKotak
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Protocol v2 byte layouts (PROTOCOL.md), and the Kotlin mirror against firmware/kotak_obat/ble_protocol.h. */
class KotakProtokolTest {
    private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }

    @Test fun statusFromTheBox() {
        // ALARM, no battery, lid open, clock set + config set, 3 waiting, uptime 3600 s
        val s = KotakProtokol.bacaStatus(bytes(1, 0xFF, 0, 0, 1, 0b101, 3, 0, 0x10, 0x0E, 0, 0))!!
        assertEquals(KeadaanKotak.ALARM, s.keadaan)
        assertNull(s.bateraiPersen)
        assertTrue(s.tutupTerbuka && s.jamDisetel && s.configDisetel && !s.bateraiLemah)
        assertEquals(3, s.antrean)
        assertEquals(3600L, s.uptimeDetik)
    }

    @Test fun eventFromTheBox() {
        // seq 0xFFFFFFFE (unsigned), LID_OPEN, no battery, epoch 0x68FC8360, during the alarm
        val e = KotakProtokol.bacaKejadian(bytes(0xFE, 0xFF, 0xFF, 0xFF, 1, 0xFF, 0x60, 0x83, 0xFC, 0x68, 0, 0, 1, 0, 0, 0))!!
        assertEquals(0xFFFFFFFEL, e.seq)
        assertEquals(JenisKejadianKotak.LID_OPEN, e.jenis)
        assertNull(e.bateraiPersen)
        assertEquals(0x68FC8360L, e.epochDetik)
        assertTrue(e.saatAlarm)
    }

    @Test fun boxSideAndPhoneSideAgree() {
        val st = StatusKotak(KeadaanKotak.CARI, 76, 3950, false, true, true, false, 513, 99_999)
        assertEquals(st, KotakProtokol.bacaStatus(KotakProtokol.buatStatus(st)))
        val e = KejadianKotak(4_000_000_000L, JenisKejadianKotak.LID_CLOSE, null, 1_795_400_000, menitTerbuka = 3)
        assertEquals(e, KotakProtokol.bacaKejadian(KotakProtokol.buatKejadian(e)))
        val c = ConfigKotak(jam = 7, menit = 30, durasiAlarmMenit = 10, jendelaAwalMenit = 120, nama = "Kotak-1A2B")
        assertEquals(c, KotakProtokol.bacaConfig(KotakProtokol.tulisConfig(c)))
        assertEquals(42L, KotakProtokol.bacaAck(KotakProtokol.tulisAck(42)))
        assertEquals(1_795_400_000L to 420, KotakProtokol.bacaWaktu(KotakProtokol.tulisWaktu(1_795_400_000, 420)))
    }

    @Test fun configLimits() {
        val unset = KotakProtokol.bacaConfig(KotakProtokol.buatConfig(ConfigKotak(jam = null, menit = 0, durasiAlarmMenit = 0)))!!
        assertNull(unset.jam)
        assertEquals(1, unset.durasiAlarmMenit)
        assertEquals(60, KotakProtokol.bacaConfig(KotakProtokol.buatConfig(ConfigKotak(7, 0, 200)))!!.durasiAlarmMenit)
        val panjang = ConfigKotak(7, 0, 10, nama = "ÉÉÉÉÉÉÉÉÉÉ") // 20 bytes in UTF-8, the field holds 19
        assertEquals("ÉÉÉÉÉÉÉÉÉ", KotakProtokol.bacaConfig(KotakProtokol.buatConfig(panjang))!!.nama)
        assertEquals(255, KotakProtokol.bacaConfig(KotakProtokol.buatConfig(ConfigKotak(7, 0, 10, jendelaAwalMenit = 999)))!!.jendelaAwalMenit)
    }

    @Test fun shortPacketsAreRejected() {
        assertNull(KotakProtokol.bacaStatus(ByteArray(11)))
        assertNull(KotakProtokol.bacaKejadian(ByteArray(15)))
        assertNull(KotakProtokol.bacaConfig(ByteArray(23)))
        assertNull(KotakProtokol.bacaWaktu(ByteArray(5)))
        assertFalse(KotakProtokol.tulisAck(1).size != 4)
    }

    /** The firmware header and this mirror must say the same thing. */
    @Test fun matchesTheFirmwareHeader() {
        val h = header().readText()
        fun def(name: String): String = Regex("#define\\s+$name\\s+(\\S+)").find(h)?.groupValues?.get(1) ?: error("$name missing")
        fun num(name: String): Int = def(name).let { if (it.startsWith("0x")) it.drop(2).toInt(16) else it.toInt() }
        assertEquals(KotakProtokol.VERSI, num("PROTOCOL_VERSION"))
        assertEquals("\"${KotakProtokol.AWALAN_NAMA}\"", def("DEVICE_NAME_PREFIX"))
        mapOf("UUID_SERVICE" to KotakProtokol.LAYANAN, "UUID_STATUS" to KotakProtokol.STATUS, "UUID_EVENT" to KotakProtokol.EVENT,
            "UUID_EVENT_ACK" to KotakProtokol.EVENT_ACK, "UUID_TIME" to KotakProtokol.TIME, "UUID_CONFIG" to KotakProtokol.CONFIG,
            "UUID_COMMAND" to KotakProtokol.COMMAND).forEach { (n, u) -> assertEquals(n, "\"$u\"", def(n)) }
        assertEquals(KotakProtokol.UKURAN_STATUS, num("STATUS_SIZE"))
        assertEquals(KotakProtokol.UKURAN_EVENT, num("EVENT_SIZE"))
        assertEquals(KotakProtokol.UKURAN_ACK, num("EVENT_ACK_SIZE"))
        assertEquals(KotakProtokol.UKURAN_TIME, num("TIME_SIZE"))
        assertEquals(KotakProtokol.UKURAN_CONFIG, num("CONFIG_SIZE"))
        assertEquals(KotakProtokol.BYTE_NAMA, num("NAME_BYTES"))
        assertEquals(KotakProtokol.TANPA_BATERAI, num("BATTERY_NONE"))
        assertEquals(KotakProtokol.JAM_BELUM_DISETEL, num("DOSE_HOUR_UNSET"))
        assertEquals(KotakProtokol.FLAG_JAM_DISETEL, num("FLAG_CLOCK_SET"))
        assertEquals(KotakProtokol.FLAG_BATERAI_LEMAH, num("FLAG_LOW_BATT"))
        assertEquals(KotakProtokol.FLAG_CONFIG_DISETEL, num("FLAG_CONFIG_SET"))
        assertEquals(KotakProtokol.ALARM_MENIT_MIN, num("ALARM_MIN_MIN"))
        assertEquals(KotakProtokol.ALARM_MENIT_MAKS, num("ALARM_MIN_MAX"))
        assertEquals(KeadaanKotak.SIAGA.kode, num("STATE_IDLE"))
        assertEquals(KeadaanKotak.ALARM.kode, num("STATE_ALARM"))
        assertEquals(KeadaanKotak.CARI.kode, num("STATE_FIND"))
        assertEquals(JenisKejadianKotak.LID_OPEN.kode, num("EVT_LID_OPEN"))
        assertEquals(JenisKejadianKotak.LID_CLOSE.kode, num("EVT_LID_CLOSE"))
        assertEquals(JenisKejadianKotak.BOX_ON.kode, num("EVT_BOX_ON"))
        assertEquals(JenisKejadianKotak.LOW_BATTERY.kode, num("EVT_LOW_BATTERY"))
        assertEquals(JenisKejadianKotak.TEST.kode, num("EVT_TEST"))
        assertEquals(PerintahKotak.FIND.toInt(), num("CMD_FIND"))
        assertEquals(PerintahKotak.TEST.toInt(), num("CMD_TEST"))
        assertEquals(PerintahKotak.DEMO_ALARM.toInt(), num("CMD_DEMO_ALARM"))
        assertEquals(PerintahKotak.STOP_ALARM.toInt(), num("CMD_STOP_ALARM"))
    }

    private fun header(): File {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null) {
            val f = File(dir, "firmware/kotak_obat/ble_protocol.h")
            if (f.exists()) return f
            dir = dir.parentFile
        }
        error("firmware/kotak_obat/ble_protocol.h not found")
    }
}
