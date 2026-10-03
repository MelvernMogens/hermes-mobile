package id.melvern.hermesmobile.ui.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * M15: adaptive layout — window size class sederhana (dari
 * material3-window-size-class di MainActivity, disimpan di HermesApp).
 */
enum class WinSize { Compact, Medium, Expanded }

/** True hanya Expanded WIDTH (>=840dp) — two-pane list-detail. Portrait tablet (Medium) single-pane. */
val WinSize.isExpanded: Boolean get() = this == WinSize.Expanded

/** Akses singkat WindowSizeClass dari CompositionLocal (diisi MainActivity). */
val LocalWinSize = staticCompositionLocalOf { WinSize.Compact }

/** M15: baca window size aktif — CompositionLocal dulu, fallback Compact. */
@Composable
@ReadOnlyComposable
fun currentWinSize(): WinSize = LocalWinSize.current

/**
 * Parser argumen route "chat/{sessionId}" — dipakai nav compact (MainActivity)
 * DAN two-pane expanded (SessionsScreen). Format:
 * "storedId" | "storedId|runtimeId" (chat baru) | "storedId|t=<encoded title>" (dari list).
 * Pure + testable (unit test state machine M15).
 */
object ChatRouteArg {
    data class Parsed(val storedId: String, val runtimeId: String?, val title: String?)

    fun parse(raw: String): Parsed {
        val segs = raw.split("|")
        val storedId = segs.first()
        val second = segs.getOrNull(1)
        val title = second
            ?.takeIf { it.startsWith("t=") }
            ?.substring(2)
            ?.let { decodeUri(it) }
        val runtime = second?.takeIf { title == null }
        return Parsed(storedId, runtime, title)
    }

    /** Percent-decode UTF-8 (pure Kotlin — android.net.Uri gak jalan di unit test JVM). */
    internal fun decodeUri(s: String): String {
        if ('%' !in s && '+' !in s) return s
        val bytes = ArrayList<Byte>(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            when {
                c == '%' && i + 2 < s.length -> {
                    val v = s.substring(i + 1, i + 3).toIntOrNull(16)
                    if (v != null) { bytes.add(v.toByte()); i += 3 }
                    else { bytes.add(c.code.toByte()); i++ }
                }
                c == '+' -> { bytes.add(' '.code.toByte()); i++ }
                else -> { bytes.add(c.code.toByte()); i++ }
            }
        }
        return String(bytes.toByteArray(), Charsets.UTF_8)
    }
}
