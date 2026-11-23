package com.fallbracelet.ui.pasien

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.fallbracelet.R
import java.time.LocalDate
import java.time.LocalTime

/** P2 Mulai pengobatan: nickname, start date and dose time. Length and phases come from the rules file. */
@Composable
fun MulaiScreen(vm: PasienViewModel) {
    val pengobatan = vm.aturan.aturan.pengobatan
    var nama by rememberSaveable { mutableStateOf("") }
    var tanggal by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var jam by rememberSaveable { mutableStateOf<String?>(null) }
    var pilihTanggal by rememberSaveable { mutableStateOf(false) }
    var pilihJam by rememberSaveable { mutableStateOf(false) }

    Scaffold(containerColor = WarnaPasien.Latar) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState())) {
            JudulLayar(stringResource(R.string.mulai_judul), stringResource(R.string.mulai_sub))
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = nama, onValueChange = { nama = it.take(20) },
                    label = { Text(stringResource(R.string.mulai_nama)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                KartuPasien {
                    LabelBagian(stringResource(R.string.mulai_tanggal))
                    Text(Format.tanggal(LocalDate.parse(tanggal)), style = MaterialTheme.typography.bodyLarge)
                    OutlinedButton(onClick = { pilihTanggal = true }) { Text(stringResource(R.string.pilih)) }
                }
                KartuPasien {
                    LabelBagian(stringResource(R.string.mulai_jam))
                    Text(jam?.let { Format.jam(LocalTime.parse(it)) } ?: "–", style = MaterialTheme.typography.bodyLarge)
                    OutlinedButton(onClick = { pilihJam = true }) { Text(stringResource(R.string.pilih)) }
                }
                Text(
                    stringResource(R.string.mulai_lama, pengobatan.totalHari, pengobatan.faseIntensifHari),
                    color = WarnaPasien.Redup, style = MaterialTheme.typography.bodySmall,
                )
                Button(
                    onClick = { vm.mulai(nama, LocalDate.parse(tanggal), LocalTime.parse(jam!!)) },
                    enabled = nama.isNotBlank() && jam != null,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.mulai_simpan)) }
            }
        }
    }
    if (pilihTanggal) PilihTanggalDialog(LocalDate.parse(tanggal), { tanggal = it.toString() }, { pilihTanggal = false })
    if (pilihJam) PilihJamDialog(jam?.let(LocalTime::parse) ?: LocalTime.of(7, 0), { jam = it.toString() }, { pilihJam = false })
}
