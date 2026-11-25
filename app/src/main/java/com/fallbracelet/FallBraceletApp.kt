package com.fallbracelet

import android.app.Application
import com.fallbracelet.caregiver.CaregiverManager
import com.fallbracelet.caregiver.NotifikasiPmo
import com.fallbracelet.data.PasienDatabase
import com.fallbracelet.data.PasienRepository
import com.fallbracelet.data.SettingsRepository
import com.fallbracelet.kotak.KotakManager
import com.fallbracelet.kotak.KotakService
import com.fallbracelet.pengingat.PengingatPasien
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class FallBraceletApp : Application() {
    lateinit var manager: HpManager
        private set
    lateinit var caregiver: CaregiverManager
        private set
    lateinit var pasien: PasienManager
        private set
    lateinit var kotak: KotakManager
        private set

    override fun onCreate() {
        super.onCreate()
        val notifPmo = NotifikasiPmo(this).apply { hapusSaluranLama() }
        // Left by the bracelet app: its event database and sensor recordings. Nothing reads them any more.
        deleteDatabase("bracelet.db")
        java.io.File(filesDir, "recordings").deleteRecursively()
        val settings = SettingsRepository(this)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        manager = HpManager(this, settings, scope)
        val pengingat = PengingatPasien(this).apply { buatSaluran() }
        KotakService.buatSaluran(this)
        pasien = PasienManager(
            this, PasienDatabase.create(this), PasienRepository(this), scope, pengingat,
            relay = manager.relay, pengaturanHp = settings, hpFlow = manager.appSettings,
        )
        kotak = KotakManager(this, pasien, scope, manager.appSettings)
        caregiver = CaregiverManager(this, settings, notifPmo, scope, manager.finder) { manager.appSettings.value }
        manager.start()
        caregiver.start()
        kotak.mulai()
    }
}

val android.content.Context.manager: HpManager
    get() = (applicationContext as FallBraceletApp).manager

val android.content.Context.caregiver: CaregiverManager
    get() = (applicationContext as FallBraceletApp).caregiver

val android.content.Context.pasien: PasienManager
    get() = (applicationContext as FallBraceletApp).pasien

val android.content.Context.kotak: KotakManager
    get() = (applicationContext as FallBraceletApp).kotak
