package com.fallbracelet.ui.pmo

import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.fallbracelet.R
import com.fallbracelet.caregiver.Berdering
import com.fallbracelet.caregiver.JenisSel
import com.fallbracelet.caregiver.KabarPmo
import com.fallbracelet.caregiver.PasienPmo
import com.fallbracelet.caregiver.StreamState
import com.fallbracelet.caregiver.TampilanPmo
import com.fallbracelet.caregiver.judulKabar
import com.fallbracelet.caregiver.ringkasKabar
import com.fallbracelet.caregiver.terakhirDibuka
import com.fallbracelet.data.AppMode
import com.fallbracelet.ui.AppViewModel
import com.fallbracelet.ui.hasInternet
import com.fallbracelet.ui.openFullScreenSettings
import com.fallbracelet.ui.openNotificationSettings
import com.fallbracelet.ui.pasien.Format
import com.fallbracelet.ui.pasien.JudulLayar
import com.fallbracelet.ui.pasien.KartuPasien
import com.fallbracelet.ui.pasien.LabelBagian
import com.fallbracelet.ui.pasien.Lencana
import com.fallbracelet.ui.pasien.TemaPasien
import com.fallbracelet.ui.pasien.WarnaPasien
import com.fallbracelet.ui.requestBatteryExemption
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * PMO mode (PRD 6.2 M1–M3), the same screen as the relay's web page (relay/web/index.html):
 * link with the patient's invite code, then per patient "Pendamping untuk [nama]", the day, an
 * alert card for each unseen ESKALASI with "Tandai dilihat", the 7-day row and the past alerts.
 * A new or re-sent ESKALASI opens an alert box; "Nanti" puts it aside until it is sent again.
 */
@Composable
fun PmoApp(vm: AppViewModel) {
    val settings by vm.settings.collectAsState()
    val pasien by vm.pasienPmo.collectAsState()
    val stream by vm.streamState.collectAsState()
    val galat by vm.caregiverError.collectAsState()
    val berdering by vm.berdering.collectAsState()
    val nanti by vm.nanti.collectAsState()
    val mengirim by vm.mengirim.collectAsState()
    var menautkan by rememberSaveable { mutableStateOf(false) }
    var konfirmasiKeluar by remember { mutableStateOf(false) }
    // Keeps "5 menit lalu" current.
    val sekarang by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(30_000)
            value = System.currentTimeMillis()
        }
    }
    val terhubung = settings.caregiverToken != null

    TemaPasien {
        Scaffold(containerColor = WarnaPasien.Latar) { pad ->
            Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState())) {
                JudulLayar(
                    stringResource(R.string.pd_judul),
                    if (terhubung && settings.caregiverName.isNotBlank()) stringResource(R.string.pd_masuk_sebagai, settings.caregiverName)
                    else stringResource(R.string.pd_sub),
                )
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    PeringatanPmo()
                    if (terhubung) {
                        pasien.forEach { p -> key(p.id) { KartuPasienPmo(p, sekarang, mengirim, vm::acknowledge) } }
                        if (pasien.isEmpty()) KartuPasien { Text(stringResource(R.string.pd_belum_mendampingi), color = WarnaPasien.Redup) }
                        KartuRelay(settings.relayUrl, stream, galat)
                    }
                    if (!terhubung || menautkan) {
                        KartuTautan(vm, settings.relayUrl, settings.caregiverName, batal = if (terhubung) ({ menautkan = false }) else null) {
                            menautkan = false
                        }
                    }
                    if (terhubung && !menautkan) {
                        OutlinedButton(onClick = { vm.clearLinkResult(); menautkan = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.pd_dampingi_lain))
                        }
                    }
                    if (terhubung) {
                        OutlinedButton(onClick = { konfirmasiKeluar = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.pd_keluar))
                        }
                    }
                    TextButton(onClick = { vm.setMode(AppMode.WEARER) }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.pd_pakai_sebagai_pasien))
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
        }

        // The web page's alert box: the oldest unseen ESKALASI not put aside with "Nanti".
        val atas = berdering.firstOrNull { it.kabar.id !in nanti }
        if (atas != null) KotakKabar(atas, berdering.size - 1, atas.kabar.id in mengirim, vm::acknowledge, vm::nanti)

        if (konfirmasiKeluar) {
            AlertDialog(
                onDismissRequest = { konfirmasiKeluar = false },
                title = { Text(stringResource(R.string.pd_keluar_judul)) },
                text = { Text(stringResource(R.string.pd_keluar_isi)) },
                confirmButton = {
                    TextButton(onClick = { vm.signOutCaregiver(); konfirmasiKeluar = false }) {
                        Text(stringResource(R.string.pd_keluar_ya), color = WarnaPasien.Buruk)
                    }
                },
                dismissButton = { TextButton(onClick = { konfirmasiKeluar = false }) { Text(stringResource(R.string.batal)) } },
            )
        }
    }
}

