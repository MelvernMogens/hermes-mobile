package id.melvern.hermesmobile.ui.theme

import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp
import id.melvern.hermesmobile.R

/**
 * Geist (Vercel, OFL — docs/licenses/Geist-OFL.txt) untuk UI; Geist Mono HANYA
 * untuk code / inline code / path / angka data. Satu keluarga → mono & sans
 * punya x-height & baseline sama (inline code gak "turun" di tengah kalimat).
 * Semua ukuran sp → ikut font scale sistem.
 */
val Sans: FontFamily = FontFamily(
    Font(R.font.geist_regular, FontWeight.Normal),
    Font(R.font.geist_medium, FontWeight.Medium),
    Font(R.font.geist_semibold, FontWeight.SemiBold),
    Font(R.font.geist_bold, FontWeight.Bold),
)

val MonoFamily: FontFamily = FontFamily(
    Font(R.font.geistmono_regular, FontWeight.Normal),
    Font(R.font.geistmono_medium, FontWeight.Medium),
    Font(R.font.geistmono_bold, FontWeight.Bold),
)

/** Trim padding font bawaan Android — teks duduk tepat di grid (baseline rapi). */
private val Tight = PlatformTextStyle(includeFontPadding = false)
private val Centered = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

private fun s(
    size: Int, line: Int, weight: FontWeight = FontWeight.Normal,
    tracking: Double = 0.0, color: androidx.compose.ui.graphics.Color = Ink.Text,
    family: FontFamily = Sans,
) = TextStyle(
    fontFamily = family, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp,
    letterSpacing = tracking.sp, color = color, platformStyle = Tight, lineHeightStyle = Centered,
)

/** Type scale — pakai ini langsung (`Type.Title`), bukan angka sp di screen. */
object Type {
    /** 30/36 SemiBold −0.9 — large title layar (Chats, Overview). */
    val Display = s(30, 36, FontWeight.SemiBold, -0.9)
    /** 22/28 SemiBold −0.5 — heading markdown H1, judul empty state. */
    val Headline = s(22, 28, FontWeight.SemiBold, -0.5)
    /** 17/22 SemiBold −0.3 — header chat, judul sheet, heading markdown H2+. */
    val Title = s(17, 22, FontWeight.SemiBold, -0.3)
    /** 16/22 Medium −0.2 — nama row list (session, bot). */
    val RowTitle = s(16, 22, FontWeight.Medium, -0.2)
    /** 15.5/24 Regular — prosa assistant + bubble user + input composer. */
    val Body = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 15.5.sp, lineHeight = 24.sp,
        letterSpacing = (-0.1).sp, color = Ink.Text, platformStyle = Tight, lineHeightStyle = Centered,
    )
    /** 16 SemiBold — label tombol solid (Connect). */
    val Button = s(16, 20, FontWeight.SemiBold, -0.2, Ink.OnAccent)
    /** 15/20 Regular — isi sheet, preview, banner. */
    val Callout = s(15, 20, FontWeight.Normal, -0.1)
    /** 14/19 Regular text2 — preview baris 2 di list. */
    val Preview = s(14, 19, FontWeight.Normal, -0.05, Ink.Text2)
    /** 13/18 Regular — model line, label kecil. */
    val Meta = s(13, 18, FontWeight.Normal, 0.0, Ink.Text2)
    val MetaMedium = Meta.copy(fontWeight = FontWeight.Medium)
    /** 12/16 Medium — timestamp, badge, day label. */
    val Caption = s(12, 16, FontWeight.Medium, 0.0, Ink.Text3)
    /** 12/16 Medium tracking +0.2 — label section (BOTS, PINNED) dalam huruf biasa, redup. */
    val Section = s(13, 18, FontWeight.Medium, 0.0, Ink.Text3)

    /** Angka besar dashboard — Mono tabular, tracking rapat. */
    val Figure = s(28, 32, FontWeight.Medium, -1.0, Ink.Text, MonoFamily)
    val FigureSmall = s(15, 20, FontWeight.Medium, -0.3, Ink.Text, MonoFamily)

    /** v28: timecode — Geist Mono tabular, tight. 00:32:14 */
    val Timecode = s(12, 16, FontWeight.Medium, -0.2, Ink.Text2, MonoFamily)
    val TimecodeLarge = s(15, 20, FontWeight.Medium, -0.4, Ink.Text, MonoFamily)
    /** v28: key cap legend — 13 Medium. */
    val Key = s(13, 16, FontWeight.Medium, -0.1, Ink.Text)
    /** v28: rack unit name — 13 SemiBold text. */
    val RackLabel = s(13, 18, FontWeight.SemiBold, -0.1, Ink.Text)
    /** v28: chat name on a monitor screen — 14 SemiBold. */
    val MonitorTitle = s(14, 18, FontWeight.SemiBold, -0.2, Ink.Text)
    /** v28: under-monitor display label — 12 Medium. */
    val Umd = s(12, 16, FontWeight.Medium, -0.1, Ink.Text)
    /** v28: catalog number (#041) — mono 11. */
    val Catalog = s(11, 14, FontWeight.Medium, 0.0, Ink.Text3, MonoFamily)

    /** Code block body 13/20. */
    val Mono = s(13, 20, FontWeight.Normal, 0.0, Ink.Text, MonoFamily)
    /** Nama bahasa di header code block / slug model. */
    val MonoMeta = s(12, 16, FontWeight.Normal, 0.0, Ink.Text3, MonoFamily)
    /** Inline code di prosa — 0.9em biar optis sama dengan sans. */
    val InlineMonoSize = 14.sp
}
