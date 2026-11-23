package com.fallbracelet.adherence

import java.time.Instant
import java.time.LocalDate

/** What starts a reminder for one dose. */
enum class JenisPemicu {
    /** At `jam_minum`. */
    JAM_MINUM,
    /** When the dose turns late (`batas_terlambat_menit`). */
    TERLAMBAT,
    /** A later repeat from `pengingat_ulang` (the first repeat falls on TERLAMBAT itself). */
    PENGINGAT_ULANG,
    /** "Ingatkan x menit lagi" pressed on the special reminder. */
    TUNDA,
}

/** One reminder moment for the dose of [tanggal]. */
data class Pemicu(val tanggal: LocalDate, val jenis: JenisPemicu, val urutan: Int, val waktu: Instant) {
    /** Unique per dose and moment, so each fires once. */
    val kunci: String get() = "$tanggal/$jenis/$urutan"
}

/** What a reminder moment does. */
enum class Tindakan {
    TIDAK_ADA,
    /** Ordinary notification with `notifikasi.pasien_jam_minum`. */
    NOTIF_JAM_MINUM,
    /** Repeat reminder for a late dose (same text as at `jam_minum`). */
    NOTIF_PENGINGAT_ULANG,
    /** The special reminder (P5): notification with `notifikasi.pasien_hari_rawan` and the full screen. */
    PESAN_KHUSUS,
}

/**
 * The plan at one moment: the reminder to act on now, the ones that came due but are
 * skipped (older than [dijalankan]), and when the next one is due.
 */
data class RencanaPengingat(
    val dijalankan: Pemicu?,
    val dilewati: List<Pemicu>,
    val berikutnya: Instant?,
)

/**
 * When reminders fire and what each does (PRD 7.2, 7.3, P5). Android-free, like
 * [LogikaKepatuhan]; the caller turns the result into notifications and an alarm.
 */
object JadwalPengingat {

    /**
     * The fixed reminder moments of one dose: `jam_minum`, the moment it turns late, and the
     * further repeats of [LogikaKepatuhan.jadwalPengingatUlang] after that.
     */
    fun pemicuDosis(jadwal: JadwalDosis, dosis: AturanDosis): List<Pemicu> {
        val ulang = LogikaKepatuhan.jadwalPengingatUlang(jadwal, dosis)
            .filter { it.isAfter(jadwal.terlambatMulai) }
        return listOf(
            Pemicu(jadwal.tanggal, JenisPemicu.JAM_MINUM, 0, jadwal.jamMinum),
            Pemicu(jadwal.tanggal, JenisPemicu.TERLAMBAT, 0, jadwal.terlambatMulai),
        ) + ulang.mapIndexed { i, w -> Pemicu(jadwal.tanggal, JenisPemicu.PENGINGAT_ULANG, i + 1, w) }
    }

    /**
     * Which reminder to act on at [sekarang]. Of all moments that are due and not yet
     * [ditangani] (keys of [Pemicu.kunci]), only the latest is acted on; older ones are
     * skipped, so a phone that was off, or a demo clock moved forward, gives one reminder
     * instead of a burst. [tunda] holds pending "Ingatkan x menit lagi" moments.
     */
    fun rencanakan(
        jadwal: List<JadwalDosis>,
        tunda: List<Pemicu>,
        ditangani: Set<String>,
        sekarang: Instant,
        dosis: AturanDosis,
    ): RencanaPengingat {
        val semua = jadwal.flatMap { pemicuDosis(it, dosis) } + tunda
        val terbuka = semua.filter { it.kunci !in ditangani }
        val jatuhTempo = terbuka.filter { !it.waktu.isAfter(sekarang) }.sortedBy { it.waktu }
        return RencanaPengingat(
            dijalankan = jatuhTempo.lastOrNull(),
            dilewati = jatuhTempo.dropLast(1),
            berikutnya = terbuka.filter { it.waktu.isAfter(sekarang) }.minOfOrNull { it.waktu },
        )
    }

    /**
     * What [pemicu] does. Nothing once the dose is taken or missed. The special reminder
     * shows at `jam_minum` and when the dose turns late if [LogikaKepatuhan.perluTampilkanPesan]
     * allows it and a message exists ([adaPesan]); a snooze shows it again without counting
     * toward `maks_per_hari`. Otherwise an ordinary reminder.
     */
    fun tindakan(
        pemicu: Pemicu,
        status: StatusDosis,
        hariRawan: HasilHariRawan,
        sudahTampil: Int,
        aturan: AturanProgram,
        adaPesan: Boolean,
    ): Tindakan {
        if (!status.masihBerjalan) return Tindakan.TIDAK_ADA
        val khusus = adaPesan && LogikaKepatuhan.perluTampilkanPesan(hariRawan, status, sudahTampil, aturan.hariRawan)
        return when (pemicu.jenis) {
            JenisPemicu.TUNDA -> if (adaPesan) Tindakan.PESAN_KHUSUS else Tindakan.NOTIF_PENGINGAT_ULANG
            JenisPemicu.JAM_MINUM -> if (khusus) Tindakan.PESAN_KHUSUS else Tindakan.NOTIF_JAM_MINUM
            JenisPemicu.TERLAMBAT -> when {
                khusus -> Tindakan.PESAN_KHUSUS
                aturan.dosis.pengingatUlang.jumlah > 0 -> Tindakan.NOTIF_PENGINGAT_ULANG
                else -> Tindakan.TIDAK_ADA
            }
            JenisPemicu.PENGINGAT_ULANG -> Tindakan.NOTIF_PENGINGAT_ULANG
        }
    }
}
