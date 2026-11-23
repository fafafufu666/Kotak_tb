package com.fallbracelet.ui.pasien

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.fallbracelet.R
import com.fallbracelet.data.AppMode
import com.fallbracelet.ui.AppViewModel

private enum class TabPasien(val judul: Int, val ikon: ImageVector) {
    Beranda(R.string.tab_beranda, Icons.Filled.Home),
    Riwayat(R.string.tab_riwayat, Icons.Filled.DateRange),
    Pmo(R.string.tab_pmo, Icons.Filled.Person),
    Pengaturan(R.string.tab_pengaturan, Icons.Filled.Settings),
}

/**
 * Patient mode (PRD 6.1): P1 consent → P2 start → P3 pair (can be skipped) → the four tabs.
 * Shown when the phone is in [AppMode.WEARER] mode.
 */
@Composable
fun PasienApp(vm: PasienViewModel, app: AppViewModel, enableBluetooth: () -> Unit) {
    val pengaturan by vm.pengaturan.collectAsState()
    val settings by app.settings.collectAsState()
    val p = pengaturan ?: return
    TemaPasien {
        when {
            p.persetujuanPada == null -> PersetujuanScreen(vm)
            !p.sudahMulai -> MulaiScreen(vm)
            settings.deviceAddress == null && !p.kotakDilewati -> PasangkanScreen(app, vm, enableBluetooth)
            else -> TabUtama(vm, app, demo = p.demo.aktif)
        }
    }
}

@Composable
private fun TabUtama(vm: PasienViewModel, app: AppViewModel, demo: Boolean) {
    var tab by rememberSaveable { mutableStateOf(TabPasien.Beranda) }
    Scaffold(
        containerColor = WarnaPasien.Latar,
        topBar = { if (demo) PitaDemo() },
        bottomBar = {
            NavigationBar(containerColor = WarnaPasien.Kartu) {
                TabPasien.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = { Icon(t.ikon, contentDescription = null) },
                        label = { Text(stringResource(t.judul)) },
                    )
                }
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            when (tab) {
                TabPasien.Beranda -> BerandaScreen(vm, app)
                TabPasien.Riwayat -> RiwayatScreen(vm)
                TabPasien.Pmo -> PmoScreen(app, vm)
                TabPasien.Pengaturan -> PengaturanScreen(vm, app)
            }
        }
    }
}

/** "DEMO" marker at the top while demo mode is on (PRD section 9). */
@Composable
private fun PitaDemo() {
    Box(
        Modifier.fillMaxWidth().background(WarnaPasien.Demo).statusBarsPadding().padding(vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(stringResource(R.string.demo_penanda), color = Color.White, style = MaterialTheme.typography.labelLarge)
    }
}

/** P3 Pasangkan kotak: the existing scan and pair, with a way to go on with the simulated box. */
@Composable
private fun PasangkanScreen(app: AppViewModel, vm: PasienViewModel, enableBluetooth: () -> Unit) {
    val found by app.found.collectAsState()
    val error by app.scanError.collectAsState()
    DisposableEffect(Unit) {
        app.startScan()
        onDispose { app.stopScan() }
    }
    Scaffold(containerColor = WarnaPasien.Latar) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            JudulLayar(stringResource(R.string.pasang_judul), stringResource(R.string.pasang_sub))
            LazyColumn(
                Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (error != null) item {
                    KartuPasien(latar = WarnaPasien.BurukMuda, garis = WarnaPasien.Buruk) {
                        Text(error!!, color = WarnaPasien.Buruk)
                        Text(stringResource(R.string.pasang_bt_mati))
                        Spacer(Modifier.size(12.dp))
                        Button(onClick = enableBluetooth) { Text(stringResource(R.string.pasang_nyalakan_bt)) }
                    }
                }
                if (found.isEmpty() && error == null) item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.size(12.dp))
                        Text(stringResource(R.string.pasang_mencari), color = WarnaPasien.Redup)
                    }
                }
                items(found, key = { it.address }) { d ->
                    KartuPasien(Modifier.clickable { app.pair(d) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(d.name, style = MaterialTheme.typography.titleMedium)
                                Text(d.address, color = WarnaPasien.Redup, style = MaterialTheme.typography.bodySmall)
                            }
                            Button(onClick = { app.pair(d) }) { Text(stringResource(R.string.pasang_tombol)) }
                        }
                    }
                }
            }
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = app::startScan, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.pasang_cari_lagi))
                }
                Button(onClick = { vm.lewatiKotak(true) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.pasang_lewati))
                }
            }
        }
    }
}

/** First launch: patient or PMO. */
@Composable
fun PilihModeScreen(onPilih: (AppMode) -> Unit) {
    TemaPasien {
        Scaffold(containerColor = WarnaPasien.Latar) { pad ->
            Column(Modifier.padding(pad).fillMaxSize()) {
                JudulLayar(stringResource(R.string.mode_judul), stringResource(R.string.mode_sub))
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    KartuMode(stringResource(R.string.mode_pasien), stringResource(R.string.mode_pasien_isi)) { onPilih(AppMode.WEARER) }
                    KartuMode(stringResource(R.string.mode_pmo), stringResource(R.string.mode_pmo_isi)) { onPilih(AppMode.CAREGIVER) }
                }
            }
        }
    }
}

@Composable
private fun KartuMode(judul: String, isi: String, onClick: () -> Unit) {
    KartuPasien(Modifier.clickable(onClick = onClick)) {
        Text(judul, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(4.dp))
        Text(isi, color = WarnaPasien.Redup)
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onClick) { Text(stringResource(R.string.pilih)) }
    }
}
