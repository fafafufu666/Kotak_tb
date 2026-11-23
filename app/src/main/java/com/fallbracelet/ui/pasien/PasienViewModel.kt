package com.fallbracelet.ui.pasien

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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

    val keadaan = p.keadaan
    val pengaturan = p.pengaturan
    val aturan = p.aturan

    fun setuju() = viewModelScope.launch { p.setuju() }
    fun mulai(nama: String, tanggalMulai: LocalDate, jam: LocalTime) = viewModelScope.launch { p.mulai(nama, tanggalMulai, jam) }
    fun ubahNama(nama: String) = viewModelScope.launch { p.repo.setNama(nama) }
    fun ubahJamMinum(jam: LocalTime) = viewModelScope.launch { p.ubahJamMinum(jam) }
    fun konfirmasiManual() = viewModelScope.launch { p.konfirmasiManual() }
    fun bukaKotakSimulasi() = viewModelScope.launch { p.bukaKotakSimulasi() }

    /** Go on without a box (the simulated box is used), or come back to pairing. */
    fun lewatiKotak(lewati: Boolean) = viewModelScope.launch { p.repo.setKotakDilewati(lewati) }

    /** Forget the paired box and show the pairing screen again. */
    fun pasangkanUlang() = viewModelScope.launch {
        m.forget()
        p.repo.setKotakDilewati(false)
    }

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
