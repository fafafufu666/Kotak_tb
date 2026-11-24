package com.fallbracelet.ui.pasien

import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Teal and white with large text (PRD 6). Refined against Abyan's mockup in stage 6. */
object WarnaPasien {
    val Teal = Color(0xFF00796B)
    val TealMuda = Color(0xFFE0F2F1)
    val Latar = Color(0xFFF7FAFA)
    val Kartu = Color(0xFFFFFFFF)
    val Garis = Color(0xFFD5E3E1)
    val Teks = Color(0xFF14211F)
    val Redup = Color(0xFF5B6B69)
    val Baik = Color(0xFF2E7D32)
    val BaikMuda = Color(0xFFE8F5E9)
    val Waspada = Color(0xFFB26A00)
    val WaspadaMuda = Color(0xFFFFF3E0)
    val Buruk = Color(0xFFC62828)
    val BurukMuda = Color(0xFFFFEBEE)
    val Demo = Color(0xFF6A1B9A)
}

private val skema = lightColorScheme(
    primary = WarnaPasien.Teal,
    onPrimary = Color.White,
    primaryContainer = WarnaPasien.TealMuda,
    onPrimaryContainer = WarnaPasien.Teks,
    secondary = WarnaPasien.Teal,
    onSecondary = Color.White,
    secondaryContainer = WarnaPasien.TealMuda,
    onSecondaryContainer = WarnaPasien.Teks,
    background = WarnaPasien.Latar,
    onBackground = WarnaPasien.Teks,
    surface = WarnaPasien.Latar,
    onSurface = WarnaPasien.Teks,
    surfaceVariant = WarnaPasien.TealMuda,
    onSurfaceVariant = WarnaPasien.Redup,
    surfaceContainer = WarnaPasien.Kartu,
    surfaceContainerHigh = WarnaPasien.Kartu,
    surfaceContainerHighest = WarnaPasien.TealMuda,
    outline = WarnaPasien.Garis,
    error = WarnaPasien.Buruk,
)

/** Slightly larger type than the Material default, for easy reading. */
private val huruf = Typography().run {
    copy(
        bodyLarge = bodyLarge.copy(fontSize = 18.sp, lineHeight = 26.sp),
        bodyMedium = bodyMedium.copy(fontSize = 16.sp, lineHeight = 22.sp),
        bodySmall = bodySmall.copy(fontSize = 14.sp, lineHeight = 20.sp),
        titleLarge = titleLarge.copy(fontSize = 24.sp, fontWeight = FontWeight.SemiBold),
        titleMedium = titleMedium.copy(fontSize = 19.sp, fontWeight = FontWeight.SemiBold),
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = labelLarge.copy(fontSize = 17.sp),
    )
}

/**
 * Edge to edge with dark status-bar icons. The app is always light (teal and white), so the icons
 * must not follow the phone's dark mode, which made them white on white.
 */
fun ComponentActivity.tepiKeTepiTerang() {
    val bening = android.graphics.Color.TRANSPARENT
    enableEdgeToEdge(statusBarStyle = SystemBarStyle.light(bening, bening))
}

@Composable
fun TemaPasien(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = skema, typography = huruf, content = content)
}

@Composable
fun KartuPasien(
    modifier: Modifier = Modifier,
    latar: Color = WarnaPasien.Kartu,
    garis: Color = WarnaPasien.Garis,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = latar,
        border = BorderStroke(1.dp, garis),
    ) {
        Column(Modifier.padding(16.dp), content = content)
    }
}

@Composable
fun LabelBagian(teks: String, modifier: Modifier = Modifier) {
    Text(
        teks.uppercase(),
        modifier = modifier.padding(bottom = 8.dp),
        style = MaterialTheme.typography.labelMedium,
        color = WarnaPasien.Redup,
    )
}

@Composable
fun JudulLayar(judul: String, sub: String? = null) {
    Column(Modifier.statusBarsPadding().padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 12.dp)) {
        Text(judul, style = MaterialTheme.typography.headlineMedium)
        if (sub != null) Text(sub, color = WarnaPasien.Redup, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Small rounded label, e.g. a dose status. */
@Composable
fun Lencana(teks: String, warna: Color, latar: Color, modifier: Modifier = Modifier) {
    Box(modifier.background(latar, RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 4.dp)) {
        Text(teks, color = warna, style = MaterialTheme.typography.labelLarge)
    }
}
