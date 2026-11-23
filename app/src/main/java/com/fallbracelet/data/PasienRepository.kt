package com.fallbracelet.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.fallbracelet.adherence.PengaturanDemo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/** The patient's own settings (PRD P2, P8, section 9). `jam_minum` lives in [PasienDatabase]. */
data class PengaturanPasien(
    val namaPanggilan: String = "",
    /** The real start date; demo mode may replace it, see [PengaturanDemo.tanggalMulai]. */
    val tanggalMulai: LocalDate? = null,
    /** The patient chose to go on without pairing a box (the simulated box is used). */
    val kotakDilewati: Boolean = false,
    val demo: PengaturanDemo = PengaturanDemo(),
    /** When the patient agreed on P1 Persetujuan, epoch ms; null = not yet. */
    val persetujuanPada: Long? = null,
    /** When the patient finished P2 (real clock, epoch ms); null on installs from before it was kept. */
    val terdaftarPada: Long? = null,
) {
    /**
     * From when doses count: setup time, or consent time on installs that set up before it
     * was kept. Doses that closed earlier are unknown (TANPA_DATA), not missed.
     */
    val dipantauSejak: Instant? get() = (terdaftarPada ?: persetujuanPada)?.let(Instant::ofEpochMilli)

    val sudahMulai: Boolean get() = namaPanggilan.isNotBlank() && tanggalMulai != null
}

private val Context.pasienStore by preferencesDataStore("pasien")

class PasienRepository(private val context: Context) {
    private object Keys {
        val nama = stringPreferencesKey("nama_panggilan")
        val mulai = stringPreferencesKey("tanggal_mulai")
        val lewatiKotak = booleanPreferencesKey("kotak_dilewati")
        val demoAktif = booleanPreferencesKey("demo_aktif")
        val demoGeserMs = longPreferencesKey("demo_geser_ms")
        val demoMulai = stringPreferencesKey("demo_tanggal_mulai")
        val persetujuan = longPreferencesKey("persetujuan_pada")
        val terdaftar = longPreferencesKey("terdaftar_pada")
        val demoDipantau = longPreferencesKey("demo_dipantau_sejak")
    }

    val pengaturan: Flow<PengaturanPasien> = context.pasienStore.data.map { p ->
        PengaturanPasien(
            namaPanggilan = p[Keys.nama] ?: "",
            tanggalMulai = p[Keys.mulai]?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
            kotakDilewati = p[Keys.lewatiKotak] ?: false,
            demo = PengaturanDemo(
                aktif = p[Keys.demoAktif] ?: false,
                geserJam = Duration.ofMillis(p[Keys.demoGeserMs] ?: 0),
                tanggalMulai = p[Keys.demoMulai]?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
                dipantauSejak = p[Keys.demoDipantau]?.let(Instant::ofEpochMilli),
            ),
            persetujuanPada = p[Keys.persetujuan],
            terdaftarPada = p[Keys.terdaftar],
        )
    }

    suspend fun setuju(pada: Long) = context.pasienStore.edit { it[Keys.persetujuan] = pada }

    suspend fun mulai(nama: String, tanggalMulai: LocalDate, pada: Long) = context.pasienStore.edit {
        it[Keys.nama] = nama.trim()
        it[Keys.mulai] = tanggalMulai.toString()
        it[Keys.terdaftar] = pada
    }

    suspend fun setNama(nama: String) = context.pasienStore.edit { it[Keys.nama] = nama.trim() }

    suspend fun setKotakDilewati(lewati: Boolean) = context.pasienStore.edit { it[Keys.lewatiKotak] = lewati }

    suspend fun setDemoAktif(aktif: Boolean) = context.pasienStore.edit { it[Keys.demoAktif] = aktif }

    suspend fun setDemoGeser(geser: Duration) = context.pasienStore.edit { it[Keys.demoGeserMs] = geser.toMillis() }

    suspend fun setDemoTanggalMulai(tanggal: LocalDate) = context.pasienStore.edit { it[Keys.demoMulai] = tanggal.toString() }

    suspend fun setDemoDipantauSejak(waktu: Instant) = context.pasienStore.edit { it[Keys.demoDipantau] = waktu.toEpochMilli() }

    /** Back to real data: demo off, no virtual clock, real start date. */
    suspend fun aturUlangDemo() = context.pasienStore.edit {
        it.remove(Keys.demoAktif)
        it.remove(Keys.demoGeserMs)
        it.remove(Keys.demoMulai)
        it.remove(Keys.demoDipantau)
    }

    /** "Berhenti ikut program": forget everything. */
    suspend fun hapusSemua() = context.pasienStore.edit { it.clear() }
}
