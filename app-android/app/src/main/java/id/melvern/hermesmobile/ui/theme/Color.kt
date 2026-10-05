package id.melvern.hermesmobile.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * "Graphite" — SATU sumber warna app. Screen dilarang pakai hex sendiri.
 *
 * Kenapa kelihatan mahal: hitam murni (bukan abu tua) + permukaan yang
 * dibangun dari tonal step kecil (bukan garis), teks putih hangat tipis,
 * dan warna HANYA untuk status (dot kecil / teks status) — tidak pernah fill
 * besar. Aksen tunggal = putih (tombol kirim, pilihan aktif).
 */
object Ink {
    // canvas & surface — tonal ladder (tiap step ±4% luminance)
    // Tema (Settings): Black = OLED murni; Graphite = abu sangat gelap, surface naik satu step.
    // Getter membaca snapshot state → semua layar ikut recompose saat tema diganti.
    private val graphite get() = id.melvern.hermesmobile.core.store.AppPrefs.theme == id.melvern.hermesmobile.core.store.AppPrefs.Theme.GRAPHITE
    val Bg: Color get() = if (graphite) Color(0xFF111113) else Color(0xFF000000)         // canvas
    val Surface1: Color get() = if (graphite) Color(0xFF1A1A1D) else Color(0xFF0E0E10)   // grouped surface, composer field, sheet
    val Surface2: Color get() = if (graphite) Color(0xFF232327) else Color(0xFF17171A)   // user bubble, inline code, chip, pressed
    val Surface3: Color get() = if (graphite) Color(0xFF2C2C31) else Color(0xFF212125)   // code header, selected segment track
    val Raised: Color get() = if (graphite) Color(0xFF34343A) else Color(0xFF2A2A2F)     // elevated control (scroll fab, toast)
    /** Garis halus — dipakai SEDIKIT (pemisah grouped list di dalam surface). */
    val Hairline: Color get() = if (graphite) Color(0xFF26262B) else Color(0xFF1C1C20)
    /** Border field idle, garis blockquote, outline tombol non-aktif. */
    val HairlineStrong = Color(0xFF34343A)

    // teks
    val Text = Color(0xFFF5F5F4)       // primary — 19.6:1 di Bg
    val Text2 = Color(0xFF9E9EA4)      // secondary — 7.9:1 di Bg, 7.4:1 di Surface1
    /** Tertiary (timestamp, placeholder) — 5.1:1 di Bg, 4.8:1 di Surface1. */
    val Text3 = Color(0xFF7C7C83)
    /** Paling redup: label section, ikon disabled. 3.4:1 — hanya teks ≥ 13sp medium / ikon. */
    val Text4 = Color(0xFF5E5E65)

    // aksen = putih; OnAccent = ikon/teks di atas fill putih
    val Accent = Text
    val OnAccent = Bg

    // semantik — dot / teks status kecil saja (sedikit desaturasi biar gak "vibrate" di hitam)
    val Live = Color(0xFF3DD68C)
    val LiveDim = Color(0x263DD68C)    // 15% — halo dot running
    val Warn = Color(0xFFF5A524)
    val Danger = Color(0xFFF2555A)

    // overlay
    val Scrim = Color.Black.copy(alpha = 0.72f)
    val Transparent = Color.Transparent

    /**
     * Tint monogram per session (avatar inisial): hue diambil dari hash judul,
     * saturasi rendah supaya tetap di dunia graphite — identitas, bukan dekorasi.
     */
    private val MonoTints = listOf(
        Color(0xFF2B3A55), Color(0xFF3B2F55), Color(0xFF4A2E3F), Color(0xFF4A3A2A),
        Color(0xFF2E4A3E), Color(0xFF2A4250), Color(0xFF45472B), Color(0xFF3A3A40),
        Color(0xFF4A2A2A), Color(0xFF1F4446), Color(0xFF2F4228), Color(0xFF452F4A),
    )
    private val MonoInk = listOf(
        Color(0xFFB9CBEA), Color(0xFFCDBDF0), Color(0xFFF0BCD3), Color(0xFFF0D2B0),
        Color(0xFFB5E6CD), Color(0xFFADD6EA), Color(0xFFE2E3AE), Color(0xFFD2D2D8),
        Color(0xFFF2B0AC), Color(0xFFA6E6E2), Color(0xFFBDE6A6), Color(0xFFE6B8EE),
    )
    /** Monogram = tonal abu (variasi terang saja) — warna hanya untuk status. */
    private val MonoGray = listOf(Color(0xFF1C1C20), Color(0xFF232328), Color(0xFF2A2A30), Color(0xFF202024))
    fun monoTint(key: String): Pair<Color, Color> {
        var g = 0x811C9DC5.toInt()
        for (ch in key) { g = g xor ch.code; g *= 0x01000193 }
        return MonoGray[(g ushr 1) % MonoGray.size] to Text2
    }

    @Suppress("unused")
    private fun monoTintColored(key: String): Pair<Color, Color> {
        var h = 0x811C9DC5.toInt()
        for (ch in key) { h = h xor ch.code; h *= 0x01000193 }
        val i = (h ushr 1) % MonoTints.size
        return MonoTints[i] to MonoInk[i]
    }
}
