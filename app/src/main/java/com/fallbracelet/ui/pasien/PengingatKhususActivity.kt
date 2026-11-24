package com.fallbracelet.ui.pasien

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.fallbracelet.PasienManager
import com.fallbracelet.R
import com.fallbracelet.adherence.LogikaKepatuhan
import com.fallbracelet.data.LogPengingat
import com.fallbracelet.pasien
import kotlinx.coroutines.launch
import java.time.ZoneId

/**
 * P5 Pengingat khusus: the message from the rules file (with its variables filled), today's
 * box status so the trigger is visible, and the two buttons. Which button was pressed (or that
 * the screen was closed) goes into the reminder log.
 */
class PengingatKhususActivity : ComponentActivity() {

    private var kunci: String = ""
    private var aksiDicatat = false

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        kunci = intent.getStringExtra(EXTRA_KUNCI).orEmpty()
        aksiDicatat = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        tepiKeTepiTerang()
        super.onCreate(savedInstanceState)
        kunci = intent.getStringExtra(EXTRA_KUNCI).orEmpty()
        val p = pasien
        setContent {
            TemaPasien {
                Surface(Modifier.fillMaxSize(), color = WarnaPasien.Latar) {
                    Isi(p, onSudahMinum = { aksi { sudahMinumDariPesan(it) } }, onTunda = { aksi { tunda(it) } }, onTutup = { finish() })
                }
            }
        }
    }

    /** Runs [blok] on the app's own scope (it must finish after this screen closes), then closes. */
    private fun aksi(blok: suspend PasienManager.(String) -> Unit) {
        aksiDicatat = true
        val k = kunci
        val p = pasien
        p.cakupan.launch { p.blok(k) }
        finish()
    }

    override fun onDestroy() {
        if (isFinishing && !aksiDicatat && kunci.isNotEmpty()) {
            val k = kunci
            val p = pasien
            p.cakupan.launch { p.catatAksi(k, LogPengingat.AKSI_DITUTUP) }
        }
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_KUNCI = "kunci"

        fun intent(context: Context, kunci: String): Intent =
            Intent(context, PengingatKhususActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(EXTRA_KUNCI, kunci)
    }
}

@androidx.compose.runtime.Composable
private fun Isi(p: PasienManager, onSudahMinum: () -> Unit, onTunda: () -> Unit, onTutup: () -> Unit) {
    val k by p.keadaan.collectAsState()
    val keadaan = k ?: return
    val a = p.aturan.aturan
    val zona = ZoneId.systemDefault()
    val pesan = LogikaKepatuhan.pilihPesan(a.pesan, PasienManager.PESAN_HARI_RAWAN, keadaan.hariKe)
    val hari = keadaan.hariIni

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (keadaan.pengaturan.demo.aktif) Lencana(stringResource(R.string.demo_penanda), WarnaPasien.Kartu, WarnaPasien.Demo)

        // The message itself, from the rules file
        KartuPasien(latar = WarnaPasien.TealMuda, garis = WarnaPasien.Teal) {
            if (pesan != null) {
                Text(pesan.judul, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                Text(
                    LogikaKepatuhan.isiPesan(pesan.isi, keadaan.pengaturan.namaPanggilan, keadaan.hariKe, a.pengobatan, keadaan.dosisTercatat),
                    style = MaterialTheme.typography.bodyLarge,
                )
            } else {
                Text(stringResource(R.string.p5_tanpa_pesan), color = WarnaPasien.Redup)
            }
        }

        // Today's box status: what the reminder is based on
        KartuPasien {
            LabelBagian(stringResource(R.string.p5_status_kotak))
            if (hari != null) {
                val (fg, bg) = warnaStatus(hari.status)
                Lencana(teksStatus(hari.status), fg, bg)
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.dosis_jam, Format.jam(hari.jamMinum)))
            }
            Text(
                keadaan.terakhirDibuka?.let { stringResource(R.string.kotak_terakhir_dibuka, Format.waktu(it, zona)) }
                    ?: stringResource(R.string.kotak_belum_dibuka),
                color = WarnaPasien.Redup,
            )
            Spacer(Modifier.height(8.dp))
            LabelBagian(stringResource(R.string.p5_dasar))
            if (keadaan.hariRawan.olehWaktu) Text(stringResource(R.string.p5_dasar_waktu, keadaan.hariKe), style = MaterialTheme.typography.bodySmall)
            if (keadaan.hariRawan.olehPola) {
                Text(
                    stringResource(R.string.p5_dasar_pola, keadaan.hariRawan.jumlahTerlewat, a.hariRawan.pola.dalamHari),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        if (hari != null && hari.status.masihBerjalan) {
            Button(onClick = onSudahMinum, modifier = Modifier.fillMaxWidth().height(60.dp)) {
                Text(stringResource(R.string.dosis_sudah_minum))
            }
            OutlinedButton(onClick = onTunda, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Text(stringResource(R.string.p5_tunda, a.dosis.pengingatUlang.jarakMenit))
            }
        } else {
            Text(stringResource(R.string.p5_sudah_tercatat), color = WarnaPasien.Redup)
        }
        TextButton(onClick = onTutup, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.p5_tutup)) }
    }
}
