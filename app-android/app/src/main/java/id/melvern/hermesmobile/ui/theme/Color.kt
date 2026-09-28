package id.melvern.hermesmobile.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * M8 "Quiet Mono" — SATU sumber warna app. Screen dilarang pakai hex sendiri.
 *
 * Neutral ramp hangat tipis (bukan biru GitHub). Aksen = putih (tombol send
 * aktif, Connect). Warna semantik HANYA untuk makna (dot 8dp / teks status
 * kecil) — tidak pernah jadi fill besar atau hiasan.
 */
object Ink {
    // canvas & surface
    val Bg = Color(0xFF0B0B0C)        // canvas
    val Surface1 = Color(0xFF141416)  // composer fill, sheet, code body, banner
    val Surface2 = Color(0xFF1C1C1F)  // user bubble, pressed row, chip, inline code
    val Surface3 = Color(0xFF26262A)  // code header, input focus bg
    val Hairline = Color(0xFF232326)  // divider
    /** "Hairline-terang": border field idle, garis blockquote, outline send redup. */
    val HairlineStrong = Color(0xFF3A3A3F)

    // teks
    val Text = Color(0xFFF2F2F3)      // primary — 18.1:1 di Bg
    val Text2 = Color(0xFFA1A1A6)     // secondary — 7.65:1 di Bg, 7.15:1 di Surface1
    /**
     * Tertiary (timestamp, placeholder). Brief minta #6C6C72 → 3.77:1 (gagal
     * 4.5); fallback brief #7A7A80 → 4.61 di Bg tapi 4.31 di Surface1 (placeholder
     * composer ada di Surface1 → masih gagal). #808086 = 5.01 Bg / 4.69 Surface1.
     */
    val Text3 = Color(0xFF808086)

    // aksen = putih; OnAccent = ikon/teks di atas fill putih
    val Accent = Text
    val OnAccent = Bg

    // semantik — dot / teks status kecil saja
    val Live = Color(0xFF34C759)
    val Warn = Color(0xFFFF9F0A)
    val Danger = Color(0xFFFF453A)

    // overlay
    val Scrim = Color.Black.copy(alpha = 0.6f)
    val Transparent = Color.Transparent
}
