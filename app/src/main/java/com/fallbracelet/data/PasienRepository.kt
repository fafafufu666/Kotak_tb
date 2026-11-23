package com.fallbracelet.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.fallbracelet.adherence.PengaturanDemo
import com.fallbracelet.adherence.PeriodeBepergian
import org.json.JSONArray
import org.json.JSONObject
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
    /** Travel mode stretches (P9), past and current, real and demo. */
    val bepergian: List<PeriodeBepergian> = emptyList(),
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
        val demoSesi = stringPreferencesKey("demo_sesi")
        val bepergian = stringPreferencesKey("bepergian")
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
                sesi = p[Keys.demoSesi],
            ),
            persetujuanPada = p[Keys.persetujuan],
            terdaftarPada = p[Keys.terdaftar],
            bepergian = p[Keys.bepergian]?.let(::bacaBepergian).orEmpty(),
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

    /**
     * Names this demo run on the relay, so a rehearsal after "Atur ulang demo" sends its ESKALASI
     * again instead of being taken for a repeat of the last run. Made on first use.
     */
    suspend fun demoSesi(): String {
        var sesi: String? = null
        context.pasienStore.edit { p ->
            sesi = p[Keys.demoSesi] ?: java.util.UUID.randomUUID().toString().take(8).also { p[Keys.demoSesi] = it }
        }
        return sesi!!
    }

    suspend fun setBepergian(periode: List<PeriodeBepergian>) = context.pasienStore.edit { p ->
        p[Keys.bepergian] = JSONArray().apply {
            periode.forEach { put(JSONObject().put("mulai", it.mulai.toString()).put("sampai", it.sampai.toString()).put("demo", it.demo)) }
        }.toString()
    }

    private fun bacaBepergian(teks: String): List<PeriodeBepergian> = runCatching {
        val a = JSONArray(teks)
        List(a.length()) { i ->
            val o = a.getJSONObject(i)
            PeriodeBepergian(LocalDate.parse(o.getString("mulai")), LocalDate.parse(o.getString("sampai")), o.optBoolean("demo"))
        }
    }.getOrDefault(emptyList())

    /** Back to real data: demo off, no virtual clock, real start date, demo travel stretches gone. */
    suspend fun aturUlangDemo() = context.pasienStore.edit {
        it[Keys.bepergian]?.let { teks -> it[Keys.bepergian] = JSONArray().apply {
            bacaBepergian(teks).filter { p -> !p.demo }.forEach { p ->
                put(JSONObject().put("mulai", p.mulai.toString()).put("sampai", p.sampai.toString()).put("demo", false))
            }
        }.toString() }
        it.remove(Keys.demoAktif)
        it.remove(Keys.demoGeserMs)
        it.remove(Keys.demoMulai)
        it.remove(Keys.demoDipantau)
        it.remove(Keys.demoSesi)
    }

    /** "Berhenti ikut program": forget everything. */
    suspend fun hapusSemua() = context.pasienStore.edit { it.clear() }
}
