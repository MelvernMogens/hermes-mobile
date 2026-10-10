package id.melvern.hermesmobile.ui.chat

import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.CheckBoxOutlineBlank
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.ErrorOutline
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
import id.melvern.hermesmobile.ui.components.DismissibleSelectionContainer
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.automirrored.rounded.Reply
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.CallSplit
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
import androidx.compose.ui.unit.sp
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
    /**
     * Run campuran tool + reasoning tanpa prosa di antaranya → SATU baris
     * "Worked · N steps" (dulu belasan pill bertumpuk kayak log debug).
     */
    data class Activity(val tools: List<ChatItem.Tool>, val thoughts: List<String>, override val key: String) : ChatRow
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
            is ChatItem.Event -> it.at
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
    return mergeActivity(out)
}

/** Gabung Tools/Thoughts yang bersebelahan jadi Activity bila run-nya > 1 baris. */
internal fun mergeActivity(rows: List<ChatRow>): List<ChatRow> {
    val out = mutableListOf<ChatRow>()
    var run = mutableListOf<ChatRow>()
    fun flush() {
        if (run.size >= 2) {
            val tools = run.filterIsInstance<ChatRow.Tools>().flatMap { it.tools }
            val thoughts = run.filterIsInstance<ChatRow.Thoughts>().flatMap { it.texts }
            out += ChatRow.Activity(tools, thoughts, "a" + run.first().key)
        } else out += run
        run = mutableListOf()
    }
    rows.forEach { r ->
        if (r is ChatRow.Tools || r is ChatRow.Thoughts) run += r else { flush(); out += r }
    }
    flush()
    return out
}

// ── Pesan ────────────────────────────────────────────────────────────

@Composable
fun DayChip(label: String) {
    // v28: day break as a log divider — hairline · label · hairline
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Dim.ScreenH).padding(top = 14.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f).height(hairline()).background(Ink.Hairline))
        Text(label, style = Type.Caption.copy(color = Ink.Text3), modifier = Modifier.padding(horizontal = 10.dp))
        Box(Modifier.weight(1f).height(hairline()).background(Ink.Hairline))
    }
}

/**
 * Bubble user: kanan, max 80%, surface2, radius 18 (kanan-bawah 6). Timestamp
 * caption di DALAM bubble pojok kanan bawah. Queued = ikon Schedule + "Queued";
 * pending = ikon jam kecil. Terkirim = tanpa centang.
 */
