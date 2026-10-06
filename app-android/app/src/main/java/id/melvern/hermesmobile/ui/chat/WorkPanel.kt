package id.melvern.hermesmobile.ui.chat

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import id.melvern.hermesmobile.core.repo.AgentWorkRepo
import id.melvern.hermesmobile.ui.components.PulsingDot
import id.melvern.hermesmobile.ui.components.QuietSheet
import id.melvern.hermesmobile.ui.components.StatusDot
import id.melvern.hermesmobile.ui.theme.Dim
import id.melvern.hermesmobile.ui.theme.Ink
import id.melvern.hermesmobile.ui.theme.Radius
import id.melvern.hermesmobile.ui.theme.Type
import id.melvern.hermesmobile.ui.theme.pressClickable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Strip tipis di bawah header chat: progress task + subagent yang jalan.
 * Tap → panel penuh (task board + subagent monitor). Hilang kalau dua-duanya kosong.
 */
@Composable
fun WorkStrip(todos: List<AgentWorkRepo.Todo>, subs: List<AgentWorkRepo.Subagent>, wide: Boolean, onOpen: () -> Unit) {
    val visibleTodos = todos.filterNot { it.cancelled }
    val liveSubs = subs.filter { it.running }
    if (visibleTodos.isEmpty() && liveSubs.isEmpty()) return
    val done = visibleTodos.count { it.done }
    val current = visibleTodos.firstOrNull { it.active } ?: visibleTodos.firstOrNull { !it.done }
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Row(
            Modifier.then(if (wide) Modifier.width(Dim.ChatMaxW) else Modifier.fillMaxWidth())
                .padding(horizontal = 12.dp, vertical = 4.dp)
                .clip(Radius.Chip).background(Ink.Surface1)
                .pressClickable(onClick = onOpen)
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (visibleTodos.isNotEmpty()) {
                ProgressRing(done, visibleTodos.size)
                Spacer(Modifier.width(10.dp))
                Text(
                    current?.content ?: "All tasks done",
                    style = Type.Callout.copy(color = if (current == null) Ink.Text3 else Ink.Text),
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                )
                Text(" $done/${visibleTodos.size}", style = Type.Caption.copy(color = Ink.Text3))
            } else {
                PulsingDot(Ink.Live, size = 7.dp)
                Spacer(Modifier.width(10.dp))
                Text(liveSubs.first().goal.ifBlank { "Subagent working" + liveSubs.first().lastTool.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty() }, style = Type.Callout, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            }
            if (liveSubs.isNotEmpty() && visibleTodos.isNotEmpty()) {
                Spacer(Modifier.width(10.dp))
                PulsingDot(Ink.Live, size = 6.dp)
                Text(" ${liveSubs.size}", style = Type.Caption.copy(color = Ink.Text2))
            }
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Rounded.ExpandMore, "Open tasks", tint = Ink.Text3, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun ProgressRing(done: Int, total: Int) {
    val frac = if (total == 0) 0f else done / total.toFloat()
    androidx.compose.foundation.Canvas(Modifier.size(16.dp)) {
        val stroke = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
        drawCircle(Ink.Hairline, style = stroke, radius = size.minDimension / 2 - 1.dp.toPx())
        drawArc(Ink.Text, -90f, 360f * frac, false, style = stroke,
            topLeft = androidx.compose.ui.geometry.Offset(1.dp.toPx(), 1.dp.toPx()),
            size = androidx.compose.ui.geometry.Size(size.width - 2.dp.toPx(), size.height - 2.dp.toPx()))
    }
}

/** Panel penuh: Tasks (checklist live) + Subagents (status, log, steer, stop). */
@Composable
fun WorkSheet(
    todos: List<AgentWorkRepo.Todo>,
    subs: List<AgentWorkRepo.Subagent>,
    repo: () -> AgentWorkRepo?,
    runtimeId: String,
    onDismiss: () -> Unit,
    onRefreshSubs: () -> Unit,
) {
    QuietSheet(onDismiss = onDismiss, title = "Agent work") {
        Column(Modifier.heightIn(max = 620.dp).verticalScroll(rememberScrollState())) {
            val visible = todos.filterNot { it.cancelled }
            if (visible.isNotEmpty()) {
                SectionLabel("Tasks", "${visible.count { it.done }}/${visible.size}")
                todos.forEach { t -> TodoRow(t) }
                Spacer(Modifier.height(12.dp))
            }
            if (subs.isNotEmpty()) {
                SectionLabel("Subagents", "${subs.count { it.running }} running")
                subs.forEach { s -> SubagentRow(s, repo, runtimeId, onRefreshSubs) }
            }
            if (visible.isEmpty() && subs.isEmpty()) {
                Text("Nothing running right now.", style = Type.Callout.copy(color = Ink.Text3),
                    modifier = Modifier.padding(horizontal = Dim.ScreenH, vertical = 16.dp))
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun SectionLabel(name: String, meta: String) {
    Row(Modifier.fillMaxWidth().padding(start = Dim.ScreenH, end = Dim.ScreenH, top = 8.dp, bottom = 6.dp)) {
        Text(name.uppercase(), style = Type.Caption.copy(color = Ink.Text2, fontWeight = FontWeight.Medium))
        Spacer(Modifier.weight(1f))
        Text(meta, style = Type.Caption.copy(color = Ink.Text3))
    }
}

@Composable
private fun TodoRow(t: AgentWorkRepo.Todo) {
    Row(
        Modifier.fillMaxWidth().padding(start = Dim.ScreenH + (if (t.parent != null) 22.dp else 0.dp), end = Dim.ScreenH, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.size(18.dp).padding(top = 1.dp), contentAlignment = Alignment.Center) {
            when {
                t.done -> Icon(Icons.Rounded.CheckCircle, "Done", tint = Ink.Text2, modifier = Modifier.size(17.dp))
                t.active -> PulsingDot(Ink.Live, size = 9.dp)
                t.cancelled -> Icon(Icons.Rounded.RemoveCircleOutline, "Cancelled", tint = Ink.Text4, modifier = Modifier.size(17.dp))
                else -> Icon(Icons.Rounded.RadioButtonUnchecked, "Pending", tint = Ink.Text4, modifier = Modifier.size(17.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(
            t.content,
            style = Type.Callout.copy(
                color = when { t.active -> Ink.Text; t.done || t.cancelled -> Ink.Text3; else -> Ink.Text2 },
                fontWeight = if (t.active) FontWeight.Medium else FontWeight.Normal,
                textDecoration = if (t.cancelled) TextDecoration.LineThrough else null,
            ),
        )
    }
}

@Composable
private fun SubagentRow(s: AgentWorkRepo.Subagent, repo: () -> AgentWorkRepo?, runtimeId: String, onRefresh: () -> Unit) {
    val scope = rememberCoroutineScope()
    var open by remember { mutableStateOf(false) }
    var log by remember { mutableStateOf<String?>(null) }
    var steerText by remember { mutableStateOf("") }
    var note by remember { mutableStateOf<String?>(null) }
    var confirmStop by remember { mutableStateOf(false) }
    LaunchedEffect(open, s.id) {
        while (open) {
            log = try { repo()?.tail(runtimeId, s.id)?.takeLast(3000) } catch (_: Throwable) { log }
            delay(3000)
        }
    }
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp)
            .clip(Radius.Card).background(Ink.Surface1).animateContentSize(),
    ) {
        Row(
            Modifier.fillMaxWidth().pressClickable { open = !open }.padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (s.running) PulsingDot(Ink.Live, size = 7.dp)
            else StatusDot(if (s.status in setOf("failed", "error", "timeout")) Ink.Danger else Ink.Text4)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(s.goal.ifBlank { "Subagent " + s.id.takeLast(6) }, style = Type.Callout, maxLines = if (open) 6 else 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(
                        s.status.ifBlank { "running" },
                        "${s.toolCount} steps".takeIf { s.toolCount > 0 },
                        s.lastTool.ifBlank { null },
                        id.melvern.hermesmobile.ui.components.Pretty.model(s.model).ifBlank { null },
                    ).joinToString(" · "),
                    style = Type.Caption.copy(color = Ink.Text3), maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, tint = Ink.Text4, modifier = Modifier.size(16.dp))
        }
        if (open) {
            Text(
                log?.ifBlank { null } ?: "No output yet.",
                style = Type.MonoMeta.copy(color = Ink.Text2),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).heightIn(max = 220.dp)
                    .clip(Radius.Chip).background(Ink.Bg).padding(10.dp)
                    .verticalScroll(rememberScrollState(Int.MAX_VALUE)),
            )
            if (s.running) {
                Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    BasicTextField(
                        value = steerText, onValueChange = { steerText = it },
                        textStyle = Type.Callout, cursorBrush = SolidColor(Ink.Text), maxLines = 3,
                        modifier = Modifier.weight(1f).clip(Radius.Chip).background(Ink.Surface2).padding(horizontal = 12.dp, vertical = 10.dp),
                        decorationBox = { inner ->
                            Box { if (steerText.isEmpty()) Text("Steer this subagent…", style = Type.Callout.copy(color = Ink.Text3)); inner() }
                        },
                    )
                    TextButton(enabled = steerText.isNotBlank() && s.acceptingSteer, onClick = {
                        val t = steerText.trim(); steerText = ""
                        scope.launch {
                            note = try { if (repo()?.steerSubagent(runtimeId, s.id, t) == true) "Sent — it picks this up after its current step" else "Subagent isn't taking input now" }
                            catch (e: Throwable) { e.message ?: "Failed" }
                        }
                    }) { Text("Send", style = Type.Callout.copy(color = if (steerText.isNotBlank()) Ink.Text else Ink.Text3)) }
                }
                Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(note.orEmpty(), style = Type.Caption.copy(color = Ink.Text3), modifier = Modifier.weight(1f))
                    TextButton(onClick = {
                        if (!confirmStop) { confirmStop = true; return@TextButton }
                        scope.launch {
                            note = try { if (repo()?.stopSubagent(runtimeId, s.id) == true) "Stopping…" else "Already finished" } catch (e: Throwable) { e.message }
                            confirmStop = false; onRefresh()
                        }
                    }) { Text(if (confirmStop) "Tap again to stop" else "Stop", style = Type.Callout.copy(color = Ink.Danger)) }
                }
            } else Spacer(Modifier.height(10.dp))
        }
    }
}

/** Pemilih aksi saat agent jalan dan user sudah mengetik: Steer (sisip) atau Queue (setelah turn). */
@Composable
fun BusySendBar(onSteer: () -> Unit, onQueue: () -> Unit, wide: Boolean) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Row(
            Modifier.then(if (wide) Modifier.width(Dim.ChatMaxW) else Modifier.fillMaxWidth())
                .padding(horizontal = Dim.ScreenH, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Agent is working", style = Type.Caption.copy(color = Ink.Text3), modifier = Modifier.weight(1f))
            Chip("Steer now", onSteer)
            Chip("Send after", onQueue)
        }
    }
}

@Composable
private fun Chip(label: String, onClick: () -> Unit) {
    Text(
        label, style = Type.Caption.copy(color = Ink.Text, fontWeight = FontWeight.Medium),
        modifier = Modifier.clip(Radius.Full).background(Ink.Surface2).pressClickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    )
}
