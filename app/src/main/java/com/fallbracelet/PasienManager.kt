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
import com.fallbracelet.adherence.ModeBepergian
import com.fallbracelet.adherence.PeriodeBepergian
import com.fallbracelet.adherence.PembacaAturan
import com.fallbracelet.adherence.Pemicu
import com.fallbracelet.adherence.RencanaEskalasi
import com.fallbracelet.adherence.RingkasanHarian
import com.fallbracelet.adherence.StatusDosis
import com.fallbracelet.adherence.Tindakan
import com.fallbracelet.data.AppMode
import com.fallbracelet.data.AppSettings
import com.fallbracelet.data.EskalasiEntity
import com.fallbracelet.data.JamMinumEntity
import com.fallbracelet.data.JenisKejadian
import com.fallbracelet.data.KejadianPasien
import com.fallbracelet.data.LogPengingat
import com.fallbracelet.data.PasienDatabase
import com.fallbracelet.data.PasienRepository
import com.fallbracelet.data.PengaturanPasien
import com.fallbracelet.data.SettingsRepository
import com.fallbracelet.data.SumberKejadian
import com.fallbracelet.pengingat.PengingatPasien
import com.fallbracelet.relay.WearerRelay
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
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
    /** ESKALASI records of the current mode (demo or real), oldest first. */
    val eskalasi: List<EskalasiEntity> = emptyList(),
    /** Travel mode stretches of the current mode (demo or real). */
    val periodeBepergian: List<PeriodeBepergian> = emptyList(),
    /** The stretch covering today, if travel mode is on (P9). */
    val bepergian: PeriodeBepergian? = null,
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
    /** The relay, shared with the PMO tab (invites) and [BraceletManager] (heartbeats). */
    private val relay: WearerRelay,
    private val pengaturanHp: SettingsRepository,
    /** The phone's settings (mode, relay address and token), shared with [BraceletManager]. */
    private val hpFlow: StateFlow<AppSettings>,
) {
    private fun hp(): AppSettings = hpFlow.value

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

    private val catatan = combine(dao.logPengingat(), dao.eskalasi()) { log, esk -> log to esk }

    val keadaan: StateFlow<KeadaanPasien?> =
        combine(repo.pengaturan, dao.semuaKejadian(), dao.jamMinum(), catatan, detak) { p, kejadian, jam, (log, esk), _ ->
            hitung(p, kejadian, jam, log, esk, Instant.now())
        }.stateIn(scope, SharingStarted.Eagerly, null)

    // Declared before init: the collector below may run at once.
    private val kunciPengingat = Mutex()
    /** The alarm last set, and whether it was exact, so it is only set again when it changes. */
    private var alarmTerakhir: Pair<Instant, Boolean>? = null

    /** Wakes the PMO sync at once (a new ESKALASI, a changed day); it also runs on its own timer. */
    private val pemicuSinkron = Channel<Unit>(Channel.CONFLATED)

    init {
        // Every new state (each 15 s tick, every new record, every demo clock change) checks
        // whether a reminder or an ESKALASI is due and sets the alarm for the next moment.
        scope.launch { keadaan.collect { periksaPengingat() } }
        scope.launch { sinkronLoop() }
        // A changed day (a dose taken or missed, travel mode, a new day): the PMO's row follows at once.
        scope.launch {
            keadaan.filterNotNull().map { ringkasanJson(it).toString() }.distinctUntilChanged().collect { pemicuSinkron.trySend(Unit) }
        }
        // A new relay address (typed, found on the WiFi) or a new registration: sync at once.
        scope.launch {
            hpFlow.map { it.relayUrl to it.wearerToken }.distinctUntilChanged().collect { pemicuSinkron.trySend(Unit) }
        }
    }

    /** The state right now, read straight from storage (for the alarm, which can't wait for a tick). */
    private suspend fun hitungSekarang(): KeadaanPasien? = hitung(
        repo.pengaturan.first(), dao.semuaKejadian().first(), dao.jamMinum().first(),
        dao.logPengingat().first(), dao.eskalasi().first(), Instant.now(),
    )

    private fun hitung(
        p: PengaturanPasien,
        kejadian: List<KejadianPasien>,
        jamDb: List<JamMinumEntity>,
        log: List<LogPengingat>,
        esk: List<EskalasiEntity>,
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
        val periode = p.bepergian.filter { it.demo == p.demo.aktif }
        val bepergianPada = { t: LocalDate -> ModeBepergian.aktifPada(t, periode) }
        val semua = RingkasanHarian.hari(
            mulai, sampai, riwayatJam, zona, sekarang, buka, manual, a.dosis,
            bepergianPada = bepergianPada, dipantauSejak = dipantau,
        )
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
            // In demo mode only this run's rows (see PasienRepository.demoSesi).
            eskalasi = esk.filter { if (p.demo.aktif) it.demo && it.perangkat == "demo-" + p.demo.sesi else !it.demo },
            periodeBepergian = periode,
            bepergian = ModeBepergian.aktif(hariIniTanggal, periode),
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

    /**
     * LID_OPEN from a box (the simulated ESP32 now, the real box over BLE later). [waktu] is the
     * box's time, or the receive time when its clock wasn't set (PRD F3). False when this
     * (box, seq) was already stored: a resend after a lost ACK.
     */
    suspend fun catatDariKotak(waktu: Instant, sumber: SumberKejadian, perangkat: String, seq: Long): Boolean {
        val demo = repo.pengaturan.first().demo.aktif
        return dao.tambahDariKotak(KejadianPasien(
            jenis = JenisKejadian.LID_OPEN.name, waktu = waktu.toEpochMilli(),
            diterimaPada = System.currentTimeMillis(), sumber = sumber.name, demo = demo,
            perangkat = perangkat, seqKotak = seq,
        )) != -1L
    }

    /** The time the box should keep: the app's clock (the virtual one in demo mode) and the UTC offset. */
    suspend fun waktuUntukKotak(): Pair<Long, Int> {
        val (waktu, _) = sekarang()
        val selisih = zona.rules.getOffset(waktu).totalSeconds / 60
        return waktu.epochSecond to selisih
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
        catatEskalasi(k)

        // Also wake at the next missed limit, when an ESKALASI may become due. The alarm runs on
        // the real clock; in demo mode the virtual clock is ahead by geserJam.
        val berikutVirtual = listOfNotNull(rencana.berikutnya, RencanaEskalasi.cekBerikutnya(jadwalDipantau(k), k.sekarang)).minOrNull()
        val berikut = berikutVirtual?.let { if (demo) it.minus(k.pengaturan.demo.geserJam) else it }
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

    // --- ESKALASI and the daily summary for the PMO (PRD 7.4, stage 3) ----------------------

    /**
     * Records the missed days that need an ESKALASI (see [RencanaEskalasi]) and wakes the sync.
     * Each date gets one record per mode and demo run, so nothing is sent twice.
     */
    private suspend fun catatEskalasi(k: KeadaanPasien) {
        if (!k.pengaturan.sudahMulai) return
        val a = aturan.aturan
        val demo = k.pengaturan.demo.aktif
        // k.eskalasi holds this mode's rows only, and "Atur ulang demo" deletes the demo ones,
        // so in demo mode these are the current run's.
        val sudah = k.eskalasi.map { LocalDate.parse(it.tanggal) }.toSet()
        val rencana = RencanaEskalasi.rencanakan(k.semuaHari, sudah, a.eskalasi, a.modeBepergian) {
            ModeBepergian.aktifPada(it, k.periodeBepergian)
        }
        if (rencana.kirim == null) return
        val perangkat = if (demo) "demo-" + repo.demoSesi() else EskalasiEntity.PERANGKAT_NYATA
        fun rekam(tanggal: LocalDate, kirim: Boolean) = EskalasiEntity(
            kunci = "$perangkat/$tanggal", tanggal = tanggal.toString(), perangkat = perangkat, demo = demo,
            dibuatPada = k.sekarang.toEpochMilli(), hariKe = LogikaKepatuhan.hariKe(k.tanggalMulai, tanggal),
            terlewatBerturut = RencanaEskalasi.terlewatBerturutPada(k.semuaHari, tanggal),
            terakhirDibuka = k.terakhirDibuka?.toEpochMilli(), hariRawan = k.hariRawan.rawan, kirim = kirim,
            terkirimPada = null, idRelay = null, dilihatOleh = null, dilihatPada = null,
        )
        rencana.dilewati.forEach { dao.tambahEskalasi(rekam(it, kirim = false)) }
        dao.tambahEskalasi(rekam(rencana.kirim, kirim = true))
        pemicuSinkron.trySend(Unit)
    }

    /**
     * Sends waiting ESKALASI and the daily summary to the relay, and picks up "Dilihat oleh …".
     * Runs when woken and on a timer: every 30 s while an ESKALASI waits to be sent or seen,
     * otherwise every 5 min. Only for a patient phone that is set up and has a relay address.
     */
    private suspend fun sinkronLoop() {
        keadaan.filterNotNull().first()
        while (true) {
            val cepat = runCatching { sinkronPmo() }.getOrDefault(true)
            withTimeoutOrNull(if (cepat) SINKRON_CEPAT_MS else SINKRON_MS) { pemicuSinkron.receive() }
        }
    }

    private var ringkasanTerkirim: String? = null
    private var ringkasanTerkirimPada = 0L

    /** One sync round; true when it should run again soon. */
    private suspend fun sinkronPmo(): Boolean {
        val k = keadaan.value ?: return false
        val s = hp()
        if (!k.pengaturan.sudahMulai || s.mode != AppMode.WEARER || s.relayUrl.isBlank()) return false
        // The relay shows the patient's nickname to the PMO (nothing else identifies them).
        if (s.wearerName != k.pengaturan.namaPanggilan) pengaturanHp.setWearerName(k.pengaturan.namaPanggilan)
        relay.ensureRegistered()
        if (!relay.configured) return true

        val a = aturan.aturan
        for (e in dao.eskalasiMenunggu()) {
            val id = relay.kirimEskalasi(JSONObject()
                .put("deviceAddress", e.perangkat)
                .put("seq", LocalDate.parse(e.tanggal).toEpochDay())
                .put("type", "ESKALASI")
                .put("occurredAt", e.dibuatPada)
                .put("message", a.notifikasi.pmoEskalasi)
                .put("demo", e.demo)
                .put("ulangKirimMenit", a.eskalasi.ulangKirimMenit)
                .put("maksUlang", a.eskalasi.maksUlang)
                .put("detail", JSONObject()
                    .put("tanggal", e.tanggal).put("hariKe", e.hariKe).put("terlewatBerturut", e.terlewatBerturut)
                    .put("terakhirDibuka", e.terakhirDibuka ?: JSONObject.NULL).put("hariRawan", e.hariRawan)))
            dao.eskalasiTerkirim(e.kunci, System.currentTimeMillis(), id)
        }

        // Read from storage, not k: an ESKALASI recorded a moment ago may not be in k yet. Only the
        // last day's count, so one the PMO never opens doesn't keep the phone polling for good.
        val menunggu = dao.jumlahBelumDilihat(System.currentTimeMillis() - 24 * 3600_000L) > 0
        val ringkasan = ringkasanJson(k)
        val teks = ringkasan.toString()
        if (teks != ringkasanTerkirim || menunggu || System.currentTimeMillis() - ringkasanTerkirimPada >= SINKRON_MS) {
            val status = relay.kirimStatusHarian(ringkasan)
            ringkasanTerkirim = teks
            ringkasanTerkirimPada = System.currentTimeMillis()
            for (st in status) {
                val oleh = st.by ?: continue
                dao.eskalasiDilihat(st.deviceAddress, LocalDate.ofEpochDay(st.seq).toString(), oleh, st.at ?: System.currentTimeMillis())
            }
        }
        return dao.jumlahBelumDilihat(System.currentTimeMillis() - 24 * 3600_000L) > 0 || dao.eskalasiMenunggu().isNotEmpty()
    }

    /** STATUS_HARIAN: the last [HARI_RINGKASAN] days for the PMO's row, the day number and the mode. */
    private fun ringkasanJson(k: KeadaanPasien): JSONObject {
        val hari = JSONArray()
        k.semuaHari.takeLast(HARI_RINGKASAN).forEach { h ->
            hari.put(JSONObject().put("tanggal", h.tanggal.toString()).put("hariKe", h.hariKe).put("status", h.status.name)
                .put("waktu", h.waktuTercatat?.toEpochMilli() ?: JSONObject.NULL))
        }
        return JSONObject()
            .put("tanggal", k.hariIniTanggal.toString())
            .put("demo", k.pengaturan.demo.aktif)
            .put("hariKe", k.hariKe)
            .put("totalHari", k.totalHari)
            .put("terakhirDibuka", k.terakhirDibuka?.toEpochMilli() ?: JSONObject.NULL)
            .put("hariRawan", k.hariRawan.rawan)
            .put("terlewatBerturut", k.terlewatBerturut)
            .put("bepergianSampai", k.bepergian?.sampai?.toString() ?: JSONObject.NULL)
            .put("hari", hari)
    }

    /** "Kirim ulang ke relay sekarang", e.g. after the relay address changed. */
    fun sinkronSekarang() { pemicuSinkron.trySend(Unit) }

    // --- Travel mode (P9) ------------------------------------------------------------------------

    /** Travel mode from today for [lamaHari] days (at most `mode_bepergian.maks_hari`); the PMO sees it. */
    suspend fun nyalakanBepergian(lamaHari: Int) {
        val p = repo.pengaturan.first()
        val (waktu, demo) = sekarang()
        val hariIni = waktu.atZone(zona).toLocalDate()
        val lain = p.bepergian.filter { it.demo != demo }
        val ini = p.bepergian.filter { it.demo == demo }
        repo.setBepergian(lain + ModeBepergian.nyalakan(hariIni, lamaHari, aturan.aturan.modeBepergian, ini, demo))
        pemicuSinkron.trySend(Unit)
    }

    /** Travel mode off from today; the days already in it keep their status. */
    suspend fun matikanBepergian() {
        val p = repo.pengaturan.first()
        val (waktu, demo) = sekarang()
        val hariIni = waktu.atZone(zona).toLocalDate()
        val lain = p.bepergian.filter { it.demo != demo }
        repo.setBepergian(lain + ModeBepergian.matikan(hariIni, p.bepergian.filter { it.demo == demo }))
        pemicuSinkron.trySend(Unit)
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
        // Under the reminder lock, and demo off before the rows go: otherwise a check running in
        // between still sees demo mode, finds its rows gone and records them again.
        kunciPengingat.withLock {
            repo.aturUlangDemo()
            dao.hapusDemo()
            dao.hapusLogDemo()
            dao.hapusEskalasiDemo()
        }
        pemicuSinkron.trySend(Unit)
    }

    /** "Berhenti ikut program": deletes every patient record on this phone. */
    suspend fun hapusSemuaData() {
        dao.hapusSemuaKejadian()
        dao.hapusJamMinum()
        dao.hapusSemuaLog()
        dao.hapusSemuaEskalasi()
        repo.hapusSemua()
        pengingat.hapusNotifikasi()
    }

    companion object {
        private const val FILE_ATURAN = "aturan_program.json"
        private const val FILE_CADANGAN = "aturan_bawaan.json"
        private const val DETAK_MS = 15_000L
        private const val SINKRON_CEPAT_MS = 30_000L
        private const val SINKRON_MS = 5 * 60_000L
        /** Days in each STATUS_HARIAN: the PMO's 7-day row. */
        private const val HARI_RINGKASAN = 7
        /** 5 days taken, then 2 missed (yesterday and the day before). */
        private val POLA_CONTOH = listOf(true, true, true, true, true, false, false)
        /** The `kapan` of the special-reminder messages in the rules file (PRD section 8). */
        const val PESAN_HARI_RAWAN = "HARI_RAWAN"

        fun awalan(demo: Boolean) = if (demo) "demo/" else ""
    }
}
