package com.fallbracelet.ui.pasien

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.fallbracelet.R

/**
 * P1 Persetujuan: the text from `assets/persetujuan.md` (written separately by Abyan), a
 * box that must be ticked, and the time of agreeing is stored.
 */
@Composable
fun PersetujuanScreen(vm: PasienViewModel) {
    val context = LocalContext.current
    val blok = remember {
        val teks = runCatching { context.assets.open(FILE).bufferedReader().use { it.readText() } }.getOrDefault("")
        TeksPersetujuan.bagi(teks)
    }
    var setuju by rememberSaveable { mutableStateOf(false) }

    Scaffold(containerColor = WarnaPasien.Latar) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            JudulLayar(stringResource(R.string.setuju_judul))
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                KartuPasien {
                    if (blok.isEmpty()) Text(stringResource(R.string.setuju_kosong), color = WarnaPasien.Buruk)
                    blok.forEach { b ->
                        when (b) {
                            is BlokTeks.Judul -> Text(b.teks, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(vertical = 4.dp))
                            is BlokTeks.Subjudul -> Text(b.teks, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
                            is BlokTeks.Butir -> Text("•  ${b.teks}", style = MaterialTheme.typography.bodyMedium)
                            is BlokTeks.Paragraf -> Text(b.teks, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 4.dp))
                        }
                    }
                }
            }
            Column(Modifier.padding(16.dp)) {
                Row(
                    Modifier.fillMaxWidth().clickable { setuju = !setuju },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = setuju, onCheckedChange = { setuju = it })
                    Text(stringResource(R.string.setuju_centang))
                }
                Button(onClick = vm::setuju, enabled = setuju, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.setuju_lanjut))
                }
            }
        }
    }
}

private const val FILE = "persetujuan.md"
