package com.fallbracelet.caregiver

import org.json.JSONObject
import java.time.LocalDate

/*
 * What the PMO side shows, read from the relay the same way as relay/web/index.html: the
 * dashboard per patient, the daily summary (STATUS_HARIAN), the 7-day row and each ESKALASI.
 * Plain Kotlin (no Android types) so it is unit tested.
 */

/** `detail` of an ESKALASI, as the patient's phone sent it. */
data class DetailEskalasi(
    val tanggal: LocalDate?,
    val hariKe: Int?,
    val terlewatBerturut: Int?,
    /** Epoch ms of the last box opening; null = never opened. */
    val terakhirDibuka: Long?,
    val hariRawan: Boolean,
) {
    companion object {
        fun dari(o: JSONObject?): DetailEskalasi? = o?.let {
            DetailEskalasi(
                tanggal = it.tanggalAtauNull("tanggal"),
                hariKe = it.angkaAtauNull("hariKe"),
                terlewatBerturut = it.angkaAtauNull("terlewatBerturut"),
                terakhirDibuka = it.panjangAtauNull("terakhirDibuka"),
                hariRawan = it.optBoolean("hariRawan"),
            )
        }
    }
}

/** One event from the relay. Only ESKALASI is shown to the PMO. */
data class KabarPmo(
    val id: Long,
    val wearerId: Long,
    val type: String,
    val message: String,
    val receivedAt: Long,
    val ackedBy: String?,
    val ackedAt: Long?,
    /** How many times the relay has sent it again because nobody marked it seen. */
    val escalations: Int,
    val demo: Boolean,
    val detail: DetailEskalasi?,
) {
    val eskalasi: Boolean get() = type == ESKALASI
    val dilihat: Boolean get() = ackedAt != null

    companion object {
        const val ESKALASI = "ESKALASI"

        fun dari(o: JSONObject) = KabarPmo(
            id = o.getLong("id"),
            wearerId = o.optLong("wearerId"),
            type = o.optString("type"),
            message = o.teksAtauKosong("message"),
            receivedAt = o.panjangAtauNull("receivedAt") ?: o.panjangAtauNull("occurredAt") ?: System.currentTimeMillis(),
            ackedBy = o.teksAtauKosong("ackedBy").ifEmpty { null },
            ackedAt = o.panjangAtauNull("ackedAt"),
            escalations = o.optInt("escalations"),
            demo = o.optBoolean("demo"),
            detail = DetailEskalasi.dari(o.optJSONObject("detail")),
        )
    }
}

/** The patient's latest daily summary on the relay. */
data class RingkasanPmo(
    /** The patient phone's "today" (the virtual one in demo mode). */
    val tanggal: LocalDate?,
    val demo: Boolean,
    val hariKe: Int?,
    val totalHari: Int?,
    /** When the relay got it, epoch ms. */
    val at: Long?,
    /** Travel mode (P9): its last day, or null when off. */
    val bepergianSampai: LocalDate?,
) {
    companion object {
        fun dari(o: JSONObject?): RingkasanPmo? = o?.let {
            RingkasanPmo(
                tanggal = it.tanggalAtauNull("tanggal"),
                demo = it.optBoolean("demo"),
                hariKe = it.angkaAtauNull("hariKe"),
                totalHari = it.angkaAtauNull("totalHari"),
                at = it.panjangAtauNull("at"),
                bepergianSampai = it.tanggalAtauNull("bepergianSampai"),
            )
        }
    }
}

/** One day of the 7-day row; [status] is a `StatusDosis` name. */
data class HariPmo(val tanggal: LocalDate, val status: String)

/** A patient this PMO follows. */
data class PasienPmo(
    val id: Long,
    val nama: String,
    val offline: Boolean,
    val lastHeartbeat: Long?,
    val ringkasan: RingkasanPmo?,
    val hari: List<HariPmo>,
    /** Newest first. */
    val kabar: List<KabarPmo>,
) {
    /** ESKALASI of the mode (demo or real) the patient's phone is in now, newest first. */
    val eskalasi: List<KabarPmo> get() = kabar.filter { it.eskalasi && sesuaiMode(it) }
    val belumDilihat: List<KabarPmo> get() = eskalasi.filter { !it.dilihat }

    /** Whether [e] belongs to the mode the patient's phone is in now (unknown mode: yes). */
    fun sesuaiMode(e: KabarPmo): Boolean = ringkasan == null || e.demo == ringkasan.demo

    companion object {
        fun dari(o: JSONObject): PasienPmo {
            val ev = o.optJSONArray("events")
            return PasienPmo(
                id = o.getLong("id"),
                nama = o.optString("name"),
                offline = o.optBoolean("offline"),
                lastHeartbeat = o.panjangAtauNull("lastHeartbeat"),
                ringkasan = RingkasanPmo.dari(o.optJSONObject("ringkasan")),
                hari = hariDari(o),
                kabar = if (ev == null) emptyList() else urutkan(List(ev.length()) { KabarPmo.dari(ev.getJSONObject(it)) }),
            )
        }

        /** The `days` array of a dashboard entry or a `days` stream message. */
        fun hariDari(o: JSONObject): List<HariPmo> {
            val a = o.optJSONArray("days") ?: return emptyList()
            return (0 until a.length()).mapNotNull { i ->
                val d = a.getJSONObject(i)
                d.tanggalAtauNull("tanggal")?.let { HariPmo(it, d.optString("status")) }
            }
        }

        fun urutkan(kabar: List<KabarPmo>): List<KabarPmo> =
            kabar.sortedWith(compareByDescending<KabarPmo> { it.receivedAt }.thenByDescending { it.id })
    }
}

