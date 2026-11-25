package com.fallbracelet.kotak

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.fallbracelet.R
import com.fallbracelet.ble.LinkState
import com.fallbracelet.caregiver.ForegroundStartGate
import com.fallbracelet.kotak
import com.fallbracelet.ui.MainActivity
import kotlinx.coroutines.launch

/**
 * Keeps the app process, and so the BLE link to the real box, alive in the background while a
 * box is paired in patient mode (PRD 2: an opening shows in the app within 5 s while the phone is
 * near). The box keeps its events in flash meanwhile, so nothing is lost if Android stops us.
 */
class KotakService : LifecycleService() {
    private var started = false

    override fun onDestroy() {
        super.onDestroy()
        NotificationManagerCompat.from(this).cancel(ID)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        val requested = intent != null
        try {
            ServiceCompat.startForeground(
                this, ID, notifikasi(this, LinkState.CONNECTING),
                if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE else 0,
            )
        } catch (e: Exception) {
            // No Bluetooth permission yet, or Android won't start us from the background.
            Log.w(TAG, "Could not start in foreground", e)
            if (requested) gate.onStartFinished()
            stopSelf(startId)
            return START_NOT_STICKY
        }
        if (requested && gate.onStartFinished()) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        if (!started) {
            started = true
            lifecycleScope.launch {
                kotak.tautan.link.collect { l ->
                    @Suppress("MissingPermission")
                    runCatching { NotificationManagerCompat.from(this@KotakService).notify(ID, notifikasi(this@KotakService, l)) }
                }
            }
        }
        return START_STICKY
    }

    companion object {
        private const val TAG = "KotakService"
        private const val ID = 400_100
        private const val SALURAN = "kotak"
        private val gate = ForegroundStartGate()

        /** The quiet channel for the "connected to the box" notification. */
        fun buatSaluran(context: Context) {
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(SALURAN, context.getString(R.string.saluran_kotak), NotificationManager.IMPORTANCE_LOW).apply {
                    description = context.getString(R.string.saluran_kotak_isi)
                    setShowBadge(false)
                },
            )
        }

        private fun notifikasi(context: Context, l: LinkState) =
            NotificationCompat.Builder(context, SALURAN)
                .setSmallIcon(R.drawable.ic_stat_bracelet)
                .setContentTitle(context.getString(R.string.app_name))
                .setContentText(
                    context.getString(if (l == LinkState.CONNECTED) R.string.layanan_kotak_terhubung else R.string.layanan_kotak_mencari),
                )
                .setOngoing(true)
                .setSilent(true)
                .setContentIntent(
                    PendingIntent.getActivity(
                        context, ID, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                    ),
                )
                .build()

        fun start(context: Context) {
            gate.onStartRequested()
            runCatching { ContextCompat.startForegroundService(context, Intent(context, KotakService::class.java)) }
                .onFailure {
                    Log.w(TAG, "start failed", it)
                    gate.onStartFinished()
                }
        }

        fun stop(context: Context) {
            if (gate.onStopRequested()) context.stopService(Intent(context, KotakService::class.java))
        }
    }
}
