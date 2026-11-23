package com.fallbracelet.ui.pasien

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.fallbracelet.R
import com.fallbracelet.adherence.Fase
import com.fallbracelet.adherence.StatusDosis
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Indonesian dates and times for the patient screens. */
object Format {
    private val id = Locale.forLanguageTag("id-ID")
    private val jamFmt = DateTimeFormatter.ofPattern("HH.mm", id)
    private val tanggalFmt = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", id)
    private val tanggalPendekFmt = DateTimeFormatter.ofPattern("EEE, d MMM", id)
    private val waktuFmt = DateTimeFormatter.ofPattern("EEE d MMM, HH.mm", id)

    fun jam(t: LocalTime): String = t.format(jamFmt)
    fun jam(t: Instant, zona: ZoneId): String = t.atZone(zona).format(jamFmt)
    fun tanggal(d: LocalDate): String = d.format(tanggalFmt)
    fun tanggalPendek(d: LocalDate): String = d.format(tanggalPendekFmt)
    fun waktu(t: Instant, zona: ZoneId): String = t.atZone(zona).format(waktuFmt)
}

@Composable
fun teksStatus(s: StatusDosis): String = stringResource(
    when (s) {
        StatusDosis.BELUM -> R.string.status_belum
        StatusDosis.TERLAMBAT -> R.string.status_terlambat
        StatusDosis.TERLEWAT -> R.string.status_terlewat
        StatusDosis.DIMINUM_KOTAK -> R.string.status_kotak
        StatusDosis.DIKONFIRMASI_MANUAL -> R.string.status_manual
        StatusDosis.BEPERGIAN -> R.string.status_bepergian
        StatusDosis.TANPA_DATA -> R.string.status_tanpa_data
    }
)

/** Text colour and background for a status. */
fun warnaStatus(s: StatusDosis): Pair<Color, Color> = when {
    s.tercatat -> WarnaPasien.Baik to WarnaPasien.BaikMuda
    s == StatusDosis.TERLEWAT -> WarnaPasien.Buruk to WarnaPasien.BurukMuda
    s == StatusDosis.TERLAMBAT -> WarnaPasien.Waspada to WarnaPasien.WaspadaMuda
    else -> WarnaPasien.Redup to WarnaPasien.TealMuda
}

@Composable
fun teksFase(f: Fase): String = stringResource(
    when (f) {
        Fase.INTENSIF -> R.string.fase_intensif
        Fase.LANJUTAN -> R.string.fase_lanjutan
        Fase.BELUM_MULAI -> R.string.fase_belum_mulai
        Fase.SELESAI -> R.string.fase_selesai
    }
)
