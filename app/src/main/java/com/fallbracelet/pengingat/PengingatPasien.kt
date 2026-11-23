package com.fallbracelet.pengingat

import android.Manifest
import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.fallbracelet.R
import com.fallbracelet.pasien
import com.fallbracelet.ui.MainActivity
import com.fallbracelet.ui.pasien.PengingatKhususActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant

/**
 * The Android side of the patient's reminders: the notification channel, the notifications
 * themselves, and one AlarmManager alarm that wakes the app at the next reminder moment.
 * When and what is decided by `adherence.JadwalPengingat`; the texts come from the rules file.
 */
class PengingatPasien(private val context: Context) {

    fun buatSaluran() {
        val nm = context.getSystemService(NotificationManager::class.java)
        val saluran = NotificationChannel(SALURAN, context.getString(R.string.saluran_pengingat), NotificationManager.IMPORTANCE_HIGH).apply {
            description = context.getString(R.string.saluran_pengingat_isi)
            enableVibration(true)
        }
        nm.createNotificationChannel(saluran)
    }

    /** Ordinary reminder (at `jam_minum`, or a repeat while late). [teks] comes from the rules file. */
    fun notifBiasa(teks: String) {
        val n = NotificationCompat.Builder(context, SALURAN)
            .setSmallIcon(R.drawable.ic_stat_bracelet)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(teks)
            .setStyle(NotificationCompat.BigTextStyle().bigText(teks))
            .setContentIntent(bukaAplikasi())
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
        kirim(ID_BIASA, n)
    }

    /**
     * The special reminder (P5): a notification with [teks] that opens the full screen, also
     * over the lock screen. With the app already on screen the full screen is opened directly,
     * since Android then shows only a banner.
     */
    fun pesanKhusus(teks: String, kunci: String) {
        val layar = PendingIntent.getActivity(
            context, ID_KHUSUS, PengingatKhususActivity.intent(context, kunci),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(context, SALURAN)
            .setSmallIcon(R.drawable.ic_stat_bracelet)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(teks)
            .setStyle(NotificationCompat.BigTextStyle().bigText(teks))
            .setContentIntent(layar)
            .setFullScreenIntent(layar, true)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
        kirim(ID_KHUSUS, n)
        if (aplikasiDiDepan()) runCatching { context.startActivity(PengingatKhususActivity.intent(context, kunci)) }
    }

    /** Clears both reminders, e.g. once the dose is recorded. */
    fun hapusNotifikasi() {
        NotificationManagerCompat.from(context).cancel(ID_BIASA)
        NotificationManagerCompat.from(context).cancel(ID_KHUSUS)
    }

    // --- Alarm -----------------------------------------------------------------------------

    /** True when Android lets the app set exact alarms (Android 12+ asks the user). */
    fun bolehAlarmTepat(): Boolean =
        Build.VERSION.SDK_INT < 31 || context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    /**
     * Wakes the app at [waktuNyata] (real clock). Exact when allowed, otherwise Android may
     * deliver it some minutes late. Replaces any earlier alarm.
     */
    @SuppressLint("ScheduleExactAlarm") // checked by bolehAlarmTepat(), with a fallback on SecurityException
    fun setelAlarm(waktuNyata: Instant) {
        val am = context.getSystemService(AlarmManager::class.java)
        val ms = maxOf(waktuNyata.toEpochMilli(), System.currentTimeMillis() + 1_000)
        val pi = alarmIntent()
        try {
            if (bolehAlarmTepat()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, pi)
        } catch (_: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, pi)
        }
    }

    fun batalkanAlarm() = context.getSystemService(AlarmManager::class.java).cancel(alarmIntent())

    private fun alarmIntent(): PendingIntent = PendingIntent.getBroadcast(
        context, 0, Intent(context, PengingatReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    // --- Helpers -----------------------------------------------------------------------------

    private fun bukaAplikasi(): PendingIntent = PendingIntent.getActivity(
        context, ID_BIASA,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun aplikasiDiDepan(): Boolean {
        val info = ActivityManager.RunningAppProcessInfo()
        ActivityManager.getMyMemoryState(info)
        return info.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
    }

    @SuppressLint("MissingPermission")
    private fun kirim(id: Int, n: android.app.Notification) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        NotificationManagerCompat.from(context).notify(id, n)
    }

    companion object {
        const val SALURAN = "pengingat"
        private const val ID_BIASA = 400_000
        private const val ID_KHUSUS = 400_001
    }
}

/** The reminder alarm went off: work out what is due now and set the next alarm. */
class PengingatReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = periksaDiLatar(context, goAsync())
}

/** Runs the reminder check from a broadcast, keeping the process alive until it is done. */
internal fun periksaDiLatar(context: Context, pending: BroadcastReceiver.PendingResult) {
    CoroutineScope(Dispatchers.Default).launch {
        try {
            withTimeoutOrNull(8_000) { context.pasien.periksaPengingat() }
        } finally {
            pending.finish()
        }
    }
}
