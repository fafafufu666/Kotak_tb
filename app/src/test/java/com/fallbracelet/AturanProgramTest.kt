package com.fallbracelet

import com.fallbracelet.adherence.Gabungan
import com.fallbracelet.adherence.HasilBacaAturan
import com.fallbracelet.adherence.PembacaAturan
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

/**
 * Reading `assets/aturan_program.json` (PRD section 8): the shipped files are valid, and a
 * broken section falls back to `aturan_bawaan.json` with a warning while the rest is kept.
 * These tests don't depend on the values Abyan puts in `aturan_program.json`.
 */
class AturanProgramTest {

    private fun aset(nama: String): String =
        listOf(File("src/main/assets/$nama"), File("app/src/main/assets/$nama"))
            .first { it.exists() }
            .readText()

    private val bawaan = aset("aturan_bawaan.json")

    /** The fallback file with one change applied, as the "main" text. */
    private fun bacaDenganUbahan(ubah: (JSONObject) -> Unit): HasilBacaAturan {
        val o = JSONObject(bawaan)
        ubah(o)
        return PembacaAturan.baca(o.toString(), bawaan)
    }

    private fun HasilBacaAturan.bagianBermasalah() = peringatan.map { it.bagian }

    @Test fun theShippedRulesFileIsValid() {
        val hasil = PembacaAturan.baca(aset("aturan_program.json"), bawaan)
        assertEquals("aturan_program.json: ${hasil.peringatan}", emptyList<Any>(), hasil.peringatan)
    }

    @Test fun theFallbackFileIsValid() {
        val hasil = PembacaAturan.baca(bawaan, bawaan)
        assertEquals(emptyList<Any>(), hasil.peringatan)
        assertTrue(hasil.aturan.pesan.isNotEmpty())
    }

    @Test fun readsEveryField() {
        val a = bacaDenganUbahan { o ->
            o.put("status_nilai", "FINAL")
            o.put("pengobatan", JSONObject("""{"total_hari": 100, "fase_intensif_hari": 30}"""))
            o.put("dosis", JSONObject("""{"jendela_awal_menit": 10, "batas_terlambat_menit": 20,
                "batas_terlewat_menit": 30, "pengingat_ulang": {"jumlah": 4, "jarak_menit": 5},
                "durasi_alarm_kotak_menit": 6}"""))
            o.put("hari_rawan", JSONObject("""{"rentang_hari": [[3, 4]], "tanggal_libur": ["2026-12-25"],
                "pola": {"dalam_hari": 5, "min_terlewat": 2}, "gabungan": "KEDUANYA", "maks_per_hari": 1}"""))
            o.put("eskalasi", JSONObject("""{"min_terlewat_berturut": 1, "ulang_kirim_menit": 15, "maks_ulang": 0}"""))
            o.put("mode_bepergian", JSONObject("""{"maks_hari": 3, "eskalasi_aktif": false}"""))
            o.put("pesan", JSONArray("""[{"id": "a", "kapan": "HARI_RAWAN", "judul": "J", "isi": "I {nama}"}]"""))
            o.put("notifikasi", JSONObject("""{"pasien_jam_minum": "x", "pasien_hari_rawan": "y", "pmo_eskalasi": "z"}"""))
        }
        assertEquals(emptyList<Any>(), a.peringatan)
        with(a.aturan) {
            assertEquals(false, nilaiContoh)
            assertEquals(100, pengobatan.totalHari)
            assertEquals(30, pengobatan.faseIntensifHari)
            assertEquals(listOf(10, 20, 30, 4, 5, 6), listOf(
                dosis.jendelaAwalMenit, dosis.batasTerlambatMenit, dosis.batasTerlewatMenit,
                dosis.pengingatUlang.jumlah, dosis.pengingatUlang.jarakMenit, dosis.durasiAlarmKotakMenit,
            ))
            assertEquals(listOf(3..4), hariRawan.rentangHari)
            assertEquals(listOf(LocalDate.of(2026, 12, 25)), hariRawan.tanggalLibur)
            assertEquals(5, hariRawan.pola.dalamHari)
            assertEquals(2, hariRawan.pola.minTerlewat)
            assertEquals(Gabungan.KEDUANYA, hariRawan.gabungan)
            assertEquals(1, hariRawan.maksPerHari)
            assertEquals(listOf(1, 15, 0), listOf(eskalasi.minTerlewatBerturut, eskalasi.ulangKirimMenit, eskalasi.maksUlang))
            assertEquals(3, modeBepergian.maksHari)
            assertEquals(false, modeBepergian.eskalasiAktif)
            assertEquals("I {nama}", pesan.single().isi)
            assertEquals(emptyList<String>(), pesan.single().variabelTersedia)
            assertEquals(listOf("x", "y", "z"), listOf(notifikasi.pasienJamMinum, notifikasi.pasienHariRawan, notifikasi.pmoEskalasi))
        }
    }

