package id.melvern.hermesmobile.ui.overview

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import id.melvern.hermesmobile.HermesApp
import id.melvern.hermesmobile.core.repo.BotCard
import id.melvern.hermesmobile.core.repo.SessionRepo
import id.melvern.hermesmobile.ui.components.QuietSheet
import id.melvern.hermesmobile.ui.components.Pretty
import id.melvern.hermesmobile.ui.theme.*
import kotlinx.coroutines.launch

/**
 * v27: kasih tugas ke bot dari HP — chat baru di profile bot itu (judul = ringkasan tugas),
 * prompt langsung dikirim. Status bot di Agents jadi Running; notif "agent replied" saat selesai.
 */
@Composable
fun GiveTaskSheet(
    app: HermesApp,
    bots: List<BotCard>,
    initial: BotCard?,
    onDismiss: () -> Unit,
    onStarted: (chatArg: String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val choices = bots.filterNot { it.isDefault }.ifEmpty { bots }
    var target by remember { mutableStateOf(initial?.takeIf { b -> choices.any { it.name == b.name } } ?: choices.firstOrNull()) }
    var task by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { fr.requestFocus() } }

    QuietSheet(onDismiss = onDismiss, title = "Give task") {
        // pilih bot — chip rapat satu baris
        Box(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = Dim.ScreenH),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            choices.forEach { b ->
                id.melvern.hermesmobile.ui.components.KeyCap(Pretty.profile(b.label), selected = b.name == target?.name) { target = b }
            }
        }
            Box(Modifier.align(Alignment.CenterEnd).width(40.dp).height(Dim.KeyH)
                .background(androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(Ink.Surface2.copy(alpha = 0f), Ink.Surface2))))
        }
        target?.task?.let {
            Text("Busy now: $it", style = Type.Meta.copy(color = Ink.Warn), maxLines = 2,
                modifier = Modifier.padding(horizontal = Dim.ScreenH).padding(top = 8.dp))
        }
        BasicTextField(
            value = task,
            onValueChange = { task = it; error = null },
            textStyle = Type.Body,
            cursorBrush = SolidColor(Ink.Text),
            minLines = 4, maxLines = 10,
            decorationBox = { inner ->
                Box {
                    if (task.isEmpty()) Text("What should ${target?.let { Pretty.profile(it.label) } ?: "the bot"} do? Paths, specs, what to report back…",
                        style = Type.Body.copy(color = Ink.Text3))
                    inner()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dim.ScreenH, vertical = 10.dp)
                .focusRequester(fr)
                .background(Ink.Glass, Radius.Key)
                .border(1.dp, Ink.KeyBezel, Radius.Key)
                .padding(12.dp),
        )
        error?.let { Text(it, style = Type.Meta.copy(color = Ink.Danger), modifier = Modifier.padding(horizontal = Dim.ScreenH)) }
        Row(Modifier.fillMaxWidth().padding(horizontal = Dim.ScreenH), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
            id.melvern.hermesmobile.ui.components.KeyCap("Cancel", onClick = onDismiss)
            id.melvern.hermesmobile.ui.components.KeyCap(
                if (busy) "Sending…" else "Send task",
                lit = !busy && task.isNotBlank() && target != null,
                enabled = !busy && task.isNotBlank() && target != null,
                onClick = {
                    val bot = target ?: return@KeyCap
                    val text = task.trim()
                    busy = true
                    scope.launch {
                        try {
                            val c = app.client ?: throw IllegalStateException("Not connected to your Mac")
                            val repo = SessionRepo(c, bot.name)
                            val (rt, stored) = repo.createSession(title = taskTitle(text))
                            repo.sendPromptResilient(stored, rt, text)
                            // buka via stored id (resume) supaya prompt + jawaban yang sedang jalan tampil
                            onStarted("$stored|t=" + android.net.Uri.encode(taskTitle(text)) + "|p=" + bot.name)
                        } catch (e: Throwable) {
                            error = "Couldn't send: ${e.message ?: "unknown error"}"
                            busy = false
                        }
                    }
                },
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}

/** Judul chat tugas: baris pertama, maks 48 karakter. */
fun taskTitle(text: String): String {
    val first = text.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() } ?: "Task"
    return if (first.length > 48) first.take(47).trimEnd() + "…" else first
}
