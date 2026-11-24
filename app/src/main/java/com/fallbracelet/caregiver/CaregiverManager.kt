package com.fallbracelet.caregiver

import android.content.Context
import android.util.Log
import com.fallbracelet.R
import com.fallbracelet.data.AppMode
import com.fallbracelet.data.AppSettings
import com.fallbracelet.data.SettingsRepository
import com.fallbracelet.relay.GalatRelay
import com.fallbracelet.relay.RelayClient
import com.fallbracelet.relay.RelayDiscovery
import com.fallbracelet.relay.RelayException
import com.fallbracelet.relay.RelayFinder
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** PUSH: no live connection because the screen is closed; ESKALASI arrive by FCM. */
enum class StreamState { OFF, CONNECTING, LIVE, PUSH }

/** An unseen ESKALASI that rang on this phone, with the patient's name. */
data class Berdering(val kabar: KabarPmo, val nama: String)

/**
 * PMO mode (PRD 6.2 M1–M3), doing what relay/web/index.html does: link with the patient's invite
 * code, keep the live stream open, follow each patient's daily summary and 7-day row, and ring
 * for each new or re-sent ESKALASI until it is marked seen ("Tandai dilihat"). Lives as long as
 * the process; [CaregiverService] keeps the process alive while linked and push is not set up.
 */