@Composable
fun UserBubble(
    item: ChatItem.User,
    onLongPress: (String) -> Unit,
    mediaFetch: (suspend (String) -> androidx.compose.ui.graphics.ImageBitmap?)? = null,
    videoFetch: (suspend (String) -> java.io.File?)? = null,
    /** v26.3: bubble gagal kirim → tap = kirim ulang. */
    onRetry: (() -> Unit)? = null,
) {
    // M11: quote block (reply) di atas isi — kaya WhatsApp.
    // M12: pesan user yang mengandung MEDIA:/path → render via MarkdownText (foto/video/player),
    // bukan Text polos (bug: media dari user tampil sebagai teks path).
    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = Dim.ScreenH)) {
        // M15: bubble max = min(80% pane, 560dp) — di tablet tidak full-stretch.
        val maxW = minOf(maxWidth * 0.8f, Dim.BubbleMaxW)
        // M9 (item 4): SelectionContainer di level konten — user bisa seleksi
        // sebagian teks native. Long-press full-copy lewat tap-gesture di
        // wrapper (SelectionContainer + combinedClickable bentrok).
        Column(
            Modifier
                .align(Alignment.CenterEnd)
                .widthIn(max = maxW)
                .clip(Radius.BubbleUser)
                .background(Ink.Surface2)
                .pointerInput(item.text, item.failed) {
                    detectTapGestures(
                        onLongPress = { onLongPress(item.text) },
                        onTap = { if (item.failed) onRetry?.invoke() },
                    )
                }
                .padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 8.dp),
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
            if (id.melvern.hermesmobile.ui.components.MarkdownParser.containsMediaLine(item.text)) {
                MarkdownText(userDisplayText(item.text), style = Type.Body, imageFetch = mediaFetch, videoFetch = videoFetch)
            } else {
                DismissibleSelectionContainer { Text(userDisplayText(item.text), style = Type.Body) }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                when {
                    item.failed -> {
                        Icon(Icons.Rounded.ErrorOutline, null, tint = Ink.Danger, modifier = Modifier.size(Dim.IconTiny))
                        Spacer(Modifier.width(4.dp))
                        Text("Not sent · Tap to retry", style = Type.Caption.copy(color = Ink.Danger))
                        if (item.time.isNotEmpty()) Spacer(Modifier.width(6.dp))
                    }
                    item.steered -> {
                        Icon(Icons.Rounded.CallSplit, null, tint = Ink.Text3, modifier = Modifier.size(Dim.IconTiny))
                        Spacer(Modifier.width(4.dp))
                        Text("Steered", style = Type.Caption)
                        if (item.time.isNotEmpty()) Spacer(Modifier.width(6.dp))
                    }
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
                if (item.time.isNotEmpty()) Text(item.time, style = Type.Timecode.copy(color = Ink.Text3, fontSize = 11.sp))
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
    /** false = pesan antara dalam satu turn → tanpa baris aksi (kurangi tangga ikon). */
    showMeta: Boolean = true,
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
        DismissibleSelectionContainer {
            MarkdownText(item.text, style = Type.Body, imageFetch = mediaFetch, videoFetch = videoFetch)
        }
        if (item.done && showMeta) {
            val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
            var copied by remember(item.text) { mutableStateOf(false) }
            LaunchedEffect(copied) { if (copied) { kotlinx.coroutines.delay(1400); copied = false } }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                // ikon 16 di target 32: geser −8dp supaya glyph sejajar tepi teks
                modifier = Modifier.padding(top = 6.dp).offset(x = (-8).dp),
            ) {
                MetaAction(if (copied) Icons.Rounded.Check else Icons.Rounded.ContentCopy, if (copied) "Copied" else "Copy") {
                    clipboard.setText(androidx.compose.ui.text.AnnotatedString(item.text)); copied = true
                }
                if (onReply != null) MetaAction(Icons.AutoMirrored.Rounded.Reply, "Reply") { onReply(item.text) }
                if (item.time.isNotEmpty()) {
                    Text(item.time, style = Type.Caption.copy(color = Ink.Text4), modifier = Modifier.padding(start = 6.dp))
                }
            }
        }
    }
}

/** Ikon aksi meta di bawah pesan — 16dp di target 32, redup. */
@Composable
private fun MetaAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(32.dp).clip(Radius.Full).pressClickable(onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = Ink.Text3, modifier = Modifier.size(15.dp))
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
            Modifier
                .heightIn(min = Dim.ToolRow)
                .clip(Radius.Key)
                .background(Ink.Surface1)
                .border(hairline(), Ink.Bezel, Radius.Key)
                .pressClickable { open = !open }
                .padding(start = 8.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Psychology, null, tint = Ink.Text3, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                when {
                    secs != null -> "Thought for ${secs}s"
                    steps > 1 -> "Reasoned · $steps steps"
                    else -> "Reasoning"
                },
                style = Type.Caption.copy(color = Ink.Text2),
            )
            Spacer(Modifier.width(2.dp))
            Icon(if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, tint = Ink.Text3, modifier = Modifier.size(14.dp))
        }
        if (open && text.isNotBlank()) QuoteText(text)
    }
}

/**
 * System report (subagent / background process finished, model switched…) — one small collapsed
 * line in the reasoning-pill style, NOT a user bubble. Tap opens the full report.
 */
