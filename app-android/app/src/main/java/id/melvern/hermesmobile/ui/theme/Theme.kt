package id.melvern.hermesmobile.ui.theme

import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RippleConfiguration
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

/**
 * M8 Quiet Mono. Typography M3 dipetakan ke [Type] supaya komponen M3
 * (TextField label, Switch, DropdownMenu) ikut Inter tanpa tracking lebar.
 */
private val HermesTypography = Typography(
    displaySmall = Type.Display,
    headlineMedium = Type.Display,
    headlineSmall = Type.Display,
    titleLarge = Type.Title,
    titleMedium = Type.Title,
    titleSmall = Type.MetaMedium,
    bodyLarge = Type.Body,
    bodyMedium = Type.Callout,
    bodySmall = Type.Meta,
    labelLarge = Type.Callout,
    labelMedium = Type.MetaMedium,
    labelSmall = Type.Caption,
)

private val Scheme = darkColorScheme(
    primary = Ink.Accent,
    onPrimary = Ink.OnAccent,
    primaryContainer = Ink.Surface2,
    onPrimaryContainer = Ink.Text,
    secondary = Ink.Text2,
    onSecondary = Ink.Bg,
    tertiary = Ink.Text2,
    background = Ink.Bg,
    onBackground = Ink.Text,
    surface = Ink.Bg,
    onSurface = Ink.Text,
    surfaceVariant = Ink.Surface1,
    onSurfaceVariant = Ink.Text2,
    surfaceContainerLowest = Ink.Bg,
    surfaceContainerLow = Ink.Surface1,
    surfaceContainer = Ink.Surface1,
    surfaceContainerHigh = Ink.Surface2,
    surfaceContainerHighest = Ink.Surface3,
    surfaceTint = Ink.Transparent,
    inverseSurface = Ink.Text,
    inverseOnSurface = Ink.Bg,
    outline = Ink.HairlineStrong,
    outlineVariant = Ink.Hairline,
    scrim = Ink.Scrim,
    error = Ink.Danger,
    onError = Ink.Bg,
)

/** Ripple lembut putih 8% (brief E) — bukan hijau. */
@OptIn(ExperimentalMaterial3Api::class)
private val QuietRipple = RippleConfiguration(
    color = Ink.Text,
    rippleAlpha = RippleAlpha(draggedAlpha = 0.08f, focusedAlpha = 0.08f, hoveredAlpha = 0.08f, pressedAlpha = 0.08f),
)

private val Selection = TextSelectionColors(handleColor = Ink.Text, backgroundColor = Ink.Text.copy(alpha = 0.25f))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HermesTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, typography = HermesTypography) {
        CompositionLocalProvider(
            LocalRippleConfiguration provides QuietRipple,
            LocalTextSelectionColors provides Selection,
            content = content,
        )
    }
}
