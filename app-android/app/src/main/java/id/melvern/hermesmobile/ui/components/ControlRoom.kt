package id.melvern.hermesmobile.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import id.melvern.hermesmobile.ui.theme.Dim
import id.melvern.hermesmobile.ui.theme.Ink
import id.melvern.hermesmobile.ui.theme.Motion
import id.melvern.hermesmobile.ui.theme.Radius
import id.melvern.hermesmobile.ui.theme.Type
import id.melvern.hermesmobile.ui.theme.hairline
import id.melvern.hermesmobile.ui.theme.pressClickable
import id.melvern.hermesmobile.ui.theme.rememberReduceMotion
import kotlinx.coroutines.delay

/*
 * v28 "Control Room" atoms — the phone is the control room for a studio that never
 * stops. One status atom app-wide (the tally lamp), time as timecode, levels as
 * segmented meters, controls as small key caps, feeds as monitor tiles.
 * Brightness = activity: idle things sit dim, live things are bright.
 */

/** Tally state — the ONLY place status colour lives. */
enum class Tally { OFF, LIVE, WAIT, FAULT, CUE }

fun Tally.color(): Color = when (this) {
    Tally.LIVE -> Ink.Live
    Tally.WAIT -> Ink.Warn
    Tally.FAULT -> Ink.Danger
    Tally.CUE -> Ink.Text
    Tally.OFF -> Ink.LampOff
}

/**
 * Tally lamp: a small horizontal pill. Unlit = a dark slot cut into the surface;
 * lit = the status colour with a soft bloom behind it. LIVE breathes slowly
 * (reduce-motion → steady).
 */
@Composable
fun TallyLamp(
    tally: Tally,
    modifier: Modifier = Modifier,
    width: Dp = Dim.LampW,
    height: Dp = Dim.LampH,
) {
    val reduce = rememberReduceMotion()
    // Breathing is read ONLY inside draw/layer lambdas (deferred read): the lamp
    // re-draws its layer each frame but never recomposes — a wall of live lamps
    // stays cheap.
    val breathe: androidx.compose.runtime.State<Float>? = if (tally == Tally.LIVE && !reduce) {
        rememberInfiniteTransition(label = "tally").animateFloat(
            initialValue = 1f, targetValue = 0.55f,
            animationSpec = infiniteRepeatable(tween(Motion.PulseMs), RepeatMode.Reverse),
            label = "tallyAlpha",
        )
    } else null
    val c = tally.color()
    val lit = tally != Tally.OFF
    Box(
        modifier
            .size(width + 6.dp, height + 6.dp)
            .semantics { contentDescription = tally.spoken() },
        contentAlignment = Alignment.Center,
    ) {
        if (lit) {
            // bloom — soft glow around the LED, fading to nothing well inside the canvas
            // (drawn outside the layout box; a cut-off gradient reads as a muddy rectangle)
            Canvas(Modifier.size(width + 6.dp, height + 6.dp).graphicsLayer { alpha = breathe?.value ?: 1f }) {
                val r = width.toPx() * 0.95f
                drawCircle(
                    brush = Brush.radialGradient(
                        0f to c.copy(alpha = 0.38f), 0.45f to c.copy(alpha = 0.14f), 1f to c.copy(alpha = 0f),
                        center = center, radius = r,
                    ),
                    radius = r, center = center,
                )
            }
        }
        Box(
            Modifier
                .size(width, height)
                .graphicsLayer { alpha = if (lit) (0.6f + 0.4f * (breathe?.value ?: 1f)) else 1f }
                .clip(Radius.Led)
                .background(if (lit) c else Ink.LampOff)
                .then(if (!lit) Modifier.border(1.dp, Ink.KeyBezel, Radius.Led) else Modifier),
        )
    }
}

