package id.melvern.hermesmobile.ui.chat

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.melvern.hermesmobile.HermesApp
import id.melvern.hermesmobile.core.model.ChatItem
import id.melvern.hermesmobile.core.model.TranscriptMessage
import id.melvern.hermesmobile.core.repo.SessionRepo
import id.melvern.hermesmobile.core.rpc.ConnState
import id.melvern.hermesmobile.core.rpc.GatewayInbound
import id.melvern.hermesmobile.ui.components.MarkdownText
import id.melvern.hermesmobile.ui.theme.F
import id.melvern.hermesmobile.ui.theme.Shape
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Chat screen — "backstage": prose serif cream full-width, user pill kanan,
 * tool row kecil, composer = tiket strip (notch + outline vermillion).
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(app: HermesApp, storedSessionId: String) {
    val scope = rememberCoroutineScope()
    // Format arg: "storedId" (dari list) atau "storedId|runtimeId" (dari NEW GIG —
    // session baru: skip resume, langsung pakai runtime yang udah nempel di koneksi kita)
    val parts = storedSessionId.split("|")
    val actualStoredId = parts[0]
    val preattachedRuntime = parts.getOrNull(1)
    var runtimeId by remember { mutableStateOf(preattachedRuntime ?: actualStoredId) }
    var items by remember { mutableStateOf<List<ChatItem>>(emptyList()) }
    var title by remember { mutableStateOf(actualStoredId.take(22)) }
    var running by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(preattachedRuntime == null) }
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val client = app.client
    val connState by client?.state?.collectAsState() ?: remember { mutableStateOf(ConnState.CLOSED) }

    fun mapTranscript(msgs: List<TranscriptMessage>): List<ChatItem> = msgs.flatMap { m ->
        when {
            m.isUser -> listOf(ChatItem.User(m.text ?: "", m.rowId))
            m.role == "tool" -> listOf(
                ChatItem.Tool(m.name ?: "tool", "done", (m.args ?: m.context ?: "").take(140))
            )
            else -> {
                val text = m.text ?: ""
                val reasoning = m.reasoning?.takeIf { it.isNotBlank() }
                if (text.isBlank() && reasoning == null) emptyList()
                else listOf(ChatItem.Assistant(text.ifBlank { "…" }, done = true, reasoning = reasoning?.take(400)))
            }
        }
    }

    LaunchedEffect(actualStoredId, preattachedRuntime) {
        if (preattachedRuntime != null) {
            // NEW GIG: session baru kosong — gak ada transcript buat di-resume
            loading = false
            return@LaunchedEffect
        }
        val c = app.client ?: return@LaunchedEffect
        var waited = 0
        while (c.state.value != ConnState.OPEN && waited < 25000) { delay(250); waited += 250 }
        if (c.state.value != ConnState.OPEN) {
            items = listOf(ChatItem.NoticeLine("Tidak terhubung ke backend."))
            loading = false; return@LaunchedEffect
        }
        try {
            val out = SessionRepo(c).resume(actualStoredId)
            runtimeId = out.runtimeId
            running = out.running
            items = mapTranscript(out.messages)
        } catch (e: Throwable) {
            items = listOf(ChatItem.NoticeLine("Gagal memuat transcript: ${e.message}"))
        } finally { loading = false }
    }

    LaunchedEffect(runtimeId) {
        app.client?.inbound?.collect { ev ->
            when (ev) {
                is GatewayInbound.RpcEvent -> if (ev.sessionId == runtimeId || ev.sessionId.isEmpty()) when (ev.type) {
                    "message.delta" -> {
                        val delta = ev.payload?.get("delta")?.jsonStr() ?: ev.payload?.get("text")?.jsonStr() ?: ""
                        if (delta.isNotEmpty()) {
                            val last = items.lastOrNull()
                            items = if (last is ChatItem.Assistant && !last.done) {
                                items.dropLast(1) + last.copy(text = last.text + delta)
                            } else items + ChatItem.Assistant(delta, done = false)
                        }
                    }
                    "message.complete" -> {
                        val text = ev.payload?.get("text")?.jsonStr() ?: ""
                        items = items.filterNot { it is ChatItem.Assistant && !it.done }
                        if (text.isNotEmpty()) items = items + ChatItem.Assistant(text, done = true)
                        running = false
                    }
                    "message.start" -> running = true
                    "message.interim" -> {
                        val text = ev.payload?.get("text")?.jsonStr() ?: ""
                        if (text.isNotEmpty()) {
                            items = items.filterNot { it is ChatItem.Assistant && !it.done }
                            items = items + ChatItem.Assistant(text, done = false)
                        }
                    }
                    "tool.start" -> {
                        running = true
                        val name = ev.payload?.get("tool")?.jsonStr() ?: ev.payload?.get("name")?.jsonStr() ?: "tool"
                        items = items + ChatItem.Tool(name, "run")
                    }
                    "tool.complete" -> {
                        val name = ev.payload?.get("tool")?.jsonStr() ?: ev.payload?.get("name")?.jsonStr() ?: ""
                        items = items.map {
                            if (it is ChatItem.Tool && it.status == "run" && (name.isEmpty() || it.name == name)) it.copy(status = "done")
                            else it
                        }
                    }
                    "session.title" -> {
                        ev.payload?.get("title")?.jsonStr()?.takeIf { it.isNotBlank() }?.let { title = it.take(24) }
                    }
                }
                is GatewayInbound.Ready -> app.client?.replaySince(runtimeId)
                else -> {}
            }
        }
    }

    LaunchedEffect(items.size) {
        if (items.isNotEmpty()) listState.animateScrollToItem(items.size - 1)
    }

    Column(Modifier.fillMaxSize().background(F.Bg).statusBarsPadding()) {
        // Header tipis — bill type
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                title.uppercase(),
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (connState != ConnState.OPEN) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(F.Error))
            } else if (running) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(F.Vermillion))
            }
        }
        HorizontalDivider(color = F.Stroke, thickness = 1.dp)

        Box(Modifier.weight(1f)) {
            if (loading) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = F.Vermillion, strokeWidth = 2.dp, modifier = Modifier.size(26.dp))
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                items(items) { item -> ChatItemView(item) }
            }
        }

        TicketComposer(
            value = input, onValueChange = { input = it },
            running = running, connected = connState == ConnState.OPEN,
            onSend = {
                val text = input.trim(); if (text.isEmpty()) return@TicketComposer
                input = ""
                items = items + ChatItem.User(text, pending = true)
                scope.launch {
                    try {
                        val repo = SessionRepo(app.client ?: return@launch)
                        val newRuntime = repo.sendPromptResilient(actualStoredId, runtimeId, text)
                        if (newRuntime != runtimeId) runtimeId = newRuntime
                        items = items.map { if (it is ChatItem.User && it.pending) it.copy(pending = false) else it }
                    } catch (e: Throwable) {
                        items = items.map { if (it is ChatItem.User && it.pending) it.copy(pending = false) else it }
                        val msg = if (e is id.melvern.hermesmobile.core.rpc.SessionNotOwnedException)
                            "Session ini lagi dibuka di desktop app — tutup dulu di sana, atau mulai GIG baru."
                        else "Gagal kirim: ${e.message}"
                        items = items + ChatItem.NoticeLine(msg)
                        input = text
                    }
                }
            },
            onStop = { scope.launch { app.client?.let { SessionRepo(it).interrupt(runtimeId) } } },
        )
    }
}

