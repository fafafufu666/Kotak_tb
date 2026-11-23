package com.fallbracelet.ui.pasien

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import com.fallbracelet.ui.openAppSettings

/**
 * Xiaomi phones (MIUI / HyperOS) have their own per-app "Tampil di Layar kunci" permission,
 * off by default. Without it the special reminder (P5) cannot open over the lock screen, even
 * with Android's full-screen permission granted: the system logs "Show when locked
 * PermissionDenied" and only the notification is left. Android has no public API for it, so it
 * is read through the MIUI app-op, and the patient is sent to MIUI's "Perizinan lainnya" page.
 */
object IzinXiaomi {
    /** MIUI's app-op for "Show on lock screen". */
    private const val OP_TAMPIL_DI_LAYAR_KUNCI = 10020

    private const val PREF = "izin_xiaomi"
    private const val KUNCI_SUDAH = "layar_kunci_sudah"

    fun hpXiaomi(): Boolean = Build.MANUFACTURER.equals("Xiaomi", ignoreCase = true)

    /**
     * True when the app may show over the lock screen, false when MIUI says no, null when the
     * phone is a Xiaomi but the answer cannot be read. Always true on other phones.
     */
    fun bolehDiLayarKunci(context: Context): Boolean? {
        if (!hpXiaomi()) return true
        return runCatching {
            val ops = context.getSystemService(AppOpsManager::class.java)
            val cek = AppOpsManager::class.java.getMethod(
                "checkOpNoThrow", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, String::class.java,
            )
            cek.invoke(ops, OP_TAMPIL_DI_LAYAR_KUNCI, Process.myUid(), context.packageName) as Int == AppOpsManager.MODE_ALLOWED
        }.getOrNull()
    }

    /** The patient said they turned it on (only asked when [bolehDiLayarKunci] cannot tell). */
    fun sudahDikonfirmasi(context: Context): Boolean =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getBoolean(KUNCI_SUDAH, false)

    fun konfirmasi(context: Context) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putBoolean(KUNCI_SUDAH, true).apply()
    }

    /** MIUI's "Perizinan lainnya" page for this app; the app info page when that is missing. */
    fun bukaIzinLainnya(context: Context) {
        val editor = Intent("miui.intent.action.APP_PERM_EDITOR")
            .putExtra("extra_pkgname", context.packageName)
        val percobaan = listOf(
            Intent(editor).setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity"),
            Intent(editor).setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.AppPermissionsEditorActivity"),
            editor,
        )
        for (i in percobaan) {
            if (runCatching { context.startActivity(i) }.isSuccess) return
        }
        openAppSettings(context)
    }
}