/** One cell of the 7-day row: [simbol] ✓ ✗ ! • – or empty before the treatment started. */
data class SelMinggu(val tanggal: LocalDate, val simbol: String, val jenis: JenisSel)

enum class JenisSel { BAIK, BURUK, WASPADA, NETRAL }

object TampilanPmo {
    /** Unseen ESKALASI this recent ring when the app (re)opens, like the web page. */
    const val JENDELA_BERDERING_MS = 12 * 3600_000L

    /** The last 7 days up to the patient phone's "today", oldest first; null before the first summary. */
    fun minggu(p: PasienPmo): List<SelMinggu>? {
        val hariIni = p.ringkasan?.tanggal ?: return null
        val perTanggal = p.hari.associateBy { it.tanggal }
        return (-6..0).map { i ->
            val t = hariIni.plusDays(i.toLong())
            val (simbol, jenis) = perTanggal[t]?.let { simbol(it.status) } ?: ("" to JenisSel.NETRAL)
            SelMinggu(t, simbol, jenis)
        }
    }

    /** The web page's symbols: ✓ taken (box, confirmed, or confirmed while travelling), ✗ missed, ! late, • not yet, – not monitored. */
    fun simbol(status: String): Pair<String, JenisSel> = when (status) {
        "DIMINUM_KOTAK", "DIKONFIRMASI_MANUAL", "BEPERGIAN" -> "✓" to JenisSel.BAIK
        "TERLEWAT" -> "✗" to JenisSel.BURUK
        "TERLAMBAT" -> "!" to JenisSel.WASPADA
        "BELUM" -> "•" to JenisSel.NETRAL
        "TANPA_DATA" -> "–" to JenisSel.NETRAL
        else -> "?" to JenisSel.NETRAL
    }

    /** Whether [e] should ring on this phone: an unseen ESKALASI of the patient's current mode, recent enough. */
    fun perluBerdering(e: KabarPmo, p: PasienPmo?, sekarang: Long): Boolean =
        e.eskalasi && !e.dilihat && (p == null || p.sesuaiMode(e)) && sekarang - e.receivedAt <= JENDELA_BERDERING_MS

    /** Adds or replaces [e] in [p]'s list, newest first. */
    fun denganKabar(p: PasienPmo, e: KabarPmo): PasienPmo {
        val lama = p.kabar.firstOrNull { it.id == e.id }
        // A re-send or replay carries no ack fields; keep what is already known.
        val baru = if (lama != null && e.ackedAt == null && lama.ackedAt != null) e.copy(ackedBy = lama.ackedBy, ackedAt = lama.ackedAt) else e
        return p.copy(kabar = PasienPmo.urutkan(listOf(baru) + p.kabar.filter { it.id != e.id }))
    }

    /** A new daily summary; when demo mode started or ended, the other mode's ESKALASI are gone from the relay. */
    fun denganRingkasan(p: PasienPmo, r: RingkasanPmo?, hari: List<HariPmo>): PasienPmo {
        val gantiMode = p.ringkasan != null && r != null && p.ringkasan.demo != r.demo
        return p.copy(
            ringkasan = r,
            hari = hari,
            kabar = if (gantiMode) p.kabar.filter { !it.eskalasi || it.demo == r!!.demo } else p.kabar,
        )
    }

    fun denganDilihat(p: PasienPmo, eventId: Long, oleh: String, pada: Long): PasienPmo =
        p.copy(kabar = p.kabar.map { if (it.id == eventId && it.ackedAt == null) it.copy(ackedBy = oleh, ackedAt = pada) else it })
}

// --- JSON helpers: the relay sends null for missing values -------------------------------------

private fun JSONObject.ada(k: String) = has(k) && !isNull(k)
private fun JSONObject.angkaAtauNull(k: String): Int? = if (ada(k)) optInt(k) else null
private fun JSONObject.panjangAtauNull(k: String): Long? = if (ada(k)) optLong(k).takeIf { it > 0 } else null
private fun JSONObject.teksAtauKosong(k: String): String = if (ada(k)) optString(k) else ""
private fun JSONObject.tanggalAtauNull(k: String): LocalDate? =
    if (ada(k)) runCatching { LocalDate.parse(getString(k)) }.getOrNull() else null
