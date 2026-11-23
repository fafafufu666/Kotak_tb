package com.fallbracelet.ui.pasien

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.fallbracelet.R
import com.fallbracelet.ui.AppViewModel
import java.time.LocalTime
import java.time.ZoneId

/** P8 Pengaturan: rules status, profile and dose time, box, demo mode, leaving the program. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PengaturanScreen(vm: PasienViewModel, app: AppViewModel) {
    val k by vm.keadaan.collectAsState()
    val settings by app.settings.collectAsState()
    val keadaan = k ?: return
    val aturan = vm.aturan
    val demo = keadaan.pengaturan.demo
    val zona = ZoneId.systemDefault()
    var ubahJam by rememberSaveable { mutableStateOf(false) }
    var ubahNama by rememberSaveable { mutableStateOf(false) }
    var berhenti by rememberSaveable { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        JudulLayar(stringResource(R.string.atur_judul))
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Rules file: placeholder ribbon and warnings (PRD section 8)
            if (aturan.aturan.nilaiContoh) {
                KartuPasien(latar = WarnaPasien.WaspadaMuda, garis = WarnaPasien.Waspada) {
                    Lencana(stringResource(R.string.atur_nilai_contoh), WarnaPasien.Waspada, WarnaPasien.Kartu)
                    Spacer(Modifier.height(6.dp))
                    Text(stringResource(R.string.atur_nilai_contoh_isi))
                }
            }
            if (aturan.peringatan.isNotEmpty()) {
                KartuPasien(latar = WarnaPasien.BurukMuda, garis = WarnaPasien.Buruk) {
                    Text(stringResource(R.string.atur_peringatan_judul), color = WarnaPasien.Buruk, style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.atur_peringatan_isi))
                    aturan.peringatan.forEach {
                        Text(stringResource(R.string.atur_peringatan_baris, it.bagian, it.alasan), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            // Profile and dose time
            KartuPasien {
                LabelBagian(stringResource(R.string.atur_profil))
                Text(keadaan.pengaturan.namaPanggilan, style = MaterialTheme.typography.titleMedium)
                keadaan.pengaturan.tanggalMulai?.let { Text(stringResource(R.string.atur_tanggal_mulai, Format.tanggal(it)), color = WarnaPasien.Redup) }
                keadaan.jamMinum?.let { Text(stringResource(R.string.atur_jam_minum, Format.jam(it)), color = WarnaPasien.Redup) }
                keadaan.pengaturan.persetujuanPada?.let {
                    Text(stringResource(R.string.atur_disetujui, Format.waktu(java.time.Instant.ofEpochMilli(it), zona)), color = WarnaPasien.Redup)
                }
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { ubahJam = true }) { Text(stringResource(R.string.atur_ubah_jam)) }
                    OutlinedButton(onClick = { ubahNama = true }) { Text(stringResource(R.string.atur_ubah_nama)) }
                }
            }

            // Box
            KartuPasien {
                LabelBagian(stringResource(R.string.atur_kotak))
                if (settings.deviceAddress != null) {
                    Text(stringResource(R.string.atur_kotak_dipasangkan, settings.deviceName ?: settings.deviceAddress!!))
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = vm::pasangkanUlang) { Text(stringResource(R.string.atur_lupakan)) }
                } else {
                    Text(stringResource(R.string.atur_kotak_tidak_ada))
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = vm::pasangkanUlang) { Text(stringResource(R.string.atur_pasangkan)) }
                }
                Text(stringResource(R.string.atur_jam_ke_kotak), color = WarnaPasien.Redup, style = MaterialTheme.typography.bodySmall)
            }

            // Relay (P8: address / find relay); PMOs are on the PMO tab
            KartuPasien {
                LabelBagian(stringResource(R.string.atur_relay))
                Text(stringResource(R.string.atur_relay_isi), color = WarnaPasien.Redup, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                var url by remember(settings.relayUrl) { mutableStateOf(settings.relayUrl) }
                val mencari by app.finding.collectAsState()
                val pesanRelay by app.relayMessage.collectAsState()
                OutlinedTextField(
                    value = url, onValueChange = { url = it },
                    label = { Text(stringResource(R.string.atur_relay_alamat)) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = app::findRelay, enabled = !mencari) {
                        Text(stringResource(if (mencari) R.string.atur_relay_mencari else R.string.atur_relay_cari))
                    }
                    if (url.trim() != settings.relayUrl) {
                        OutlinedButton(onClick = { app.setRelayUrl(url) }) { Text(stringResource(R.string.simpan)) }
                    }
                }
                pesanRelay?.let { Text(it, color = WarnaPasien.Buruk) }
            }

            // Demo mode (PRD section 9)
            KartuPasien(garis = if (demo.aktif) WarnaPasien.Demo else WarnaPasien.Garis) {
                LabelBagian(stringResource(R.string.atur_demo))
                Text(stringResource(R.string.atur_demo_isi), color = WarnaPasien.Redup, style = MaterialTheme.typography.bodySmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.atur_demo_aktif), Modifier.weight(1f))
                    Switch(checked = demo.aktif, onCheckedChange = vm::setDemoAktif)
                }
                if (demo.aktif) {
                    Text(stringResource(R.string.atur_demo_jam, Format.waktu(keadaan.sekarang, zona)), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    var hariKe by remember { mutableStateOf("") }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = hariKe, onValueChange = { v -> hariKe = v.filter { it.isDigit() }.take(4) },
                            label = { Text(stringResource(R.string.atur_demo_lompat)) }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = { hariKe.toIntOrNull()?.let { vm.lompatKeHari(it) } },
                            enabled = (hariKe.toIntOrNull() ?: 0) >= 1,
                        ) { Text(stringResource(R.string.atur_demo_lompat_tombol)) }
                    }
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { vm.majukanJam(15) }) { Text(stringResource(R.string.atur_demo_15m)) }
                        OutlinedButton(onClick = { vm.majukanJam(60) }) { Text(stringResource(R.string.atur_demo_1j)) }
                        OutlinedButton(onClick = { vm.majukanJam(24 * 60) }) { Text(stringResource(R.string.atur_demo_1h)) }
                        OutlinedButton(onClick = vm::majukanKeBatasTerlewat) { Text(stringResource(R.string.atur_demo_ke_terlewat)) }
                        OutlinedButton(onClick = vm::isiRiwayatContoh) { Text(stringResource(R.string.atur_demo_riwayat)) }
                        OutlinedButton(onClick = vm::bukaKotakSimulasi) { Text(stringResource(R.string.kotak_buka_simulasi)) }
                    }
                    Text(stringResource(R.string.atur_demo_alarm_kotak), color = WarnaPasien.Redup, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = vm::aturUlangDemo, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.atur_demo_reset))
                }
            }

            // Leave the program
            TextButton(onClick = { berhenti = true }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.atur_berhenti), color = WarnaPasien.Buruk)
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    if (ubahJam) PilihJamDialog(keadaan.jamMinum ?: LocalTime.of(7, 0), vm::ubahJamMinum) { ubahJam = false }
    if (ubahNama) {
        var nama by remember { mutableStateOf(keadaan.pengaturan.namaPanggilan) }
        AlertDialog(
            onDismissRequest = { ubahNama = false },
            title = { Text(stringResource(R.string.atur_ubah_nama)) },
            text = {
                OutlinedTextField(value = nama, onValueChange = { nama = it.take(20) }, singleLine = true,
                    label = { Text(stringResource(R.string.mulai_nama)) })
            },
            confirmButton = {
                TextButton(onClick = { vm.ubahNama(nama); ubahNama = false }, enabled = nama.isNotBlank()) {
                    Text(stringResource(R.string.simpan))
                }
            },
            dismissButton = { TextButton(onClick = { ubahNama = false }) { Text(stringResource(R.string.batal)) } },
        )
    }
    if (berhenti) {
        AlertDialog(
            onDismissRequest = { berhenti = false },
            title = { Text(stringResource(R.string.atur_berhenti)) },
            text = { Text(stringResource(R.string.atur_berhenti_isi)) },
            confirmButton = {
                TextButton(onClick = { berhenti = false; vm.berhentiIkut() }) {
                    Text(stringResource(R.string.atur_berhenti_ya), color = WarnaPasien.Buruk)
                }
            },
            dismissButton = { TextButton(onClick = { berhenti = false }) { Text(stringResource(R.string.batal)) } },
        )
    }
}
