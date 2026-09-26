package id.melvern.hermesmobile.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkScheme = darkColorScheme(
    primary = Accent,
    onPrimary = Color(0xFF0A0A0D),
    secondary = TextSecondary,
    onSecondary = Bg,
    tertiary = Cream,
    background = Bg,
    onBackground = TextPrimary,
    surface = Bg,
    onSurface = TextPrimary,
    surfaceVariant = BgCard,
    onSurfaceVariant = TextSecondary,
    outline = BorderSubtle,
    outlineVariant = BorderSubtle,
    error = Danger,
    onError = Color.White,
)

@Composable
fun HermesTheme(content: @Composable () -> Unit) {
    // Dark-first by design; light sengaja belum (PRD non-goal M1).
    MaterialTheme(colorScheme = DarkScheme, content = content)
}
