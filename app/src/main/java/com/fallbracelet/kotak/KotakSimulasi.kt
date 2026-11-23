package com.fallbracelet.kotak

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import kotlin.random.Random

/** The simulated box's flash (NVS): the queue, the event counter and the config survive a restart. */
interface FlashKotak {
    fun muat(): String?
    fun simpan(isi: String)
}

/** What the simulated buzzer plays; the Android side turns it into sound. */
enum class PolaBunyi { CARI, ALARM }

/**
 * A stand-in for the box's ESP32 firmware (PRD 5.2), driven only through protocol v2 bytes:
 * the phone writes TIME, CONFIG, EVENT_ACK and COMMAND and reads STATUS, CONFIG and EVENT,
 * exactly as it will over BLE. Behaves like the firmware should:
 * - F1: each lid opening queues LID_OPEN with the box's clock (LID_CLOSE on closing);
 * - F2: events wait in flash and go out one at a time, oldest first, until the phone ACKs
 *   that seq; with no ACK after [ULANG_MS] the same event is sent again;
 * - F3: the clock comes from the phone's TIME; before that, events carry time 0;
 * - F4: at the dose time the buzzer rings until the lid is opened or the alarm length passes,
 *   unless the lid was already opened inside the dose window (from `jendelaAwalMenit` before);
 * - F5: FIND, TEST, DEMO_ALARM (and STOP_ALARM);
 * - F6: STATUS carries the clock-set flag, the queue length and (no) battery.
 *
 * Android-free and synchronised; time always comes in as a parameter (real clock, ms), so tests
 * can drive it. [keluar] receives notifications and indications, [bunyi] the buzzer.
 */