private fun Tally.spoken(): String = when (this) {
    Tally.LIVE -> "Running"
    Tally.WAIT -> "Waiting"
    Tally.FAULT -> "Error"
    Tally.CUE -> "Selected"
    Tally.OFF -> "Idle"
}

/** Lamp + short status word in caps-free caption; the word flips when it changes. */
@Composable
fun TallyLabel(tally: Tally, label: String, modifier: Modifier = Modifier, style: TextStyle = Type.Caption) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        TallyLamp(tally)
        Spacer(Modifier.width(5.dp))
        FlipText(label, style.copy(color = if (tally == Tally.OFF) Ink.Text3 else tally.color()))
    }
}

/**
 * Split-flap style change: the old text drops out, the new one drops in (180ms).
 * Used for status words and timecodes so a change is noticed, not just redrawn.
 */
@Composable
fun FlipText(text: String, style: TextStyle, modifier: Modifier = Modifier, maxLines: Int = 1) {
    val reduce = rememberReduceMotion()
    if (reduce) {
        Text(text, style = style, maxLines = maxLines, overflow = TextOverflow.Ellipsis, modifier = modifier)
        return
    }
    AnimatedContent(
        targetState = text,
        transitionSpec = {
            (slideInVertically(tween(Motion.FlipMs)) { -it / 2 } + fadeIn(tween(Motion.FlipMs)))
                .togetherWith(slideOutVertically(tween(Motion.FlipMs)) { it / 2 } + fadeOut(tween(Motion.FlipMs / 2)))
        },
        label = "flip",
        modifier = modifier,
    ) { t -> Text(t, style = style, maxLines = maxLines, overflow = TextOverflow.Ellipsis) }
}

// ── Timecode ────────────────────────────────────────────────────────────

object Timecode {
    /** 754 → "0:12:34", 4000 → "1:06:40" — always H:MM:SS so it reads as elapsed, never as a clock. */
    fun of(secs: Long): String {
        val s = secs.coerceAtLeast(0)
        val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
        return "%d:%02d:%02d".format(h, m, sec)
    }

    /** Short duration for logs: "45s", "12m", "1h 05m", "3d". */
    fun short(secs: Long): String {
        val s = secs.coerceAtLeast(0)
        return when {
            s < 60 -> "${s}s"
            s < 3600 -> "${s / 60}m"
            s < 86_400 -> "%dh %02dm".format(s / 3600, (s % 3600) / 60)
            else -> "${s / 86_400}d"
        }
    }
}

/**
 * Live timecode since [startEpochSec] — ticks every second while composed.
 * Null start → shows nothing (never a fake 00:00).
 */
@Composable
fun LiveTimecode(startEpochSec: Double?, style: TextStyle = Type.Timecode, modifier: Modifier = Modifier) {
    if (startEpochSec == null || startEpochSec <= 0) return
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(startEpochSec) {
        while (true) { now = System.currentTimeMillis(); delay(1000L - (now % 1000L)) }
    }
    val secs = ((now / 1000.0) - startEpochSec).toLong()
    Text(Timecode.of(secs), style = style, maxLines = 1, modifier = modifier)
}

// ── Meter ───────────────────────────────────────────────────────────────

/**
 * Segmented level meter (studio bargraph): [segments] cells, lit ones white,
 * cells beyond [warnAt] amber and beyond [faultAt] red — but only when lit.
 * Unlit cells are dark slots. Fill animates once on change.
 */
@Composable
fun SegmentMeter(
    fraction: Float,
    modifier: Modifier = Modifier,
    segments: Int = 24,
    warnAt: Float = 0.8f,
    faultAt: Float = 0.95f,
    height: Dp = Dim.MeterH,
) {
    val f by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(Motion.MeterMs), label = "meter")
    val off = Ink.LampOff
    val on = Ink.Text
    val warn = Ink.Warn
    val fault = Ink.Danger
    Canvas(modifier.fillMaxWidth().height(height)) {
        val gap = 2.dp.toPx()
        val w = (size.width - gap * (segments - 1)) / segments
        val r = CornerRadius(1.5.dp.toPx(), 1.5.dp.toPx())
        for (i in 0 until segments) {
            val pos = (i + 1f) / segments
            val lit = pos <= f + 0.0001f || (i == 0 && f > 0f)
            val c = when {
                !lit -> off
                pos > faultAt -> fault
                pos > warnAt -> warn
                else -> on
            }
            drawRoundRect(c, topLeft = Offset(i * (w + gap), 0f), size = Size(w, size.height), cornerRadius = r)
        }
    }
}

