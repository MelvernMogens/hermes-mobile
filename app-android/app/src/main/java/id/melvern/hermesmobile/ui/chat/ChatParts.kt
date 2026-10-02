package id.melvern.hermesmobile.ui.chat

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Reply
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import id.melvern.hermesmobile.core.model.ChatItem
import id.melvern.hermesmobile.ui.components.CodeBox
import id.melvern.hermesmobile.ui.components.MarkdownText
import id.melvern.hermesmobile.ui.components.PulsingDot
import id.melvern.hermesmobile.ui.components.StatusDot
import id.melvern.hermesmobile.ui.theme.Dim
import id.melvern.hermesmobile.ui.theme.Ink
import id.melvern.hermesmobile.ui.theme.Radius
import id.melvern.hermesmobile.ui.theme.Type
import id.melvern.hermesmobile.ui.theme.hairline
import id.melvern.hermesmobile.ui.theme.pressClickable
import id.melvern.hermesmobile.ui.theme.rememberReduceMotion

// ── Baris tampilan (UI-only) ─────────────────────────────────────────

/** Satu baris LazyColumn chat: pesan, grup tool berurutan, atau pemisah hari. */
sealed interface ChatRow {
    val key: String
    data class Day(val label: String, override val key: String) : ChatRow
    data class Item(val item: ChatItem, val index: Int, override val key: String) : ChatRow
    data class Tools(val tools: List<ChatItem.Tool>, val firstIndex: Int, override val key: String) : ChatRow
    /** Langkah assistant tanpa teks (reasoning saja) yang berurutan — satu baris "Thought". */
    data class Thoughts(val texts: List<String>, override val key: String) : ChatRow
}

/** Assistant tanpa teks jawaban (transcript turn tool-only → text "…"). */
fun ChatItem.Assistant.isThoughtOnly(): Boolean = text.isBlank() || text == "…"

/**
 * Susun baris: tool berurutan digabung jadi satu grup; pemisah hari disisipkan
 * saat tanggal pesan berganti (pesan tanpa `at` tidak memicu pemisah).
 */
fun buildRows(items: List<ChatItem>, dayOf: (Double?) -> java.time.LocalDate?, label: (java.time.LocalDate) -> String): List<ChatRow> {
    val out = mutableListOf<ChatRow>()
    var lastDay: java.time.LocalDate? = null
    var toolRun = mutableListOf<ChatItem.Tool>()
    var toolStart = -1
    var thoughtRun = mutableListOf<String>()
    var thoughtStart = -1
    fun flushTools() {
        if (toolRun.isNotEmpty()) {
            out += ChatRow.Tools(toolRun.toList(), toolStart, "t$toolStart")
            toolRun = mutableListOf(); toolStart = -1
        }
    }
    fun flushThoughts() {
        if (thoughtRun.isNotEmpty()) {
            out += ChatRow.Thoughts(thoughtRun.toList(), "r$thoughtStart")
            thoughtRun = mutableListOf(); thoughtStart = -1
        }
    }
    items.forEachIndexed { i, it ->
        if (it is ChatItem.Tool) {
            flushThoughts()
            if (toolStart < 0) toolStart = i
            toolRun += it
            return@forEachIndexed
        }
        if (it is ChatItem.Assistant && it.done && it.isThoughtOnly()) {
            flushTools()
            if (thoughtStart < 0) thoughtStart = i
            thoughtRun += it.reasoning.orEmpty()
            return@forEachIndexed
        }
        flushTools(); flushThoughts()
        val at = when (it) {
            is ChatItem.User -> it.at
            is ChatItem.Assistant -> it.at
            else -> null
        }
        val day = dayOf(at)
        if (day != null && day != lastDay) {
            out += ChatRow.Day(label(day), "d$day")
            lastDay = day
        }
        out += ChatRow.Item(it, i, "i$i")
    }
    flushTools(); flushThoughts()
    return out
}

