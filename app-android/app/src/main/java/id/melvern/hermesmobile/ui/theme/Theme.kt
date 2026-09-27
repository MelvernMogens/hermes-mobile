package id.melvern.hermesmobile.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.melvern.hermesmobile.R

/**
 * Type system Fillmore: dua voice —
 *  - "bill" (sans condensed-tracking, uppercase) untuk chrome/header/meta:
 *    header screen, session title kecil-nya, label tool, tombol.
 *  - "prose" (serif italic) untuk konten assistant — signature rasa handbill.
 * User message = sans regular di pill (kontras role).
 *
 * Font bundling: serif = Times New Roman Bold Italic look (fallback serif
 * italic system), sans = default. TANPA font file eksternal dulu —
 * keputusan M2.1: pakai generic serif italic + default sans supaya APK
 * tetap kecil; upgrade ke font file di polish pass kalau perlu.
 */

val ProseSerif: FontFamily = FontFamily.Serif
val BillSans: FontFamily = FontFamily.SansSerif

val HermesType = Typography(
    // prose assistant — serif italic 17sp, nyaman panjang
    bodyLarge = TextStyle(
        fontFamily = ProseSerif, fontStyle = FontStyle.Italic,
        fontWeight = FontWeight.Medium, fontSize = 17.sp, lineHeight = 26.sp,
        color = F.Cream,
    ),
    // user message + composer input
    bodyMedium = TextStyle(
        fontFamily = BillSans, fontWeight = FontWeight.Normal,
        fontSize = 16.sp, lineHeight = 22.sp, color = F.Cream,
    ),
    // meta line (tool label, preview)
    bodySmall = TextStyle(
        fontFamily = BillSans, fontWeight = FontWeight.Medium,
        fontSize = 12.sp, lineHeight = 17.sp, letterSpacing = 0.8.sp, color = F.Lavender,
    ),
    // empty state greeting / TONIGHT
    titleLarge = TextStyle(
        fontFamily = ProseSerif, fontStyle = FontStyle.Italic,
        fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 40.sp,
        color = F.Cream,
    ),
    // session row title (bill type)
    titleMedium = TextStyle(
        fontFamily = ProseSerif, fontStyle = FontStyle.Italic,
        fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp,
        color = F.Cream,
    ),
    // header wordmark / condensed labels
    labelLarge = TextStyle(
        fontFamily = BillSans, fontWeight = FontWeight.Bold,
        fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 3.sp,
        color = F.Cream,
    ),
    labelSmall = TextStyle(
        fontFamily = BillSans, fontWeight = FontWeight.Bold,
        fontSize = 10.sp, lineHeight = 14.sp, letterSpacing = 1.6.sp,
        color = F.Lavender,
    ),
)

/** Radius system — 3 step + pill. */
object Shape {
    val Xs = RoundedCornerShape(8.dp)
    val S = RoundedCornerShape(12.dp)
    val M = RoundedCornerShape(20.dp)
    val Pill = RoundedCornerShape(999.dp)
    val Ticket = RoundedCornerShape(24.dp)
}

private val Scheme = darkColorScheme(
    primary = F.Vermillion,
    onPrimary = F.Cream,
    secondary = F.Lavender,
    onSecondary = F.Bg,
    tertiary = F.Warn,
    background = F.Bg,
    onBackground = F.Cream,
    surface = F.Bg,
    onSurface = F.Cream,
    surfaceVariant = F.Surface1,
    onSurfaceVariant = F.Lavender,
    surfaceContainer = F.Surface1,
    surfaceContainerHigh = F.Surface2,
    outline = F.Stroke,
    outlineVariant = F.Stroke,
    error = F.Error,
    onError = F.BgDeep,
)

@Composable
fun HermesTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, typography = HermesType, content = content)
}
