package com.fallbracelet.kotak

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

/**
 * The medicine box's BLE contract, protocol version 2: mirror of
 * `firmware/kotak_obat/ble_protocol.h` (see PROTOCOL.md). The phone side parses STATUS, EVENT and
 * CONFIG and builds ACK, TIME, CONFIG and COMMAND; the box side (`buat*` and `baca*` for what the
 * phone writes) is what the firmware does, used by [KotakSimulasi] and the tests.
 */
object KotakProtokol {
    const val VERSI = 2
    const val AWALAN_NAMA = "Kotak"

    val LAYANAN: UUID = uuid(1)
    val STATUS: UUID = uuid(2)
    val EVENT: UUID = uuid(3)
    val EVENT_ACK: UUID = uuid(4)
    val TIME: UUID = uuid(5)
    val CONFIG: UUID = uuid(6)
    val COMMAND: UUID = uuid(7)

    private fun uuid(n: Int): UUID = UUID.fromString("5b0a%04x-1f3c-4d2e-8a71-6b6f74616b00".format(n))

    const val UKURAN_STATUS = 12
    const val UKURAN_EVENT = 16
    const val UKURAN_ACK = 4
    const val UKURAN_TIME = 6
    const val UKURAN_CONFIG = 24
    const val BYTE_NAMA = 19
    const val AWAL_NAMA = 5

    /** Battery % on a box without a battery (USB power). */
    const val TANPA_BATERAI = 0xFF
    const val JAM_BELUM_DISETEL = 0xFF
    const val ALARM_MENIT_MIN = 1
    const val ALARM_MENIT_MAKS = 60

    const val FLAG_JAM_DISETEL = 0x01
    const val FLAG_BATERAI_LEMAH = 0x02
    const val FLAG_CONFIG_DISETEL = 0x04

    private fun le(n: Int) = ByteBuffer.allocate(n).order(ByteOrder.LITTLE_ENDIAN)
    private fun le(b: ByteArray) = ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN)
    private fun u8(b: ByteArray, i: Int) = b[i].toInt() and 0xFF

    // --- Phone side ----------------------------------------------------------------------------

    fun bacaStatus(b: ByteArray): StatusKotak? {
        if (b.size < UKURAN_STATUS) return null
        val buf = le(b)
        val flags = u8(b, 5)
        return StatusKotak(
            keadaan = KeadaanKotak.dari(u8(b, 0)),
            bateraiPersen = u8(b, 1).takeIf { it != TANPA_BATERAI },
            bateraiMv = buf.getShort(2).toInt() and 0xFFFF,
            tutupTerbuka = u8(b, 4) == 1,
            jamDisetel = flags and FLAG_JAM_DISETEL != 0,
            bateraiLemah = flags and FLAG_BATERAI_LEMAH != 0,
            configDisetel = flags and FLAG_CONFIG_DISETEL != 0,
            antrean = buf.getShort(6).toInt() and 0xFFFF,
            uptimeDetik = buf.getInt(8).toLong() and 0xFFFFFFFFL,
        )
    }

    fun bacaKejadian(b: ByteArray): KejadianKotak? {
        if (b.size < UKURAN_EVENT) return null
        val buf = le(b)
        return KejadianKotak(
            seq = buf.getInt(0).toLong() and 0xFFFFFFFFL,
            jenis = JenisKejadianKotak.dari(u8(b, 4)),
            bateraiPersen = u8(b, 5).takeIf { it != TANPA_BATERAI },
            epochDetik = buf.getInt(6).toLong() and 0xFFFFFFFFL,
            menitTerbuka = buf.getShort(10).toInt() and 0xFFFF,
            saatAlarm = u8(b, 12) == 1,
        )
    }

    fun bacaConfig(b: ByteArray): ConfigKotak? {
        if (b.size < UKURAN_CONFIG) return null
        val namaBytes = b.copyOfRange(AWAL_NAMA, AWAL_NAMA + BYTE_NAMA)
        val akhir = namaBytes.indexOf(0).let { if (it < 0) BYTE_NAMA else it }
        return ConfigKotak(
            jam = u8(b, 0).takeIf { it != JAM_BELUM_DISETEL },
            menit = u8(b, 1),
            durasiAlarmMenit = u8(b, 2),
            versi = u8(b, 3),
            jendelaAwalMenit = u8(b, 4),
            nama = String(namaBytes, 0, akhir, Charsets.UTF_8),
        )
    }

    fun tulisAck(seq: Long): ByteArray = le(UKURAN_ACK).putInt(seq.toInt()).array()

    fun tulisWaktu(epochDetik: Long, selisihUtcMenit: Int): ByteArray =
        le(UKURAN_TIME).putInt(epochDetik.toInt()).putShort(selisihUtcMenit.toShort()).array()

    /** The dose time and alarm length; the name and version bytes are the box's and are sent back as read. */
    fun tulisConfig(c: ConfigKotak): ByteArray = buatConfig(c)

    // --- Box side: what the firmware sends, and how it reads what the phone writes -------------

    fun buatStatus(s: StatusKotak): ByteArray {
        var flags = 0
        if (s.jamDisetel) flags = flags or FLAG_JAM_DISETEL
        if (s.bateraiLemah) flags = flags or FLAG_BATERAI_LEMAH
        if (s.configDisetel) flags = flags or FLAG_CONFIG_DISETEL
        return le(UKURAN_STATUS)
            .put(s.keadaan.kode.toByte())
            .put((s.bateraiPersen ?: TANPA_BATERAI).toByte())
            .putShort(s.bateraiMv.toShort())
            .put(if (s.tutupTerbuka) 1 else 0)
            .put(flags.toByte())
            .putShort(s.antrean.coerceAtMost(0xFFFF).toShort())
            .putInt(s.uptimeDetik.toInt())
            .array()
    }

    fun buatKejadian(e: KejadianKotak): ByteArray = le(UKURAN_EVENT)
        .putInt(e.seq.toInt())
        .put(e.jenis.kode.toByte())
        .put((e.bateraiPersen ?: TANPA_BATERAI).toByte())
        .putInt(e.epochDetik.toInt())
        .putShort(e.menitTerbuka.toShort())
        .put(if (e.saatAlarm) 1 else 0)
        .put(0).put(0).put(0)
        .array()

    fun buatConfig(c: ConfigKotak): ByteArray {
        val out = ByteArray(UKURAN_CONFIG)
        out[0] = (c.jam?.coerceIn(0, 23) ?: JAM_BELUM_DISETEL).toByte()
        out[1] = c.menit.coerceIn(0, 59).toByte()
        out[2] = c.durasiAlarmMenit.coerceIn(ALARM_MENIT_MIN, ALARM_MENIT_MAKS).toByte()
        out[3] = c.versi.toByte()
        out[4] = c.jendelaAwalMenit.coerceIn(0, 255).toByte()
        potongUtf8(c.nama, BYTE_NAMA).copyInto(out, AWAL_NAMA)
        return out
    }

    fun bacaAck(b: ByteArray): Long? = if (b.size < UKURAN_ACK) null else le(b).getInt(0).toLong() and 0xFFFFFFFFL

    /** Unix seconds and the UTC offset in minutes. */
    fun bacaWaktu(b: ByteArray): Pair<Long, Int>? {
        if (b.size < UKURAN_TIME) return null
        val buf = le(b)
        return (buf.getInt(0).toLong() and 0xFFFFFFFFL) to buf.getShort(4).toInt()
    }

    /** Cuts at a character boundary so the firmware never sees half a multi-byte character. */
    private fun potongUtf8(s: String, maks: Int): ByteArray {
        var bytes = s.toByteArray(Charsets.UTF_8)
        var akhir = s.length
        while (bytes.size > maks && akhir > 0) {
            akhir--
            bytes = s.substring(0, akhir).toByteArray(Charsets.UTF_8)
        }
        return bytes
    }
}