@Composable
private fun KotakKabar(b: Berdering, lainnya: Int, mengirim: Boolean, tandai: (Long) -> Unit, nanti: (Long) -> Unit) {
    val context = LocalContext.current
    val e = b.kabar
    AlertDialog(
        onDismissRequest = {}, // only the two buttons close it
        title = { Text(judulKabar(context, e, b.nama), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                if (e.message.isNotBlank()) Text(e.message, textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp))
                Text(ringkasKabar(context, e), color = WarnaPasien.Buruk, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
                Spacer(Modifier.height(4.dp))
                Text(
                    listOfNotNull(
                        terakhirDibuka(context, e.detail?.terakhirDibuka),
                        if (e.detail?.hariRawan == true) stringResource(R.string.pd_hari_rawan) else null,
                        if (lainnya > 0) stringResource(R.string.pd_kabar_lain_menunggu, lainnya) else null,
                    ).joinToString(" · "),
                    color = WarnaPasien.Redup, textAlign = TextAlign.Center,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { tandai(e.id) }, enabled = !mengirim,
                colors = ButtonDefaults.buttonColors(containerColor = WarnaPasien.Buruk),
            ) { Text(stringResource(if (mengirim) R.string.pd_mengirim else R.string.pd_tandai_dilihat)) }
        },
        dismissButton = { TextButton(onClick = { nanti(e.id) }) { Text(stringResource(R.string.pd_nanti)) } },
    )
}

@Composable
private fun KartuPasienPmo(p: PasienPmo, sekarang: Long, mengirim: Set<Long>, tandai: (Long) -> Unit) {
    val context = LocalContext.current
    val r = p.ringkasan
    val terbuka = p.belumDilihat
    KartuPasien {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                LabelBagian(stringResource(R.string.pd_pendamping_untuk), Modifier.padding(bottom = 0.dp))
                Text(p.nama, style = MaterialTheme.typography.titleLarge)
            }
            if (r?.demo == true) Lencana(stringResource(R.string.pd_demo), WarnaPasien.Demo, WarnaPasien.TealMuda)
        }
        Spacer(Modifier.height(6.dp))
        when {
            p.offline -> Lencana(stringResource(R.string.pd_hp_terputus), WarnaPasien.Buruk, WarnaPasien.BurukMuda)
            p.lastHeartbeat != null -> Lencana(stringResource(R.string.pd_hp_tersambung), WarnaPasien.Baik, WarnaPasien.BaikMuda)
        }
        val keterangan = listOfNotNull(
            r?.hariKe?.let { h -> r.totalHari?.let { stringResource(R.string.pd_hari_dari, h, it) } ?: stringResource(R.string.pd_hari, h) },
            r?.at?.let { stringResource(R.string.pd_kabar_terakhir, lalu(context, it, sekarang)) },
        )
        if (keterangan.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(keterangan.joinToString(" · "), color = WarnaPasien.Redup, style = MaterialTheme.typography.bodySmall)
        }
        r?.bepergianSampai?.let { sampai ->
            Spacer(Modifier.height(12.dp))
            KartuPasien(latar = WarnaPasien.TealMuda, garis = WarnaPasien.Teal) {
                Text(stringResource(R.string.pd_bepergian_sampai, Format.tanggalPendek(sampai)), color = WarnaPasien.Teal, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.pd_bepergian_isi), style = MaterialTheme.typography.bodySmall)
            }
        }
        terbuka.firstOrNull()?.let { atas ->
            Spacer(Modifier.height(12.dp))
            KartuPasien(latar = WarnaPasien.BurukMuda, garis = WarnaPasien.Buruk) {
                Text(ringkasKabar(context, atas), color = WarnaPasien.Buruk, style = MaterialTheme.typography.titleMedium)
                Text(terakhirDibuka(context, atas.detail?.terakhirDibuka))
                if (terbuka.size > 1) Text(stringResource(R.string.pd_kabar_lain_belum, terbuka.size - 1), style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { tandai(atas.id) }, enabled = atas.id !in mengirim, modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = WarnaPasien.Buruk),
                ) { Text(stringResource(if (atas.id in mengirim) R.string.pd_mengirim else R.string.pd_tandai_dilihat)) }
            }
        }
        Spacer(Modifier.height(14.dp))
        LabelBagian(stringResource(R.string.pd_tujuh_hari))
        DeretMinggu(p)
        Spacer(Modifier.height(14.dp))
        LabelBagian(stringResource(R.string.pd_kabar))
        val kabar = p.eskalasi
        if (kabar.isEmpty()) Text(stringResource(R.string.pd_belum_ada_kabar), color = WarnaPasien.Redup)
        kabar.take(20).forEachIndexed { i, e ->
            if (i > 0) HorizontalDivider(Modifier.padding(vertical = 10.dp), color = WarnaPasien.Garis)
            BarisKabar(e, sekarang, e.id in mengirim, tandai)
        }
    }
}

