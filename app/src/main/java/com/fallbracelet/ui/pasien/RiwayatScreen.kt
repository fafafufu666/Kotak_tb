package com.fallbracelet.ui.pasien

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.fallbracelet.R
import com.fallbracelet.adherence.CatatanHari
import com.fallbracelet.adherence.StatusDosis
import java.time.Instant
import java.time.ZoneId

/** One row of the history: a treatment day, or a box opening outside every window. */
private sealed interface BarisRiwayat {
    val urut: Instant
    data class Hari(val c: CatatanHari) : BarisRiwayat { override val urut: Instant get() = c.jadwal.jamMinum }
    data class DiLuar(val waktu: Instant) : BarisRiwayat { override val urut: Instant get() = waktu }
}

/** P6 Riwayat: status per day with source (box / manual) and time, newest first. */
@Composable
fun RiwayatScreen(vm: PasienViewModel) {
    val k by vm.keadaan.collectAsState()
    val keadaan = k ?: return
    val zona = ZoneId.systemDefault()
    val baris = (keadaan.semuaHari.map { BarisRiwayat.Hari(it) } + keadaan.diLuarJadwal.map { BarisRiwayat.DiLuar(it) })
        .sortedByDescending { it.urut }

    Column(Modifier.fillMaxSize()) {
        JudulLayar(stringResource(R.string.riwayat_judul), stringResource(R.string.riwayat_sub))
        if (baris.isEmpty()) {
            Text(stringResource(R.string.riwayat_kosong), Modifier.padding(16.dp), color = WarnaPasien.Redup)
        } else LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(baris) { b ->
                when (b) {
                    is BarisRiwayat.Hari -> KartuPasien {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    stringResource(R.string.riwayat_hari_ke, b.c.hariKe, Format.tanggalPendek(b.c.tanggal)),
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                val waktu = b.c.waktuTercatat?.let { Format.jam(it, zona) }
                                when {
                                    waktu != null && b.c.status == StatusDosis.DIMINUM_KOTAK ->
                                        Text(stringResource(R.string.riwayat_sumber_kotak, waktu), color = WarnaPasien.Redup)
                                    waktu != null ->
                                        Text(stringResource(R.string.riwayat_sumber_manual, waktu), color = WarnaPasien.Redup)
                                    else -> Text(stringResource(R.string.dosis_jam, Format.jam(b.c.jamMinum)), color = WarnaPasien.Redup)
                                }
                            }
                            val (fg, bg) = warnaStatus(b.c.status)
                            Lencana(teksStatus(b.c.status), fg, bg)
                        }
                    }
                    is BarisRiwayat.DiLuar -> KartuPasien(latar = WarnaPasien.Latar) {
                        Text(stringResource(R.string.riwayat_di_luar), style = MaterialTheme.typography.bodyMedium)
                        Text(Format.waktu(b.waktu, zona), color = WarnaPasien.Redup, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