// ── Pesan ────────────────────────────────────────────────────────────

@Composable
fun DayChip(label: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = 4.dp), contentAlignment = Alignment.Center) {
        Text(
            label,
            style = Type.Caption.copy(color = Ink.Text2),
            modifier = Modifier.clip(Radius.Chip).background(Ink.Surface1).padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

/**
 * Bubble user: kanan, max 80%, surface2, radius 18 (kanan-bawah 6). Timestamp
 * caption di DALAM bubble pojok kanan bawah. Queued = ikon Schedule + "Queued";
 * pending = ikon jam kecil. Terkirim = tanpa centang.
 */
@Composable
fun UserBubble(item: ChatItem.User, onLongPress: (String) -> Unit) {
    // M11: quote block (reply) di atas isi — kaya WhatsApp.
    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = Dim.ScreenH)) {
        val maxW = maxWidth * 0.8f
        // M9 (item 4): SelectionContainer di level konten — user bisa seleksi
        // sebagian teks native. Long-press full-copy lewat tap-gesture di
        // wrapper (SelectionContainer + combinedClickable bentrok).
        Column(
            Modifier
                .align(Alignment.CenterEnd)
                .widthIn(max = maxW)
                .clip(Radius.BubbleUser)
                .background(Ink.Surface2)
                .pointerInput(item.text) {
                    detectTapGestures(onLongPress = { onLongPress(item.text) })
                }
                .padding(horizontal = 12.dp, vertical = 9.dp),
            horizontalAlignment = Alignment.End,
        ) {
            item.quote?.let { q ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp)
                        .clip(Radius.Chip)
                        .background(Ink.Surface1)
                        .border(hairline(), Ink.Hairline, Radius.Chip)
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                ) {
                    Text(q, style = Type.Callout.copy(color = Ink.Text2), maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
            }
            SelectionContainer { Text(item.text, style = Type.Body) }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                when {
                    item.queued -> {
                        Icon(Icons.Rounded.Schedule, null, tint = Ink.Text3, modifier = Modifier.size(Dim.IconTiny))
                        Spacer(Modifier.width(4.dp))
                        Text("Queued", style = Type.Caption)
                        if (item.time.isNotEmpty()) Spacer(Modifier.width(6.dp))
                    }
                    item.pending -> {
                        Icon(Icons.Rounded.AccessTime, "Sending", tint = Ink.Text3, modifier = Modifier.size(Dim.IconTiny))
                        if (item.time.isNotEmpty()) Spacer(Modifier.width(4.dp))
                    }
                }
                if (item.time.isNotEmpty()) Text(item.time, style = Type.Caption)
            }
        }
    }
}

/** Assistant: tanpa bubble, full width, markdown body 16/24. */
@Composable
fun AssistantBlock(
    item: ChatItem.Assistant,
    onLongPress: (String) -> Unit,
    mediaFetch: (suspend (String) -> androidx.compose.ui.graphics.ImageBitmap?)?,
    videoFetch: (suspend (String) -> java.io.File?)? = null,
    onReply: ((String) -> Unit)? = null,
) {
    Column(
        Modifier
            .fillMaxWidth()
            // M9 (item 4): long-press full-copy via tap-gesture wrapper;
            // konten dibungkus SelectionContainer di bawah (seleksi sebagian).
            .pointerInput(item.text) {
                detectTapGestures(onLongPress = { onLongPress(item.text) })
            }
            .padding(horizontal = Dim.ScreenH)
            // animateContentSize HANYA saat done: tanpa churn layout per delta (perf).
            .then(if (item.done) Modifier.animateContentSize() else Modifier),
    ) {
        // Transcript: baris assistant tanpa teks tapi ada reasoning (turn tool-only)
        // dipetakan text="…" — tampilkan cuma baris "Thought", tanpa elipsis/jam.
        val thoughtOnly = item.isThoughtOnly()
        item.reasoning?.let {
            ThoughtRow(it, item.thoughtSecs)
            if (!thoughtOnly) Spacer(Modifier.height(8.dp))
        }
        if (thoughtOnly) return@Column
        SelectionContainer {
            MarkdownText(item.text, style = Type.Body, imageFetch = mediaFetch, videoFetch = videoFetch)
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 4.dp),
        ) {
            if (item.done && onReply != null) {
                Row(
                    Modifier
                        .clip(Radius.Chip)
                        .pressClickable { onReply(item.text) }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Reply, "Reply", tint = Ink.Text3, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Reply", style = Type.Caption.copy(color = Ink.Text3))
                }
            }
            if (item.done && item.time.isNotEmpty()) {
                Text(item.time, style = Type.Caption, modifier = Modifier.padding(start = 10.dp, top = 2.dp))
            }
        }
    }
}

