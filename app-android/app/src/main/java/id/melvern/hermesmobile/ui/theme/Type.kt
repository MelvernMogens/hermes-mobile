package id.melvern.hermesmobile.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import id.melvern.hermesmobile.R

/**
 * M8: Inter (rsms/inter v4.1, OFL — docs/licenses/Inter-OFL.txt) untuk semua UI;
 * JetBrains Mono HANYA untuk code / inline code / path.
 * Semua ukuran sp → ikut font scale sistem. Nol uppercase + tracking lebar.
 */
val Inter: FontFamily = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

val JetBrainsMono: FontFamily = FontFamily(
    Font(R.font.jetbrainsmono_regular, FontWeight.Normal),
    Font(R.font.jetbrainsmono_italic, FontWeight.Normal, FontStyle.Italic),
    Font(R.font.jetbrainsmono_bold, FontWeight.Bold),
    Font(R.font.jetbrainsmono_bold_italic, FontWeight.Bold, FontStyle.Italic),
)

/** Type scale M8 — pakai ini langsung (`Type.Title`), bukan angka sp di screen. */
object Type {
    /** 28/34 SemiBold −0.4 — large title "Chats", judul Connect. */
    val Display = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-0.4).sp, color = Ink.Text)
    /** 17/22 SemiBold −0.2 — header chat, nama session, judul sheet. */
    val Title = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 22.sp, letterSpacing = (-0.2).sp, color = Ink.Text)
    /** 16/24 Regular — prosa assistant + bubble user + input composer. */
    val Body = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp, color = Ink.Text)
    /** 16 SemiBold — label tombol solid (Connect). */
    val Button = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 20.sp, color = Ink.OnAccent)
    /** 15/20 Regular — preview baris 2, isi sheet, banner. */
    val Callout = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 20.sp, color = Ink.Text)
    /** 13/18 Regular — model line, timestamp list, label kecil. */
    val Meta = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp, color = Ink.Text2)
    val MetaMedium = Meta.copy(fontWeight = FontWeight.Medium)
    /** 12/16 Medium — timestamp bubble, badge, day chip. */
    val Caption = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, color = Ink.Text3)

    /** Code block body 13/19. */
    val Mono = TextStyle(fontFamily = JetBrainsMono, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 19.sp, color = Ink.Text)
    /** Nama bahasa di header code block — meta, lowercase, mono. */
    val MonoMeta = TextStyle(fontFamily = JetBrainsMono, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp, color = Ink.Text2)
    /** Inline code di prosa. */
    val InlineMonoSize = 14.sp
}
