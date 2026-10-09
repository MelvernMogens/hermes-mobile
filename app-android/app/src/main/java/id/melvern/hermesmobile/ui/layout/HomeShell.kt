package id.melvern.hermesmobile.ui.layout

import androidx.compose.material.icons.rounded.Laptop
import androidx.compose.material.icons.outlined.Laptop
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.melvern.hermesmobile.HermesApp
import id.melvern.hermesmobile.core.repo.BotFleet
import id.melvern.hermesmobile.core.repo.BotStatus
import id.melvern.hermesmobile.ui.components.Hairline
import id.melvern.hermesmobile.ui.theme.Dim
import id.melvern.hermesmobile.ui.theme.Ink
import id.melvern.hermesmobile.ui.theme.Motion
import id.melvern.hermesmobile.ui.theme.Radius
import id.melvern.hermesmobile.ui.theme.Type
import id.melvern.hermesmobile.ui.theme.hairline

/**
 * M18: shell 2 tab (Chats | Overview). Compact/Medium = bottom bar 56dp;
 * Expanded (>=1000dp) = rail kiri 72dp ikon. Tab state di-hoist pemanggil
 * (rememberSaveable) supaya survive rotate; tab juga di-reset ke Chats oleh
 * deep-link notif.
 */
object HomeTabs {
    const val CHATS = 0
    const val OVERVIEW = 1
    const val LIMITS = 2
    const val SETTINGS = 3
}

@Composable
fun HomeShell(
    tab: Int,
    onTab: (Int) -> Unit,
    chats: @Composable () -> Unit,
    overview: @Composable () -> Unit,
    limits: @Composable () -> Unit = {},
    settings: @Composable () -> Unit = {},
) {
    val winSize = currentWinSize()
    // Badge tab Overview: jumlah bot Running (dari snapshot poller M14).
    val fleet by BotFleet.bots.collectAsState()
    val running = fleet?.count { it.status == BotStatus.RUNNING } ?: 0

    if (winSize.isExpanded) {
        // Rail kiri 72dp ikon — konten two-pane Chats tetap utuh di kanan.
        Row(Modifier.fillMaxSize()) {
            Rail(
                tab = tab, onTab = onTab, running = running,
                modifier = Modifier.width(80.dp).fillMaxHeight(),
            )
            Box(Modifier.weight(1f).fillMaxHeight()) {
                when (tab) { HomeTabs.CHATS -> chats(); HomeTabs.LIMITS -> limits(); HomeTabs.SETTINGS -> settings(); else -> overview() }
            }
        }
    } else {
        // Bottom bar 56dp. consumeWindowInsets: konten Chats yang menambahkan
        // padding navigationBars sendiri (LazyColumn list) gak ke-double inset.
        Column(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .consumeWindowInsets(WindowInsets.navigationBars),
            ) {
                when (tab) { HomeTabs.CHATS -> chats(); HomeTabs.LIMITS -> limits(); HomeTabs.SETTINGS -> settings(); else -> overview() }
                // konten melebur ke nav (gak ada seam / baris kepotong keras)
                Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(16.dp)
                    .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Ink.Bg.copy(alpha = 0f), Ink.Bg))))
            }
            BottomBar(tab = tab, onTab = onTab, running = running)
        }
    }
}

@Composable
private fun BottomBar(tab: Int, onTab: (Int) -> Unit, running: Int) {
    // Surface terangkat (bukan hitam + garis) — konten scroll di belakangnya
    // kebaca sebagai lapisan, bukan dipotong.
    Column(Modifier.fillMaxWidth().background(Ink.Bg).navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(Dim.NavBar).padding(horizontal = 8.dp)) {
            TabItem(Icons.Rounded.ChatBubble, Icons.Outlined.ChatBubbleOutline, "Chats", selected = tab == HomeTabs.CHATS,
                badge = null, modifier = Modifier.weight(1f).fillMaxHeight()) { onTab(HomeTabs.CHATS) }
            // badge cuma muncul kalau kita TIDAK di tab Overview (di sana angkanya sudah kelihatan)
            TabItem(Icons.Rounded.Hub, Icons.Outlined.Hub, "Agents", selected = tab == HomeTabs.OVERVIEW,
                badge = running.takeIf { tab != HomeTabs.OVERVIEW }, modifier = Modifier.weight(1f).fillMaxHeight()) { onTab(HomeTabs.OVERVIEW) }
            TabItem(Icons.Rounded.Laptop, Icons.Outlined.Laptop, "Mac", selected = tab == HomeTabs.LIMITS,
                badge = null, modifier = Modifier.weight(1f).fillMaxHeight()) { onTab(HomeTabs.LIMITS) }
            TabItem(Icons.Rounded.Settings, Icons.Outlined.Settings, "Settings", selected = tab == HomeTabs.SETTINGS,
                badge = null, modifier = Modifier.weight(1f).fillMaxHeight()) { onTab(HomeTabs.SETTINGS) }
        }
    }
}