/** Beberapa langkah reasoning berurutan (dari transcript) → satu baris. */
@Composable
fun ThoughtsRow(texts: List<String>) {
    Box(Modifier.padding(horizontal = Dim.ScreenH)) {
        val joined = texts.filter { it.isNotBlank() }.joinToString("\n\n")
        ThoughtRow(joined, secs = null, steps = texts.size)
    }
}

/** "Thought for 12s" (atau "Thought") — tap expand isi reasoning. */
@Composable
fun ThoughtRow(text: String, secs: Int?, steps: Int = 1) {
    var open by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.heightIn(min = Dim.ToolRow).clip(Radius.Chip).pressClickable { open = !open }.padding(end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                when {
                    secs != null -> "Thought for ${secs}s"
                    steps > 1 -> "Thought · $steps steps"
                    else -> "Thought"
                },
                style = Type.Meta.copy(color = Ink.Text3),
            )
            Spacer(Modifier.width(2.dp))
            Icon(if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, tint = Ink.Text3, modifier = Modifier.size(Dim.IconSmall))
        }
        if (open && text.isNotBlank()) QuoteText(text)
    }
}

/** Live: "Thinking…" dengan shimmer teks; tap expand stream reasoning. */
@Composable
fun ThinkingRow(text: String) {
    var open by remember { mutableStateOf(false) }
    val reduce = rememberReduceMotion()
    val alpha = if (reduce) 1f else {
        val a by rememberInfiniteTransition(label = "think").animateFloat(
            initialValue = 0.45f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse),
            label = "thinkAlpha",
        )
        a
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = Dim.ScreenH)) {
        Row(
            Modifier.heightIn(min = Dim.ToolRow).clip(Radius.Chip).pressClickable(enabled = text.isNotBlank()) { open = !open }.padding(end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Thinking…", style = Type.Meta.copy(color = Ink.Text3), modifier = Modifier.graphicsLayer { this.alpha = alpha })
            if (text.isNotBlank()) {
                Spacer(Modifier.width(2.dp))
                Icon(if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, tint = Ink.Text3, modifier = Modifier.size(Dim.IconSmall))
            }
        }
        if (open && text.isNotBlank()) QuoteText(text)
    }
}

@Composable
private fun QuoteText(text: String) {
    Row(Modifier.padding(top = 4.dp).height(IntrinsicSize.Min)) {
        Box(Modifier.width(2.dp).fillMaxHeight().background(Ink.HairlineStrong))
        Text(text, style = Type.Meta, modifier = Modifier.padding(start = 12.dp))
    }
}

// ── Tool activity ────────────────────────────────────────────────────

private data class ToolLook(val icon: ImageVector, val done: String, val running: String)

