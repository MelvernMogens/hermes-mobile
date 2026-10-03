package id.melvern.hermesmobile.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import id.melvern.hermesmobile.ui.layout.WinSize
import id.melvern.hermesmobile.ui.layout.isExpanded
import id.melvern.hermesmobile.ui.theme.Dim
import id.melvern.hermesmobile.ui.theme.Ink
import id.melvern.hermesmobile.ui.theme.Motion
import id.melvern.hermesmobile.ui.theme.Radius
import id.melvern.hermesmobile.ui.theme.Type
import id.melvern.hermesmobile.ui.theme.hairline
import id.melvern.hermesmobile.ui.theme.pressClickable
import id.melvern.hermesmobile.ui.theme.rememberReduceMotion

/** Ikon tappable standar: ikon 22dp di target 48dp, warna default text. */
@Composable
fun QuietIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Ink.Text,
    enabled: Boolean = true,
    iconSize: Dp = Dim.Icon,
) {
    IconButton(onClick = onClick, enabled = enabled, modifier = modifier.size(Dim.Touch)) {
        Icon(icon, contentDescription, tint = if (enabled) tint else Ink.Text3, modifier = Modifier.size(iconSize))
    }
}

/** Dot status semantik 8dp (live / warn / danger). */
@Composable
fun StatusDot(color: Color, modifier: Modifier = Modifier, size: Dp = Dim.Dot) {
    Box(modifier.size(size).clip(Radius.Full).background(color))
}

/** Dot live yang pulse pelan (alpha 0.4↔1, 1.2s). Diam kalau reduce motion. */
@Composable
fun PulsingDot(color: Color = Ink.Live, modifier: Modifier = Modifier) {
    val reduce = rememberReduceMotion()
    val alpha = if (reduce) 1f else {
        val a by rememberInfiniteTransition(label = "pulse").animateFloat(
            initialValue = 1f, targetValue = 0.4f,
            animationSpec = infiniteRepeatable(tween(Motion.PulseMs / 2), RepeatMode.Reverse),
            label = "pulseAlpha",
        )
        a
    }
    StatusDot(color, modifier.graphicsLayer { this.alpha = alpha })
}

/** Alpha shimmer halus untuk skeleton (0.5↔1). */
@Composable
fun shimmerAlpha(): Float {
    if (rememberReduceMotion()) return 0.8f
    val a by rememberInfiniteTransition(label = "shimmer").animateFloat(
        initialValue = 0.5f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse),
        label = "shimmerAlpha",
    )
    return a
}

@Composable
fun SkeletonBar(width: Dp?, height: Dp, alpha: Float, modifier: Modifier = Modifier) {
    Box(
        modifier
            .then(if (width != null) Modifier.width(width) else Modifier.fillMaxWidth())
            .height(height)
            .graphicsLayer { this.alpha = alpha }
            .clip(RoundedCornerShape(height / 2))
            .background(Ink.Surface1)
    )
}

/** Skeleton row session: avatar + 2 baris — anatomi sama dengan row asli. */
@Composable
fun SkeletonSessionRow(alpha: Float) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = Dim.RowMin).padding(horizontal = Dim.ScreenH, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(Dim.AvatarRow).graphicsLayer { this.alpha = alpha }.clip(Radius.Full).background(Ink.Surface1))
        Spacer(Modifier.width(Dim.RowGap))
        Column(Modifier.weight(1f)) {
            SkeletonBar(160.dp, 14.dp, alpha)
            Spacer(Modifier.height(10.dp))
            SkeletonBar(null, 12.dp, alpha)
        }
    }
}

/**
 * Sheet standar M8: container surface1, radius atas 22, handle 32x4 text3,
 * scrim hitam 60%. Isi di-pad bawah nav bar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuietSheet(
    onDismiss: () -> Unit,
    title: String? = null,
    skipPartiallyExpanded: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    // M15: expanded → pola konsisten Dialog center widthIn(max 480) —
    // bukan bottom sheet stretch. Compact/Medium = ModalBottomSheet seperti M8.
    val winSize = id.melvern.hermesmobile.ui.layout.currentWinSize()
    if (winSize.isExpanded) {
        androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
            Column(
                Modifier
                    .widthIn(max = Dim.SheetMaxW)
                    .clip(Radius.Card)
                    .background(Ink.Surface1)
                    .navigationBarsPadding()
                    .padding(bottom = 12.dp),
            ) {
                if (title != null) {
                    Text(
                        title, style = Type.Title,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = Dim.ScreenH, vertical = 8.dp),
                    )
                }
                content()
            }
        }
        return
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = skipPartiallyExpanded),
        containerColor = Ink.Surface1,
        contentColor = Ink.Text,
        scrimColor = Ink.Scrim,
        shape = Radius.Sheet,
        tonalElevation = 0.dp,
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 8.dp, bottom = 8.dp)
                    .size(width = 32.dp, height = 4.dp)
                    .clip(Radius.Full)
                    .background(Ink.Text3)
            )
        },
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 12.dp)) {
            if (title != null) {
                Text(
                    title, style = Type.Title,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = Dim.ScreenH, vertical = 8.dp),
                )
            }
            content()
        }
    }
}

/** Baris aksi sheet: ikon 22 + label callout; danger = merah. */
@Composable
fun SheetActionRow(
    label: String,
    icon: ImageVector? = null,
    danger: Boolean = false,
    enabled: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    val color = when {
        !enabled -> Ink.Text3
        danger -> Ink.Danger
        else -> Ink.Text
    }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Dim.ActionRow)
            .pressClickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = Dim.ScreenH),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = color, modifier = Modifier.size(Dim.Icon))
            Spacer(Modifier.width(16.dp))
        }
        Text(label, style = Type.Callout.copy(color = color), modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

@Composable
fun Hairline(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier, thickness = hairline(), color = Ink.Hairline)
}

/** Teks satu baris ellipsis — helper singkat. */
@Composable
fun OneLine(text: String, style: TextStyle, modifier: Modifier = Modifier) {
    Text(text, style = style, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = modifier)
}

/** Box helper untuk konten terpusat. */
@Composable
fun Centered(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(modifier, contentAlignment = Alignment.Center, content = content)
}
