package com.fallbracelet.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fallbracelet.R
import com.fallbracelet.ble.BleScanner
import com.fallbracelet.ble.FoundDevice
import com.fallbracelet.caregiver
import com.fallbracelet.data.AppMode
import com.fallbracelet.manager
import com.fallbracelet.pasien
import com.fallbracelet.relay.GalatRelay
import com.fallbracelet.relay.Invite
import com.fallbracelet.relay.LinkedCaregiver
import com.fallbracelet.relay.RelayDiscovery
import com.fallbracelet.relay.RelayException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Phone-wide state: mode, box scan (P3), the relay as the patient's phone, and PMO mode. */
class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val m = app.manager
    private val scanner = BleScanner(app)

    val settings = m.appSettings
    val relayHealth = combine(m.appSettings, m.relayError, m.caregiverCount) { s, err, n ->
        RelayHealth(s.relayUrl, s.wearerToken != null, err, n)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // --- Box scan (P3) -------------------------------------------------------------------

    private val _found = MutableStateFlow<List<FoundDevice>>(emptyList())
    val found: StateFlow<List<FoundDevice>> = _found.asStateFlow()
    private val _scanError = MutableStateFlow<String?>(null)
    val scanError: StateFlow<String?> = _scanError.asStateFlow()
    private var scanJob: Job? = null

    fun startScan() {
        scanJob?.cancel()
        _found.value = emptyList()
        _scanError.value = null
        scanJob = viewModelScope.launch {
            scanner.scan()
                .catch { _scanError.value = it.message }
                .collect { d ->
                    _found.value = (_found.value.filterNot { it.address == d.address } + d).sortedByDescending { it.rssi }
                }
        }
    }

    fun stopScan() {
        scanJob?.cancel()
    }

    fun pair(d: FoundDevice) = viewModelScope.launch {
        stopScan()
        m.pair(d.address, d.name)
    }

    /** After a permission or Bluetooth prompt: scan again if no box is paired yet. */
    fun retryConnect() {
        if (settings.value.mode == AppMode.WEARER && settings.value.deviceAddress == null) startScan()
    }

    fun setMode(mode: AppMode) = viewModelScope.launch { m.setMode(mode) }

    // --- Relay, patient side -------------------------------------------------------------

    fun setRelayUrl(url: String) = viewModelScope.launch {
        m.settings.setRelay(url, RelayDiscovery.check(url.trim())?.relayId)
        m.syncNow()
        getApplication<Application>().pasien.sinkronSekarang()
    }

    private val _finding = MutableStateFlow(false)
    val finding: StateFlow<Boolean> = _finding.asStateFlow()

    /** Scans the WiFi for the relay and switches to it. */
    fun findRelay() = viewModelScope.launch {
        _finding.value = true
        val found = m.finder.findNow()
        _finding.value = false
        _relayMessage.value = if (found == null) teks(R.string.atur_relay_tidak_ditemukan) else null
        if (found != null) { m.syncNow(); getApplication<Application>().pasien.sinkronSekarang() }
    }

    private val _invite = MutableStateFlow<Invite?>(null)
    val invite: StateFlow<Invite?> = _invite.asStateFlow()
    private val _caregivers = MutableStateFlow<List<LinkedCaregiver>>(emptyList())
    val caregivers: StateFlow<List<LinkedCaregiver>> = _caregivers.asStateFlow()
    /** Outcome of finding the relay (Pengaturan, Relay card; PMO link card). */
    private val _relayMessage = MutableStateFlow<String?>(null)
    val relayMessage: StateFlow<String?> = _relayMessage.asStateFlow()

    /** Outcome of an invite or a PMO removal (PMO tab). */
    private val _pmoMessage = MutableStateFlow<String?>(null)
    val pmoMessage: StateFlow<String?> = _pmoMessage.asStateFlow()

    fun newInvite() = viewModelScope.launch {
        runCatching { m.relay.ensureRegistered(); m.relay.invite() }
            .onSuccess { _invite.value = it; _pmoMessage.value = null }
            .onFailure { _pmoMessage.value = pesanGalat(R.string.pmo_gagal_undangan, it) }
    }

    fun refreshCaregivers() = viewModelScope.launch {
        if (!m.relay.configured) return@launch
        runCatching { m.relay.caregivers() }.onSuccess { _caregivers.value = it }
    }

    fun removeCaregiver(id: Long) = viewModelScope.launch {
        runCatching { m.relay.removeCaregiver(id) }
            .onSuccess { _pmoMessage.value = null }
            .onFailure { _pmoMessage.value = pesanGalat(R.string.pmo_gagal_hapus, it) }
        refreshCaregivers()
        m.syncNow()
    }

    private fun teks(id: Int, vararg isi: Any): String = getApplication<Application>().getString(id, *isi)

    /** "[what failed] [why, in plain words]"; the exception text itself is not shown. */
    private fun pesanGalat(apa: Int, e: Throwable): String {
        val status = (e as? RelayException)?.status ?: 0
        val sebab = when (GalatRelay.dari(e)) {
            GalatRelay.TIDAK_TERHUBUNG -> teks(R.string.relay_galat_tidak_terhubung, settings.value.relayUrl)
            GalatRelay.BEDA_JARINGAN -> teks(R.string.relay_galat_beda_jaringan)
            GalatRelay.RELAY_BERMASALAH -> teks(R.string.relay_galat_bermasalah, status)
            GalatRelay.DITOLAK -> teks(R.string.relay_galat_ditolak, status)
            GalatRelay.LAIN -> teks(R.string.relay_galat_lain)
        }
        return teks(apa) + " " + sebab
    }

    // --- PMO mode ------------------------------------------------------------------------

    private val cg = app.caregiver
    val pasienPmo = cg.pasien
    val streamState = cg.stream
    val caregiverError = cg.error
    val berdering = cg.berdering
    val nanti = cg.nanti

    /** Outcome of linking; [berhasil] true means linked to [teks]'s patient. */
    data class HasilTautan(val berhasil: Boolean, val teks: String)

    private val _linkResult = MutableStateFlow<HasilTautan?>(null)
    val linkResult: StateFlow<HasilTautan?> = _linkResult.asStateFlow()
    private val _linking = MutableStateFlow(false)
    val linking: StateFlow<Boolean> = _linking.asStateFlow()

    fun link(url: String, code: String, name: String) = viewModelScope.launch {
        _linking.value = true
        _linkResult.value = cg.link(url, code, name).fold(
            { HasilTautan(true, teks(R.string.pd_terhubung_dengan, it)) },
            {
                val salahKode = (it as? RelayException)?.status == 404
                HasilTautan(false, if (salahKode) teks(R.string.pd_kode_salah) else pesanGalat(R.string.pd_gagal_menghubungkan, it))
            },
        )
        _linking.value = false
    }

    fun clearLinkResult() { _linkResult.value = null }

    /** "Cari relay" on the PMO link card: puts the address it found in the field. */
    fun findRelayPmo(onFound: (String) -> Unit) = viewModelScope.launch {
        _finding.value = true
        val url = cg.findRelay()
        _finding.value = false
        _relayMessage.value = if (url == null) teks(R.string.atur_relay_tidak_ditemukan) else null
        if (url != null) onFound(url)
    }

    fun signOutCaregiver() = viewModelScope.launch {
        cg.signOut()
        clearLinkResult()
    }

    private val _mengirim = MutableStateFlow<Set<Long>>(emptySet())
    /** ESKALASI whose "Tandai dilihat" is on its way. */
    val mengirim: StateFlow<Set<Long>> = _mengirim.asStateFlow()

    fun acknowledge(eventId: Long) = viewModelScope.launch {
        _mengirim.value += eventId
        cg.acknowledge(eventId)
        _mengirim.value -= eventId
    }

    fun nanti(eventId: Long) = cg.nanti(eventId)
}
