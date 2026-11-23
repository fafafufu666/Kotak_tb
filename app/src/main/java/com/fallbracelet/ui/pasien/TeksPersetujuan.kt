package com.fallbracelet.ui.pasien

/** One block of the consent text. */
sealed interface BlokTeks {
    data class Judul(val teks: String) : BlokTeks
    data class Subjudul(val teks: String) : BlokTeks
    data class Butir(val teks: String) : BlokTeks
    data class Paragraf(val teks: String) : BlokTeks
}

/**
 * Splits `assets/persetujuan.md` into blocks: `# ` title, `## ` subtitle, `- ` or `* ` list
 * item, and paragraphs (consecutive lines joined, a blank line ends one). Nothing else of
 * Markdown is interpreted; the text is shown as written. Android-free so it can be unit tested.
 */
object TeksPersetujuan {
    fun bagi(md: String): List<BlokTeks> {
        val hasil = ArrayList<BlokTeks>()
        val paragraf = ArrayList<String>()
        fun tutupParagraf() {
            if (paragraf.isNotEmpty()) hasil += BlokTeks.Paragraf(paragraf.joinToString(" "))
            paragraf.clear()
        }
        for (mentah in md.lines()) {
            val baris = mentah.trim()
            when {
                baris.isEmpty() -> tutupParagraf()
                baris.startsWith("## ") -> { tutupParagraf(); hasil += BlokTeks.Subjudul(baris.removePrefix("## ").trim()) }
                baris.startsWith("# ") -> { tutupParagraf(); hasil += BlokTeks.Judul(baris.removePrefix("# ").trim()) }
                baris.startsWith("- ") || baris.startsWith("* ") -> { tutupParagraf(); hasil += BlokTeks.Butir(baris.drop(2).trim()) }
                else -> paragraf += baris
            }
        }
        tutupParagraf()
        return hasil
    }
}