/** Label + ikon manusiawi dari nama tool. */
private fun toolLook(name: String): ToolLook {
    val n = name.lowercase()
    return when {
        listOf("terminal", "shell", "bash", "exec", "command", "process").any { it in n } ->
            ToolLook(Icons.Rounded.Terminal, "Ran terminal", "Running terminal")
        listOf("search", "grep", "find").any { it in n } && "web" !in n ->
            ToolLook(Icons.Rounded.Search, "Searched files", "Searching files")
        "web" in n || "browser" in n || "fetch" in n || "http" in n ->
            ToolLook(Icons.Rounded.Language, "Browsed the web", "Browsing the web")
        listOf("write", "patch", "edit", "replace").any { it in n } ->
            ToolLook(Icons.Rounded.Edit, "Edited file", "Editing file")
        listOf("read", "file", "cat", "view").any { it in n } ->
            ToolLook(Icons.Rounded.Description, "Read file", "Reading file")
        listOf("todo", "memory", "note", "skill").any { it in n } ->
            ToolLook(Icons.AutoMirrored.Rounded.Notes, "Updated ${name.replace('_', ' ')}", "Updating ${name.replace('_', ' ')}")
        else -> ToolLook(Icons.Rounded.Build, "Used ${name.replace('_', ' ')}", "Using ${name.replace('_', ' ')}")
    }
}

/** Grup tool berurutan. 1 tool = satu baris; >1 = "Used N tools" collapsed. */
@Composable
fun ToolGroup(tools: List<ChatItem.Tool>) {
    val anyRunning = tools.any { it.status == "run" }
    if (tools.size == 1) {
        Box(Modifier.padding(horizontal = Dim.ScreenH)) { ToolLine(tools.first()) }
        return
    }
    var open by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(horizontal = Dim.ScreenH).animateContentSize()) {
        Row(
            Modifier.heightIn(min = Dim.ToolRow).clip(Radius.Chip).pressClickable { open = !open }.padding(end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (anyRunning) PulsingDot(Ink.Live, Modifier.padding(horizontal = 4.dp))
            else Icon(Icons.Rounded.Build, null, tint = Ink.Text2, modifier = Modifier.size(Dim.IconSmall))
            Spacer(Modifier.width(8.dp))
            Text(
                if (anyRunning) "Using ${tools.size} tools" else "Used ${tools.size} tools",
                style = Type.Meta,
            )
            Spacer(Modifier.width(2.dp))
            Icon(if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, tint = Ink.Text3, modifier = Modifier.size(Dim.IconSmall))
        }
        if (open) Column(Modifier.padding(start = 24.dp)) { tools.forEach { ToolLine(it) } }
    }
}

@Composable
private fun ToolLine(tool: ChatItem.Tool) {
    val look = toolLook(tool.name)
    val running = tool.status == "run"
    val detail = tool.detail?.takeIf { it.isNotBlank() }
    var open by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .heightIn(min = Dim.ToolRow)
                .clip(Radius.Chip)
                .pressClickable(enabled = detail != null) { open = !open }
                .padding(end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (running) PulsingDot(Ink.Live, Modifier.padding(horizontal = 4.dp))
            else Icon(look.icon, null, tint = Ink.Text2, modifier = Modifier.size(Dim.IconSmall))
            Spacer(Modifier.width(8.dp))
            Text(if (running) look.running + "…" else look.done, style = Type.Meta)
            if (detail != null) {
                Spacer(Modifier.width(2.dp))
                Icon(if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, tint = Ink.Text3, modifier = Modifier.size(Dim.IconSmall))
            }
        }
        if (open && detail != null) CodeBox(tool.name, detail, Modifier.padding(top = 4.dp, bottom = 4.dp))
    }
}

@Composable
fun NoticeRow(text: String, onRetry: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(start = Dim.ScreenH, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        StatusDot(Ink.Warn)
        Spacer(Modifier.width(8.dp))
        Text(text, style = Type.Meta, modifier = Modifier.weight(1f))
        if (onRetry != null) TextButton(onClick = onRetry) {
            Text("Retry", style = Type.Callout.copy(fontWeight = FontWeight.SemiBold))
        }
    }
}

// ── Approval / clarify ───────────────────────────────────────────────

