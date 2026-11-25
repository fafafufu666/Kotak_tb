package com.fallbracelet.kotak

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.fallbracelet.ble.LinkState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

/**
 * [TautanKotak] to the real box (`firmware/kotak_obat`) over Bluetooth LE, protocol v2.
 *
 * Android allows only one outstanding GATT operation, so every read and write goes through [op],
 * which waits for the matching callback. Once [sambung] is called the link is kept up: after a
 * drop it reopens with autoConnect, which waits until the box is back in range, and it opens
 * again when Bluetooth is switched back on. The box needs a bonded, encrypted link; the first
 * secure write (TIME) brings up Android's pairing prompt.
 */
@SuppressLint("MissingPermission") // KotakManager only connects with BLUETOOTH_CONNECT granted.
class TautanBle(
    private val context: Context,
    private val scope: CoroutineScope,
    val alamat: String,
) : TautanKotak {

    private val _link = MutableStateFlow(LinkState.DISCONNECTED)
    override val link: StateFlow<LinkState> = _link.asStateFlow()
    private val _status = MutableStateFlow<StatusKotak?>(null)
    override val status: StateFlow<StatusKotak?> = _status.asStateFlow()
    private val _config = MutableStateFlow<ConfigKotak?>(null)
    override val config: StateFlow<ConfigKotak?> = _config.asStateFlow()
    private val _kejadian = MutableSharedFlow<KejadianKotak>(extraBufferCapacity = 32)
    override val kejadian: SharedFlow<KejadianKotak> = _kejadian.asSharedFlow()

    private val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter

    @Volatile private var gatt: BluetoothGatt? = null
    @Volatile private var aktif = false
    @Volatile private var pending: CompletableDeferred<ByteArray?>? = null
    private var waktu: (suspend () -> Pair<Long, Int>)? = null
    private var setupJob: Job? = null
    private val opLock = Mutex()
    private var penerimaTerdaftar = false

    /** Bluetooth off drops the link and any pending autoConnect; on again opens a fresh one. */
    private val keadaanAdapter = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            when (i.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)) {
                BluetoothAdapter.STATE_TURNING_OFF, BluetoothAdapter.STATE_OFF -> {
                    Log.i(TAG, "Bluetooth off")
                    tutupGatt()
                    if (aktif) _link.value = LinkState.CONNECTING
                }
                BluetoothAdapter.STATE_ON -> if (aktif && gatt == null) {
                    Log.i(TAG, "Bluetooth on, reconnecting")
                    buka(autoConnect = false)
                }
            }
        }
    }

    override fun sambung(waktu: suspend () -> Pair<Long, Int>) {
        this.waktu = waktu
        if (aktif) return
        aktif = true
        if (!penerimaTerdaftar) {
            ContextCompat.registerReceiver(
                context, keadaanAdapter, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            penerimaTerdaftar = true
        }
        buka(autoConnect = false)
    }

    override fun putus() {
        aktif = false
        if (penerimaTerdaftar) {
            runCatching { context.unregisterReceiver(keadaanAdapter) }
            penerimaTerdaftar = false
        }
        gatt?.disconnect()
        tutupGatt()
        _link.value = LinkState.DISCONNECTED
    }

    private fun tutupGatt() {
        setupJob?.cancel()
        pending?.complete(null)
        gatt?.close()
        gatt = null
        _status.value = null
    }

    private fun buka(autoConnect: Boolean) {
        if (!aktif) return
        _link.value = LinkState.CONNECTING
        // With Bluetooth off connectGatt fails quietly; the adapter receiver opens again on STATE_ON.
        if (adapter?.isEnabled != true) return
        try {
            gatt = adapter.getRemoteDevice(alamat).connectGatt(context, autoConnect, callback, android.bluetooth.BluetoothDevice.TRANSPORT_LE)
        } catch (e: SecurityException) {
            Log.w(TAG, "No Bluetooth permission", e)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Bad address $alamat", e)
        }
    }

    override suspend fun setelWaktu(epochDetik: Long, selisihUtcMenit: Int) =
        tulis(KotakProtokol.TIME, KotakProtokol.tulisWaktu(epochDetik, selisihUtcMenit))

    override suspend fun ack(seq: Long) = tulis(KotakProtokol.EVENT_ACK, KotakProtokol.tulisAck(seq))

    override suspend fun perintah(p: Byte) = tulis(KotakProtokol.COMMAND, byteArrayOf(p))

    override suspend fun tulisConfig(c: ConfigKotak): Boolean {
        val ok = tulis(KotakProtokol.CONFIG, KotakProtokol.tulisConfig(c))
        if (ok) baca(KotakProtokol.CONFIG)?.let { b -> KotakProtokol.bacaConfig(b)?.let { _config.value = it } }
        return ok
    }

    // --- Setup after connecting ------------------------------------------------------------------

    private suspend fun siapkan(g: BluetoothGatt) {
        if (op(timeoutMs = 15_000) { g.discoverServices() } == null) return gagal(g, "service discovery")
        if (g.getService(KotakProtokol.LAYANAN) == null) {
            // Android keeps a bonded device's services in a cache. A board that ran other firmware
            // before (e.g. the bracelet) answers from that stale list: clear it and look again.
            Log.i(TAG, "Box service not in the cached list, refreshing")
            segarkanCache(g)
            delay(500)
            if (op(timeoutMs = 15_000) { g.discoverServices() } == null) return gagal(g, "service discovery")
            if (g.getService(KotakProtokol.LAYANAN) == null) return gagal(g, "box service not found")
        }
        // A bonded box gets its subscriptions back as soon as the link is encrypted and may send an
        // event before our own CCCD writes below. Android drops values for characteristics the app
        // hasn't registered, the box then waits 10 s to resend, so register locally first.
        for (uuid in listOf(KotakProtokol.EVENT, KotakProtokol.STATUS)) karakteristik(uuid)?.let { g.setCharacteristicNotification(it, true) }
        op { g.requestMtu(MTU) } // CONFIG is 24 bytes; the default MTU carries 20

        // The first secure write may bring up the pairing prompt, so give the patient time.
        val (epoch, selisih) = waktu?.invoke() ?: return gagal(g, "no clock")
        if (!tulis(KotakProtokol.TIME, KotakProtokol.tulisWaktu(epoch, selisih), timeoutMs = 45_000)) {
            return gagal(g, "set time")
        }
        baca(KotakProtokol.CONFIG)?.let { b -> KotakProtokol.bacaConfig(b)?.let { _config.value = it } }
        baca(KotakProtokol.STATUS)?.let { b -> KotakProtokol.bacaStatus(b)?.let { _status.value = it } }
        langganan(g, KotakProtokol.STATUS, indikasi = false)
        // Last: the box starts sending queued events as soon as this is on.
        if (!langganan(g, KotakProtokol.EVENT, indikasi = true)) return gagal(g, "subscribe to events")
        _link.value = LinkState.CONNECTED
        Log.i(TAG, "Connected to $alamat")
    }

    /** `BluetoothGatt.refresh()` is hidden but long-standing; without it the cache stays stale. */
    private fun segarkanCache(g: BluetoothGatt) {
        runCatching { g.javaClass.getMethod("refresh").invoke(g) }
            .onFailure { Log.w(TAG, "GATT cache refresh not available", it) }
    }

    private fun gagal(g: BluetoothGatt, apa: String) {
        Log.w(TAG, "Setup failed: $apa")
        g.disconnect() // onConnectionStateChange reopens
    }

    private suspend fun langganan(g: BluetoothGatt, uuid: UUID, indikasi: Boolean): Boolean {
        val ch = karakteristik(uuid) ?: return false
        if (!g.setCharacteristicNotification(ch, true)) return false
        val cccd = ch.getDescriptor(CCCD) ?: return false
        val nilai = if (indikasi) BluetoothGattDescriptor.ENABLE_INDICATION_VALUE else BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        return op { tulisDescriptor(it, cccd, nilai) } != null
    }

    private suspend fun baca(uuid: UUID): ByteArray? {
        val ch = karakteristik(uuid) ?: return null
        return op { it.readCharacteristic(ch) }
    }

    private suspend fun tulis(uuid: UUID, nilai: ByteArray, timeoutMs: Long = 10_000): Boolean {
        val ch = karakteristik(uuid) ?: return false
        return op(timeoutMs) { tulisKarakteristik(it, ch, nilai) } != null
    }

    private fun karakteristik(uuid: UUID): BluetoothGattCharacteristic? =
        gatt?.getService(KotakProtokol.LAYANAN)?.getCharacteristic(uuid)

    /** Starts one GATT operation and suspends until its callback; null on failure or timeout. */
    private suspend fun op(timeoutMs: Long = 10_000, mulai: (BluetoothGatt) -> Boolean): ByteArray? =
        opLock.withLock {
            val g = gatt ?: return null
            val selesai = CompletableDeferred<ByteArray?>()
            pending = selesai
            try {
                if (!mulai(g)) return null
                withTimeoutOrNull(timeoutMs) { selesai.await() }
            } finally {
                pending = null
            }
        }

    private fun selesai(status: Int, nilai: ByteArray = OK) {
        pending?.complete(if (status == BluetoothGatt.GATT_SUCCESS) nilai else null)
    }

    // --- API 33 changed the read/write signatures; support both ------------------------------------

    @Suppress("DEPRECATION")
    private fun tulisKarakteristik(g: BluetoothGatt, ch: BluetoothGattCharacteristic, nilai: ByteArray): Boolean {
        val jenis = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        return if (Build.VERSION.SDK_INT >= 33) {
            g.writeCharacteristic(ch, nilai, jenis) == BluetoothStatusCodes.SUCCESS
        } else {
            ch.writeType = jenis
            ch.value = nilai
            g.writeCharacteristic(ch)
        }
    }

    @Suppress("DEPRECATION")
    private fun tulisDescriptor(g: BluetoothGatt, d: BluetoothGattDescriptor, nilai: ByteArray): Boolean =
        if (Build.VERSION.SDK_INT >= 33) {
            g.writeDescriptor(d, nilai) == BluetoothStatusCodes.SUCCESS
        } else {
            d.value = nilai
            g.writeDescriptor(d)
        }

    private fun diterima(uuid: UUID, nilai: ByteArray) {
        when (uuid) {
            KotakProtokol.STATUS -> KotakProtokol.bacaStatus(nilai)?.let { _status.value = it }
            KotakProtokol.EVENT -> KotakProtokol.bacaKejadian(nilai)?.let { _kejadian.tryEmit(it) }
        }
    }

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (g != gatt) return
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                setupJob = scope.launch { siapkan(g) }
                return
            }
            if (newState != BluetoothProfile.STATE_DISCONNECTED) return
            Log.i(TAG, "Disconnected, status $status")
            tutupGatt()
            if (!aktif) {
                _link.value = LinkState.DISCONNECTED
                return
            }
            // Keep trying: autoConnect waits for the box in the background.
            _link.value = LinkState.CONNECTING
            scope.launch {
                delay(1_000)
                if (aktif && gatt == null) buka(autoConnect = true)
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) = selesai(status)

        override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) = selesai(status)

        override fun onCharacteristicWrite(g: BluetoothGatt, ch: BluetoothGattCharacteristic, status: Int) = selesai(status)

        override fun onDescriptorWrite(g: BluetoothGatt, d: BluetoothGattDescriptor, status: Int) = selesai(status)

        override fun onCharacteristicRead(g: BluetoothGatt, ch: BluetoothGattCharacteristic, value: ByteArray, status: Int) =
            selesai(status, value)

        @Deprecated("Used below API 33")
        @Suppress("DEPRECATION")
        override fun onCharacteristicRead(g: BluetoothGatt, ch: BluetoothGattCharacteristic, status: Int) {
            if (Build.VERSION.SDK_INT < 33) selesai(status, ch.value ?: OK)
        }

        override fun onCharacteristicChanged(g: BluetoothGatt, ch: BluetoothGattCharacteristic, value: ByteArray) =
            diterima(ch.uuid, value)

        @Deprecated("Used below API 33")
        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(g: BluetoothGatt, ch: BluetoothGattCharacteristic) {
            if (Build.VERSION.SDK_INT < 33) diterima(ch.uuid, ch.value ?: return)
        }
    }

    private companion object {
        const val TAG = "TautanBle"
        const val MTU = 64
        val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        val OK = ByteArray(0)
    }
}