private fun kotlinx.serialization.json.JsonElement?.jsonStr(): String =
    (this as? kotlinx.serialization.json.JsonPrimitive)?.takeIf { it.isString }?.content
        ?: (this as? kotlinx.serialization.json.JsonPrimitive)?.content ?: ""

@Composable
private fun ChatItemView(item: ChatItem) {
    when (item) {
        is ChatItem.User -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Text(
                item.text,
                style = MaterialTheme.typography.bodyMedium,
                color = F.CreamDim,
                modifier = Modifier
                    .widthIn(max = 300.dp)
                    .background(F.Surface1, Shape.M)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
        // animateContentSize HANYA saat done: transisi halus saat caret hilang,
        // tanpa churn re-layout per delta streaming (perf).
        is ChatItem.Assistant -> Column(
            if (item.done) Modifier.animateContentSize() else Modifier
        ) {
            if (item.reasoning != null) Text(
                "· MIKIR — " + item.reasoning!!.take(120),
                style = MaterialTheme.typography.labelSmall,
                color = F.LavenderDim,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(bottom = 6.dp),
            )
            // M3.1: jawaban assistant dirender markdown (bold/italic/code/list/
            // heading/link/blockquote). User tetap plain (pill).
            SelectionContainer {
                MarkdownText(item.text, style = MaterialTheme.typography.bodyLarge)
            }
            if (!item.done) VermillionCaret()
        }
        is ChatItem.Tool -> Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(if (item.status == "run") F.Vermillion else F.Ok))
            Text(
                (item.name.lowercase() + if (item.status == "run") " · jalan" else "").uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = F.Lavender,
            )
        }
        is ChatItem.NoticeLine -> Text(item.text, style = MaterialTheme.typography.labelSmall, color = F.Warn)
    }
}

