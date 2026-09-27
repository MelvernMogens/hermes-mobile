package id.melvern.hermesmobile.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * GitHub dark — match Hermes desktop skin github (theme-presets.ts).
 * Nama token TETAP (screens gak perlu diubah) — cuma nilai hex yang ganti.
 */
object F {
    // canvas & tone steps
    val Bg = Color(0xFF0D1117)        // background utama
    val BgDeep = Color(0xFF010409)    // card / paling gelap, statusbar/scrim
    val Surface1 = Color(0xFF161B22)  // popover — pill user, composer fill
    val Surface2 = Color(0xFF21262D)  // VS Code widget bg — pressed, chip aktif
    val Surface3 = Color(0xFF30363D)  // border-step utk pressed/dialog
    val Stroke = Color(0xFF30363D)    // hairline divider 1dp
    val StrokeBright = Color(0xFF484F58)

    // teks
    val Cream = Color(0xFFE6EDF3)     // foreground utama + prose assistant
    val CreamDim = Color(0xFFC9D1D9)  // teks di dalam surface pill
    val Lavender = Color(0xFF7D8590)  // mutedForeground — meta, secondary
    val LavenderDim = Color(0xFF6E7681) // placeholder, tertiary

    // accent — GitHub green (nama token tetap Vermillion), coverage <10%
    val Vermillion = Color(0xFF4F9E5E)
    val VermillionDeep = Color(0xFF3FB950)
    val VermillionSoft = Color(0xFF1F382B) // soft green bg

    // semantik — error tetap merah (safety: error harus bisa dibedain)
    val Ok = Color(0xFF4F9E5E)
    val Warn = Color(0xFFD29922)
    val Error = Color(0xFFF85149)

    // kode (belakang panggung)
    val CodeBg = Color(0xFF010409)

    // UserBubble — VS Code neutral (bukan greenish)
    val UserBubble = Color(0xFF1C2128)
}
