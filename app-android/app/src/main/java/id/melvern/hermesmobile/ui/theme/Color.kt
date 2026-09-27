package id.melvern.hermesmobile.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Monochrome — user request 27 Sep: tema hitam-putih murni, tanpa tint rona.
 * Nama token TETAP (screens gak perlu diubah) — cuma nilai hex yang ganti.
 * Depth = tone steps abu, kontras = putih di atas hitam, bukan hue.
 */
object F {
    // canvas & tone steps
    val Bg = Color(0xFF0A0A0A)        // canvas utama
    val BgDeep = Color(0xFF000000)    // statusbar/scrim bawah
    val Surface1 = Color(0xFF161616)  // pill user, composer fill, card
    val Surface2 = Color(0xFF1F1F1F)  // pressed, chip aktif
    val Surface3 = Color(0xFF2A2A2A)  // dialog/FAB tone
    val Stroke = Color(0xFF262626)    // hairline divider 1dp
    val StrokeBright = Color(0xFF333333)

    // teks
    val Cream = Color(0xFFF5F5F5)     // teks utama + prose assistant
    val CreamDim = Color(0xFFE0E0E0)  // teks di dalam surface pill
    val Lavender = Color(0xFF9A9A9A)  // meta, secondary
    val LavenderDim = Color(0xFF666666) // placeholder, tertiary

    // accent — PUTIH (monochrome), coverage <10%
    val Vermillion = Color(0xFFFFFFFF)
    val VermillionDeep = Color(0xFFDDDDDD)
    val VermillionSoft = Color(0x26FFFFFF) // 15% — glow/thinking bg

    // semantik — error tetap merah (safety: error harus bisa dibedain)
    val Ok = Color(0xFF8F8F8F)
    val Warn = Color(0xFFCFCFCF)
    val Error = Color(0xFFFF6B6B)

    // kode (belakang panggung)
    val CodeBg = Color(0xFF050505)
}
