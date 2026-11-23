package com.fallbracelet.adherence

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * A section of `aturan_program.json` that was missing or invalid and was replaced by the same
 * section from `aturan_bawaan.json`. [bagian] is the JSON key; [alasan] is a technical detail
 * for the developer. The Settings screen words the warning from `strings.xml`.
 */
data class PeringatanAturan(val bagian: String, val alasan: String)

data class HasilBacaAturan(val aturan: AturanProgram, val peringatan: List<PeringatanAturan>)

/**
 * Reads the program rules (PRD section 8). Each top-level section is read from [baca]'s main
 * text; a section that is missing or invalid is taken from the fallback text instead and
 * reported in [HasilBacaAturan.peringatan]. Android-free apart from `org.json`.
 */
object PembacaAturan {

    /** Key names of the top-level sections, in file order. */
    val BAGIAN = listOf(
        "status_nilai", "pengobatan", "dosis", "hari_rawan", "eskalasi", "mode_bepergian", "pesan", "notifikasi",
    )

    /**
     * @param teksUtama contents of `aturan_program.json`, or null if it could not be opened.
     * @param teksCadangan contents of `aturan_bawaan.json`; must be fully valid.
     * @throws IllegalStateException if the fallback itself is invalid (a build mistake, caught by tests).
     */
    fun baca(teksUtama: String?, teksCadangan: String): HasilBacaAturan {
        val cadangan = try {
            JSONObject(teksCadangan)
        } catch (e: JSONException) {
            throw IllegalStateException("aturan_bawaan.json is not valid JSON: ${e.message}", e)
        }
        val peringatan = mutableListOf<PeringatanAturan>()
        val utama: JSONObject? = when (teksUtama) {
            null -> { peringatan += PeringatanAturan("file", "aturan_program.json tidak bisa dibuka"); null }
            else -> try {
                JSONObject(teksUtama)
            } catch (e: JSONException) {
                peringatan += PeringatanAturan("file", "aturan_program.json bukan JSON yang valid: ${e.message}")
                null
            }
        }

        fun <T> bagian(kunci: String, bacaBagian: (JSONObject) -> T): T {
            if (utama != null) {
                try {
                    return bacaBagian(utama)
                } catch (e: Exception) {
                    when (e) {
                        is JSONException, is IllegalArgumentException, is DateTimeParseException ->
                            peringatan += PeringatanAturan(kunci, e.message ?: e.javaClass.simpleName)
                        else -> throw e
                    }
                }
            }
            return try {
                bacaBagian(cadangan)
            } catch (e: Exception) {
                throw IllegalStateException("aturan_bawaan.json, section '$kunci': ${e.message}", e)
            }
        }

        val statusNilai = bagian("status_nilai") { it.teks("status_nilai") }
        val pengobatan = bagian("pengobatan") { bacaPengobatan(it.getJSONObject("pengobatan")) }
        val dosis = bagian("dosis") { bacaDosis(it.getJSONObject("dosis")) }
        val hariRawan = bagian("hari_rawan") { bacaHariRawan(it.getJSONObject("hari_rawan")) }
        val eskalasi = bagian("eskalasi") { bacaEskalasi(it.getJSONObject("eskalasi")) }
        val bepergian = bagian("mode_bepergian") { bacaBepergian(it.getJSONObject("mode_bepergian")) }
        val pesan = bagian("pesan") { bacaDaftarPesan(it.getJSONArray("pesan")) }
        val notifikasi = bagian("notifikasi") { bacaNotifikasi(it.getJSONObject("notifikasi")) }

        val aturan = AturanProgram(statusNilai, pengobatan, dosis, hariRawan, eskalasi, bepergian, pesan, notifikasi)
        return HasilBacaAturan(aturan, peringatan)
    }

    private fun bacaPengobatan(o: JSONObject): AturanPengobatan {
        val total = o.angka("total_hari", min = 1)
        val intensif = o.angka("fase_intensif_hari", min = 0)
        require(intensif <= total) { "fase_intensif_hari ($intensif) melebihi total_hari ($total)" }
        return AturanPengobatan(total, intensif)
    }

    private fun bacaDosis(o: JSONObject): AturanDosis {
        val awal = o.angka("jendela_awal_menit", min = 0)
        val terlambat = o.angka("batas_terlambat_menit", min = 0)
        val terlewat = o.angka("batas_terlewat_menit", min = 1)
        require(terlambat <= terlewat) { "batas_terlambat_menit ($terlambat) melebihi batas_terlewat_menit ($terlewat)" }
        // Each day's window must be shorter than a day, so two days' windows never overlap.
        require(awal + terlewat < MENIT_SEHARI) {
            "jendela_awal_menit + batas_terlewat_menit harus kurang dari $MENIT_SEHARI (sekarang ${awal + terlewat})"
        }
        val ulang = o.getJSONObject("pengingat_ulang")
        return AturanDosis(
            jendelaAwalMenit = awal,
            batasTerlambatMenit = terlambat,
            batasTerlewatMenit = terlewat,
            pengingatUlang = PengingatUlang(ulang.angka("jumlah", min = 0), ulang.angka("jarak_menit", min = 1)),
            durasiAlarmKotakMenit = o.angka("durasi_alarm_kotak_menit", min = 1),
        )
    }