@Composable
private fun Rail(tab: Int, onTab: (Int) -> Unit, running: Int, modifier: Modifier = Modifier) {
    Row(modifier.background(Ink.Surface1)) {
        Column(Modifier.fillMaxHeight().width(80.dp).statusBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(16.dp))
            RailItem(Icons.Rounded.ChatBubble, Icons.Outlined.ChatBubbleOutline, "Chats", selected = tab == HomeTabs.CHATS, badge = null) {
                onTab(HomeTabs.CHATS)
            }
            Spacer(Modifier.height(4.dp))
            RailItem(Icons.Rounded.Hub, Icons.Outlined.Hub, "Agents", selected = tab == HomeTabs.OVERVIEW,
                badge = running.takeIf { tab != HomeTabs.OVERVIEW }) {
                onTab(HomeTabs.OVERVIEW)
            }
            Spacer(Modifier.height(4.dp))
            RailItem(Icons.Rounded.Laptop, Icons.Outlined.Laptop, "Mac", selected = tab == HomeTabs.LIMITS, badge = null) {
                onTab(HomeTabs.LIMITS)
            }
            Spacer(Modifier.height(4.dp))
            RailItem(Icons.Rounded.Settings, Icons.Outlined.Settings, "Settings", selected = tab == HomeTabs.SETTINGS, badge = null) {
                onTab(HomeTabs.SETTINGS)
            }
        }
        Hairline(Modifier.width(hairline()).fillMaxHeight())
    }
}

@Composable
private fun TabItem(
    iconOn: ImageVector,
    iconOff: ImageVector,
    label: String,
    selected: Boolean,
    badge: Int?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val tint by animateColorAsState(if (selected) Ink.Text else Ink.Text3, tween(Motion.NavMs), label = "tabTint")
    val pill by animateColorAsState(if (selected) Ink.Surface3 else Ink.Transparent, tween(Motion.NavMs), label = "tabPill")
    Column(
        modifier.clickable(
            interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick,
        ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.size(width = 56.dp, height = 30.dp).clip(Radius.Full).background(pill),
            contentAlignment = Alignment.Center,
        ) {
            Icon(if (selected) iconOn else iconOff, label, tint = tint, modifier = Modifier.size(20.dp))
            if (badge != null && badge > 0) CountBadge(badge, Modifier.align(Alignment.TopEnd).offset(x = (-8).dp, y = 1.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(label, style = Type.Caption.copy(color = tint))
    }
}

@Composable
private fun RailItem(
    iconOn: ImageVector,
    iconOff: ImageVector,
    label: String,
    selected: Boolean,
    badge: Int?,
    onClick: () -> Unit,
) {
    val tint by animateColorAsState(if (selected) Ink.Text else Ink.Text3, tween(Motion.NavMs), label = "railTint")
    val pill by animateColorAsState(if (selected) Ink.Surface3 else Ink.Transparent, tween(Motion.NavMs), label = "railPill")
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(width = 56.dp, height = 32.dp).clip(Radius.Full).background(pill),
            contentAlignment = Alignment.Center,
        ) {
            Icon(if (selected) iconOn else iconOff, label, tint = tint, modifier = Modifier.size(20.dp))
            if (badge != null && badge > 0) CountBadge(badge, Modifier.align(Alignment.TopEnd).offset(x = (-8).dp, y = 1.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(label, style = Type.Caption.copy(color = tint))
    }
}

/** Badge jumlah agent running — hijau (status), bukan merah (alarm). */
@Composable
private fun CountBadge(count: Int, modifier: Modifier = Modifier) {
    Box(
        modifier
            .heightIn(min = 15.dp)
            .widthIn(min = 15.dp)
            .clip(Radius.Full)
            .background(Ink.Live)
            .border(1.5.dp, Ink.Bg, Radius.Full)
            .padding(horizontal = 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (count > 9) "9+" else count.toString(),
            style = Type.Caption.copy(color = Ink.OnAccent, fontSize = 10.sp, lineHeight = 12.sp),
            maxLines = 1,
        )
    }
}
