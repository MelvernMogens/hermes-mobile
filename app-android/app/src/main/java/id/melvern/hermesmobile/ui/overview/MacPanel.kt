package id.melvern.hermesmobile.ui.overview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Coffee
import androidx.compose.material.icons.outlined.DesktopAccessDisabled
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Tab
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import id.melvern.hermesmobile.HermesApp
import id.melvern.hermesmobile.core.repo.LimitsRepo
import id.melvern.hermesmobile.core.repo.MacRepo
import id.melvern.hermesmobile.ui.components.StatusDot
import id.melvern.hermesmobile.ui.components.KeyCap
import id.melvern.hermesmobile.ui.theme.Ink
import id.melvern.hermesmobile.ui.theme.Radius
import id.melvern.hermesmobile.ui.theme.Type
import id.melvern.hermesmobile.ui.theme.pressClickable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * v24 Mac control panel: status ringkas + aksi aman (allow-list server) + daftar
 * dev server untuk web preview. Aksi yang mengganggu (lock, restart) minta tap dua kali.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MacPanel(app: HermesApp, onPreview: (url: String, title: String) -> Unit) {
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<MacRepo.Status?>(null) }
    var ports by remember { mutableStateOf<Pair<List<MacRepo.Port>, Int>?>(null) }
    var note by remember { mutableStateOf<String?>(null) }
    var armed by remember { mutableStateOf<String?>(null) }
    var kick by remember { mutableStateOf(0) }

    LaunchedEffect(kick) {
        while (true) {
            val conn = app.connection ?: run { delay(2000); null } ?: continue
            val repo = MacRepo(conn)
            repo.status()?.let { status = it }
            repo.ports()?.let { ports = it }
            delay(30_000)
        }
    }
    LaunchedEffect(armed) { if (armed != null) { delay(3500); armed = null } }

    fun act(key: String, action: String, arg: String = "", confirm: Boolean = false) {
        if (confirm && armed != key) { armed = key; note = "Tap again to confirm"; return }
        armed = null
        note = "…"
        scope.launch {
            val conn = app.connection ?: return@launch
            val (_, msg) = MacRepo(conn).action(action, arg)
            note = msg
            kick++
        }
    }

    val s = status
    id.melvern.hermesmobile.ui.components.RackUnit(s?.host ?: "Your Mac", trailing = {
        if (s != null) id.melvern.hermesmobile.ui.components.TallyLabel(id.melvern.hermesmobile.ui.components.Tally.LIVE, "Online")
        else id.melvern.hermesmobile.ui.components.TallyLabel(id.melvern.hermesmobile.ui.components.Tally.OFF, "Checking…")
    }) {
        if (s != null) {
            // vitals as a readout row: label over mono value, four equal columns
            Row(Modifier.fillMaxWidth()) {
                @Composable fun Readout(label: String, value: String) {
                    Column(Modifier.weight(1f)) {
                        Text(label, style = Type.Caption.copy(color = Ink.Text3))
                        Text(value, style = Type.TimecodeLarge.copy(color = Ink.Text))
                    }
                }
                Readout(if (s.onAc) "Battery · AC" else "Battery", s.batteryPct?.let { "$it%" } ?: "—")
                Readout("CPU", s.load1?.let { "${((it / s.cpus) * 100).toInt().coerceAtMost(999)}%" } ?: "—")
                Readout("Disk free", LimitsRepo.gb(s.diskFree))
                Readout("Uptime", s.uptimeDays?.let { "${it.toInt()}d" } ?: "—")
            }
            val down = s.services.filterNot { it.running }
            if (down.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(Ink.Danger); Spacer(Modifier.width(6.dp))
                    Text(down.joinToString { it.name } + " stopped", style = Type.Caption.copy(color = Ink.Danger))
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val awake = s?.keepAwake == true
            KeyCap(if (awake) "Awake · on" else "Keep awake", icon = Icons.Outlined.Coffee, lit = awake) {
                act("awake", "keep_awake", if (awake) "off" else "on")
            }
            KeyCap(if (armed == "lock") "Tap to lock" else "Lock screen", icon = Icons.Outlined.Lock, armed = armed == "lock") { act("lock", "lock", confirm = true) }
            KeyCap("Display off", icon = Icons.Outlined.DesktopAccessDisabled) { act("disp", "display_sleep") }
            s?.botTabs?.takeIf { it > 1 }?.let { n ->
                KeyCap("Close $n bot tabs", icon = Icons.Outlined.Tab) { act("tabs", "close_bot_tabs") }
            }
            KeyCap(if (armed == "rs") "Tap to restart" else "Restart server", icon = Icons.Outlined.RestartAlt, armed = armed == "rs") {
                act("rs", "restart_service", "com.hermes.mobile-serve", confirm = true)
            }
        }
        note?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, style = Type.Caption.copy(color = Ink.Text2))
        }

        val p = ports
        if (p != null && p.first.any { it.html }) {
            Spacer(Modifier.height(14.dp))
            Text("Web preview", style = Type.Section.copy(color = Ink.Text2))
            Spacer(Modifier.height(4.dp))
            p.first.filter { it.html }.forEach { port ->
                Row(
                    Modifier.fillMaxWidth().clip(Radius.Chip).pressClickable {
                        val conn = app.connection ?: return@pressClickable
                        scope.launch {
                            val url = MacRepo(conn).previewTicketUrl(port.port)
                            if (url == null) note = "Couldn't open preview" else onPreview(url, port.title.ifBlank { "localhost:${port.port}" })
                        }
                    }.padding(vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Language, null, tint = Ink.Text2, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(port.title.ifBlank { "Untitled page" }, style = Type.Callout, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("localhost:${port.port} · ${port.process}", style = Type.Caption.copy(color = Ink.Text3))
                    }
                    Icon(androidx.compose.material.icons.Icons.AutoMirrored.Rounded.KeyboardArrowRight, "Open", tint = Ink.Text3, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
private fun Action(label: String, icon: ImageVector, selected: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.clip(Radius.Full).background(if (selected) Ink.Text else Ink.Surface2).pressClickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (selected) Ink.Bg else Ink.Text2, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, style = Type.Caption.copy(color = if (selected) Ink.Bg else Ink.Text, fontWeight = FontWeight.Medium))
    }
}

@Suppress("unused") private val keepBedtime = Icons.Outlined.Bedtime
@Suppress("unused") private val keepBox: @Composable () -> Unit = { Box {} }