    @Test fun placeholderValuesAreFlagged() {
        val a = bacaDenganUbahan { it.put("status_nilai", "CONTOH - belum ditetapkan") }.aturan
        assertTrue(a.nilaiContoh)
    }

    @Test fun missingOrUnreadableFileFallsBackCompletely() {
        val cadangan = PembacaAturan.baca(bawaan, bawaan).aturan
        for (teks in listOf(null, "", "{ ini bukan json")) {
            val hasil = PembacaAturan.baca(teks, bawaan)
            assertEquals("$teks", listOf("file"), hasil.bagianBermasalah())
            assertEquals("$teks", cadangan, hasil.aturan)
        }
    }

    @Test fun aBrokenSectionFallsBackAloneAndTheRestIsKept() {
        val hasil = bacaDenganUbahan { o ->
            o.getJSONObject("dosis").put("jendela_awal_menit", "120") // text, not a number
            o.getJSONObject("eskalasi").put("min_terlewat_berturut", 1)
        }
        assertEquals(listOf("dosis"), hasil.bagianBermasalah())
        assertEquals(1, hasil.aturan.eskalasi.minTerlewatBerturut)
        assertEquals(PembacaAturan.baca(bawaan, bawaan).aturan.dosis, hasil.aturan.dosis)
    }

    @Test fun invalidValuesAreRejected() {
        fun k(bagian: String, ubah: (JSONObject) -> Unit) = bagian to ubah
        val kasus = listOf(
            k("pengobatan") { o -> o.getJSONObject("pengobatan").put("fase_intensif_hari", 999) },
            k("pengobatan") { o -> o.getJSONObject("pengobatan").put("total_hari", 0) },
            k("dosis") { o -> o.getJSONObject("dosis").put("batas_terlambat_menit", 500) },
            k("dosis") { o -> o.getJSONObject("dosis").put("jendela_awal_menit", 1200) }, // window ≥ 24 h
            k("dosis") { o -> o.getJSONObject("dosis").put("batas_terlewat_menit", 1.5) },
            k("dosis") { o -> o.getJSONObject("dosis").remove("pengingat_ulang") },
            k("hari_rawan") { o -> o.getJSONObject("hari_rawan").put("gabungan", "SALAH SATU") },
            k("hari_rawan") { o -> o.getJSONObject("hari_rawan").put("tanggal_libur", JSONArray(listOf("25-12-2026"))) },
            k("hari_rawan") { o -> o.getJSONObject("hari_rawan").put("rentang_hari", JSONArray("[[28, 14]]")) },
            k("hari_rawan") { o -> o.getJSONObject("hari_rawan").put("rentang_hari", JSONArray("[[14]]")) },
            k("eskalasi") { o -> o.getJSONObject("eskalasi").put("min_terlewat_berturut", 0) },
            k("mode_bepergian") { o -> o.getJSONObject("mode_bepergian").put("eskalasi_aktif", "ya") },
            k("pesan") { o -> o.getJSONArray("pesan").getJSONObject(0).remove("isi") },
            k("notifikasi") { o -> o.remove("notifikasi") },
            k("status_nilai") { o -> o.remove("status_nilai") },
        )
        for ((bagian, ubah) in kasus) {
            assertEquals(listOf(bagian), bacaDenganUbahan(ubah).bagianBermasalah())
        }
    }

    @Test(expected = IllegalStateException::class)
    fun anInvalidFallbackIsABuildMistake() {
        val rusak = JSONObject(bawaan).apply { remove("dosis") }.toString()
        PembacaAturan.baca(bawaan, rusak)
    }
}
