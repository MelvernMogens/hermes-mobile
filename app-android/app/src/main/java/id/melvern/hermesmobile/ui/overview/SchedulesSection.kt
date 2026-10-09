package id.melvern.hermesmobile.ui.overview

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import id.melvern.hermesmobile.HermesApp
import id.melvern.hermesmobile.core.repo.ScheduleRepo
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
 * v25 Schedules (cron): kartu daftar jadwal di tab Agents. Tap jadwal → Run now /
 * Pause / Delete. "+" → buat jadwal (nama, prompt, preset waktu atau ekspresi sendiri).
 */
@Composable
fun SchedulesSection(app: HermesApp) {
    val scope = rememberCoroutineScope()
    var jobs by remember { mutableStateOf<List<ScheduleRepo.Job>?>(null) }
    var kick by remember { mutableStateOf(0) }
    var creating by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<ScheduleRepo.Job?>(null) }
    var loadFailed by remember { mutableStateOf(false) }
    LaunchedEffect(kick) {
        var conn = app.connection
        while (conn == null) { delay(1500); conn = app.connection }
        val got = ScheduleRepo(conn).jobs()
        if (got != null) { jobs = got; loadFailed = false } else loadFailed = jobs == null
    }

    Column(Modifier.fillMaxWidth()) {
        id.melvern.hermesmobile.ui.components.LogHeader("Schedules", trailing = jobs?.size?.toString(), action = "New", onAction = { creating = true })
        Column(Modifier.padding(horizontal = Dim.ScreenH).fillMaxWidth().clip(Radius.Rack).background(Ink.Surface1)
            .border(1.dp, Ink.Bezel, Radius.Rack)) {
            val js = jobs
            when {
                js == null && loadFailed -> Text("Couldn't load schedules — tap to retry", style = Type.Caption.copy(color = Ink.Text3),
                    modifier = Modifier.fillMaxWidth().pressClickable { kick++ }.padding(16.dp))
                js == null -> Text("Loading…", style = Type.Caption.copy(color = Ink.Text3), modifier = Modifier.padding(16.dp))
                js.isEmpty() -> Text("No scheduled tasks. Tap New to have Hermes do something every morning, hourly, etc.",
                    style = Type.Caption.copy(color = Ink.Text3), modifier = Modifier.padding(16.dp))
                else -> js.forEach { j ->
                    Row(
                        Modifier.fillMaxWidth().pressClickable { selected = j }.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        id.melvern.hermesmobile.ui.components.TallyLamp(when {
                            j.paused -> id.melvern.hermesmobile.ui.components.Tally.OFF
                            j.lastStatus in setOf("error", "failed") -> id.melvern.hermesmobile.ui.components.Tally.FAULT
                            else -> id.melvern.hermesmobile.ui.components.Tally.CUE
                        })
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(j.name.ifBlank { j.prompt.take(40) }, style = Type.Callout, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                listOfNotNull(j.scheduleLabel, if (j.paused) "paused" else null, j.profile?.takeIf { it != "default" }).joinToString(" · "),
                                style = Type.Caption.copy(color = Ink.Text3), maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }

    if (creating) NewScheduleSheet(onDismiss = { creating = false }) { name, prompt, sched, done ->
        scope.launch {
            val conn = app.connection ?: return@launch
            val r = ScheduleRepo(conn).create(name, prompt, sched)
            done(r.exceptionOrNull()?.message)
            if (r.isSuccess) { creating = false; kick++ }
        }
    }
    selected?.let { j ->
        var note by remember(j.id) { mutableStateOf<String?>(null) }
        var confirmDel by remember(j.id) { mutableStateOf(false) }
        fun run(block: suspend (ScheduleRepo) -> Result<Unit>, ok: String, close: Boolean = false) {
            note = "…"
            scope.launch {
                val conn = app.connection ?: return@launch
                val r = block(ScheduleRepo(conn))
                note = r.exceptionOrNull()?.message ?: ok
                kick++
                if (close && r.isSuccess) selected = null
            }
        }
        QuietSheet(onDismiss = { selected = null }, title = j.name.ifBlank { "Schedule" }) {
            Column(Modifier.padding(horizontal = Dim.ScreenH)) {
                Text(j.scheduleLabel + (j.nextRunAt?.let { " · next ${it.take(16).replace('T', ' ')}" } ?: ""), style = Type.Caption.copy(color = Ink.Text3))
                Spacer(Modifier.height(8.dp))
                Text(j.prompt, style = Type.Callout.copy(color = Ink.Text2), maxLines = 8, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("Run now") { run({ it.runNow(j.id) }, "Started") }
                    Pill(if (j.paused) "Resume" else "Pause") { run({ it.pause(j.id, !j.paused) }, if (j.paused) "Resumed" else "Paused") }
                    Pill(if (confirmDel) "Confirm delete" else "Delete", danger = true) {
                        if (!confirmDel) confirmDel = true else run({ it.delete(j.id) }, "Deleted", close = true)
                    }
                }
                note?.let { Spacer(Modifier.height(8.dp)); Text(it, style = Type.Caption.copy(color = Ink.Text2)) }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NewScheduleSheet(onDismiss: () -> Unit, onCreate: (String, String, String, (String?) -> Unit) -> Unit) {
    var name by remember { mutableStateOf("") }
    var prompt by remember { mutableStateOf("") }
    var sched by remember { mutableStateOf(ScheduleRepo.Presets.first().second) }
    var custom by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    QuietSheet(onDismiss = onDismiss, title = "New schedule") {
        Column(Modifier.padding(horizontal = Dim.ScreenH)) {
            Field(name, { name = it.take(60) }, "Name (e.g. Morning brief)", single = true)
            Spacer(Modifier.height(8.dp))
            Field(prompt, { prompt = it }, "What should Hermes do?", single = false)
            Spacer(Modifier.height(12.dp))
            Text("WHEN", style = Type.Caption.copy(color = Ink.Text3, fontWeight = FontWeight.Medium))
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ScheduleRepo.Presets.forEach { (label, expr) ->
                    Pill(label, selected = !custom && sched == expr) { custom = false; sched = expr }
                }
                Pill("Custom", selected = custom) { custom = true; sched = "" }
            }
            if (custom) {
                Spacer(Modifier.height(8.dp))
                Field(sched, { sched = it }, "Cron (0 7 * * *) or every 2h", single = true)
            }
            err?.let { Spacer(Modifier.height(8.dp)); Text(it, style = Type.Caption.copy(color = Ink.Danger)) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                val ok = prompt.isNotBlank() && sched.isNotBlank() && !busy
                TextButton(enabled = ok, onClick = {
                    busy = true; err = null
                    onCreate(name.trim(), prompt.trim(), sched.trim()) { e -> busy = false; err = e }
                }) { Text(if (busy) "Creating…" else "Create", style = Type.Callout.copy(color = if (ok) Ink.Text else Ink.Text3)) }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun Field(value: String, onChange: (String) -> Unit, hint: String, single: Boolean) {
    BasicTextField(
        value = value, onValueChange = onChange, textStyle = Type.Body, cursorBrush = SolidColor(Ink.Text),
        singleLine = single, maxLines = if (single) 1 else 6,
        modifier = Modifier.fillMaxWidth().heightIn(min = if (single) 0.dp else 88.dp)
            .clip(Radius.Chip).background(Ink.Surface2).padding(horizontal = 14.dp, vertical = 12.dp),
        decorationBox = { inner -> Box { if (value.isEmpty()) Text(hint, style = Type.Body.copy(color = Ink.Text3)); inner() } },
    )
}

@Composable
private fun Pill(label: String, selected: Boolean = false, danger: Boolean = false, onClick: () -> Unit) {
    Text(
        label,
        style = Type.Caption.copy(color = when { selected -> Ink.Bg; danger -> Ink.Danger; else -> Ink.Text }, fontWeight = FontWeight.Medium),
        modifier = Modifier.clip(Radius.Full).background(if (selected) Ink.Text else Ink.Surface2).pressClickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}
