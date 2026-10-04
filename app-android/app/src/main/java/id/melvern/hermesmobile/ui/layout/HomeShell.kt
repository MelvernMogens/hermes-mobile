package id.melvern.hermesmobile.ui.layout

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.SpaceDashboard
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import id.melvern.hermesmobile.HermesApp
import id.melvern.hermesmobile.core.repo.BotFleet
import id.melvern.hermesmobile.core.repo.BotStatus
import id.melvern.hermesmobile.ui.components.Hairline
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
}

@Composable
fun HomeShell(
    tab: Int,
    onTab: (Int) -> Unit,
    chats: @Composable () -> Unit,
    overview: @Composable () -> Unit,
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
                modifier = Modifier.width(72.dp).fillMaxHeight(),
            )
            Box(Modifier.weight(1f).fillMaxHeight()) {
                if (tab == HomeTabs.CHATS) chats() else overview()
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
                if (tab == HomeTabs.CHATS) chats() else overview()
            }
            BottomBar(tab = tab, onTab = onTab, running = running)
        }
    }
}

@Composable
private fun BottomBar(tab: Int, onTab: (Int) -> Unit, running: Int) {
    Column(Modifier.fillMaxWidth().background(Ink.Bg).navigationBarsPadding()) {
        Hairline()
        Row(Modifier.fillMaxWidth().height(56.dp)) {
            TabItem(Icons.Rounded.ChatBubbleOutline, "Chats", selected = tab == HomeTabs.CHATS, badge = null,
                modifier = Modifier.weight(1f).fillMaxHeight()) { onTab(HomeTabs.CHATS) }
            TabItem(Icons.Rounded.SpaceDashboard, "Overview", selected = tab == HomeTabs.OVERVIEW, badge = running,
                modifier = Modifier.weight(1f).fillMaxHeight()) { onTab(HomeTabs.OVERVIEW) }
        }
    }
}

@Composable
private fun Rail(tab: Int, onTab: (Int) -> Unit, running: Int, modifier: Modifier = Modifier) {
    Row(modifier.background(Ink.Bg)) {
        Column(Modifier.fillMaxHeight().width(72.dp).statusBarsPadding()) {
            Spacer(Modifier.height(12.dp))
            RailItem(Icons.Rounded.ChatBubbleOutline, "Chats", selected = tab == HomeTabs.CHATS, badge = null) {
                onTab(HomeTabs.CHATS)
            }
            Spacer(Modifier.height(8.dp))
            RailItem(Icons.Rounded.SpaceDashboard, "Overview", selected = tab == HomeTabs.OVERVIEW, badge = running) {
                onTab(HomeTabs.OVERVIEW)
            }
        }
        Hairline(Modifier.width(hairline()).fillMaxHeight())
    }
}

@Composable
private fun TabItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    badge: Int?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val tint by animateColorAsState(if (selected) Ink.Text else Ink.Text3, tween(Motion.NavMs), label = "tabTint")
    Column(
        modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) {
        Box {
            Icon(icon, label, tint = tint, modifier = Modifier.size(24.dp))
            if (badge != null && badge > 0) RunningBadge(badge, Modifier.align(Alignment.TopEnd).offset(x = 6.dp, y = (-4).dp))
        }
        Spacer(Modifier.height(3.dp))
        Text(label, style = Type.Caption.copy(color = tint))
    }
}

@Composable
private fun RailItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    badge: Int?,
    onClick: () -> Unit,
) {
    val tint by animateColorAsState(if (selected) Ink.Text else Ink.Text3, tween(Motion.NavMs), label = "railTint")
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            Icon(icon, label, tint = tint, modifier = Modifier.size(24.dp))
            if (badge != null && badge > 0) RunningBadge(badge, Modifier.align(Alignment.TopEnd).offset(x = 6.dp, y = (-4).dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(label, style = Type.Caption.copy(color = tint))
    }
}

/** Badge merah angka jumlah bot running (clamp 9+ — kotak 16dp muat 1 digit). */
@Composable
private fun RunningBadge(count: Int, modifier: Modifier = Modifier) {
    Box(
        modifier.size(16.dp).clip(Radius.Full).background(Ink.Danger),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (count > 9) "9+" else count.toString(),
            // teks di atas fill Danger — Ink.Text satu-satunya token terang yang pas
            style = Type.Caption.copy(color = Ink.Text),
            maxLines = 1,
        )
    }
}
