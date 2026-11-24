package com.fallbracelet

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.fallbracelet.data.AppMode
import com.fallbracelet.data.AppSettings
import com.fallbracelet.data.SettingsRepository
import com.fallbracelet.relay.RelayDiscovery
import com.fallbracelet.relay.RelayFinder
import com.fallbracelet.relay.WearerRelay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The phone's settings and its link to the relay as the patient's phone: registers, keeps the
 * nickname the PMO sees current, and sends a heartbeat every 5 minutes so the relay can tell the
 * PMO when the phone goes quiet. Dose events (ESKALASI, STATUS_HARIAN) are sent by [PasienManager].
 */
class HpManager(
    private val context: Context,
    val settings: SettingsRepository,
    private val scope: CoroutineScope,
) {
    val appSettings: StateFlow<AppSettings> = settings.settings.stateIn(scope, SharingStarted.Eagerly, AppSettings())

    val relay = WearerRelay(settings) { appSettings.value }
    /** Re-finds the relay when it moves to another WiFi (e.g. a phone hotspot at the demo). */
    val finder = RelayFinder(settings) { appSettings.value }

    private val _relayError = MutableStateFlow<String?>(null)
    /** Null when the last relay call worked. */
    val relayError: StateFlow<String?> = _relayError.asStateFlow()
    private val _caregiverCount = MutableStateFlow<Int?>(null)
    /** Linked PMOs, as of the last heartbeat; null before the first one. */
    val caregiverCount: StateFlow<Int?> = _caregiverCount.asStateFlow()

    fun start() {
        scope.launch { relayLoop() }
        scope.launch {
            // The patient's nickname is the name the PMO sees.
            appSettings.map { it.wearerName }.distinctUntilChanged().collect { runCatching { relay.rename(it) } }
        }
    }

    /** Register once, then heartbeat every 5 min. */
    private suspend fun relayLoop() {
        var lastHeartbeat = 0L
        while (true) {
            // Read the stored settings, not the StateFlow's initial placeholder.
            val s = settings.settings.first()
            if (s.mode == AppMode.WEARER) {
                val reachable = s.relayUrl.isNotBlank() && RelayDiscovery.check(s.relayUrl) != null
                if (!reachable && finder.afterFailure() != null) {
                    delay(2_000) // let the new address land in settings, then carry on below
                }
                val result = runCatching {
                    relay.ensureRegistered()
                    if (System.currentTimeMillis() - lastHeartbeat >= HEARTBEAT_MS) {
                        heartbeat()
                        lastHeartbeat = System.currentTimeMillis()
                    }
                }
                _relayError.value = result.exceptionOrNull()?.message
            }
            delay(60_000)
        }
    }

    private suspend fun heartbeat() {
        _caregiverCount.value = relay.heartbeat()
    }

    /** After a new relay address, or when a heartbeat is wanted right now (e.g. a PMO was removed). */
    fun syncNow() = scope.launch {
        _relayError.value = runCatching { relay.ensureRegistered(); heartbeat() }.exceptionOrNull()?.message
    }

    fun canConnect(): Boolean = Build.VERSION.SDK_INT < 31 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    /** P3: the medicine box the patient picked. */
    suspend fun pair(address: String, name: String) = settings.setDevice(address, name)

    suspend fun setMode(mode: AppMode) {
        if (mode == AppMode.CAREGIVER) forget()
        settings.setMode(mode)
    }

    suspend fun forget() = settings.setDevice(null, null)

    private companion object {
        const val HEARTBEAT_MS = 5 * 60_000L
    }
}