/** State approval card. `respondRaw` = jawab via respond frame; return Boolean: frame terkirim. */
data class AskApproval(
    val id: String,
    val requestId: String?,
    val title: String,
    val command: String,
    val responded: String? = null,
    val respondRaw: (String) -> Boolean = { false },
)

/** State clarify card. `questionId` = qid pertanyaan batch pertama (null = single). */
data class AskClarify(
    val id: String,
    val question: String,
    val questionId: String? = null,
    val responded: String? = null,
    val respondRaw: (String) -> Boolean = { false },
)

@Composable
private fun CardShell(dim: Boolean, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Dim.ScreenH)
            .clip(Radius.Card)
            .background(Ink.Surface1)
            .border(hairline(), Ink.Hairline, Radius.Card)
            .graphicsLayer { alpha = if (dim) 0.6f else 1f }
            .padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 6.dp),
        content = content,
    )
}

/**
 * M4→M8 approval card — SECURITY BOUNDARY. Tombol teks sejajar kanan:
 * "Deny" text2, "Allow" putih SemiBold (tidak ada default focus). Setelah
 * respond → dim + status, jejak keputusan tetap ada.
 */
@Composable
fun ApprovalCard(ap: AskApproval, onChoice: (String) -> Unit) {
    val answered = ap.responded != null
    CardShell(dim = answered) {
        Text("Allow ${ap.title.lowercase().replace('_', ' ')}?", style = Type.Title, modifier = Modifier.padding(end = 8.dp))
        Spacer(Modifier.height(6.dp))
        Text(
            ap.command.ifBlank { "No details provided." },
            style = if (ap.command.isBlank()) Type.Callout.copy(color = Ink.Text2) else Type.Mono.copy(color = Ink.Text2),
            maxLines = 6, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(end = 8.dp),
        )
        Row(Modifier.fillMaxWidth().heightIn(min = Dim.Touch), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            if (answered) {
                Text(if (ap.responded == "once") "Allowed" else "Denied", style = Type.Meta, modifier = Modifier.padding(end = 8.dp))
            } else {
                TextButton(onClick = { onChoice("deny") }) { Text("Deny", style = Type.Callout.copy(color = Ink.Text2)) }
                TextButton(onClick = { onChoice("once") }) { Text("Allow", style = Type.Callout.copy(fontWeight = FontWeight.SemiBold)) }
            }
        }
    }
}

/** M4→M8 clarify card: pertanyaan + field + "Send"; setelah jawab → dim. */
@Composable
fun ClarifyCard(cq: AskClarify, onSubmit: (String) -> Unit) {
    var answer by remember(cq.id) { mutableStateOf("") }
    val answered = cq.responded != null
    CardShell(dim = answered) {
        Text(cq.question, style = Type.Title, modifier = Modifier.padding(end = 8.dp))
        Spacer(Modifier.height(8.dp))
        if (answered) {
            Text(cq.responded ?: "", style = Type.Callout.copy(color = Ink.Text2), modifier = Modifier.padding(bottom = 8.dp, end = 8.dp))
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicTextField(
                    value = answer,
                    onValueChange = { answer = it },
                    textStyle = Type.Callout,
                    cursorBrush = SolidColor(Ink.Text),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { if (answer.isNotBlank()) onSubmit(answer.trim()) }),
                    modifier = Modifier
                        .weight(1f)
                        .clip(Radius.Chip)
                        .background(Ink.Surface2)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    decorationBox = { inner ->
                        Box {
                            if (answer.isEmpty()) Text("Your answer", style = Type.Callout.copy(color = Ink.Text3))
                            inner()
                        }
                    },
                )
                TextButton(onClick = { if (answer.isNotBlank()) onSubmit(answer.trim()) }, enabled = answer.isNotBlank()) {
                    Text("Send", style = Type.Callout.copy(fontWeight = FontWeight.SemiBold, color = if (answer.isNotBlank()) Ink.Text else Ink.Text3))
                }
            }
        }
    }
}