enum class KeadaanKotak(val kode: Int) {
    SIAGA(0), ALARM(1), CARI(2), TIDAK_DIKENAL(-1);

    companion object {
        fun dari(kode: Int) = entries.firstOrNull { it.kode == kode } ?: TIDAK_DIKENAL
    }
}

enum class JenisKejadianKotak(val kode: Int) {
    LID_OPEN(1), LID_CLOSE(2), BOX_ON(3), LOW_BATTERY(4), TEST(5), TIDAK_DIKENAL(-1);

    companion object {
        fun dari(kode: Int) = entries.firstOrNull { it.kode == kode } ?: TIDAK_DIKENAL
    }
}

object PerintahKotak {
    const val FIND: Byte = 0x01
    const val TEST: Byte = 0x02
    const val DEMO_ALARM: Byte = 0x03
    const val STOP_ALARM: Byte = 0x04
}

data class StatusKotak(
    val keadaan: KeadaanKotak,
    /** Null without a battery (USB power). */
    val bateraiPersen: Int?,
    val bateraiMv: Int,
    val tutupTerbuka: Boolean,
    val jamDisetel: Boolean,
    val bateraiLemah: Boolean,
    val configDisetel: Boolean,
    /** Events waiting in the box's flash queue. */
    val antrean: Int,
    val uptimeDetik: Long,
)

data class KejadianKotak(
    val seq: Long,
    val jenis: JenisKejadianKotak,
    val bateraiPersen: Int?,
    /** 0 when the box's clock wasn't set yet. */
    val epochDetik: Long,
    /** LID_CLOSE only. */
    val menitTerbuka: Int = 0,
    /** LID_OPEN: the dose reminder was ringing. */
    val saatAlarm: Boolean = false,
)

data class ConfigKotak(
    /** Dose time; null = not set yet. */
    val jam: Int?,
    val menit: Int,
    val durasiAlarmMenit: Int,
    /** The dose window opens this long before the dose time; an opening from then on cancels that day's alarm. */
    val jendelaAwalMenit: Int = 0,
    val versi: Int = KotakProtokol.VERSI,
    val nama: String = "",
)
