package com.fallbracelet.ui.pasien

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fallbracelet.kotak
import com.fallbracelet.manager
import com.fallbracelet.pasien
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime

/** Patient-mode screens (PRD 6.1). Actions go to [com.fallbracelet.PasienManager]. */
class PasienViewModel(app: Application) : AndroidViewModel(app) {
    private val p = app.pasien
    private val m = app.manager
    private val k = app.kotak

    val keadaan = p.keadaan
    val pengaturan = p.pengaturan
    val aturan = p.aturan

    // The medicine box in use (the real one or the simulated ESP32), protocol v2
    val linkKotak = k.tautan.link
    val statusKotak = k.tautan.status
    val configKotak = k.tautan.config
    val kotakDalamJangkauan = k.dalamJangkauan
    val ujiKotakDiterima = k.ujiDiterima

    fun setuju() = viewModelScope.launch { p.setuju() }
    fun mulai(nama: String, tanggalMulai: LocalDate, jam: LocalTime) = viewModelScope.launch { p.mulai(nama, tanggalMulai, jam) }
    fun ubahNama(nama: String) = viewModelScope.launch { p.repo.setNama(nama) }
    fun ubahJamMinum(jam: LocalTime) = viewModelScope.launch { p.ubahJamMinum(jam) }
    fun konfirmasiManual() = viewModelScope.launch { p.konfirmasiManual() }
    /** The simulated box's lid opens and closes; the event comes back over protocol v2. */
    fun bukaKotakSimulasi() { k.bukaTutup() }
    fun setKotakDalamJangkauan(ya: Boolean) = k.setDalamJangkauan(ya)
    fun cariKotak() { k.cari() }
    fun ujiKotak() { k.uji() }
    fun picuAlarmKotak() { k.picuAlarm() }
    fun hentikanAlarmKotak() { k.hentikanAlarm() }

    /** Go on without a box (the simulated box is used), or come back to pairing. */
    fun lewatiKotak(lewati: Boolean) = viewModelScope.launch { p.repo.setKotakDilewati(lewati) }

    /** Forget the paired box and show the pairing screen again. */
    fun pasangkanUlang() = viewModelScope.launch {
        m.forget()
        p.repo.setKotakDilewati(false)
    }

    fun nyalakanBepergian(lamaHari: Int) = viewModelScope.launch { p.nyalakanBepergian(lamaHari) }
    fun matikanBepergian() = viewModelScope.launch { p.matikanBepergian() }

    fun setDemoAktif(aktif: Boolean) = viewModelScope.launch { p.setDemoAktif(aktif) }
    fun lompatKeHari(hariKe: Int) = viewModelScope.launch { p.lompatKeHari(hariKe) }
    fun majukanJam(menit: Long) = viewModelScope.launch { p.majukanJam(Duration.ofMinutes(menit)) }
    fun majukanKeBatasTerlewat() = viewModelScope.launch { p.majukanKeBatasTerlewat() }
    fun isiRiwayatContoh() = viewModelScope.launch { p.isiRiwayatContoh() }
    fun aturUlangDemo() = viewModelScope.launch { p.aturUlangDemo() }

    /** "Berhenti ikut program": delete local data, forget the box, back to the mode choice. */
    fun berhentiIkut() = viewModelScope.launch {
        p.hapusSemuaData()
        m.forget()
        m.settings.clearMode()
    }
}