// ── Key cap ─────────────────────────────────────────────────────────────

/**
 * Small hardware key: 1px bezel, dark face, label + optional icon. [lit] = the
 * screen's ONE primary action (white face, black legend). [armed] = waiting for
 * a confirm tap (amber bezel). Never a big button.
 */
@Composable
fun KeyCap(
    label: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    lit: Boolean = false,
    armed: Boolean = false,
    enabled: Boolean = true,
    /** Selector key that is ON (filter/segment): raised face + bright bezel, NOT the white lit fill. */
    selected: Boolean = false,
    onClick: () -> Unit,
) {
    val face = when { lit -> Ink.Text; selected -> Ink.Raised; else -> Ink.KeyFace }
    val legend = when {
        !enabled -> Ink.Text4
        lit -> Ink.OnAccent
        armed -> Ink.Warn
        selected -> Ink.Text
        else -> Ink.Text2
    }
    Row(
        modifier
            .heightIn(min = Dim.KeyH)
            .clip(Radius.Key)
            .background(face)
            .border(1.dp, when { armed -> Ink.Warn; lit -> Ink.Text; selected -> Ink.Text2; else -> Ink.KeyBezel }, Radius.Key)
            .pressClickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = legend, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(label, style = Type.Key.copy(color = legend), maxLines = 1)
    }
}

// ── Monitor tile ────────────────────────────────────────────────────────

/**
 * A monitor in the wall: near-black glass with a hairline bezel, content on the
 * "screen", and an under-monitor label strip (UMD) with tally + name + timecode.
 * Idle monitors are dimmed (dark-cockpit rule).
 */
