package com.fallbracelet

import android.content.Context
import com.fallbracelet.adherence.CatatanHari
import com.fallbracelet.adherence.Fase
import com.fallbracelet.adherence.HasilBacaAturan
import com.fallbracelet.adherence.HasilHariRawan
import com.fallbracelet.adherence.JadwalDosis
import com.fallbracelet.adherence.JadwalPengingat
import com.fallbracelet.adherence.JamMinumBerlaku
import com.fallbracelet.adherence.JenisPemicu
import com.fallbracelet.adherence.LogikaKepatuhan
import com.fallbracelet.adherence.PembacaAturan
import com.fallbracelet.adherence.Pemicu
import com.fallbracelet.adherence.RingkasanHarian
import com.fallbracelet.adherence.StatusDosis
import com.fallbracelet.adherence.Tindakan
import com.fallbracelet.data.JamMinumEntity
import com.fallbracelet.data.JenisKejadian
import com.fallbracelet.data.KejadianPasien
import com.fallbracelet.data.LogPengingat
import com.fallbracelet.data.PasienDatabase
import com.fallbracelet.data.PasienRepository
import com.fallbracelet.data.PengaturanPasien
import com.fallbracelet.data.SumberKejadian
import com.fallbracelet.pengingat.PengingatPasien
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    /** The `jam_minum` history. */
    val riwayatJam: List<JamMinumBerlaku>,
    /** Reminder log rows of the current mode (demo or real), oldest first. */
    val logPengingat: List<LogPengingat>,
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
    /** Lives as long as the process; for work that must outlive a screen. */
    val cakupan: CoroutineScope,
    private val pengingat: PengingatPasien,
) {
    private val scope = cakupan
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
        combine(repo.pengaturan, dao.semuaKejadian(), dao.jamMinum(), dao.logPengingat(), detak) { p, kejadian, jam, log, _ ->
            hitung(p, kejadian, jam, log, Instant.now())
        }.stateIn(scope, SharingStarted.Eagerly, null)

    // Declared before init: the collector below may run at once.
    private val kunciPengingat = Mutex()
    /** The alarm last set, and whether it was exact, so it is only set again when it changes. */
    private var alarmTerakhir: Pair<Instant, Boolean>? = null

    init {
        // Every new state (each 15 s tick, every new record, every demo clock change) checks
        // whether a reminder is due and sets the alarm for the next one.
        scope.launch { keadaan.collect { periksaPengingat() } }
    }

    /** The state right now, read straight from storage (for the alarm, which can't wait for a tick). */
    private suspend fun hitungSekarang(): KeadaanPasien? =
        hitung(repo.pengaturan.first(), dao.semuaKejadian().first(), dao.jamMinum().first(), dao.logPengingat().first(), Instant.now())

    private fun hitung(
        p: PengaturanPasien,
        kejadian: List<KejadianPasien>,
        jamDb: List<JamMinumEntity>,
        log: List<LogPengingat>,
        nyata: Instant,
    ): KeadaanPasien? {
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
        val dipantau = RingkasanHarian.dipantauSejak(p.dipantauSejak, p.demo)
        val semua = RingkasanHarian.hari(mulai, sampai, riwayatJam, zona, sekarang, buka, manual, a.dosis, dipantauSejak = dipantau)
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
            riwayatJam = riwayatJam,
            logPengingat = log.filter { it.demo == p.demo.aktif },
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
        repo.mulai(nama, tanggalMulai, System.currentTimeMillis())
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

    // --- P1 Persetujuan ---------------------------------------------------------------------

    /** The patient ticked the consent box; the real time is stored. */
    suspend fun setuju() = repo.setuju(System.currentTimeMillis())

    // --- Reminders (P5, notifications) -------------------------------------------------------

    /**
     * Acts on the reminder that is due now, if any (see [JadwalPengingat]), records it in the
     * reminder log, and sets the alarm for the next one. Safe to call any time; each moment
     * fires once. Nothing happens before consent or before the treatment is set up.
     */
    suspend fun periksaPengingat(): Unit = kunciPengingat.withLock<Unit> {
        val k = hitungSekarang()
        if (k == null || k.pengaturan.persetujuanPada == null) {
            pengingat.batalkanAlarm()
            alarmTerakhir = null
            return@withLock
        }
        val a = aturan.aturan
        val demo = k.pengaturan.demo.aktif
        val awalan = awalan(demo)
        val log = k.logPengingat
        val ditangani = log.filter { it.ditanganiPada != null }.map { it.kunci.removePrefix(awalan) }.toSet()
        val tunda = log.filter { it.jenis == JenisPemicu.TUNDA.name && it.ditanganiPada == null }.map {
            Pemicu(LocalDate.parse(it.tanggal), JenisPemicu.TUNDA, it.urutan, Instant.ofEpochMilli(it.waktuPemicu))
        }
        val rencana = JadwalPengingat.rencanakan(jadwalDipantau(k), tunda, ditangani, k.sekarang, a.dosis)

        rencana.dilewati.forEach { simpanLog(it, demo, k.sekarang, LogPengingat.DILEWATI, null) }
        rencana.dijalankan?.let { p ->
            val status = k.semuaHari.firstOrNull { it.tanggal == p.tanggal }?.status ?: StatusDosis.BELUM
            val rawan = if (p.tanggal == k.hariIniTanggal) k.hariRawan else HasilHariRawan(false, false, false, 0)
            val sudahTampil = log.count {
                it.tanggal == p.tanggal.toString() && it.hasil == Tindakan.PESAN_KHUSUS.name && it.jenis != JenisPemicu.TUNDA.name
            }
            val pesan = LogikaKepatuhan.pilihPesan(a.pesan, PESAN_HARI_RAWAN, LogikaKepatuhan.hariKe(k.tanggalMulai, p.tanggal))
            val tindakan = JadwalPengingat.tindakan(p, status, rawan, sudahTampil, a, adaPesan = pesan != null)
            simpanLog(p, demo, k.sekarang, tindakan.name, pesan?.id?.takeIf { tindakan == Tindakan.PESAN_KHUSUS })
            when (tindakan) {
                Tindakan.NOTIF_JAM_MINUM, Tindakan.NOTIF_PENGINGAT_ULANG -> pengingat.notifBiasa(a.notifikasi.pasienJamMinum)
                Tindakan.PESAN_KHUSUS -> pengingat.pesanKhusus(a.notifikasi.pasienHariRawan, awalan + p.kunci)
                Tindakan.TIDAK_ADA -> Unit
            }
        }
        if (k.hariIni?.status?.tercatat == true) pengingat.hapusNotifikasi()

        // The alarm runs on the real clock; in demo mode the virtual clock is ahead by geserJam.
        val berikut = rencana.berikutnya?.let { if (demo) it.minus(k.pengaturan.demo.geserJam) else it }
        val alarm = berikut?.let { it to pengingat.bolehAlarmTepat() }
        if (alarm != alarmTerakhir) {
            if (berikut == null) pengingat.batalkanAlarm() else pengingat.setelAlarm(berikut)
            alarmTerakhir = alarm
        }
    }

    /** Whether Android allows exact alarms (the warning banner asks for it when not). */
    fun bolehAlarmTepat(): Boolean = pengingat.bolehAlarmTepat()

    /** Doses whose reminders matter now: yesterday's (a window may cross midnight), today's, tomorrow's. */
    private fun jadwalDipantau(k: KeadaanPasien): List<JadwalDosis> {
        val a = aturan.aturan
        val akhir = k.tanggalMulai.plusDays(a.pengobatan.totalHari - 1L)
        val tanggal = listOf(k.hariIniTanggal.minusDays(1), k.hariIniTanggal, k.hariIniTanggal.plusDays(1), k.tanggalMulai)
        return tanggal.distinct()
            .filter { !it.isBefore(k.tanggalMulai) && !it.isAfter(akhir) && !it.isAfter(k.hariIniTanggal.plusDays(1)) }
            .mapNotNull { d -> RingkasanHarian.jamMinumPada(d, k.riwayatJam)?.let { LogikaKepatuhan.jadwal(d, it, zona, a.dosis) } }
    }

    private suspend fun simpanLog(p: Pemicu, demo: Boolean, pada: Instant, hasil: String, pesanId: String?) {
        dao.simpanLog(LogPengingat(
            kunci = awalan(demo) + p.kunci, tanggal = p.tanggal.toString(), jenis = p.jenis.name, urutan = p.urutan,
            waktuPemicu = p.waktu.toEpochMilli(), ditanganiPada = pada.toEpochMilli(), hasil = hasil, pesanId = pesanId,
            aksi = null, aksiPada = null, demo = demo,
        ))
    }

    /** Records the button pressed on the special reminder [kunci] (first press only). */
    suspend fun catatAksi(kunci: String, aksi: String) {
        val (waktu, _) = sekarang()
        dao.catatAksi(kunci, aksi, waktu.toEpochMilli())
    }

    /** "Saya sudah minum obat" on the special reminder. */
    suspend fun sudahMinumDariPesan(kunci: String) {
        konfirmasiManual()
        catatAksi(kunci, LogPengingat.AKSI_SUDAH_MINUM)
        pengingat.hapusNotifikasi()
    }

    /** "Ingatkan x menit lagi": shows the special reminder again after `pengingat_ulang.jarak_menit`. */
    suspend fun tunda(kunci: String) {
        val (waktu, demo) = sekarang()
        val asal = dao.logPengingatSekarang().firstOrNull { it.kunci == kunci } ?: return
        val urutan = dao.logPengingatSekarang().count {
            it.demo == demo && it.tanggal == asal.tanggal && it.jenis == JenisPemicu.TUNDA.name
        } + 1
        val jarak = Duration.ofMinutes(aturan.aturan.dosis.pengingatUlang.jarakMenit.toLong())
        val p = Pemicu(LocalDate.parse(asal.tanggal), JenisPemicu.TUNDA, urutan, waktu.plus(jarak))
        dao.simpanLog(LogPengingat(
            kunci = awalan(demo) + p.kunci, tanggal = asal.tanggal, jenis = p.jenis.name, urutan = urutan,
            waktuPemicu = p.waktu.toEpochMilli(), ditanganiPada = null, hasil = null, pesanId = null,
            aksi = null, aksiPada = null, demo = demo,
        ))
        catatAksi(kunci, LogPengingat.AKSI_TUNDA)
        pengingat.hapusNotifikasi()
        periksaPengingat()
    }

    // --- Demo mode (PRD section 9) -----------------------------------------------------------

    suspend fun setDemoAktif(aktif: Boolean) = repo.setDemoAktif(aktif)

    /**
     * The days jumped over have no records, so they count as unknown, not missed: demo counting
     * starts now unless a sample history already set an earlier start.
     */
    suspend fun lompatKeHari(hariKe: Int) {
        val (waktu, _) = sekarang()
        repo.setDemoTanggalMulai(RingkasanHarian.tanggalMulaiUntukHariKe(waktu.atZone(zona).toLocalDate(), hariKe))
        if (repo.pengaturan.first().demo.dipantauSejak == null) repo.setDemoDipantauSejak(waktu)
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
        // The sample's missed days must count, so demo counting starts at its oldest day.
        val awal = RingkasanHarian.awalRiwayatContoh(k.hariIniTanggal, POLA_CONTOH, zona)
        val sebelumnya = k.pengaturan.demo.dipantauSejak
        if (sebelumnya == null || awal.isBefore(sebelumnya)) repo.setDemoDipantauSejak(awal)
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
        dao.hapusLogDemo()
        repo.aturUlangDemo()
    }

    /** "Berhenti ikut program": deletes every patient record on this phone. */
    suspend fun hapusSemuaData() {
        dao.hapusSemuaKejadian()
        dao.hapusJamMinum()
        dao.hapusSemuaLog()
        repo.hapusSemua()
        pengingat.hapusNotifikasi()
    }

    companion object {
        private const val FILE_ATURAN = "aturan_program.json"
        private const val FILE_CADANGAN = "aturan_bawaan.json"
        private const val DETAK_MS = 15_000L
        /** 5 days taken, then 2 missed (yesterday and the day before). */
        private val POLA_CONTOH = listOf(true, true, true, true, true, false, false)
        /** The `kapan` of the special-reminder messages in the rules file (PRD section 8). */
        const val PESAN_HARI_RAWAN = "HARI_RAWAN"

        fun awalan(demo: Boolean) = if (demo) "demo/" else ""
    }
}