@Composable
private fun VermillionCaret() {
    val alpha by rememberInfiniteTransition().animateFloat(
        initialValue = 1f, targetValue = 0.25f,
        animationSpec = infiniteRepeatable(tween(550), RepeatMode.Reverse),
        label = "caret",
    )
    Box(Modifier.padding(top = 6.dp).size(width = 4.dp, height = 20.dp).background(F.Vermillion.copy(alpha = alpha)))
}

/**
 * Composer tiket: outline vermillion + notch kiri-kanan, send button =
 * satu-satunya lingkaran vermillion penuh di layar; morph send→stop satu slot.
 */
@Composable
private fun TicketComposer(
    value: String, onValueChange: (String) -> Unit,
    running: Boolean, connected: Boolean,
    onSend: () -> Unit, onStop: () -> Unit,
) {
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .height(64.dp)
            .background(F.Surface1, Shape.Ticket)
            .border(1.5.dp, F.Vermillion.copy(alpha = 0.8f), Shape.Ticket),
    ) {
        // notch kiri & kanan (lingkaran bg menembus)
        Box(Modifier.align(Alignment.CenterStart).offset(x = (-11).dp).size(22.dp).clip(CircleShape).background(F.Bg))
        Box(Modifier.align(Alignment.CenterEnd).offset(x = 11.dp).size(22.dp).clip(CircleShape).background(F.Bg))

        Row(
            Modifier.fillMaxSize().padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = TextStyle(color = F.Cream, fontSize = 16.sp),
                cursorBrush = SolidColor(F.Vermillion),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    Box {
                        if (value.isEmpty()) Text("kirim pesan…", style = MaterialTheme.typography.bodyMedium, color = F.LavenderDim)
                        inner()
                    }
                },
            )
            val interaction = remember { MutableInteractionSource() }
            val pressed by interaction.collectIsPressedAsState()
            val scale by animateFloatAsState(
                targetValue = if (pressed) 0.97f else 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
                label = "sendScale",
            )
            Box(
                Modifier
                    .size(44.dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
                    .clip(CircleShape)
                    .background(
                        when {
                            running -> F.Surface2
                            connected && value.isNotBlank() -> F.Vermillion
                            else -> F.Surface2.copy(alpha = 0.5f)
                        }
                    )
                    .clickable(
                        interactionSource = interaction,
                        indication = null,
                        enabled = running || (connected && value.isNotBlank()),
                        onClick = {
                            if (running) onStop()
                            else if (connected && value.isNotBlank()) onSend()
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (running) Box(Modifier.size(13.dp).background(F.Cream, Shape.Xs))
                else Text("▲", color = F.Cream, fontSize = 15.sp)
            }
        }
    }
}
