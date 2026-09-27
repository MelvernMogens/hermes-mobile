package id.melvern.hermesmobile.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Fillmore Handbill tokens — palet terverifikasi dari quality bar board/hero
 * (PIL extraction: indigo #002070/#1B2A52 dominan, vermillion #E05000, cream #F3EAD8).
 * Ground = deep indigo (BUKAN abu netral / pure black), accent vermillion
 * cuma untuk aksi/hidup. Depth = tone steps, bukan shadow.
 */
object F {
    // canvas & tone steps
    val Bg = Color(0xFF131A33)        // canvas utama
    val BgDeep = Color(0xFF0E1426)    // statusbar/scrim bawah
    val Surface1 = Color(0xFF1B2A52)  // pill user, composer fill, card
    val Surface2 = Color(0xFF24305C)  // pressed, chip aktif
    val Surface3 = Color(0xFF2E3B6E)  // dialog/FAB tone
    val Stroke = Color(0xFF2A3560)    // hairline divider 1dp
    val StrokeBright = Color(0xFF3A4A85)

    // teks
    val Cream = Color(0xFFF3EAD8)     // teks utama + prose assistant
    val CreamDim = Color(0xFFD6DCF0)  // teks di dalam surface pill
    val Lavender = Color(0xFF8A93C4)  // meta, secondary
    val LavenderDim = Color(0xFF5A6390) // placeholder, tertiary

    // accent — VERMILLION, coverage <10%
    val Vermillion = Color(0xFFE05000)
    val VermillionDeep = Color(0xFFC24400)
    val VermillionSoft = Color(0x26E05000) // 15% — glow/thinking bg

    // semantik
    val Ok = Color(0xFF7FB069)
    val Warn = Color(0xFFE8B96F)
    val Error = Color(0xFFF14D42)

    // kode (belakang panggung)
    val CodeBg = Color(0xFF0C1220)
}
