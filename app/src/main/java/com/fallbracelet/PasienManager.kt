package com.fallbracelet

import android.content.Context
import com.fallbracelet.adherence.CatatanHari
import com.fallbracelet.adherence.Fase
import com.fallbracelet.adherence.HasilBacaAturan
import com.fallbracelet.adherence.HasilHariRawan
import com.fallbracelet.adherence.JamMinumBerlaku
import com.fallbracelet.adherence.LogikaKepatuhan
import com.fallbracelet.adherence.PembacaAturan
import com.fallbracelet.adherence.RingkasanHarian
import com.fallbracelet.data.JamMinumEntity
import com.fallbracelet.data.JenisKejadian
import com.fallbracelet.data.KejadianPasien
import com.fallbracelet.data.PasienDatabase
import com.fallbracelet.data.PasienRepository
import com.fallbracelet.data.PengaturanPasien
import com.fallbracelet.data.SumberKejadian
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Everything the patient screens show, worked out at [sekarang]. */
data class KeadaanPasien(
    val pengaturan: PengaturanPasien,
    val sekarang: Instant,
    val hariIniTanggal: LocalDate,
    /** The start date in use (demo mode may move it). */
    val tanggalMulai: LocalDate,
    val hariKe: Int,
    val totalHari: Int,
    val fase: Fase,
    val sisaHari: Int,
    val jamMinum: LocalTime?,
    /** Today's dose; null before the start or after the last day. */
    val hariIni: CatatanHari?,
    /** Every day from the start to today, oldest first. */
    val semuaHari: List<CatatanHari>,
    val minggu: List<Pair<LocalDate, CatatanHari?>>,
    /** Box openings outside every dose window, newest first. */
    val diLuarJadwal: List<Instant>,
    val terakhirDibuka: Instant?,
    val hariRawan: HasilHariRawan,
    val terlewatBerturut: Int,
    val dosisTercatat: Int,
)

/**
 * Patient-mode owner for the life of the process, like [BraceletManager] for the bracelet.
 * Reads the rules once at start (PRD section 8: changes apply after the app is reopened),
 * records box openings and confirmations, and works out [keadaan] from them with the pure
 * functions in `adherence/`.
 */
