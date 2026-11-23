package com.fallbracelet.ui.pasien

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.fallbracelet.R
import com.fallbracelet.adherence.Fase
import com.fallbracelet.ble.LinkState
import com.fallbracelet.ui.AppViewModel
import java.time.ZoneId
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue

/** P4 Beranda: greeting, "Hari X dari N" ring, phase, box card, today's dose, this week. */
@Composable
fun BerandaScreen(vm: PasienViewModel, app: AppViewModel) {
    val k by vm.keadaan.collectAsState()
    val settings by app.settings.collectAsState()
    val link by app.link.collectAsState()
    val keadaan = k ?: return
    val zona = ZoneId.systemDefault()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        JudulLayar(stringResource(R.string.app_name), stringResource(R.string.beranda_halo, keadaan.pengaturan.namaPanggilan))
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Progress ring
            KartuPasien {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(120.dp), contentAlignment = Alignment.Center) {
                        val hari = keadaan.hariKe.coerceIn(0, keadaan.totalHari)
                        CircularProgressIndicator(
                            progress = { hari.toFloat() / keadaan.totalHari },
                            modifier = Modifier.size(120.dp),
                            strokeWidth = 10.dp,
                            trackColor = WarnaPasien.TealMuda,
                        )
                        Text(
                            "$hari",
                            style = MaterialTheme.typography.headlineMedium,
                            textAlign = TextAlign.Center,
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                    Column {
                        when (keadaan.fase) {
                            Fase.BELUM_MULAI -> Text(
                                stringResource(R.string.beranda_belum_mulai, Format.tanggal(keadaan.tanggalMulai)),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Fase.SELESAI -> Text(stringResource(R.string.beranda_selesai), style = MaterialTheme.typography.titleMedium)
                            else -> {
                                Text(
                                    stringResource(R.string.beranda_hari_dari, keadaan.hariKe, keadaan.totalHari),
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Text(stringResource(R.string.beranda_sisa, keadaan.sisaHari), color = WarnaPasien.Redup)
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Lencana(teksFase(keadaan.fase), WarnaPasien.Teal, WarnaPasien.TealMuda)
                    }
                }
            }

            // Box
            KartuPasien {
                LabelBagian(stringResource(R.string.kotak_judul))
                val adaKotak = settings.deviceAddress != null
                val (teksLink, warna) = when {
                    !adaKotak -> stringResource(R.string.kotak_simulasi) to WarnaPasien.Redup
                    link == LinkState.CONNECTED -> stringResource(R.string.kotak_terhubung) to WarnaPasien.Baik
                    link == LinkState.CONNECTING -> stringResource(R.string.kotak_menghubungkan) to WarnaPasien.Waspada
                    else -> stringResource(R.string.kotak_terputus) to WarnaPasien.Buruk
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(12.dp).background(warna, CircleShape))
                    Spacer(Modifier.width(8.dp))
                    Text(teksLink, style = MaterialTheme.typography.titleMedium)
                }
                Text(
                    keadaan.terakhirDibuka?.let { stringResource(R.string.kotak_terakhir_dibuka, Format.waktu(it, zona)) }
                        ?: stringResource(R.string.kotak_belum_dibuka),
                    color = WarnaPasien.Redup,
                )
                if (!adaKotak) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = vm::bukaKotakSimulasi) { Text(stringResource(R.string.kotak_buka_simulasi)) }
                }
            }

            // Today's dose
            KartuPasien {
                LabelBagian(stringResource(R.string.dosis_judul))
                val hari = keadaan.hariIni
                if (hari == null) {
                    Text(stringResource(R.string.dosis_tidak_ada), color = WarnaPasien.Redup)
                } else {
                    val (fg, bg) = warnaStatus(hari.status)
                    Lencana(teksStatus(hari.status), fg, bg)
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.dosis_jam, Format.jam(hari.jamMinum)), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(
                            R.string.dosis_jendela,
                            Format.jam(hari.jadwal.mulaiJendela, zona),
                            Format.jam(hari.jadwal.terlewatMulai, zona),
                        ),
                        color = WarnaPasien.Redup, style = MaterialTheme.typography.bodySmall,
                    )
                    hari.waktuTercatat?.let {
                        Text(stringResource(R.string.dosis_tercatat_pada, Format.jam(it, zona)), color = WarnaPasien.Redup)
                    }
                    if (hari.status.masihBerjalan) {
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = vm::konfirmasiManual, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                            Text(stringResource(R.string.dosis_sudah_minum))
                        }
                    }
                }
            }

            // This week, Mon–Sun
            KartuPasien {
                LabelBagian(stringResource(R.string.minggu_judul))
                val namaHari = stringArrayResource(R.array.hari_singkat)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    keadaan.minggu.forEachIndexed { i, (tanggal, catatan) ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                namaHari[i],
                                color = if (tanggal == keadaan.hariIniTanggal) WarnaPasien.Teal else WarnaPasien.Redup,
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Spacer(Modifier.height(4.dp))
                            val (fg, bg) = catatan?.let { warnaStatus(it.status) } ?: (WarnaPasien.Garis to WarnaPasien.Latar)
                            Box(Modifier.size(34.dp).background(bg, CircleShape), contentAlignment = Alignment.Center) {
                                Text(
                                    when {
                                        catatan == null -> ""
                                        catatan.status.tercatat -> "✓"
                                        catatan.status == com.fallbracelet.adherence.StatusDosis.TERLEWAT -> "✗"
                                        else -> "•"
                                    },
                                    color = fg, style = MaterialTheme.typography.titleMedium,
                                )
                            }
                            Text("${tanggal.dayOfMonth}", color = WarnaPasien.Redup, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
