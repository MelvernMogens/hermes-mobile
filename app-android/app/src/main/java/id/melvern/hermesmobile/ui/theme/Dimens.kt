package id.melvern.hermesmobile.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** M8: grid 4dp. Semua ukuran komponen di sini — screen tidak pakai angka ajaib. */
object Dim {
    val ScreenH = 16.dp          // margin horizontal layar
    val Icon = 22.dp             // ikon standar
    val IconSmall = 16.dp        // ikon di baris tool / model chevron
    val IconTiny = 12.dp         // ikon status di bubble (Schedule)
    val Touch = 48.dp            // touch target minimum
    val TopBar = 56.dp
    val Dot = 8.dp               // dot status semantik

    val AvatarBar = 32.dp        // avatar di top bar
    val AvatarRow = 44.dp        // avatar row session
    val AvatarSheet = 40.dp      // avatar row profile sheet
    val AvatarEmpty = 56.dp      // avatar empty chat
    val RunningRing = 2.dp

    val RowMin = 72.dp           // row session
    val RowGap = 12.dp           // avatar ↔ teks
    val SheetRow = 64.dp
    val ActionRow = 52.dp

    val ComposerMin = 44.dp
    val SendButton = 36.dp
    val ScrollFab = 36.dp
    val CodeHeader = 32.dp
    val ToolRow = 32.dp
    val BannerH = 36.dp
    val FieldH = 56.dp
    val ButtonH = 50.dp
    val Thumb = 40.dp
    val ConnectMaxW = 420.dp
    val LogoConnect = 56.dp
    val EmptyIcon = 48.dp

    /** Inset divider session = margin + avatar + gap (sejajar teks, gaya iOS/WA). */
    val RowDividerInset: Dp = ScreenH + AvatarRow + RowGap
}

/** Radius M8: 10 chip/code, 18 bubble/composer, 22 sheet top, full avatar/send. */
object Radius {
    val Inline = RoundedCornerShape(6.dp)    // inline code
    val Thumb = RoundedCornerShape(8.dp)     // thumbnail attachment
    val Chip = RoundedCornerShape(10.dp)     // chip, code block
    val Field = RoundedCornerShape(12.dp)    // field Connect, tombol Connect
    val Card = RoundedCornerShape(14.dp)     // approval / clarify card
    val Bubble = RoundedCornerShape(18.dp)
    /** Bubble user: sudut kanan-bawah 6 (ekor). */
    val BubbleUser = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomEnd = 6.dp, bottomStart = 18.dp)
    val Sheet = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)
    val Full = CircleShape
}

/** Hairline: 1dp, 0.5dp kalau density >= 3 (tetap ≥ 1px fisik). */
@Composable
@ReadOnlyComposable
fun hairline(): Dp = if (LocalDensity.current.density >= 3f) 0.5.dp else 1.dp
