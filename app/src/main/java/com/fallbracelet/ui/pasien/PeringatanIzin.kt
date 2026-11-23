package com.fallbracelet.ui.pasien

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.fallbracelet.R
import com.fallbracelet.manager
import com.fallbracelet.pasien
import com.fallbracelet.ui.openAppSettings
import com.fallbracelet.ui.openFullScreenSettings
import com.fallbracelet.ui.openNotificationSettings
import com.fallbracelet.ui.requestBatteryExemption
import kotlinx.coroutines.delay

/**
 * A setting that stops reminders reaching the patient, with the fix. [abaikan] is set when the
 * app cannot check the setting itself, so the patient can say it is done.
 */
private data class MasalahIzin(
    val teks: Int,
    val tombol: Int,
    val perbaiki: (Context) -> Unit,
    val abaikan: ((Context) -> Unit)? = null,
)

/**
 * The phone settings that stop reminders getting through, the same checks as the bracelet's
 * `SetupWarnings` plus exact alarms, in Indonesian. Relay checks come with stage 3.
 */
private fun masalahIzin(context: Context, adaKotak: Boolean): List<MasalahIzin> = buildList {
    if (adaKotak && !context.manager.canConnect()) {
        add(MasalahIzin(R.string.izin_bluetooth, R.string.izin_buka_pengaturan, ::openAppSettings))
    }
    if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
        add(MasalahIzin(R.string.izin_notifikasi, R.string.izin_nyalakan_notifikasi, ::openNotificationSettings))
    }
    if (Build.VERSION.SDK_INT >= 31 && !context.pasien.bolehAlarmTepat()) {
        add(MasalahIzin(R.string.izin_alarm_tepat, R.string.izin_izinkan_alarm, ::bukaIzinAlarmTepat))
    }
    if (Build.VERSION.SDK_INT >= 34 && !context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()) {
        add(MasalahIzin(R.string.izin_layar_penuh, R.string.izin_izinkan_layar_penuh, ::openFullScreenSettings))
    }
    when (IzinXiaomi.bolehDiLayarKunci(context)) {
        false -> add(MasalahIzin(R.string.izin_layar_kunci, R.string.izin_izinkan_layar_kunci, IzinXiaomi::bukaIzinLainnya))
        null -> if (!IzinXiaomi.sudahDikonfirmasi(context)) {
            add(MasalahIzin(R.string.izin_layar_kunci, R.string.izin_izinkan_layar_kunci, IzinXiaomi::bukaIzinLainnya, IzinXiaomi::konfirmasi))
        }
        true -> Unit
    }
    if (!context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)) {
        add(MasalahIzin(R.string.izin_baterai, R.string.izin_izinkan_latar, ::requestBatteryExemption))
    }
}

private fun bukaIzinAlarmTepat(context: Context) {
    if (Build.VERSION.SDK_INT < 31) return
    runCatching {
        context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")))
    }.onFailure { openAppSettings(context) }
}

/** Warning card on the patient's Beranda. Re-checked on resume and every 15 s; hidden when all is well. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PeringatanIzin(adaKotak: Boolean) {
    val context = LocalContext.current
    var detak by remember { mutableIntStateOf(0) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val pengamat = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) detak++ }
        lifecycle.addObserver(pengamat)
        onDispose { lifecycle.removeObserver(pengamat) }
    }
    LaunchedEffect(Unit) {
        while (true) { delay(15_000); detak++ }
    }
    val masalah = remember(detak, adaKotak) { masalahIzin(context, adaKotak) }
    if (masalah.isEmpty()) return

    KartuPasien(latar = WarnaPasien.BurukMuda, garis = WarnaPasien.Buruk) {
        Text(stringResource(R.string.izin_judul), color = WarnaPasien.Buruk, style = MaterialTheme.typography.titleMedium)
        masalah.forEach { m ->
            Spacer(Modifier.height(10.dp))
            Text(stringResource(m.teks), style = MaterialTheme.typography.bodyMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { m.perbaiki(context) }) { Text(stringResource(m.tombol)) }
                m.abaikan?.let { abaikan ->
                    TextButton(onClick = { abaikan(context); detak++ }) { Text(stringResource(R.string.izin_sudah_diizinkan)) }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.izin_hilang_sendiri), color = WarnaPasien.Redup, style = MaterialTheme.typography.bodySmall)
    }
}
