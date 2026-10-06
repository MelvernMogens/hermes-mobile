package id.melvern.hermesmobile.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager

/**
 * v26.1: SelectionContainer yang bisa dibatalkan seperti teks biasa di Android —
 * tap sekali (di dalam area ini) atau tombol Back melepas seleksi.
 * Seleksi Compose hidup selama container memegang fokus; lepas fokus = seleksi hilang.
 */
@Composable
fun DismissibleSelectionContainer(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val focusManager = LocalFocusManager.current
    var active by remember { mutableStateOf(false) }
    BackHandler(enabled = active) { focusManager.clearFocus(force = true) }
    SelectionContainer(
        modifier
            .onFocusChanged { active = it.hasFocus }
            .pointerInput(Unit) { dismissOnTap({ active }) { focusManager.clearFocus(force = true) } },
    ) { content() }
}

/** Tap pendek (bukan drag / long-press) saat seleksi aktif → [onDismiss]. Event tidak dikonsumsi. */
suspend fun androidx.compose.ui.input.pointer.PointerInputScope.dismissOnTap(active: () -> Boolean, onDismiss: () -> Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        if (!active()) return@awaitEachGesture
        val up = waitForUpOrCancellation(pass = PointerEventPass.Initial) ?: return@awaitEachGesture
        if ((up.position - down.position).getDistance() < viewConfiguration.touchSlop &&
            up.uptimeMillis - down.uptimeMillis < viewConfiguration.longPressTimeoutMillis) onDismiss()
    }
}
