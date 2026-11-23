package com.fallbracelet.ui.pasien

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.fallbracelet.R
import com.fallbracelet.relay.LinkedCaregiver
import com.fallbracelet.ui.AppViewModel
import java.time.Instant
import java.time.ZoneId

/**
 * P7 PMO: linked PMOs, a 6-digit invite code, and removing a PMO at any time. Reuses the
 * relay invite and caregiver list from the bracelet app (AppViewModel → WearerRelay).
 */
@Composable
fun PmoScreen(app: AppViewModel, vm: PasienViewModel) {
    val keadaan by vm.keadaan.collectAsState()
    val settings by app.settings.collectAsState()
    val invite by app.invite.collectAsState()
    val daftar by app.caregivers.collectAsState()
    val pesan by app.pmoMessage.collectAsState()
    val health by app.relayHealth.collectAsState()
    val zona = ZoneId.systemDefault()
    var hapus by rememberSaveable { mutableStateOf<Long?>(null) }
    LaunchedEffect(settings.wearerToken, health?.caregivers) { app.refreshCaregivers() }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        JudulLayar(stringResource(R.string.pmo_judul), stringResource(R.string.pmo_sub))
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            KartuPasien {
                LabelBagian(stringResource(R.string.pmo_terhubung))
                if (settings.relayUrl.isBlank()) {
                    Text(stringResource(R.string.pmo_relay_kosong), color = WarnaPasien.Waspada)
                } else if (daftar.isEmpty()) {
                    Text(stringResource(R.string.pmo_belum_ada), color = WarnaPasien.Redup)
                }
                daftar.forEach { c -> BarisPmo(c, zona) { hapus = c.id } }
            }

            KartuPasien {
                LabelBagian(stringResource(R.string.pmo_undang))
                Text(stringResource(R.string.pmo_undang_isi), color = WarnaPasien.Redup, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                Button(onClick = app::newInvite, enabled = settings.relayUrl.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.pmo_buat_kode))
                }
                invite?.let { inv ->
                    if (inv.expiresAt > System.currentTimeMillis()) {
                        Spacer(Modifier.height(12.dp))
                        Text(stringResource(R.string.pmo_kode), color = WarnaPasien.Redup, style = MaterialTheme.typography.bodySmall)
                        Text(inv.code.chunked(3).joinToString(" "), style = MaterialTheme.typography.headlineMedium, fontFamily = FontFamily.Monospace)
                        Text(
                            stringResource(R.string.pmo_kode_isi, Format.waktu(Instant.ofEpochMilli(inv.expiresAt), zona)),
                            color = WarnaPasien.Redup, style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                pesan?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = WarnaPasien.Buruk)
                }
            }
            // The latest ESKALASI and whether the PMO saw it (PRD scenario 5: "Dilihat oleh …")
            KartuPasien {
                LabelBagian(stringResource(R.string.pmo_kabar_terakhir))
                val terakhir = keadaan?.eskalasi?.lastOrNull { it.kirim }
                if (terakhir == null) {
                    Text(
                        stringResource(R.string.pmo_kabar_isi, vm.aturan.aturan.eskalasi.minTerlewatBerturut),
                        color = WarnaPasien.Redup, style = MaterialTheme.typography.bodySmall,
                    )
                } else {
                    Text(stringResource(R.string.riwayat_hari_ke, terakhir.hariKe, Format.tanggalPendek(java.time.LocalDate.parse(terakhir.tanggal))))
                    Text(teksKabar(terakhir), color = WarnaPasien.Redup)
                }
            }
            Text(stringResource(R.string.pmo_relay_di_pengaturan), color = WarnaPasien.Redup, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(16.dp))
        }
    }

    hapus?.let { id ->
        val nama = daftar.firstOrNull { it.id == id }?.name.orEmpty()
        AlertDialog(
            onDismissRequest = { hapus = null },
            title = { Text(stringResource(R.string.pmo_hapus_judul)) },
            text = { Text(stringResource(R.string.pmo_hapus_isi, nama)) },
            confirmButton = {
                TextButton(onClick = { app.removeCaregiver(id); hapus = null }) {
                    Text(stringResource(R.string.pmo_hapus), color = WarnaPasien.Buruk)
                }
            },
            dismissButton = { TextButton(onClick = { hapus = null }) { Text(stringResource(R.string.batal)) } },
        )
    }
}

@Composable
private fun BarisPmo(c: LinkedCaregiver, zona: ZoneId, onHapus: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(c.name, style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.pmo_terhubung_sejak, Format.waktu(Instant.ofEpochMilli(c.linkedAt), zona)),
                color = WarnaPasien.Redup, style = MaterialTheme.typography.bodySmall,
            )
        }
        TextButton(onClick = onHapus) { Text(stringResource(R.string.pmo_hapus), color = WarnaPasien.Buruk) }
    }
}