class KotakSimulasi(
    private val flash: FlashKotak,
    private val acak: Random = Random.Default,
) {
    /** STATUS notifications and EVENT indications to the phone (only while it is subscribed). */
    var keluar: ((UUID, ByteArray) -> Unit)? = null
    var bunyi: ((PolaBunyi) -> Unit)? = null

    // Kept in flash
    private var seqBerikut = 0L
    private val antrean = ArrayList<KejadianKotak>()
    private var config = ConfigKotak(jam = null, menit = 0, durasiAlarmMenit = 10, nama = "")

    // Lost at power-off, like RAM
    private var hidupPadaMs = 0L
    private var jam: Pair<Long, Long>? = null          // (box epoch ms at set, real ms at set)
    private var selisihUtcMenit = 0
    private var tersambung = false
    private var langgananStatus = false
    private var langgananEvent = false
    private var menungguAck: Long? = null
    private var dikirimPadaMs = 0L
    private var tutupTerbuka = false
    private var dibukaPadaMs = 0L                        // box clock
    private var terakhirDibukaMs: Long? = null           // box clock
    private var alarmSampaiMs: Long? = null              // box clock
    private var alarmSelesaiTanggal: LocalDate? = null
    private var alarmBunyiTerakhirMs = 0L
    private var cariSampaiMs = 0L                        // real clock
    private var statusTerkirim: ByteArray? = null

    val nama: String get() = config.nama

    /** Power on: load flash (a fresh box gets a random event counter and a name), queue BOX_ON. */
    @Synchronized
    fun hidup(sekarangMs: Long) {
        hidupPadaMs = sekarangMs
        muat()
        antre(JenisKejadianKotak.BOX_ON, sekarangMs)
    }

    // --- The BLE link ---------------------------------------------------------------------------

    @Synchronized
    fun sambung() {
        tersambung = true
    }

    /** Out of range, or Bluetooth off: the queue stays, and the unACKed event goes again next time. */
    @Synchronized
    fun putus() {
        tersambung = false
        langgananStatus = false
        langgananEvent = false
        menungguAck = null
        statusTerkirim = null
    }

    @Synchronized
    fun baca(karakteristik: UUID, sekarangMs: Long): ByteArray? = when (karakteristik) {
        KotakProtokol.STATUS -> KotakProtokol.buatStatus(status(sekarangMs))
        KotakProtokol.CONFIG -> KotakProtokol.buatConfig(config)
        KotakProtokol.EVENT -> antrean.firstOrNull()?.let(KotakProtokol::buatKejadian) ?: ByteArray(KotakProtokol.UKURAN_EVENT)
        else -> null
    }

    /** A write from the phone; false for an unknown characteristic or a malformed value. */
    @Synchronized
    fun tulis(karakteristik: UUID, nilai: ByteArray, sekarangMs: Long): Boolean {
        if (!tersambung) return false
        when (karakteristik) {
            KotakProtokol.TIME -> {
                val (epoch, selisih) = KotakProtokol.bacaWaktu(nilai) ?: return false
                jam = epoch * 1000 to sekarangMs
                selisihUtcMenit = selisih
            }
            KotakProtokol.CONFIG -> {
                val c = KotakProtokol.bacaConfig(nilai) ?: return false
                // Name and version are the box's own: only the dose time and alarm length are taken.
                config = config.copy(
                    jam = c.jam, menit = c.menit,
                    durasiAlarmMenit = c.durasiAlarmMenit.coerceIn(KotakProtokol.ALARM_MENIT_MIN, KotakProtokol.ALARM_MENIT_MAKS),
                    jendelaAwalMenit = c.jendelaAwalMenit,
                )
                simpan()
            }
            KotakProtokol.EVENT_ACK -> {
                val seq = KotakProtokol.bacaAck(nilai) ?: return false
                if (antrean.firstOrNull()?.seq == seq) {
                    antrean.removeAt(0)
                    simpan()
                    menungguAck = null
                    kirimBerikut(sekarangMs)
                }
            }
            KotakProtokol.COMMAND -> when (nilai.firstOrNull()) {
                PerintahKotak.FIND -> {
                    cariSampaiMs = sekarangMs + CARI_MS
                    bunyi?.invoke(PolaBunyi.CARI)
                }
                PerintahKotak.TEST -> antre(JenisKejadianKotak.TEST, sekarangMs)
                PerintahKotak.DEMO_ALARM -> mulaiAlarm(waktuKotak(sekarangMs) + config.durasiAlarmMenit * 60_000L, sekarangMs)
                PerintahKotak.STOP_ALARM -> hentikanAlarm(sekarangMs)
                else -> return false
            }
            else -> return false
        }
        kabarkanStatus(sekarangMs)
        return true
    }

    @Synchronized
    fun langganan(karakteristik: UUID, aktif: Boolean, sekarangMs: Long) {
        if (!tersambung) return
        when (karakteristik) {
            KotakProtokol.STATUS -> langgananStatus = aktif
            KotakProtokol.EVENT -> {
                langgananEvent = aktif
                if (aktif) kirimBerikut(sekarangMs)
            }
        }
    }

    // --- The lid ----------------------------------------------------------------------------------

    @Synchronized
    fun buka(sekarangMs: Long) {
        if (tutupTerbuka) return
        tutupTerbuka = true
        val t = waktuKotak(sekarangMs)
        dibukaPadaMs = t
        terakhirDibukaMs = t
        val saatAlarm = alarmSampaiMs != null
        if (saatAlarm) hentikanAlarm(sekarangMs)
        antre(JenisKejadianKotak.LID_OPEN, sekarangMs, saatAlarm = saatAlarm)
    }

    @Synchronized
    fun tutup(sekarangMs: Long) {
        if (!tutupTerbuka) return
        tutupTerbuka = false
        val menit = ((waktuKotak(sekarangMs) - dibukaPadaMs) / 60_000).toInt().coerceAtLeast(0)
        antre(JenisKejadianKotak.LID_CLOSE, sekarangMs, menitTerbuka = menit)
    }

    // --- The main loop ----------------------------------------------------------------------------

    /** Call often (e.g. twice a second): dose alarm, FIND timeout, resending an unACKed event, STATUS. */
    @Synchronized
    fun detak(sekarangMs: Long) {
        periksaAlarm(sekarangMs)
        if (alarmSampaiMs != null && sekarangMs - alarmBunyiTerakhirMs >= BUNYI_ULANG_MS) {
            alarmBunyiTerakhirMs = sekarangMs
            bunyi?.invoke(PolaBunyi.ALARM)
        }
        val tunggu = menungguAck
        if (tunggu != null && sekarangMs - dikirimPadaMs >= ULANG_MS) {
            menungguAck = null
            kirimBerikut(sekarangMs)
        }
        kabarkanStatus(sekarangMs)
    }

    // --- Inside -------------------------------------------------------------------------------------

    /** The box's clock in ms; before TIME it counts from 0 at power-on, like millis(). */
    private fun waktuKotak(sekarangMs: Long): Long = jam?.let { (epoch, nyata) -> epoch + (sekarangMs - nyata) } ?: (sekarangMs - hidupPadaMs)

    private fun status(sekarangMs: Long) = StatusKotak(
        keadaan = when {
            alarmSampaiMs != null -> KeadaanKotak.ALARM
            sekarangMs < cariSampaiMs -> KeadaanKotak.CARI
            else -> KeadaanKotak.SIAGA
        },
        bateraiPersen = null, bateraiMv = 0,
        tutupTerbuka = tutupTerbuka,
        jamDisetel = jam != null,
        bateraiLemah = false,
        configDisetel = config.jam != null,
        antrean = antrean.size,
        uptimeDetik = (sekarangMs - hidupPadaMs) / 1000,
    )

    private fun antre(jenis: JenisKejadianKotak, sekarangMs: Long, menitTerbuka: Int = 0, saatAlarm: Boolean = false) {
        antrean += KejadianKotak(
            seq = seqBerikut++, jenis = jenis, bateraiPersen = null,
            epochDetik = if (jam != null) waktuKotak(sekarangMs) / 1000 else 0,
            menitTerbuka = menitTerbuka, saatAlarm = saatAlarm,
        )
        simpan()
        if (menungguAck == null) kirimBerikut(sekarangMs)
        kabarkanStatus(sekarangMs)
    }

    private fun kirimBerikut(sekarangMs: Long) {
        if (!tersambung || !langgananEvent) return
        val e = antrean.firstOrNull() ?: return
        menungguAck = e.seq
        dikirimPadaMs = sekarangMs
        keluar?.invoke(KotakProtokol.EVENT, KotakProtokol.buatKejadian(e))
    }

    private fun kabarkanStatus(sekarangMs: Long) {
        if (!tersambung || !langgananStatus) return
        val b = KotakProtokol.buatStatus(status(sekarangMs))
        if (statusTerkirim?.contentEquals(b.copyOf(8)) == true) return // uptime alone isn't news
        statusTerkirim = b.copyOf(8)
        keluar?.invoke(KotakProtokol.STATUS, b)
    }

    /** F4: ring from the dose time for the alarm length, unless the lid was opened since then. */
    private fun periksaAlarm(sekarangMs: Long) {
        val t = waktuKotak(sekarangMs)
        alarmSampaiMs?.let { sampai ->
            if (t >= sampai) {
                alarmSampaiMs = null
                alarmSelesaiTanggal = tanggalLokal(t)
            }
            return
        }
        val jamDosis = config.jam ?: return
        if (jam == null) return
        val hariIni = tanggalLokal(t)
        if (alarmSelesaiTanggal == hariIni) return
        val mulai = hariIni.atTime(jamDosis, config.menit).toInstant(ZoneOffset.ofTotalSeconds(selisihUtcMenit * 60)).toEpochMilli()
        val sampai = mulai + config.durasiAlarmMenit * 60_000L
        if (t < mulai || t >= sampai) return
        if ((terakhirDibukaMs ?: Long.MIN_VALUE) >= mulai - config.jendelaAwalMenit * 60_000L) {
            alarmSelesaiTanggal = hariIni
            return
        }
        mulaiAlarm(sampai, sekarangMs)
    }

    private fun mulaiAlarm(sampaiMs: Long, sekarangMs: Long) {
        alarmSampaiMs = sampaiMs
        alarmBunyiTerakhirMs = sekarangMs
        bunyi?.invoke(PolaBunyi.ALARM)
    }

    private fun hentikanAlarm(sekarangMs: Long) {
        if (alarmSampaiMs == null) return
        alarmSampaiMs = null
        alarmSelesaiTanggal = tanggalLokal(waktuKotak(sekarangMs))
    }

    private fun tanggalLokal(epochMs: Long): LocalDate =
        Instant.ofEpochMilli(epochMs).atOffset(ZoneOffset.ofTotalSeconds(selisihUtcMenit * 60)).toLocalDate()

    private fun muat() {
        val teks = flash.muat()
        if (teks == null) {
            // A fresh box: a random first seq (never 1, so a wiped box can't reuse old numbers) and a name.
            seqBerikut = acak.nextLong(1_000, 1_000_000_000)
            config = config.copy(nama = "%s-%04X".format(KotakProtokol.AWALAN_NAMA, acak.nextInt(0x10000)))
            simpan()
            return
        }
        val j = JSONObject(teks)
        seqBerikut = j.getLong("seq")
        config = ConfigKotak(
            jam = if (j.isNull("jam")) null else j.getInt("jam"),
            menit = j.getInt("menit"), durasiAlarmMenit = j.getInt("durasi"), jendelaAwalMenit = j.optInt("awal", 0),
            nama = j.getString("nama"),
        )
        antrean.clear()
        val a = j.getJSONArray("antrean")
        for (i in 0 until a.length()) {
            val e = a.getJSONObject(i)
            antrean += KejadianKotak(
                seq = e.getLong("seq"), jenis = JenisKejadianKotak.dari(e.getInt("jenis")), bateraiPersen = null,
                epochDetik = e.getLong("epoch"), menitTerbuka = e.getInt("menit"), saatAlarm = e.getBoolean("alarm"),
            )
        }
    }

    private fun simpan() {
        val a = JSONArray()
        antrean.forEach { e ->
            a.put(JSONObject().put("seq", e.seq).put("jenis", e.jenis.kode).put("epoch", e.epochDetik)
                .put("menit", e.menitTerbuka).put("alarm", e.saatAlarm))
        }
        flash.simpan(JSONObject()
            .put("seq", seqBerikut).put("jam", config.jam ?: JSONObject.NULL).put("menit", config.menit)
            .put("durasi", config.durasiAlarmMenit).put("awal", config.jendelaAwalMenit).put("nama", config.nama).put("antrean", a)
            .toString())
    }

    companion object {
        /** No ACK after this long: send the same event again. */
        const val ULANG_MS = 10_000L
        const val CARI_MS = 3_000L
        /** While the reminder rings, the buzzer pattern repeats this often. */
        const val BUNYI_ULANG_MS = 20_000L
    }
}