private val idLocale = Locale.forLanguageTag("id-ID")
private val hariPendek = DateTimeFormatter.ofPattern("EEE", idLocale)

/** ✓ ✗ ! • – for the last 7 days, as on the web page. */
@Composable
private fun DeretMinggu(p: PasienPmo) {
    val sel = TampilanPmo.minggu(p)
    if (sel == null) {
        Text(stringResource(R.string.pd_belum_ada_ringkasan), color = WarnaPasien.Redup, style = MaterialTheme.typography.bodySmall)
        return
    }
    Row(Modifier.fillMaxWidth()) {
        sel.forEach { s ->
            val (warna, latar) = when (s.jenis) {
                JenisSel.BAIK -> WarnaPasien.Baik to WarnaPasien.BaikMuda
                JenisSel.BURUK -> WarnaPasien.Buruk to WarnaPasien.BurukMuda
                JenisSel.WASPADA -> WarnaPasien.Waspada to WarnaPasien.WaspadaMuda
                JenisSel.NETRAL -> WarnaPasien.Redup to WarnaPasien.Latar
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(s.tanggal.format(hariPendek), color = WarnaPasien.Redup, style = MaterialTheme.typography.bodySmall)
                Box(Modifier.padding(vertical = 4.dp).size(36.dp).background(latar, CircleShape), contentAlignment = Alignment.Center) {
                    Text(s.simbol, color = warna, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
                Text(s.tanggal.dayOfMonth.toString(), color = WarnaPasien.Redup, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    Text(stringResource(R.string.pd_keterangan_minggu), color = WarnaPasien.Redup, style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun BarisKabar(e: KabarPmo, sekarang: Long, mengirim: Boolean, tandai: (Long) -> Unit) {
    val context = LocalContext.current
    val zona = ZoneId.systemDefault()
    val d = e.detail
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Lencana(stringResource(R.string.pd_dosis_terlewat), WarnaPasien.Buruk, WarnaPasien.BurukMuda)
            Spacer(Modifier.size(8.dp))
            Text(
                listOfNotNull(d?.tanggal?.let(Format::tanggalPendek), d?.hariKe?.let { stringResource(R.string.pd_hari_ke, it) }).joinToString(" · "),
                color = WarnaPasien.Redup, style = MaterialTheme.typography.bodySmall,
            )
        }
        d?.terlewatBerturut?.takeIf { it > 0 }?.let { Text(stringResource(R.string.pd_terlewat_berturut, it)) }
        Text(
            terakhirDibuka(context, d?.terakhirDibuka) + " · " +
                stringResource(R.string.pd_dikirim, Format.jam(Instant.ofEpochMilli(e.receivedAt), zona)) +
                if (e.escalations > 0) stringResource(R.string.pd_diulang, e.escalations) else "",
            color = WarnaPasien.Redup, style = MaterialTheme.typography.bodySmall,
        )
        if (e.ackedAt != null) {
            Text(stringResource(R.string.pd_dilihat_oleh, e.ackedBy.orEmpty(), lalu(context, e.ackedAt, sekarang)), color = WarnaPasien.Baik, style = MaterialTheme.typography.bodySmall)
        } else {
            OutlinedButton(onClick = { tandai(e.id) }, enabled = !mengirim) {
                Text(stringResource(if (mengirim) R.string.pd_mengirim else R.string.pd_tandai_dilihat))
            }
        }
    }
}

@Composable
private fun KartuRelay(url: String, stream: StreamState, galat: String?) {
    KartuPasien {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                LabelBagian(stringResource(R.string.pd_relay))
                Text(url, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
            }
            when (stream) {
                StreamState.LIVE -> Lencana(stringResource(R.string.pd_tersambung), WarnaPasien.Baik, WarnaPasien.BaikMuda)
                StreamState.PUSH -> Lencana(stringResource(R.string.pd_lewat_push), WarnaPasien.Baik, WarnaPasien.BaikMuda)
                StreamState.CONNECTING -> Lencana(stringResource(R.string.pd_menyambung), WarnaPasien.Redup, WarnaPasien.TealMuda)
                StreamState.OFF -> Lencana(stringResource(R.string.pd_terputus), WarnaPasien.Buruk, WarnaPasien.BurukMuda)
            }
        }
        galat?.let { Text(it, color = WarnaPasien.Buruk, style = MaterialTheme.typography.bodySmall) }
    }
}

private val gayaKode = TextStyle(fontSize = 24.sp, fontFamily = FontFamily.Monospace, letterSpacing = 7.sp, textAlign = TextAlign.Center)

@Composable
private fun KartuTautan(vm: AppViewModel, relayUrl: String, namaTersimpan: String, batal: (() -> Unit)?, selesai: () -> Unit) {
    val hasil by vm.linkResult.collectAsState()
    val menghubungkan by vm.linking.collectAsState()
    val mencari by vm.finding.collectAsState()
    val pesanRelay by vm.relayMessage.collectAsState()
    var url by rememberSaveable { mutableStateOf(relayUrl) }
    var kode by rememberSaveable { mutableStateOf("") }
    var nama by rememberSaveable { mutableStateOf(namaTersimpan) }
    LaunchedEffect(hasil) { if (hasil?.berhasil == true) { kode = ""; selesai() } }
    KartuPasien {
        LabelBagian(stringResource(R.string.pd_hubungkan_judul))
        Text(stringResource(R.string.pd_hubungkan_isi), color = WarnaPasien.Redup)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = kode, onValueChange = { kode = it.filter(Char::isDigit).take(6) },
            label = { Text(stringResource(R.string.pd_kode)) },
            placeholder = { Text("000000", style = gayaKode, modifier = Modifier.fillMaxWidth()) },
            textStyle = gayaKode, singleLine = true, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        OutlinedTextField(
            value = nama, onValueChange = { nama = it.take(40) },
            label = { Text(stringResource(R.string.pd_nama_kamu)) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        // The web page is served by the relay, so only the app has to be told where it is.
        OutlinedTextField(
            value = url, onValueChange = { url = it },
            label = { Text(stringResource(R.string.pd_alamat_relay)) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        )
        OutlinedButton(onClick = { vm.findRelayPmo { url = it } }, enabled = !mencari) {
            Text(stringResource(if (mencari) R.string.pd_mencari else R.string.pd_cari_relay))
        }
        pesanRelay?.let { Text(it, color = WarnaPasien.Buruk, style = MaterialTheme.typography.bodySmall) }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { vm.link(url, kode, nama) },
            enabled = !menghubungkan && kode.length == 6 && nama.isNotBlank() && url.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(if (menghubungkan) R.string.pd_menghubungkan else R.string.pd_hubungkan)) }
        if (batal != null) {
            OutlinedButton(onClick = batal, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.batal)) }
        }
        hasil?.let { Text(it.teks, color = if (it.berhasil) WarnaPasien.Baik else WarnaPasien.Buruk) }
    }
}

/** A phone setting that stops ESKALASI reaching the PMO, with the fix. */
private data class MasalahPmo(val teks: Int, val tombol: Int?, val perbaiki: ((Context) -> Unit)?)

private fun masalahPmo(context: Context): List<MasalahPmo> = buildList {
    if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
        add(MasalahPmo(R.string.pd_izin_notifikasi, R.string.izin_nyalakan_notifikasi, ::openNotificationSettings))
    }
    if (Build.VERSION.SDK_INT >= 34 && !context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()) {
        add(MasalahPmo(R.string.pd_izin_layar_penuh, R.string.izin_izinkan_layar_penuh, ::openFullScreenSettings))
    }
    if (!context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)) {
        add(MasalahPmo(R.string.pd_izin_baterai, R.string.izin_izinkan_latar, ::requestBatteryExemption))
    }
    if (!hasInternet(context)) add(MasalahPmo(R.string.pd_izin_jaringan, null, null))
}

/** Re-checked on resume and every 15 s; hidden when all is well. */
@Composable
private fun PeringatanPmo() {
    val context = LocalContext.current
    var detak by remember { mutableIntStateOf(0) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val pengamat = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) detak++ }
        lifecycle.addObserver(pengamat)
        onDispose { lifecycle.removeObserver(pengamat) }
    }
    LaunchedEffect(Unit) {
        while (true) { delay(15_000); detak++ }
    }
    val masalah = remember(detak) { masalahPmo(context) }
    if (masalah.isEmpty()) return
    KartuPasien(latar = WarnaPasien.BurukMuda, garis = WarnaPasien.Buruk) {
        Text(stringResource(R.string.pd_izin_judul), color = WarnaPasien.Buruk, style = MaterialTheme.typography.titleMedium)
        masalah.forEach { m ->
            Spacer(Modifier.height(10.dp))
            Text(stringResource(m.teks), style = MaterialTheme.typography.bodyMedium)
            if (m.perbaiki != null && m.tombol != null) {
                OutlinedButton(onClick = { m.perbaiki.invoke(context) }) { Text(stringResource(m.tombol)) }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.izin_hilang_sendiri), color = WarnaPasien.Redup, style = MaterialTheme.typography.bodySmall)
    }
}

/** "baru saja", "5 menit lalu", "2 jam lalu", "3 hari lalu". */
private fun lalu(context: Context, t: Long, sekarang: Long): String {
    val s = (sekarang - t) / 1000
    return when {
        s < 60 -> context.getString(R.string.pd_baru_saja)
        s < 3600 -> context.getString(R.string.pd_menit_lalu, (s / 60).toInt())
        s < 86_400 -> context.getString(R.string.pd_jam_lalu, (s / 3600).toInt())
        else -> context.getString(R.string.pd_hari_lalu, (s / 86_400).toInt())
    }
}