    private fun bacaHariRawan(o: JSONObject): AturanHariRawan {
        val rentang = o.getJSONArray("rentang_hari").let { arr ->
            List(arr.length()) { i ->
                val pasangan = arr.getJSONArray(i)
                require(pasangan.length() == 2) { "rentang_hari[$i] harus berisi 2 angka" }
                val dari = pasangan.angkaDi(0, "rentang_hari[$i][0]")
                val sampai = pasangan.angkaDi(1, "rentang_hari[$i][1]")
                require(dari >= 1 && sampai >= dari) { "rentang_hari[$i] = [$dari, $sampai] tidak valid" }
                dari..sampai
            }
        }
        val libur = o.getJSONArray("tanggal_libur").let { arr ->
            List(arr.length()) { i -> LocalDate.parse(arr.getString(i)) }
        }
        val pola = o.getJSONObject("pola")
        val gabunganTeks = o.getString("gabungan")
        val gabungan = Gabungan.entries.firstOrNull { it.name == gabunganTeks }
            ?: throw IllegalArgumentException(
                "gabungan \"$gabunganTeks\" tidak dikenal; pilihan: ${Gabungan.entries.joinToString()}",
            )
        return AturanHariRawan(
            rentangHari = rentang,
            tanggalLibur = libur,
            pola = AturanPola(pola.angka("dalam_hari", min = 1), pola.angka("min_terlewat", min = 1)),
            gabungan = gabungan,
            maksPerHari = o.angka("maks_per_hari", min = 0),
        )
    }

    private fun bacaEskalasi(o: JSONObject) = AturanEskalasi(
        minTerlewatBerturut = o.angka("min_terlewat_berturut", min = 1),
        ulangKirimMenit = o.angka("ulang_kirim_menit", min = 1),
        maksUlang = o.angka("maks_ulang", min = 0),
    )

    private fun bacaBepergian(o: JSONObject): AturanModeBepergian {
        if (!o.has("eskalasi_aktif") || o.get("eskalasi_aktif") !is Boolean) {
            throw IllegalArgumentException("eskalasi_aktif harus true atau false")
        }
        return AturanModeBepergian(o.angka("maks_hari", min = 1), o.getBoolean("eskalasi_aktif"))
    }

    private fun bacaDaftarPesan(arr: JSONArray): List<Pesan> = List(arr.length()) { i ->
        val o = arr.getJSONObject(i)
        val variabel = o.optJSONArray("variabel_tersedia")
        Pesan(
            id = o.teks("id"),
            kapan = o.teks("kapan"),
            judul = o.teks("judul"),
            isi = o.teks("isi"),
            variabelTersedia = if (variabel == null) emptyList() else List(variabel.length()) { variabel.getString(it) },
        )
    }

    private fun bacaNotifikasi(o: JSONObject) = TeksNotifikasi(
        pasienJamMinum = o.teks("pasien_jam_minum"),
        pasienHariRawan = o.teks("pasien_hari_rawan"),
        pmoEskalasi = o.teks("pmo_eskalasi"),
    )

    private const val MENIT_SEHARI = 24 * 60

    /** A whole number ≥ [min]; rejects decimals and text ("30" in quotes is a typo, not a number). */
    private fun JSONObject.angka(kunci: String, min: Int): Int {
        if (!has(kunci)) throw IllegalArgumentException("$kunci tidak ada")
        return keAngka(get(kunci), kunci).also { require(it >= min) { "$kunci = $it, minimal $min" } }
    }

    private fun JSONArray.angkaDi(i: Int, nama: String): Int = keAngka(get(i), nama)

    private fun keAngka(nilai: Any, nama: String): Int {
        if (nilai !is Int && nilai !is Long) throw IllegalArgumentException("$nama harus bilangan bulat, bukan \"$nilai\"")
        val n = (nilai as Number).toLong()
        require(n in Int.MIN_VALUE..Int.MAX_VALUE) { "$nama terlalu besar" }
        return n.toInt()
    }

    private fun JSONObject.teks(kunci: String): String {
        if (!has(kunci) || get(kunci) !is String) throw IllegalArgumentException("$kunci harus berupa teks")
        return getString(kunci)
    }
}
