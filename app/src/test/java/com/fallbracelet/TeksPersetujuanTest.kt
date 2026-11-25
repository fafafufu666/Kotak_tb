package com.fallbracelet

import com.fallbracelet.ui.pasien.BlokTeks
import com.fallbracelet.ui.pasien.TeksPersetujuan
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/** P1: how `assets/persetujuan.md` is split into blocks. */
class TeksPersetujuanTest {

    @Test fun titlesListItemsAndParagraphs() {
        val md = """
            # Judul

            ## Bagian satu
            Baris pertama
            baris kedua.

            - butir a
            * butir b
            Paragraf akhir.
        """.trimIndent()
        assertEquals(
            listOf(
                BlokTeks.Judul("Judul"),
                BlokTeks.Subjudul("Bagian satu"),
                BlokTeks.Paragraf("Baris pertama baris kedua."),
                BlokTeks.Butir("butir a"),
                BlokTeks.Butir("butir b"),
                BlokTeks.Paragraf("Paragraf akhir."),
            ),
            TeksPersetujuan.bagi(md),
        )
    }

    @Test fun emptyTextGivesNoBlocks() {
        assertEquals(emptyList<BlokTeks>(), TeksPersetujuan.bagi("\n  \n"))
    }

    /** The shipped text starts with its title, has its sections, and no placeholder is left in it. */
    @Test fun theAssetFileParses() {
        val file = listOf("src/main/assets/persetujuan.md", "app/src/main/assets/persetujuan.md").map(::File).first { it.exists() }
        val teks = file.readText()
        val blok = TeksPersetujuan.bagi(teks)
        assertEquals(BlokTeks.Judul::class, blok.first()::class)
        assertEquals(4, blok.count { it is BlokTeks.Subjudul })
        assertEquals(false, teks.contains("DIISI"))
    }
}
