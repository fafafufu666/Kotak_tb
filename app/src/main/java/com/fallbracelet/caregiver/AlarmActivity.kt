package com.fallbracelet.caregiver

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.fallbracelet.R
import com.fallbracelet.caregiver
import com.fallbracelet.ui.pasien.TemaPasien
import com.fallbracelet.ui.pasien.WarnaPasien
import com.fallbracelet.ui.pasien.tepiKeTepiTerang
import kotlinx.coroutines.launch

/**
 * An ESKALASI over the lock screen, the app's version of the web page's alert box: who, how many
 * doses in a row, when the box was last opened, then "Tandai dilihat" or "Nanti".
 */
class AlarmActivity : ComponentActivity() {
    private data class Tampil(val id: Long, val judul: String, val pesan: String, val ringkas: String, val lagi: String)

    private var tampil by mutableStateOf(Tampil(-1, "", "", "", ""))
    private var mengirim by mutableStateOf(false)
    private var gagal by mutableStateOf(false)

    private fun tampilkan(i: Intent) {
        tampil = Tampil(
            i.getLongExtra(EXTRA_ID, -1), i.getStringExtra(EXTRA_JUDUL).orEmpty(), i.getStringExtra(EXTRA_PESAN).orEmpty(),
            i.getStringExtra(EXTRA_RINGKAS).orEmpty(), i.getStringExtra(EXTRA_LAGI).orEmpty(),
        )
        mengirim = false
        gagal = false
    }

    /** A newer ESKALASI while this one is open replaces it; the older one stays in the shade. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        tampilkan(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        tepiKeTepiTerang()
        super.onCreate(savedInstanceState)
        tampilkan(intent)
        setContent {
            val t = tampil
            val scope = rememberCoroutineScope()
            TemaPasien {
                Column(
                    Modifier.fillMaxSize().background(WarnaPasien.Latar).systemBarsPadding().padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(t.judul, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(16.dp))
                    Text(t.ringkas, color = WarnaPasien.Buruk, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    if (t.pesan.isNotBlank()) Text(t.pesan, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    Text(t.lagi, color = WarnaPasien.Redup, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(32.dp))
                    Button(
                        onClick = {
                            mengirim = true
                            scope.launch {
                                if (caregiver.acknowledge(t.id)) finish() else { mengirim = false; gagal = true }
                            }
                        },
                        enabled = !mengirim,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = WarnaPasien.Buruk),
                    ) {
                        Text(
                            stringResource(
                                when {
                                    mengirim -> R.string.pd_mengirim
                                    gagal -> R.string.pd_gagal_coba_lagi
                                    else -> R.string.pd_tandai_dilihat
                                }
                            ),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { caregiver.nanti(t.id); finish() },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    ) { Text(stringResource(R.string.pd_nanti)) }
                }
            }
        }
    }

    companion object {
        private const val EXTRA_ID = "id"
        private const val EXTRA_JUDUL = "judul"
        private const val EXTRA_PESAN = "pesan"
        private const val EXTRA_RINGKAS = "ringkas"
        private const val EXTRA_LAGI = "lagi"

        fun intent(context: Context, e: KabarPmo, nama: String): Intent =
            Intent(context, AlarmActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(EXTRA_ID, e.id)
                .putExtra(EXTRA_JUDUL, judulKabar(context, e, nama))
                .putExtra(EXTRA_PESAN, e.message)
                .putExtra(EXTRA_RINGKAS, ringkasKabar(context, e))
                .putExtra(
                    EXTRA_LAGI,
                    listOfNotNull(
                        terakhirDibuka(context, e.detail?.terakhirDibuka),
                        if (e.detail?.hariRawan == true) context.getString(R.string.pd_hari_rawan) else null,
                    ).joinToString(" · "),
                )
    }
}