class PasienManager(
    context: Context,
    private val db: PasienDatabase,
    val repo: PasienRepository,
    scope: CoroutineScope,
) {
    private val dao = db.pasien()
    val zona: ZoneId get() = ZoneId.systemDefault()

    /** The program rules and any warnings about them, read when the app starts. */
    val aturan: HasilBacaAturan = PembacaAturan.baca(
        teksUtama = runCatching { context.assets.open(FILE_ATURAN).bufferedReader().use { it.readText() } }.getOrNull(),
        teksCadangan = context.assets.open(FILE_CADANGAN).bufferedReader().use { it.readText() },
    )

    private val detak = flow {
        while (true) {
            emit(Unit)
            delay(DETAK_MS)
        }
    }

    val pengaturan: StateFlow<PengaturanPasien?> = repo.pengaturan.stateIn(scope, SharingStarted.Eagerly, null)

    val keadaan: StateFlow<KeadaanPasien?> =
        combine(repo.pengaturan, dao.semuaKejadian(), dao.jamMinum(), detak) { p, kejadian, jam, _ ->
            hitung(p, kejadian, jam, Instant.now())
        }.stateIn(scope, SharingStarted.Eagerly, null)

    private fun hitung(p: PengaturanPasien, kejadian: List<KejadianPasien>, jamDb: List<JamMinumEntity>, nyata: Instant): KeadaanPasien? {
        val mulaiNyata = p.tanggalMulai ?: return null
        val a = aturan.aturan
        val sekarang = RingkasanHarian.sekarang(nyata, p.demo)
        val hariIniTanggal = sekarang.atZone(zona).toLocalDate()
        val mulai = RingkasanHarian.tanggalMulai(mulaiNyata, p.demo)
        val riwayatJam = jamDb.map { JamMinumBerlaku(LocalDate.parse(it.berlakuMulai), LocalTime.ofSecondOfDay(it.menitDalamHari * 60L)) }

        // Demo rows count only while demo mode is on.
        val dipakai = kejadian.filter { p.demo.aktif || !it.demo }
        val buka = dipakai.filter { it.jenis == JenisKejadian.LID_OPEN.name }.map { Instant.ofEpochMilli(it.waktu) }
        val manual = dipakai.filter { it.jenis == JenisKejadian.KONFIRMASI_MANUAL.name }.map { Instant.ofEpochMilli(it.waktu) }

        val hariKe = LogikaKepatuhan.hariKe(mulai, hariIniTanggal)
        val sampai = minOf(hariIniTanggal, mulai.plusDays(a.pengobatan.totalHari - 1L))
        val semua = RingkasanHarian.hari(mulai, sampai, riwayatJam, zona, sekarang, buka, manual, a.dosis)
        val statusHarian = semua.map { it.status }
        return KeadaanPasien(
            pengaturan = p,
            sekarang = sekarang,
            hariIniTanggal = hariIniTanggal,
            tanggalMulai = mulai,
            hariKe = hariKe,
            totalHari = a.pengobatan.totalHari,
            fase = LogikaKepatuhan.fase(hariKe, a.pengobatan),
            sisaHari = LogikaKepatuhan.sisaHari(hariKe, a.pengobatan),
            jamMinum = RingkasanHarian.jamMinumPada(hariIniTanggal, riwayatJam),
            hariIni = semua.lastOrNull()?.takeIf { it.tanggal == hariIniTanggal },
            semuaHari = semua,
            minggu = RingkasanHarian.mingguIni(hariIniTanggal, semua),
            diLuarJadwal = RingkasanHarian.dibukaDiLuarJadwal(buka, mulai, riwayatJam, zona, sekarang, a.dosis),
            terakhirDibuka = buka.filter { !it.isAfter(sekarang) }.maxOrNull(),
            hariRawan = LogikaKepatuhan.hariRawan(hariKe, hariIniTanggal, statusHarian, a.hariRawan, a.pengobatan),
            terlewatBerturut = LogikaKepatuhan.terlewatBerturut(statusHarian),
            dosisTercatat = RingkasanHarian.dosisTercatat(semua),
        )
    }

    private suspend fun sekarang(): Pair<Instant, Boolean> {
        val p = repo.pengaturan.first()
        return RingkasanHarian.sekarang(Instant.now(), p.demo) to p.demo.aktif
    }

    // --- Start (P2) and settings (P8) -----------------------------------------------------

    suspend fun mulai(nama: String, tanggalMulai: LocalDate, jamMinum: LocalTime) {
        dao.hapusJamMinum()
        dao.simpanJamMinum(JamMinumEntity(tanggalMulai.toString(), jamMinum.toSecondOfDay() / 60))
        repo.mulai(nama, tanggalMulai)
    }

    /** A new `jam_minum` from today on; earlier days keep theirs. */
    suspend fun ubahJamMinum(jam: LocalTime) {
        val p = repo.pengaturan.first()
        val (waktu, _) = sekarang()
        val hariIni = waktu.atZone(zona).toLocalDate()
        // Never before the real start, so the history always has an entry on the start date.
        val berlaku = maxOf(hariIni, p.tanggalMulai ?: hariIni)
        dao.simpanJamMinum(JamMinumEntity(berlaku.toString(), jam.toSecondOfDay() / 60))
    }

    // --- Records -----------------------------------------------------------------------------

    /**
     * The one path for every box opening: the simulated box now, the real box over BLE from
     * stage 4 (its event time, or receive time when the box clock wasn't set).
     */
    suspend fun catatPembukaanKotak(waktu: Instant, sumber: SumberKejadian, demo: Boolean) {
        dao.tambah(KejadianPasien(
            jenis = JenisKejadian.LID_OPEN.name, waktu = waktu.toEpochMilli(),
            diterimaPada = System.currentTimeMillis(), sumber = sumber.name, demo = demo,
        ))
    }

    suspend fun bukaKotakSimulasi() {
        val (waktu, demo) = sekarang()
        catatPembukaanKotak(waktu, SumberKejadian.SIMULASI, demo)
    }

    /** "Saya sudah minum obat". */
    suspend fun konfirmasiManual() {
        val (waktu, demo) = sekarang()
        dao.tambah(KejadianPasien(
            jenis = JenisKejadian.KONFIRMASI_MANUAL.name, waktu = waktu.toEpochMilli(),
            diterimaPada = System.currentTimeMillis(), sumber = SumberKejadian.PASIEN.name, demo = demo,
        ))
    }

    // --- Demo mode (PRD section 9) -----------------------------------------------------------

    suspend fun setDemoAktif(aktif: Boolean) = repo.setDemoAktif(aktif)

    suspend fun lompatKeHari(hariKe: Int) {
        val (waktu, _) = sekarang()
        repo.setDemoTanggalMulai(RingkasanHarian.tanggalMulaiUntukHariKe(waktu.atZone(zona).toLocalDate(), hariKe))
        repo.setDemoAktif(true)
    }

    suspend fun majukanJam(geser: Duration) {
        val p = repo.pengaturan.first()
        repo.setDemoGeser(p.demo.geserJam.plus(geser))
        repo.setDemoAktif(true)
    }

    /** Moves the virtual clock just past today's missed limit. */
    suspend fun majukanKeBatasTerlewat() {
        val hariIni = keadaan.value?.hariIni ?: return
        val (waktu, _) = sekarang()
        majukanJam(RingkasanHarian.geserKeBatasTerlewat(hariIni.jadwal, waktu))
    }

    /** Sample history for the days before today (PRD: e.g. 5 taken, 2 missed). Replaces earlier demo rows. */
    suspend fun isiRiwayatContoh() {
        val k = keadaan.value ?: return
        val riwayatJam = dao.jamMinum().first().map {
            JamMinumBerlaku(LocalDate.parse(it.berlakuMulai), LocalTime.ofSecondOfDay(it.menitDalamHari * 60L))
        }
        val buka = RingkasanHarian.riwayatContoh(k.hariIniTanggal, k.tanggalMulai, POLA_CONTOH, riwayatJam, zona)
        dao.hapusDemo()
        dao.tambahSemua(buka.map {
            KejadianPasien(
                jenis = JenisKejadian.LID_OPEN.name, waktu = it.toEpochMilli(), diterimaPada = System.currentTimeMillis(),
                sumber = SumberKejadian.SIMULASI.name, demo = true,
            )
        })
        repo.setDemoAktif(true)
    }

    /** "Atur ulang demo": demo rows gone, real clock and start date back. */
    suspend fun aturUlangDemo() {
        dao.hapusDemo()
        repo.aturUlangDemo()
    }

    /** "Berhenti ikut program": deletes every patient record on this phone. */
    suspend fun hapusSemuaData() {
        dao.hapusSemuaKejadian()
        dao.hapusJamMinum()
        repo.hapusSemua()
    }

    private companion object {
        const val FILE_ATURAN = "aturan_program.json"
        const val FILE_CADANGAN = "aturan_bawaan.json"
        const val DETAK_MS = 15_000L
        /** 5 days taken, then 2 missed (yesterday and the day before). */
        val POLA_CONTOH = listOf(true, true, true, true, true, false, false)
    }
}
