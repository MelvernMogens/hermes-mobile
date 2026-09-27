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
 * Type system monochrome (M3.1): dua voice —
 *  - "bill" (sans bold, tracking, uppercase) untuk chrome/header/meta:
 *    header screen, session title, label tool, tombol.
 *  - "prose" (sans regular) untuk konten assistant — diganti dari serif
 *    italic ke sans 16/24 (user 27 Sep: "pusing dibaca"). Kontras role
 *    cukup dari layout: user pill kanan vs assistant full-width.
 */

val BillSans: FontFamily = FontFamily.SansSerif

val HermesType = Typography(
    // prose assistant — sans regular, ukuran baca nyaman
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal,
        fontSize = 16.sp, lineHeight = 24.sp,
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
    // empty state greeting / TONIGHT — sans Bold (ikut monochrome M3.1)
    titleLarge = TextStyle(
        fontFamily = BillSans,
        fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 40.sp,
        color = F.Cream,
    ),
    // session row title — sans SemiBold (bukan serif italic, M3.1)
    titleMedium = TextStyle(
        fontFamily = BillSans, fontWeight = FontWeight.SemiBold,
        fontSize = 19.sp, lineHeight = 25.sp,
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