class CaregiverManager(
    private val context: Context,
    private val settings: SettingsRepository,
    private val notif: NotifikasiPmo,
    private val scope: CoroutineScope,
    private val finder: RelayFinder,
    private val current: () -> AppSettings,
) {
    private val _pasien = MutableStateFlow<List<PasienPmo>>(emptyList())
    val pasien: StateFlow<List<PasienPmo>> = _pasien.asStateFlow()
    private val _stream = MutableStateFlow(StreamState.OFF)
    val stream: StateFlow<StreamState> = _stream.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _berdering = MutableStateFlow<List<Berdering>>(emptyList())
    /** Unseen ESKALASI that rang, oldest first: the in-app alert shows the first one not put aside. */
    val berdering: StateFlow<List<Berdering>> = _berdering.asStateFlow()
    private val _nanti = MutableStateFlow<Set<Long>>(emptySet())
    /** Put aside with "Nanti" until the relay sends them again. */
    val nanti: StateFlow<Set<Long>> = _nanti.asStateFlow()

    private var streamJob: Job? = null

    /** The PMO screen is on screen. */
    private val visible = MutableStateFlow(false)
    /** The relay has this phone's FCM token, so ESKALASI can wake it without a live connection. */
    private val _pushReady = MutableStateFlow(false)

    /** Whether the background service should run (linked, no push). */
    @Volatile private var layananDiminta = false

    fun setVisible(on: Boolean) {
        visible.value = on
        // Android refuses to start the service from the background (e.g. right after an app update
        // or a reboot); opening the app is always allowed, so try again then.
        if (on && layananDiminta) CaregiverService.start(context)
    }

    /**
     * With push (FCM) the live stream only runs while the screen is open. Without it (no Firebase
     * project yet, the current plan) the stream and its foreground service stay on in the background.
     */
    fun start() {
        val link = settings.settings.map { s -> (s.caregiverToken.takeIf { s.mode == AppMode.CAREGIVER }) to s.relayUrl }
            .distinctUntilChanged()
        scope.launch {
            link.collect { (token, url) -> if (token != null && url.isNotBlank()) registerPush() else _pushReady.value = false }
        }
        scope.launch {
            combine(link, visible, _pushReady) { (token, url), shown, push ->
                val linked = token != null && url.isNotBlank()
                (if (linked && (shown || !push)) url to token!! else null) to (linked && !push)
            }.distinctUntilChanged().collect { (wantStream, wantService) ->
                streamJob?.cancel()
                layananDiminta = wantService
                if (wantService) CaregiverService.start(context) else CaregiverService.stop(context)
                if (wantStream == null) {
                    _stream.value = if (_pushReady.value) StreamState.PUSH else StreamState.OFF
                } else {
                    streamJob = scope.launch { streamLoop(wantStream.first, wantStream.second) }
                }
            }
        }
    }

    // --- Push (FCM) ----------------------------------------------------------------------

    private suspend fun registerPush() {
        val token = runCatching { FirebaseMessaging.getInstance().token.await() }
            .onFailure { Log.w(TAG, "no FCM token, staying on the live stream", it) }
            .getOrNull() ?: return
        sendPushToken(token)
    }

    /** Firebase rotated the token (FcmService.onNewToken). */
    fun onNewPushToken(token: String) = scope.launch { sendPushToken(token) }

    private suspend fun sendPushToken(token: String) {
        var backoff = 5_000L
        while (scope.isActive) {
            val s = current()
            if (s.caregiverToken == null || s.relayUrl.isBlank() || s.mode != AppMode.CAREGIVER) return
            val ok = runCatching { client().put("/v1/push-token", JSONObject().put("token", token)) }
                .onFailure { Log.w(TAG, "relay didn't take the FCM token: ${it.message}") }.isSuccess
            if (ok) { _pushReady.value = true; return }
            delay(backoff)
            backoff = (backoff * 2).coerceAtMost(5 * 60_000L)
        }
    }

    /** A data message from FcmService. Same handling as the stream, so nothing rings twice. */
    fun handlePush(data: Map<String, String>) {
        if (current().caregiverToken == null || current().mode != AppMode.CAREGIVER) return
        when (data["kind"]) {
            "event" -> if (data["type"] == KabarPmo.ESKALASI) {
                val e = KabarPmo(
                    id = data["id"]?.toLongOrNull() ?: return,
                    wearerId = data["wearerId"]?.toLongOrNull() ?: 0,
                    type = KabarPmo.ESKALASI,
                    message = data["message"].orEmpty(),
                    receivedAt = data["receivedAt"]?.toLongOrNull() ?: System.currentTimeMillis(),
                    ackedBy = null,
                    ackedAt = null,
                    escalations = data["escalations"]?.toIntOrNull() ?: 0,
                    demo = data["demo"] == "1",
                    detail = data["detail"]?.let { runCatching { DetailEskalasi.dari(JSONObject(it)) }.getOrNull() },
                )
                onEvent(e, data["wearerName"].orEmpty(), data["escalated"] == "1")
            }
            "ack" -> data["eventId"]?.toLongOrNull()?.let { berhentiBerdering(it) }
            "offline" -> data["wearerId"]?.toLongOrNull()?.let { notif.pasienTerputus(it, data["name"].orEmpty()) }
            "online" -> data["wearerId"]?.toLongOrNull()?.let { notif.pasienTersambung(it) }
        }
        if (visible.value) scope.launch { runCatching { refresh() } }
    }

    private fun client() = current().let { RelayClient(it.relayUrl, it.caregiverToken) }

    /** Links to a patient with their invite code. A phone already linked keeps its key and follows one more. */
    suspend fun link(relayUrl: String, code: String, name: String): Result<String> = runCatching {
        if (relayUrl.trim() != current().relayUrl) settings.setRelay(relayUrl, RelayDiscovery.check(relayUrl.trim())?.relayId)
        val token = current().caregiverToken
        val r = RelayClient(relayUrl.trim(), token).post("/v1/link", JSONObject().put("code", code.trim()).put("name", name.trim()))
        settings.setCaregiver(r.optString("token").takeIf { it.isNotEmpty() && it != "null" }, name.trim())
        refresh()
        r.getJSONObject("wearer").getString("name")
    }.onFailure { Log.w(TAG, "link failed", it) }

    /** "Cari relay": scans the WiFi and returns the address it found. */
    suspend fun findRelay(): String? = finder.findNow()?.url

    suspend fun refresh() {
        val a = client().get("/v1/dashboard?limit=200").getJSONArray("wearers")
        val daftar = List(a.length()) { PasienPmo.dari(a.getJSONObject(it)) }
        _pasien.value = daftar
        // Keep only what the relay still has unseen (a demo that ended, or seen on another device, is gone).
        val terbuka = daftar.flatMap { p -> p.belumDilihat.map { it.id } }.toSet()
        _berdering.value.filter { it.kabar.id !in terbuka }.forEach { berhentiBerdering(it.kabar.id) }
        for (p in daftar) for (e in p.belumDilihat.reversed()) bunyikan(e, p.nama, diulang = false)
    }

    /** Stops the PMO side on this phone, like the web page's "Keluar". The name is kept for the next link. */
    suspend fun signOut() {
        _berdering.value.forEach { notif.batalkan(it.kabar.id) }
        _berdering.value = emptyList()
        _nanti.value = emptySet()
        settings.clearCaregiverLink()
        _pasien.value = emptyList()
        _error.value = null
    }

    /** "Tandai dilihat": the patient then sees "Dilihat oleh …". Returns whether the relay took it. */
    suspend fun acknowledge(eventId: Long): Boolean {
        val ok = runCatching { client().post("/v1/events/$eventId/ack") }
            .onFailure { _error.value = teks(R.string.pd_galat_tandai) + " " + sebab(it) }
            .isSuccess
        if (ok) {
            val oleh = current().caregiverName.ifBlank { teks(R.string.pd_kamu) }
            _pasien.update { l -> l.map { TampilanPmo.denganDilihat(it, eventId, oleh, System.currentTimeMillis()) } }
            berhentiBerdering(eventId)
        }
        return ok
    }

    fun acknowledgeLater(eventId: Long) = scope.launch { acknowledge(eventId) }

    /** "Nanti": put the alert aside until the relay sends it again. The notification stays. */
    fun nanti(eventId: Long) = _nanti.update { it + eventId }

    // --- Live stream ---------------------------------------------------------------------

    private suspend fun streamLoop(url: String, token: String) {
        var backoff = 2_000L
        while (scope.isActive) {
            _stream.value = StreamState.CONNECTING
            try {
                refresh()
                readStream(url, token)
                backoff = 2_000L
            } catch (e: RelayException) {
                if (e.status == 401) {
                    _error.value = teks(R.string.pd_galat_dilepas)
                    signOut()
                    return
                }
                _error.value = teks(R.string.pd_galat_relay) + " " + sebab(e)
                // Found at a new address: the settings change restarts the stream from start().
                if (e.status == 0 && finder.afterFailure()?.url.let { it != null && it != url }) return
            } catch (e: Exception) {
                _error.value = teks(R.string.pd_galat_relay) + " " + sebab(e)
                if (finder.afterFailure()?.url.let { it != null && it != url }) return
            }
            _stream.value = StreamState.CONNECTING
            delay(backoff)
            backoff = (backoff * 2).coerceAtMost(60_000L)
        }
    }

    /** Blocks until the stream drops. */
    private suspend fun readStream(url: String, token: String) = withContext(Dispatchers.IO) {
        val c = RelayClient(url, token).open("/v1/stream")
        c.connectTimeout = 10_000
        c.readTimeout = 60_000 // the relay sends a keepalive every 25 s
        c.setRequestProperty("Accept", "text/event-stream")
        current().lastStreamEventId.takeIf { it > 0 }?.let { c.setRequestProperty("Last-Event-ID", it.toString()) }
        try {
            if (c.responseCode != 200) throw RelayException(c.responseCode, "stream refused (HTTP ${c.responseCode})")
            _stream.value = StreamState.LIVE
            _error.value = null
            val reader = c.inputStream.bufferedReader()
            var type = "message"
            var id: Long? = null
            val data = StringBuilder()
            while (true) {
                val line = reader.readLine() ?: break
                when {
                    line.isEmpty() -> {
                        if (data.isNotEmpty()) dispatch(type, JSONObject(data.toString()), id)
                        type = "message"; id = null; data.clear()
                    }
                    line.startsWith("event:") -> type = line.substringAfter(':').trim()
                    line.startsWith("data:") -> data.append(line.substringAfter(':').trim())
                    line.startsWith("id:") -> id = line.substringAfter(':').trim().toLongOrNull()
                }
            }
        } finally {
            c.disconnect()
        }
    }

    private suspend fun dispatch(type: String, d: JSONObject, id: Long?) {
        val wearerId = d.optLong("wearerId")
        when (type) {
            "event" -> {
                val e = KabarPmo.dari(d)
                if (_pasien.value.none { it.id == e.wearerId }) runCatching { refresh() }
                else onEvent(e, d.optString("wearerName"), d.optBoolean("escalated"))
            }
            "days" -> {
                val r = RingkasanPmo.dari(d.optJSONObject("ringkasan"))
                val lama = _pasien.value.firstOrNull { it.id == wearerId }
                if (lama != null && r?.bepergianSampai != null && r.bepergianSampai != lama.ringkasan?.bepergianSampai) {
                    notif.bepergian(wearerId, lama.nama, r.bepergianSampai)
                }
                _pasien.update { l -> l.map { if (it.id == wearerId) TampilanPmo.denganRingkasan(it, r, PasienPmo.hariDari(d)) else it } }
                // Demo started or ended: the other mode's ESKALASI no longer ring.
                val p = _pasien.value.firstOrNull { it.id == wearerId }
                if (p != null) _berdering.value.filter { it.kabar.wearerId == wearerId && !p.sesuaiMode(it.kabar) }
                    .forEach { berhentiBerdering(it.kabar.id) }
            }
            "status" -> _pasien.update { l -> l.map { if (it.id == wearerId) it.copy(offline = false, lastHeartbeat = d.optLong("at")) else it } }
            "online" -> {
                _pasien.update { l -> l.map { if (it.id == wearerId) it.copy(offline = false) else it } }
                notif.pasienTersambung(wearerId)
            }
            "offline" -> {
                _pasien.update { l -> l.map { if (it.id == wearerId) it.copy(offline = true) else it } }
                notif.pasienTerputus(wearerId, d.optString("name"))
            }
            "ack" -> {
                val eventId = d.getLong("eventId")
                _pasien.update { l -> l.map { TampilanPmo.denganDilihat(it, eventId, d.optString("by"), d.optLong("at")) } }
                berhentiBerdering(eventId)
            }
            "unlinked", "hello" -> runCatching { refresh() }
        }
        if (id != null && id > current().lastStreamEventId) settings.setLastStreamEventId(id)
    }

    private fun onEvent(e: KabarPmo, nama: String, diulang: Boolean) {
        _pasien.update { l -> l.map { if (it.id == e.wearerId) TampilanPmo.denganKabar(it, e) else it } }
        val namaPasien = nama.ifBlank { _pasien.value.firstOrNull { it.id == e.wearerId }?.nama.orEmpty() }
        bunyikan(e, namaPasien, diulang)
    }

    /** Alerts already raised, as "eventId:escalations": push and stream can both deliver one. */
    private val sudahBerbunyi = object : LinkedHashMap<String, Unit>() {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Unit>) = size > 200
    }

    private fun bunyikan(e: KabarPmo, nama: String, diulang: Boolean) {
        val p = _pasien.value.firstOrNull { it.id == e.wearerId }
        if (!TampilanPmo.perluBerdering(e, p, System.currentTimeMillis())) return
        if (diulang) _nanti.update { it - e.id }
        _berdering.update { l -> if (l.any { it.kabar.id == e.id }) l.map { if (it.kabar.id == e.id) Berdering(e, nama) else it } else l + Berdering(e, nama) }
        synchronized(sudahBerbunyi) { if (sudahBerbunyi.put("${e.id}:${e.escalations}", Unit) != null) return }
        notif.eskalasi(e, nama)
    }

    private fun berhentiBerdering(eventId: Long) {
        notif.batalkan(eventId)
        _berdering.update { l -> l.filter { it.kabar.id != eventId } }
        _nanti.update { it - eventId }
    }

    private fun teks(id: Int, vararg isi: Any): String = context.getString(id, *isi)

    /** Why a relay call failed, in plain words (no exception text). */
    private fun sebab(e: Throwable): String {
        val status = (e as? RelayException)?.status ?: 0
        return when (GalatRelay.dari(e)) {
            GalatRelay.TIDAK_TERHUBUNG -> teks(R.string.relay_galat_tidak_terhubung, current().relayUrl)
            GalatRelay.BEDA_JARINGAN -> teks(R.string.relay_galat_beda_jaringan)
            GalatRelay.RELAY_BERMASALAH -> teks(R.string.relay_galat_bermasalah, status)
            GalatRelay.DITOLAK -> teks(R.string.relay_galat_ditolak, status)
            GalatRelay.LAIN -> teks(R.string.relay_galat_lain)
        }
    }

    private companion object {
        const val TAG = "CaregiverManager"
    }
}