@Composable
fun EventRow(e: ChatItem.Event) {
    var open by remember(e.rowId, e.body.length) { mutableStateOf(false) }
    val icon = when (e.kind) {
        "async_delegation_complete" -> Icons.Outlined.Psychology
        else -> Icons.Rounded.ExpandMore
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = Dim.ScreenH)) {
        Row(
            Modifier
                .heightIn(min = Dim.ToolRow)
                .clip(Radius.Key)
                .background(Ink.Surface1)
                .border(hairline(), Ink.Bezel, Radius.Key)
                .pressClickable { open = !open }
                .padding(start = 8.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (e.kind == "async_delegation_complete") {
                Icon(icon, null, tint = Ink.Text3, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
            } else {
                StatusDot(Ink.Text3)
                Spacer(Modifier.width(6.dp))
            }
            Text(
                e.label,
                style = Type.Caption.copy(color = Ink.Text2),
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.width(2.dp))
            Icon(if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, tint = Ink.Text3, modifier = Modifier.size(14.dp))
        }
        if (open && e.body.isNotBlank()) QuoteText(e.body.take(20_000))
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
        "vision" in n || "image" in n && "generate" !in n ->
            ToolLook(Icons.Outlined.Image, "Looked at image", "Looking at image")
        "delegate" in n || "subagent" in n ->
            ToolLook(Icons.Outlined.Hub, "Ran subagents", "Running subagents")
        "execute_code" in n || n == "python" ->
            ToolLook(Icons.Outlined.Code, "Ran code", "Running code")
        "web" in n || "browser" in n || "fetch" in n || "http" in n ->
            ToolLook(Icons.Outlined.Language, "Browsed the web", "Browsing the web")
        listOf("terminal", "shell", "bash", "exec", "command", "process").any { it in n } ->
            ToolLook(Icons.Outlined.Terminal, "Ran terminal", "Running terminal")
        listOf("search", "grep", "find").any { it in n } ->
            ToolLook(Icons.Outlined.Search, "Searched files", "Searching files")
        listOf("write", "patch", "edit", "replace").any { it in n } ->
            ToolLook(Icons.Outlined.Edit, "Edited file", "Editing file")
        listOf("read", "file", "cat", "view").any { it in n } ->
            ToolLook(Icons.Outlined.Description, "Read file", "Reading file")
        listOf("todo", "memory", "note", "skill").any { it in n } ->
            ToolLook(Icons.AutoMirrored.Outlined.Notes, "Updated ${name.replace('_', ' ')}", "Updating ${name.replace('_', ' ')}")
        else -> ToolLook(Icons.Outlined.Build, "Used ${name.replace('_', ' ')}", "Using ${name.replace('_', ' ')}")
    }
}

/** v28: "Running terminal" / "Reading file" — what a live feed is doing right now. */
fun toolRunningLabel(name: String): String = toolLook(name).running
fun toolIcon(name: String): ImageVector = toolLook(name).icon
/** v28: one-word source label for the monitor strip: Terminal, Code, Image, Web, Files… */
fun toolShort(name: String): String {
    val n = name.lowercase()
    return when {
        "vision" in n || "image" in n -> "Looking"
        "delegate" in n || "subagent" in n -> "Delegating"
        "execute_code" in n -> "Coding"
        "web" in n || "browser" in n || "fetch" in n || "http" in n -> "Browsing"
        listOf("terminal", "shell", "bash", "exec", "command", "process").any { it in n } -> "Running"
        listOf("write", "patch", "edit", "replace").any { it in n } -> "Editing"
        listOf("search", "grep", "find").any { it in n } -> "Searching"
        listOf("read", "file", "cat", "view").any { it in n } -> "Reading"
        listOf("todo", "memory", "note", "skill").any { it in n } -> "Planning"
        else -> "Working"
    }
}

/** Grup tool berurutan. 1 tool = satu baris; >1 = "Used N tools" collapsed. */
@Composable
fun ToolGroup(tools: List<ChatItem.Tool>) {
    val anyRunning = tools.any { it.status == "run" }
    if (tools.size == 1) {
        Box(Modifier.padding(horizontal = Dim.ScreenH)) { StepPill(tools, emptyList(), single = tools.first()) }
        return
    }
    // >1 tool = bahasa yang sama dengan run campuran: "Worked · N steps"
    Box(Modifier.padding(horizontal = Dim.ScreenH)) { StepPill(tools, emptyList()) }
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
            val failed = tool.status == "error"
            when {
                running -> PulsingDot(Ink.Live, Modifier.padding(horizontal = 4.dp), size = 6.dp)
                failed -> Icon(Icons.Outlined.ErrorOutline, "Failed", tint = Ink.Danger, modifier = Modifier.size(14.dp))
                else -> Icon(look.icon, null, tint = Ink.Text3, modifier = Modifier.size(14.dp))
            }
            Spacer(Modifier.width(8.dp))
            Text(
                when { running -> look.running + "…"; failed -> look.done + " · failed"; else -> look.done },
                style = Type.Caption.copy(color = if (failed) Ink.Danger else Ink.Text2),
            )
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

/** Satu pertanyaan clarify. `qid` null = mode single (server lama / pertanyaan tunggal). */
data class ClarifyQ(
    val qid: String?,
    val question: String,
    val choices: List<String> = emptyList(),
    val multi: Boolean = false,
)

/**
 * State clarify card. Single → result {answer}; batch → {answers:{qid:…}} untuk
 * SEMUA pertanyaan sekaligus (server menunggu set lengkap).
 */
data class AskClarify(
    val id: String,
    val questions: List<ClarifyQ>,
    val responded: String? = null,
    val respondRaw: (kotlinx.serialization.json.JsonObject) -> Boolean = { false },
) {
    val question: String get() = questions.firstOrNull()?.question ?: "Something needs confirmation"

    fun result(answers: List<String>): kotlinx.serialization.json.JsonObject =
        kotlinx.serialization.json.buildJsonObject {
            val batch = questions.any { it.qid != null }
            if (!batch) put("answer", kotlinx.serialization.json.JsonPrimitive(answers.firstOrNull().orEmpty()))
            else put("answers", kotlinx.serialization.json.buildJsonObject {
                questions.forEachIndexed { i, q ->
                    put(q.qid ?: "q$i", kotlinx.serialization.json.JsonPrimitive(answers.getOrNull(i).orEmpty()))
                }
            })
        }

    companion object {
        /** Parse params request clarify (single atau batch). Pure — di-unit-test. */
        fun parse(id: String, p: kotlinx.serialization.json.JsonObject?): AskClarify {
            fun strs(e: kotlinx.serialization.json.JsonElement?): List<String> =
                (e as? kotlinx.serialization.json.JsonArray)?.mapNotNull {
                    (it as? kotlinx.serialization.json.JsonPrimitive)?.content?.takeIf { s -> s.isNotBlank() }
                } ?: emptyList()
            fun bool(e: kotlinx.serialization.json.JsonElement?) =
                (e as? kotlinx.serialization.json.JsonPrimitive)?.content == "true"
            fun str(e: kotlinx.serialization.json.JsonElement?) =
                (e as? kotlinx.serialization.json.JsonPrimitive)?.takeIf { it !is kotlinx.serialization.json.JsonNull }?.content
            val arr = p?.get("questions") as? kotlinx.serialization.json.JsonArray
            val qs = arr?.mapNotNull { e ->
                val o = e as? kotlinx.serialization.json.JsonObject ?: return@mapNotNull null
                ClarifyQ(
                    qid = str(o["qid"]) ?: return@mapNotNull null,
                    question = str(o["question"]).orEmpty(),
                    choices = strs(o["choices"]),
                    multi = bool(o["multi_select"]),
                )
            }.orEmpty()
            return if (qs.isNotEmpty()) AskClarify(id, qs)
            else AskClarify(id, listOf(ClarifyQ(
                qid = null,
                question = str(p?.get("question")) ?: "Something needs confirmation",
                choices = strs(p?.get("choices")),
                multi = bool(p?.get("multi_select")),
            )))
        }
    }
}

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
                Spacer(Modifier.width(4.dp))
                Box(
                    Modifier.padding(end = 6.dp).heightIn(min = 36.dp).clip(Radius.Full).background(Ink.Accent)
                        .pressClickable { onChoice("once") }.padding(horizontal = 18.dp),
                    contentAlignment = Alignment.Center,
                ) { Text("Allow", style = Type.Callout.copy(color = Ink.OnAccent, fontWeight = FontWeight.SemiBold)) }
            }
        }
    }
}

