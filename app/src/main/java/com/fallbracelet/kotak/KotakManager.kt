package com.fallbracelet.kotak

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import com.fallbracelet.PasienManager
import com.fallbracelet.ble.LinkState
import com.fallbracelet.data.AppMode
import com.fallbracelet.data.AppSettings
import com.fallbracelet.data.SumberKejadian
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * Patient mode's medicine box (PRD 5, stage 4). For now the box is [KotakSimulasi], an ESP32
 * stand-in inside the app, reached through [TautanSimulasi] with protocol v2 bytes; the real box
 * over BLE plugs in as another [TautanKotak]. This class does what the phone does with any box:
 * - connects while the patient uses the simulated box, sets its clock (the app's clock, so the
 *   virtual one in demo mode) and writes the dose time, alarm length and early window when they change;
 * - stores each LID_OPEN through [PasienManager.catatDariKotak] (box time, or receive time if
 *   the box clock wasn't set), then ACKs it, so the box only drops it once it is stored;
 * - plays the simulated buzzer.
 */
class KotakManager(
    context: Context,
    private val pasien: PasienManager,
    private val scope: CoroutineScope,
    private val hp: StateFlow<AppSettings>,
) {
    private val prefs = context.getSharedPreferences("kotak_simulasi", Context.MODE_PRIVATE)
    private val flash = object : FlashKotak {
        override fun muat(): String? = prefs.getString(KUNCI_FLASH, null)
        override fun simpan(isi: String) { prefs.edit().putString(KUNCI_FLASH, isi).apply() }
    }

    /** The simulated ESP32. Powered on with the app (the box is on USB power in the demo). */
    val simulasi = KotakSimulasi(flash).apply { hidup(System.currentTimeMillis()) }
    val tautan: TautanKotak = TautanSimulasi(simulasi, scope)

    private val _dalamJangkauan = MutableStateFlow(true)
    /** Simulated range: false = as if the phone were away from the box or Bluetooth were off. */
    val dalamJangkauan: StateFlow<Boolean> = _dalamJangkauan.asStateFlow()

    private val _ujiDiterima = MutableStateFlow<Instant?>(null)
    /** When the last TEST event arrived (shown in Pengaturan). */
    val ujiDiterima: StateFlow<Instant?> = _ujiDiterima.asStateFlow()

    // The alarm stream, like a real buzzer: it doesn't go quiet when the phone is on silent.
    private val nada = runCatching { ToneGenerator(AudioManager.STREAM_ALARM, 70) }.getOrNull()

    fun mulai() {
        simulasi.bunyi = { pola -> scope.launch { bunyikan(pola) } }
        // The box's main loop.
        scope.launch {
            while (true) {
                simulasi.detak(System.currentTimeMillis())
                delay(DETAK_MS)
            }
        }
        // Connected while the patient uses the simulated box and it is "in range".
        scope.launch {
            combine(pasien.pengaturan, hp, _dalamJangkauan) { p, s, jangkau ->
                p != null && p.sudahMulai && p.kotakDilewati && s.mode == AppMode.WEARER && jangkau
            }.distinctUntilChanged().collect { aktif ->
                if (aktif) tautan.sambung { pasien.waktuUntukKotak() } else tautan.putus()
            }
        }
        scope.launch { tautan.kejadian.collect { tangani(it) } }
        // Dose time and alarm length to the box, on connecting and whenever they change (PRD P8).
        scope.launch {
            combine(tautan.link, pasien.keadaan.filterNotNull().map { it.jamMinum }.distinctUntilChanged(), tautan.config) { link, jam, c ->
                Triple(link, jam, c)
            }.collect { (link, jam, c) ->
                if (link != LinkState.CONNECTED || jam == null || c == null) return@collect
                val dosis = pasien.aturan.aturan.dosis
                val durasi = dosis.durasiAlarmKotakMenit.coerceIn(KotakProtokol.ALARM_MENIT_MIN, KotakProtokol.ALARM_MENIT_MAKS)
                val awal = dosis.jendelaAwalMenit.coerceIn(0, 255)
                if (c.jam != jam.hour || c.menit != jam.minute || c.durasiAlarmMenit != durasi || c.jendelaAwalMenit != awal) {
                    tautan.tulisConfig(c.copy(jam = jam.hour, menit = jam.minute, durasiAlarmMenit = durasi, jendelaAwalMenit = awal))
                }
            }
        }
        // Demo mode moves the app's clock: the box follows, so its events and its alarm match the app.
        scope.launch {
            pasien.pengaturan.filterNotNull().map { it.demo.aktif to it.demo.geserJam }.distinctUntilChanged().collect {
                if (tautan.link.value == LinkState.CONNECTED) {
                    val (epoch, selisih) = pasien.waktuUntukKotak()
                    tautan.setelWaktu(epoch, selisih)
                }
            }
        }
    }

    /** Store, then ACK: the box keeps the event in flash until it hears the ACK (PRD F2). */
    private suspend fun tangani(e: KejadianKotak) {
        when (e.jenis) {
            JenisKejadianKotak.LID_OPEN -> {
                val waktu = if (e.epochDetik > 0) Instant.ofEpochSecond(e.epochDetik) else Instant.ofEpochSecond(pasien.waktuUntukKotak().first)
                pasien.catatDariKotak(waktu, SumberKejadian.SIMULASI, simulasi.nama, e.seq)
            }
            JenisKejadianKotak.TEST -> _ujiDiterima.value = Instant.now()
            else -> Unit // LID_CLOSE, BOX_ON, LOW_BATTERY: nothing to record yet
        }
        tautan.ack(e.seq)
    }

    // --- What the patient (or the demo) does to the simulated box -------------------------------

    /** The lid opened and, a moment later, closed again: a hand taking the day's dose. */
    fun bukaTutup() = scope.launch {
        simulasi.buka(System.currentTimeMillis())
        delay(TUTUP_SESUDAH_MS)
        simulasi.tutup(System.currentTimeMillis())
    }

    fun setDalamJangkauan(ya: Boolean) {
        _dalamJangkauan.value = ya
    }

    /** P3 "Uji bunyi" / FIND. */
    fun cari() = scope.launch { tautan.perintah(PerintahKotak.FIND) }

    /** A TEST event through the whole path. */
    fun uji() = scope.launch { tautan.perintah(PerintahKotak.TEST) }

    /** Demo: ring the box's dose reminder now (PRD 9 "Picu alarm kotak"). */
    fun picuAlarm() = scope.launch { tautan.perintah(PerintahKotak.DEMO_ALARM) }

    fun hentikanAlarm() = scope.launch { tautan.perintah(PerintahKotak.STOP_ALARM) }

    private suspend fun bunyikan(pola: PolaBunyi) {
        val n = nada ?: return
        val (kali, nadaId) = when (pola) {
            PolaBunyi.CARI -> 2 to ToneGenerator.TONE_PROP_BEEP2
            PolaBunyi.ALARM -> 3 to ToneGenerator.TONE_PROP_BEEP
        }
        repeat(kali) {
            n.startTone(nadaId, 300)
            delay(450)
        }
    }

    private companion object {
        const val KUNCI_FLASH = "flash"
        const val DETAK_MS = 500L
        const val TUTUP_SESUDAH_MS = 2_000L
    }
}
