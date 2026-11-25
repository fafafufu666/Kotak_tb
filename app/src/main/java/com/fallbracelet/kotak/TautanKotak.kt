package com.fallbracelet.kotak

import com.fallbracelet.ble.LinkState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The phone's side of the link to one medicine box, in protocol v2 terms. [TautanBle] talks to the
 * real box over Bluetooth LE, [TautanSimulasi] to [KotakSimulasi]; both go through the same steps.
 */
interface TautanKotak {
    val link: StateFlow<LinkState>
    val status: StateFlow<StatusKotak?>
    val config: StateFlow<ConfigKotak?>
    /** EVENT indications, parsed. Each must be answered with [ack]. */
    val kejadian: SharedFlow<KejadianKotak>

    /** Connects and sets up: TIME, read CONFIG and STATUS, subscribe to STATUS, then to EVENT last. */
    fun sambung(waktu: suspend () -> Pair<Long, Int>)
    fun putus()

    suspend fun setelWaktu(epochDetik: Long, selisihUtcMenit: Int): Boolean
    suspend fun ack(seq: Long): Boolean
    suspend fun perintah(p: Byte): Boolean
    suspend fun tulisConfig(c: ConfigKotak): Boolean
}

/**
 * [TautanKotak] to the simulated ESP32. Every exchange is protocol v2 bytes, built and parsed with
 * [KotakProtokol], so the phone code above it can't tell this box from the real one.
 */
class TautanSimulasi(
    private val kotak: KotakSimulasi,
    private val scope: CoroutineScope,
    private val jamNyata: () -> Long = System::currentTimeMillis,
) : TautanKotak {

    private val _link = MutableStateFlow(LinkState.DISCONNECTED)
    override val link: StateFlow<LinkState> = _link.asStateFlow()
    private val _status = MutableStateFlow<StatusKotak?>(null)
    override val status: StateFlow<StatusKotak?> = _status.asStateFlow()
    private val _config = MutableStateFlow<ConfigKotak?>(null)
    override val config: StateFlow<ConfigKotak?> = _config.asStateFlow()
    private val _kejadian = MutableSharedFlow<KejadianKotak>(extraBufferCapacity = 32)
    override val kejadian: SharedFlow<KejadianKotak> = _kejadian.asSharedFlow()

    /** One GATT-like operation at a time, as on Android. */
    private val op = Mutex()

    init {
        kotak.keluar = { uuid, nilai ->
            when (uuid) {
                KotakProtokol.STATUS -> KotakProtokol.bacaStatus(nilai)?.let { _status.value = it }
                KotakProtokol.EVENT -> KotakProtokol.bacaKejadian(nilai)?.let { _kejadian.tryEmit(it) }
            }
        }
    }

    override fun sambung(waktu: suspend () -> Pair<Long, Int>) {
        if (_link.value != LinkState.DISCONNECTED) return
        _link.value = LinkState.CONNECTING
        scope.launch {
            delay(SAMBUNG_MS) // advertising, connection and service discovery take a moment on real BLE
            op.withLock {
                if (_link.value != LinkState.CONNECTING) return@launch
                kotak.sambung()
                val (epoch, selisih) = waktu()
                kotak.tulis(KotakProtokol.TIME, KotakProtokol.tulisWaktu(epoch, selisih), jamNyata())
                kotak.baca(KotakProtokol.CONFIG, jamNyata())?.let { _config.value = KotakProtokol.bacaConfig(it) }
                kotak.baca(KotakProtokol.STATUS, jamNyata())?.let { _status.value = KotakProtokol.bacaStatus(it) }
                kotak.langganan(KotakProtokol.STATUS, true, jamNyata())
                _link.value = LinkState.CONNECTED
                // Last: the box starts sending queued events as soon as this is on.
                kotak.langganan(KotakProtokol.EVENT, true, jamNyata())
            }
        }
    }

    override fun putus() {
        kotak.putus()
        _link.value = LinkState.DISCONNECTED
        _status.value = null
    }

    private suspend fun tulis(uuid: java.util.UUID, nilai: ByteArray): Boolean = op.withLock {
        _link.value == LinkState.CONNECTED && kotak.tulis(uuid, nilai, jamNyata())
    }

    override suspend fun setelWaktu(epochDetik: Long, selisihUtcMenit: Int) =
        tulis(KotakProtokol.TIME, KotakProtokol.tulisWaktu(epochDetik, selisihUtcMenit))

    override suspend fun ack(seq: Long) = tulis(KotakProtokol.EVENT_ACK, KotakProtokol.tulisAck(seq))

    override suspend fun perintah(p: Byte) = tulis(KotakProtokol.COMMAND, byteArrayOf(p))

    override suspend fun tulisConfig(c: ConfigKotak): Boolean {
        val ok = tulis(KotakProtokol.CONFIG, KotakProtokol.tulisConfig(c))
        if (ok) op.withLock { kotak.baca(KotakProtokol.CONFIG, jamNyata())?.let { _config.value = KotakProtokol.bacaConfig(it) } }
        return ok
    }

    private companion object {
        const val SAMBUNG_MS = 600L
    }
}

/**
 * Whichever box is in use now (the real one or the simulated one), as one [TautanKotak] for the
 * screens. Changing [pilihan] switches every flow over; connecting is left to [KotakManager].
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class TautanAktif(val pilihan: StateFlow<TautanKotak>, scope: CoroutineScope) : TautanKotak {
    override val link: StateFlow<LinkState> =
        pilihan.flatMapLatest { it.link }.stateIn(scope, SharingStarted.Eagerly, LinkState.DISCONNECTED)
    override val status: StateFlow<StatusKotak?> =
        pilihan.flatMapLatest { it.status }.stateIn(scope, SharingStarted.Eagerly, null)
    override val config: StateFlow<ConfigKotak?> =
        pilihan.flatMapLatest { it.config }.stateIn(scope, SharingStarted.Eagerly, null)
    override val kejadian: SharedFlow<KejadianKotak> =
        pilihan.flatMapLatest { it.kejadian }.shareIn(scope, SharingStarted.Eagerly)

    override fun sambung(waktu: suspend () -> Pair<Long, Int>) = pilihan.value.sambung(waktu)
    override fun putus() = pilihan.value.putus()
    override suspend fun setelWaktu(epochDetik: Long, selisihUtcMenit: Int) = pilihan.value.setelWaktu(epochDetik, selisihUtcMenit)
    override suspend fun ack(seq: Long) = pilihan.value.ack(seq)
    override suspend fun perintah(p: Byte) = pilihan.value.perintah(p)
    override suspend fun tulisConfig(c: ConfigKotak) = pilihan.value.tulisConfig(c)
}