/**
 * Clarify card: pertanyaan + pilihan yang bisa di-tap (seperti desktop), plus
 * "Other" untuk jawaban bebas. Batch → satu per satu ("1 of 3"), jawaban dikirim
 * sekaligus di akhir. Multi-select → centang lalu "Done".
 */
@Composable
fun ClarifyCard(cq: AskClarify, onSubmit: (List<String>) -> Unit) {
    var step by remember(cq.id) { mutableStateOf(0) }
    val answers = remember(cq.id) { mutableStateListOf<String>() }
    var typed by remember(cq.id, step) { mutableStateOf("") }
    var typing by remember(cq.id, step) { mutableStateOf(false) }
    val picked = remember(cq.id, step) { mutableStateListOf<String>() }
    val answered = cq.responded != null
    val q = cq.questions.getOrNull(step) ?: cq.questions.first()
    val total = cq.questions.size

    fun commit(answer: String) {
        if (answered) return
        answers.add(answer)
        if (answers.size >= total) onSubmit(answers.toList()) else step++
    }

    CardShell(dim = answered) {
        if (total > 1 && !answered) {
            Text("${step + 1} of $total", style = Type.Meta, modifier = Modifier.padding(bottom = 4.dp))
        }
        Text(if (answered) cq.questions.joinToString("\n") { it.question } else q.question,
            style = Type.Title, modifier = Modifier.padding(end = 8.dp))
        Spacer(Modifier.height(10.dp))
        if (answered) {
            Text(cq.responded ?: "", style = Type.Callout.copy(color = Ink.Text2), modifier = Modifier.padding(bottom = 10.dp, end = 8.dp))
            return@CardShell
        }
        q.choices.forEach { choice ->
            val on = choice in picked
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(end = 8.dp, bottom = 6.dp)
                    .clip(Radius.Chip)
                    .background(if (on) Ink.Surface3 else Ink.Surface2)
                    .pressClickable {
                        if (q.multi) { if (on) picked.remove(choice) else picked.add(choice) }
                        else commit(choice)
                    }
                    .heightIn(min = 44.dp)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (q.multi) {
                    Icon(if (on) Icons.Rounded.CheckBox else Icons.Rounded.CheckBoxOutlineBlank, null,
                        tint = if (on) Ink.Text else Ink.Text3, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                }
                Text(choice, style = Type.Callout, modifier = Modifier.weight(1f))
            }
        }
        if (q.choices.isEmpty() || typing) {
            Row(Modifier.padding(end = 0.dp), verticalAlignment = Alignment.CenterVertically) {
                BasicTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    textStyle = Type.Callout,
                    cursorBrush = SolidColor(Ink.Text),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { if (typed.isNotBlank()) commit(typed.trim()) }),
                    modifier = Modifier
                        .weight(1f)
                        .clip(Radius.Chip)
                        .background(Ink.Surface2)
                        .padding(horizontal = 14.dp, vertical = 11.dp),
                    decorationBox = { inner ->
                        Box {
                            if (typed.isEmpty()) Text("Your answer", style = Type.Callout.copy(color = Ink.Text3))
                            inner()
                        }
                    },
                )
                TextButton(onClick = { if (typed.isNotBlank()) commit(typed.trim()) }, enabled = typed.isNotBlank()) {
                    Text("Send", style = Type.Callout.copy(fontWeight = FontWeight.SemiBold, color = if (typed.isNotBlank()) Ink.Text else Ink.Text3))
                }
            }
        }
        Row(Modifier.fillMaxWidth().heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
            if (q.choices.isNotEmpty() && !typing) {
                TextButton(onClick = { typing = true }) { Text("Other…", style = Type.Callout.copy(color = Ink.Text2)) }
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { commit("") }) { Text("Skip", style = Type.Callout.copy(color = Ink.Text3)) }
            if (q.multi) {
                TextButton(onClick = { commit(picked.joinToString(", ")) }, enabled = picked.isNotEmpty()) {
                    Text("Done", style = Type.Callout.copy(fontWeight = FontWeight.SemiBold, color = if (picked.isNotEmpty()) Ink.Text else Ink.Text3))
                }
            }
        }
    }
}