@Composable
fun MonitorTile(
    modifier: Modifier = Modifier,
    tally: Tally,
    umd: @Composable () -> Unit,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    screen: @Composable BoxScope.() -> Unit,
) {
    val dim = tally == Tally.OFF
    val bezel = when (tally) {
        Tally.WAIT -> Ink.Warn.copy(alpha = 0.6f)
        Tally.FAULT -> Ink.Danger.copy(alpha = 0.6f)
        else -> Ink.Bezel
    }
    // frame (bezel body) → recessed glass screen → under-monitor label on the frame
    Column(
        modifier
            .clip(Radius.Monitor)
            .background(Ink.Frame)
            .border(1.dp, bezel, Radius.Monitor)
            .then(if (onClick != null) Modifier.combinedClick(onClick, onLongClick) else Modifier)
            .padding(start = 4.dp, end = 4.dp, top = 4.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f, fill = true)
                .clip(Radius.Screen)
                .background(Brush.verticalGradient(listOf(Ink.GlassSheen, Ink.Glass)))
                .scanlines()
                .recess()
                .graphicsLayer { alpha = if (dim) 0.72f else 1f }
                .padding(horizontal = 10.dp, vertical = 8.dp),
            content = screen,
        )
        Box(
            Modifier.fillMaxWidth().padding(start = 6.dp, end = 6.dp, top = 6.dp, bottom = 7.dp),
            contentAlignment = Alignment.CenterStart,
        ) { umd() }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
private fun Modifier.combinedClick(onClick: () -> Unit, onLong: (() -> Unit)?): Modifier =
    this.combinedClickable(onClick = onClick, onLongClick = onLong)

/** Inset bevel: a dark line along the top edge + a faint light lip at the bottom = the glass sits IN the frame. */
fun Modifier.recess(): Modifier = this.drawBehind {
    val px = 1.dp.toPx()
    drawRect(Color.Black.copy(alpha = 0.85f), topLeft = Offset(0f, 0f), size = Size(size.width, px * 1.5f))
    drawRect(Color.Black.copy(alpha = 0.5f), topLeft = Offset(0f, 0f), size = Size(px, size.height))
    drawRect(Color.White.copy(alpha = 0.05f), topLeft = Offset(0f, size.height - px), size = Size(size.width, px))
}

/** Very faint horizontal scanlines on monitor glass — texture, not decoration noise. */
fun Modifier.scanlines(): Modifier = this.drawBehind {
    val step = 3.dp.toPx()
    var y = 0f
    val c = Color.White.copy(alpha = 0.022f)
    while (y < size.height) {
        drawRect(c, topLeft = Offset(0f, y), size = Size(size.width, 1f))
        y += step
    }
}

// ── Signal line ─────────────────────────────────────────────────────────

/**
 * Full-width connection status line under the top bar. Shown ONLY when the
 * link to the Mac is not healthy: lamp + plain words + optional trailing action.
 */
@Composable
fun SignalLine(tally: Tally, text: String, modifier: Modifier = Modifier, action: String? = null, onClick: (() -> Unit)? = null) {
    Row(
        modifier
            .fillMaxWidth()
            .background(Ink.Surface1)
            .then(if (onClick != null) Modifier.pressClickable(onClick = onClick) else Modifier)
            .padding(horizontal = Dim.ScreenH, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TallyLamp(tally)
        Spacer(Modifier.width(10.dp))
        Text(text, style = Type.Meta.copy(color = Ink.Text), modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (action != null) {
            Spacer(Modifier.width(8.dp))
            Text(action, style = Type.Key.copy(color = Ink.Text2))
        }
    }
}

// ── Rack panel ──────────────────────────────────────────────────────────

/**
 * Equipment rack unit: a full-width panel with a top label row (name left,
 * status right) and content. Panels stack edge to edge with 1px seams —
 * replaces floating rounded cards.
 */
@Composable
fun RackUnit(
    title: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(Radius.Rack)
            .background(Ink.Surface1)
            .border(1.dp, Ink.Bezel, Radius.Rack)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = Type.RackLabel, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            trailing?.invoke()
        }
        Spacer(Modifier.height(10.dp))
        content()
    }
}

/** Two tiny screw heads — the rack-ear mark in front of every unit title. */
@Composable
private fun RackScrews() {
    Canvas(Modifier.size(width = 14.dp, height = 6.dp)) {
        val r = size.height / 2
        val c = Ink.KeyBezel
        drawCircle(c, r, Offset(r, r))
        drawCircle(c, r, Offset(size.width - r, r))
        drawLine(Ink.Bg, Offset(r - r * 0.6f, r), Offset(r + r * 0.6f, r), strokeWidth = 1f)
        drawLine(Ink.Bg, Offset(size.width - r, r - r * 0.6f), Offset(size.width - r, r + r * 0.6f), strokeWidth = 1f)
    }
}

/** Section label for logs/rundowns: label + mono count right beside it, optional key action at right. */
@Composable
fun LogHeader(text: String, modifier: Modifier = Modifier, trailing: String? = null, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().padding(start = Dim.ScreenH, end = if (action != null) 8.dp else Dim.ScreenH, top = 20.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = Type.Section.copy(color = Ink.Text2))
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            Text(trailing, style = Type.MonoMeta.copy(color = Ink.Text2))
        }
        Spacer(Modifier.weight(1f))
        if (action != null && onAction != null) {
            Text(
                action, style = Type.Key.copy(color = Ink.Text),
                modifier = Modifier.clip(Radius.Key).pressClickable(onClick = onAction).padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}
