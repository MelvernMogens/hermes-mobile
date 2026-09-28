package id.melvern.hermesmobile.ui.theme

import androidx.compose.foundation.clickable
import androidx.compose.ui.Modifier

/**
 * M8: tappable standar = clickable + ripple theme (putih 8%, lihat HermesTheme).
 * Dulu scale-press tanpa ripple (dunia Fillmore) — sekarang ripple lembut.
 */
fun Modifier.pressClickable(enabled: Boolean = true, onClick: () -> Unit): Modifier =
    this.clickable(enabled = enabled, onClick = onClick)