/** Baris aktivitas gabungan (tool + reasoning) — satu pill, expand → daftar langkah. */
@Composable
fun ActivityRow(tools: List<ChatItem.Tool>, thoughts: List<String>) {
    Box(Modifier.padding(horizontal = Dim.ScreenH)) { StepPill(tools, thoughts) }
}

/**
 * Pill langkah: satu bahasa visual untuk tool / reasoning / campuran.
 * Ringkasan: "Worked · 7 steps" (running: dot + "Working · 3 steps"; ada yang
 * gagal: ikon merah + "1 failed"). Expand → reasoning (kutipan) + baris tool.
 */
@Composable
private fun StepPill(tools: List<ChatItem.Tool>, thoughts: List<String>, single: ChatItem.Tool? = null) {
    var open by remember { mutableStateOf(false) }
    val running = tools.any { it.status == "run" }
    val failed = tools.count { it.status == "error" }
    val steps = tools.size + thoughts.count { it.isNotBlank() }.coerceAtLeast(if (thoughts.isNotEmpty()) 1 else 0)
    // v28 as-run log strip: lamp · what happened · step count in mono · chevron.
    // Running → live lamp + the tool running now; done → dim; failure → red lamp.
    val current = single ?: tools.lastOrNull { it.status == "run" }
    val label = when {
        single != null -> toolLook(single.name).let { l ->
            when (single.status) { "run" -> l.running; "error" -> l.done + " · failed"; else -> l.done }
        }
        running && current != null -> toolLook(current.name).running
        running -> "Working"
        else -> "Worked"
    }
    val tally = when {
        running -> id.melvern.hermesmobile.ui.components.Tally.LIVE
        failed > 0 || single?.status == "error" -> id.melvern.hermesmobile.ui.components.Tally.FAULT
        else -> id.melvern.hermesmobile.ui.components.Tally.OFF
    }
    Column(Modifier.fillMaxWidth().animateContentSize()) {
        Row(
            Modifier
                .heightIn(min = Dim.ToolRow)
                .clip(Radius.Key)
                .background(Ink.Surface1)
                .border(hairline(), Ink.Bezel, Radius.Key)
                .pressClickable { open = !open }
                .padding(start = 6.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            id.melvern.hermesmobile.ui.components.TallyLamp(tally)
            Spacer(Modifier.width(4.dp))
            val icon = when {
                single != null -> toolLook(single.name).icon
                current != null -> toolLook(current.name).icon
                else -> Icons.Outlined.Bolt
            }
            Icon(icon, null, tint = if (running) Ink.Text2 else Ink.Text3, modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, style = Type.Caption.copy(color = if (running) Ink.Text else Ink.Text2))
            if (single == null && steps > 0) {
                Text("  ", style = Type.Caption)
                Text("$steps ${if (steps == 1) "step" else "steps"}", style = Type.Timecode.copy(color = Ink.Text3, fontSize = 11.sp))
            }
            if (failed > 0 && single == null) {
                Text(" · $failed failed", style = Type.Caption.copy(color = Ink.Danger))
            }
            Spacer(Modifier.width(4.dp))
            Icon(if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, tint = Ink.Text3, modifier = Modifier.size(14.dp))
        }
        if (open) Column(Modifier.padding(start = 12.dp, top = 6.dp)) {
            if (single != null) {
                single.detail?.takeIf { it.isNotBlank() }?.let { CodeBox(single.name, it, Modifier.padding(top = 2.dp, bottom = 4.dp)) }
            } else {
                val joined = thoughts.filter { it.isNotBlank() }.joinToString("\n\n")
                if (joined.isNotBlank()) QuoteText(joined.take(4000))
                tools.forEach { ToolLine(it) }
            }
        }
    }
}


private val ATTACHED_CONTEXT_RE = Regex("""(?:^|\n)--- Attached Context ---\s*\n""")

/**
 * Teks prompt user untuk ditampilkan: buang blok "--- Attached Context ---"
 * (isi file yang di-inline server untuk model — desktop juga memotongnya) dan
 * baris ref "@file:…" (bubble menampilkan medianya lewat baris MEDIA:).
 */
fun userDisplayText(raw: String): String {
    val cut = ATTACHED_CONTEXT_RE.find(raw)?.let { raw.substring(0, it.range.first) } ?: raw
    return cut.lineSequence().filterNot { val l = it.trim(); l.startsWith("@file:") || l.startsWith("@image:") }.joinToString("\n").trim()
}


/** Kartu hasil slash command: baris perintah mono + output (dipotong, tap untuk buka semua). */
@Composable
fun CommandCard(item: ChatItem.Command) {
    var expanded by remember { mutableStateOf(false) }
    val out = item.output
    val long = (out?.lines()?.size ?: 0) > 14
    Column(
        Modifier.fillMaxWidth().padding(horizontal = Dim.ScreenH, vertical = 4.dp)
            .clip(Radius.Card).background(Ink.Surface1)
            .then(if (long) Modifier.pressClickable { expanded = !expanded } else Modifier)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(item.command, style = Type.Mono.copy(color = Ink.Text, fontWeight = FontWeight.Medium), maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            when {
                out == null -> { Spacer(Modifier.width(8.dp)); PulsingDot(Ink.Live, size = 8.dp) }
                item.failed -> { Spacer(Modifier.width(8.dp)); StatusDot(Ink.Danger) }
            }
        }
        if (out != null) {
            Spacer(Modifier.height(8.dp))
            val shown = if (long && !expanded) out.lines().take(14).joinToString("\n") else out
            Text(shown, style = Type.MonoMeta.copy(color = if (item.failed) Ink.Danger else Ink.Text2))
            if (long) {
                Spacer(Modifier.height(6.dp))
                Text(if (expanded) "Show less" else "Show all", style = Type.Caption.copy(color = Ink.Text3))
            }
        }
    }
}
