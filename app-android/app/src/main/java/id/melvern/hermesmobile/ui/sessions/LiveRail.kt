package id.melvern.hermesmobile.ui.sessions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import id.melvern.hermesmobile.ui.chat.toolIcon
import id.melvern.hermesmobile.ui.chat.toolRunningLabel
import id.melvern.hermesmobile.ui.chat.toolShort
import id.melvern.hermesmobile.ui.components.LiveTimecode
import id.melvern.hermesmobile.ui.components.LogHeader
import id.melvern.hermesmobile.ui.components.MonitorTile
import id.melvern.hermesmobile.ui.components.OneLine
import id.melvern.hermesmobile.ui.components.Tally
import id.melvern.hermesmobile.ui.components.TallyLamp
import id.melvern.hermesmobile.ui.theme.Dim
import id.melvern.hermesmobile.ui.theme.Ink
import id.melvern.hermesmobile.ui.theme.Type

/**
 * v28 Control Room — one live feed: a chat or a bot task working RIGHT NOW.
 * [startAt] = epoch seconds the current turn began (null = unknown → no timecode,
 * never a fake one). [tool] = the tool it is running right now, if known.
 */
data class LiveFeed(
    val key: String,
    val title: String,
    val text: String,
    val startAt: Double?,
    val tally: Tally,
    /** Chat route arg (`storedId|t=…|p=…`). */
    val arg: String,
    val source: String? = null,
    val tool: String? = null,
    /** Stored chat id when this feed is a chat of the active profile (long-press → chat actions). */
    val sessionId: String? = null,
)

/**
 * "Live now" — the monitor wall at the top of the rundown. Every chat or bot task
 * that is working right now gets a monitor showing what it is doing (current tool +
 * latest words) and how long this turn has been on air. Live chats live HERE while
 * they run (not duplicated in the list below); they drop back into the rundown when
 * they finish. 1 feed = one wide monitor, 2 = a pair, 3+ = a scrolling wall with a
 * deliberate quarter-tile peek.
 */
@Composable
fun LiveRail(feeds: List<LiveFeed>, onOpen: (LiveFeed) -> Unit, onLongPress: (LiveFeed) -> Unit = {}) {
    if (feeds.isEmpty()) return
    Column(Modifier.fillMaxWidth()) {
        LogHeader("Live now", trailing = feeds.size.toString())
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val pad = Dim.ScreenH
            val gap = 10.dp
            val avail = maxWidth - pad * 2
            val tileW: Dp = when (feeds.size) {
                1 -> avail
                2 -> (avail - gap) / 2
                else -> (avail - gap) / 1.75f
            }
            // plain scrolling row (not lazy): a handful of monitors, and the scroll offset
            // stays at the first monitor when feeds come and go (no key-anchored drift).
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = pad),
                horizontalArrangement = Arrangement.spacedBy(gap),
            ) {
                feeds.forEach { f ->
                    androidx.compose.runtime.key(f.key) {
                        Monitor(f, Modifier.size(tileW, if (feeds.size == 1) Dim.MonitorH - 12.dp else Dim.MonitorH),
                            onLongClick = { onLongPress(f) }) { onOpen(f) }
                    }
                }
            }
            if (feeds.size > 2) {
                // right-edge fade: the peek reads as "more monitors", not as overflow
                Box(
                    Modifier.align(Alignment.CenterEnd).width(24.dp).height(Dim.MonitorH)
                        .background(Brush.horizontalGradient(listOf(Ink.Bg.copy(alpha = 0f), Ink.Bg))),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun Monitor(f: LiveFeed, modifier: Modifier, onLongClick: () -> Unit, onClick: () -> Unit) {
    val wait = f.tally == Tally.WAIT
    MonitorTile(
        modifier = modifier,
        tally = f.tally,
        onClick = onClick,
        onLongClick = onLongClick,
        umd = {
            // under-monitor strip: lamp · what it's doing (one word) · on-air timecode
            Row(verticalAlignment = Alignment.CenterVertically) {
                TallyLamp(f.tally)
                Spacer(Modifier.width(5.dp))
                val status = when {
                    wait -> "Needs you"
                    f.tool != null -> toolShort(f.tool)
                    f.source != null -> f.source
                    else -> "Thinking"
                }
                if (f.tool != null && !wait) {
                    Icon(toolIcon(f.tool), null, tint = Ink.Text3, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(4.dp))
                }
                OneLine(status, Type.Caption.copy(color = if (wait) Ink.Warn else Ink.Text2), Modifier.weight(1f))
                Spacer(Modifier.width(6.dp))
                LiveTimecode(f.startAt, Type.Timecode.copy(color = if (wait) Ink.Warn else Ink.Text))
            }
        },
    ) {
        Column(Modifier.fillMaxHeight()) {
            OneLine(f.title, Type.MonitorTitle)
            Spacer(Modifier.height(3.dp))
            Text(
                f.text.ifBlank { if (wait) "Waiting for your answer" else "Working on it…" },
                style = Type.Meta.copy(color = if (f.text.isBlank()) Ink.Text3 else Ink.Text2),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
