package com.fallbracelet.caregiver

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.fallbracelet.R
import com.fallbracelet.ui.MainActivity
import com.fallbracelet.ui.pasien.Format
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The PMO's notifications (PRD 6.2), in Indonesian and neutral: no diagnosis, no medicine names,
 * so they can show on a lock screen. Channels are made only on a PMO phone, the first time one is
 * needed, so a patient's phone never lists them. Each kind has its own group, so Android doesn't
 * bundle them under an automatic group header that stays after the alert is gone.
 */
class NotifikasiPmo(private val context: Context) {
    private val nm = context.getSystemService(NotificationManager::class.java)

    /** The bracelet app's channels ("Fall and SOS alarms", "Bracelet updates", "Connection"). */
    fun hapusSaluranLama() {
        listOf("alarm", "info", "service").forEach { runCatching { nm.deleteNotificationChannel(it) } }
    }

    private fun pastikanSaluran() {
        if (nm.getNotificationChannel(SALURAN_KABAR) != null) return
        val kabar = NotificationChannel(SALURAN_KABAR, context.getString(R.string.pd_saluran_kabar), NotificationManager.IMPORTANCE_HIGH).apply {
            description = context.getString(R.string.pd_saluran_kabar_isi)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 300, 150, 300)
        }
        val info = NotificationChannel(SALURAN_INFO, context.getString(R.string.pd_saluran_info), NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = context.getString(R.string.pd_saluran_info_isi)
        }
        val sambungan = NotificationChannel(SALURAN_SAMBUNGAN, context.getString(R.string.pd_saluran_sambungan), NotificationManager.IMPORTANCE_LOW).apply {
            description = context.getString(R.string.pd_saluran_sambungan_isi)
        }
        nm.createNotificationChannels(listOf(kabar, info, sambungan))
    }

    /** A new or re-sent ESKALASI: heads-up, or full screen on a locked phone, until marked seen. */
    fun eskalasi(e: KabarPmo, nama: String) {
        pastikanSaluran()
        val judul = judulKabar(context, e, nama)
        val ringkas = ringkasKabar(context, e)
        val isi = listOf(ringkas, e.message, terakhirDibuka(context, e.detail?.terakhirDibuka))
            .filter { it.isNotBlank() }.joinToString("\n")
        val buka = layarKabar(e, nama)
        val n = NotificationCompat.Builder(context, SALURAN_KABAR)
            .setSmallIcon(R.drawable.ic_stat_bracelet)
            .setContentTitle(judul)
            .setContentText(ringkas)
            .setStyle(NotificationCompat.BigTextStyle().bigText(isi))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setFullScreenIntent(buka, true)
            .setContentIntent(buka)
            .setOngoing(true)
            .addAction(0, context.getString(R.string.pd_tandai_dilihat), tandaiDilihat(e.id))
            .setGroup(SALURAN_KABAR)
            .build()
        kirim(idKabar(e.id), n)
    }

    fun batalkan(eventId: Long) = NotificationManagerCompat.from(context).cancel(idKabar(eventId))

    fun pasienTerputus(wearerId: Long, nama: String) =
        info(ID_TERPUTUS + (wearerId % 10_000).toInt(), nama.ifBlank { context.getString(R.string.pd_pasien) }, context.getString(R.string.pd_notif_terputus))

    fun pasienTersambung(wearerId: Long) = NotificationManagerCompat.from(context).cancel(ID_TERPUTUS + (wearerId % 10_000).toInt())

    fun bepergian(wearerId: Long, nama: String, sampai: LocalDate) = info(
        ID_BEPERGIAN + (wearerId % 10_000).toInt(),
        nama.ifBlank { context.getString(R.string.pd_pasien) },
        context.getString(R.string.pd_notif_bepergian, Format.tanggal(sampai)),
    )

    /** For [CaregiverService] while there is no push and the stream keeps running in the background. */
    fun layanan(teks: String): Notification {
        pastikanSaluran()
        return NotificationCompat.Builder(context, SALURAN_SAMBUNGAN)
            .setSmallIcon(R.drawable.ic_stat_bracelet)
            .setContentTitle(context.getString(R.string.pd_judul))
            .setContentText(teks)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(bukaAplikasi(0))
            .setGroup(SALURAN_SAMBUNGAN)
            .build()
    }

    private fun info(id: Int, judul: String, teks: String) {
        pastikanSaluran()
        val n = NotificationCompat.Builder(context, SALURAN_INFO)
            .setSmallIcon(R.drawable.ic_stat_bracelet)
            .setContentTitle(judul)
            .setContentText(teks)
            .setStyle(NotificationCompat.BigTextStyle().bigText(teks))
            .setContentIntent(bukaAplikasi(id))
            .setAutoCancel(true)
            .setGroup(SALURAN_INFO)
            .build()
        kirim(id, n)
    }

    private fun idKabar(eventId: Long) = ID_KABAR + (eventId % 100_000).toInt()

    private fun layarKabar(e: KabarPmo, nama: String): PendingIntent = PendingIntent.getActivity(
        context, idKabar(e.id), AlarmActivity.intent(context, e, nama),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun tandaiDilihat(eventId: Long): PendingIntent = PendingIntent.getBroadcast(
        context, idKabar(eventId),
        Intent(context, AckReceiver::class.java).putExtra(AckReceiver.EXTRA_EVENT_ID, eventId),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun bukaAplikasi(requestCode: Int): PendingIntent = PendingIntent.getActivity(
        context, requestCode,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    @SuppressLint("MissingPermission")
    private fun kirim(id: Int, n: Notification) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        NotificationManagerCompat.from(context).notify(id, n)
    }

    companion object {
        const val SALURAN_KABAR = "pmo_kabar"
        const val SALURAN_INFO = "pmo_info"
        const val SALURAN_SAMBUNGAN = "pmo_sambungan"
        const val ID_LAYANAN = 3
        private const val ID_KABAR = 400_000
        private const val ID_TERPUTUS = 500_000
        private const val ID_BEPERGIAN = 510_000
    }
}

/** "Ani" or "Ani (demo)". */
fun judulKabar(context: Context, e: KabarPmo, nama: String): String =
    nama.ifBlank { context.getString(R.string.pd_pasien) } + if (e.demo) " " + context.getString(R.string.pd_demo_kurung) else ""

/** "2 dosis terlewat berturut-turut", or "Dosis terlewat". */
fun ringkasKabar(context: Context, e: KabarPmo): String =
    e.detail?.terlewatBerturut?.takeIf { it > 0 }?.let { context.getString(R.string.pd_terlewat_berturut, it) }
        ?: context.getString(R.string.pd_dosis_terlewat)

/** "Terakhir dibuka: Senin 07.02", or "Kotak belum pernah dibuka". */
fun terakhirDibuka(context: Context, t: Long?): String =
    if (t == null) context.getString(R.string.pd_belum_pernah_dibuka)
    else context.getString(R.string.pd_terakhir_dibuka, Format.waktu(Instant.ofEpochMilli(t), ZoneId.systemDefault()))
