package id.melvern.hermesmobile.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Grid 4dp. Semua ukuran komponen di sini — screen tidak pakai angka ajaib. */
object Dim {
    val ScreenH = 20.dp          // margin horizontal layar (lebih lega = lebih mahal)
    val GroupInset = 12.dp       // margin grouped surface (kartu list) ke tepi layar
    val GroupPadH = 14.dp        // padding isi di dalam grouped surface
    val Icon = 22.dp             // ikon standar
    val IconSmall = 16.dp        // ikon di baris tool / chevron
    val IconTiny = 12.dp         // ikon status di bubble (Schedule)
    val Touch = 48.dp            // touch target minimum
    val TopBar = 56.dp
    val Dot = 8.dp               // dot status semantik

    val AvatarBar = 32.dp        // avatar di top bar
    val AvatarRow = 48.dp        // avatar row session
    val AvatarSheet = 40.dp      // avatar row profile sheet / bot
    val AvatarEmpty = 72.dp      // avatar empty chat
    val RunningRing = 2.dp

    val RowMin = 76.dp           // row session
    val RowGap = 14.dp           // avatar ↔ teks
    val SheetRow = 60.dp
    val ActionRow = 52.dp

    // adaptive tablet — list-detail two-pane (Expanded width).
    val PaneListW = 360.dp
    val ChatMaxW = 680.dp
    val BubbleMaxW = 560.dp
    val SheetMaxW = 480.dp
    val ArtifactSheetW = 380.dp

    val ComposerMin = 44.dp
    val SendButton = 36.dp
    val ScrollFab = 40.dp
    val CodeHeader = 34.dp
    val ToolRow = 30.dp
    val BannerH = 40.dp
    val FieldH = 56.dp
    val ButtonH = 52.dp
    val Thumb = 40.dp
    val ConnectMaxW = 420.dp
    val LogoConnect = 64.dp
    val EmptyIcon = 48.dp
    val NavBar = 64.dp

    /** Inset divider session = margin + avatar + gap (sejajar teks, gaya iOS/WA). */
    val RowDividerInset: Dp = ScreenH + AvatarRow + RowGap
}

/** Radius: 8 inline/thumb · 12 chip/code · 16 card/group · 20 bubble/composer · 24 sheet. */
object Radius {
    val Inline = RoundedCornerShape(6.dp)
    val Thumb = RoundedCornerShape(8.dp)
    val Chip = RoundedCornerShape(10.dp)
    val Field = RoundedCornerShape(14.dp)
    val Card = RoundedCornerShape(16.dp)
    val Group = RoundedCornerShape(18.dp)
    val Bubble = RoundedCornerShape(20.dp)
    /** Bubble user: sudut kanan-bawah 6 (ekor). */
    val BubbleUser = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomEnd = 6.dp, bottomStart = 20.dp)
    val Sheet = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    val Full = CircleShape
}

/** Hairline: 1dp, 0.5dp kalau density >= 3 (tetap ≥ 1px fisik). */
@Composable
@ReadOnlyComposable
fun hairline(): Dp = if (LocalDensity.current.density >= 3f) 0.5.dp else 1.dp
