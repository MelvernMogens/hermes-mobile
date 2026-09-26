package id.melvern.hermesmobile.ui.chat

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
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
import id.melvern.hermesmobile.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(app: HermesApp, storedSessionId: String) {
    val scope = rememberCoroutineScope()
    var runtimeId by remember { mutableStateOf(storedSessionId) }
    var items by remember { mutableStateOf<List<ChatItem>>(emptyList()) }
    var title by remember { mutableStateOf(storedSessionId.take(22)) }
    var running by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val client = app.client
    val connState by client?.state?.collectAsState() ?: remember { mutableStateOf(ConnState.CLOSED) }

    fun mapTranscript(msgs: List<TranscriptMessage>): List<ChatItem> = msgs.flatMap { m ->
        when {
            m.isUser -> listOf(ChatItem.User(m.text ?: "", m.rowId))
            m.role == "tool" -> listOf(
                ChatItem.Tool(
                    name = m.name ?: "tool",
                    status = "done",
                    detail = (m.args ?: m.context ?: "").take(100),
                )
            )
            else -> { // assistant
                val text = m.text ?: ""
                val reasoning = m.reasoning?.takeIf { it.isNotBlank() }
                if (text.isBlank() && reasoning == null) emptyList()
                else listOf(ChatItem.Assistant(text.ifBlank { "…" }, done = true, reasoning = reasoning?.take(300)))
            }
        }
    }

    // Load transcript penuh (defer_history=false — verified)
    LaunchedEffect(storedSessionId) {
        val c = app.client ?: return@LaunchedEffect
        var waited = 0
        while (c.state.value != ConnState.OPEN && waited < 25000) { delay(250); waited += 250 }
        if (c.state.value != ConnState.OPEN) {
            items = listOf(ChatItem.NoticeLine("Tidak terhubung ke backend."))
            loading = false; return@LaunchedEffect
        }
        try {
            val out = SessionRepo(c).resume(storedSessionId)
            runtimeId = out.runtimeId
            running = out.running
            items = mapTranscript(out.messages)
        } catch (e: Throwable) {
            items = listOf(ChatItem.NoticeLine("Gagal memuat transcript: ${e.message}"))
        } finally { loading = false }
    }

    // Live events
    LaunchedEffect(runtimeId) {
        app.client?.inbound?.collect { ev ->
            when (ev) {
                is GatewayInbound.RpcEvent -> {
                    if (ev.sessionId == runtimeId || ev.sessionId.isEmpty()) when (ev.type) {
                    "message.delta" -> {
                        val delta = ev.payload?.get("delta")?.jsonStr() ?: ev.payload?.get("text")?.jsonStr() ?: ""
                        if (delta.isNotEmpty()) {
                            val last = items.lastOrNull()
                            items = if (last is ChatItem.Assistant && !last.done) {
                                items.dropLast(1) + last.copy(text = last.text + delta)
                            } else {
                                items + ChatItem.Assistant(delta, done = false)
                            }
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
                        val last = items.lastOrNull()
                        items = if (last is ChatItem.Tool && last.status == "run") {
                            items.dropLast(1) + ChatItem.Tool(name, "run")
                        } else items + ChatItem.Tool(name, "run")
                    }
                    "tool.complete" -> {
                        val name = ev.payload?.get("tool")?.jsonStr() ?: ev.payload?.get("name")?.jsonStr() ?: ""
                        items = items.map {
                            if (it is ChatItem.Tool && it.status == "run" && (name.isEmpty() || it.name == name)) it.copy(status = "done")
                            else it
                        }
                    }
                    "thinking.delta" -> { /* M2: reasoning collapsible */ }
                    "session.title" -> {
                        ev.payload?.get("title")?.jsonStr()?.takeIf { it.isNotBlank() }?.let { title = it.take(24) }
                    }
                    "session.reclaimed" -> {
                        items = items + ChatItem.NoticeLine("Sesi diambil alih dari permukaan lain.")
                    }
                    else -> {}
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

    Scaffold(
        containerColor = Bg,
        topBar = {
            TopAppBar(
                title = { Text(title, color = TextPrimary, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Bg),
                navigationIcon = {
                    TextButton(onClick = { /* system back */ }) { Text("‹", color = TextSecondary, fontSize = 26.sp) }
                },
                actions = {
                    if (connState != ConnState.OPEN) Text("●", color = Danger, fontSize = 10.sp, modifier = Modifier.padding(end = 16.dp))
                }
            )
        },
        bottomBar = {
            Composer(
                value = input, onValueChange = { input = it },
                running = running, connected = connState == ConnState.OPEN,
                onSend = {
                    val text = input.trim(); if (text.isEmpty()) return@Composer
                    input = ""
                    items = items + ChatItem.User(text, pending = true)
                    scope.launch {
                        try {
                            val repo = SessionRepo(app.client ?: return@launch)
                            val newRuntime = repo.sendPromptResilient(storedSessionId, runtimeId, text)
                            if (newRuntime != runtimeId) runtimeId = newRuntime
                            items = items.map { if (it is ChatItem.User && it.pending) it.copy(pending = false) else it }
                        } catch (e: Throwable) {
                            items = items.map { if (it is ChatItem.User && it.pending) it.copy(pending = false) else it }
                            items = items + ChatItem.NoticeLine("Gagal kirim: ${e.message}")
                            input = text
                        }
                    }
                },
                onStop = { scope.launch { app.client?.let { SessionRepo(it).interrupt(runtimeId) } } },
            )
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            if (loading) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Accent, strokeWidth = 2.dp, modifier = Modifier.size(26.dp))
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(items) { item -> ChatItemView(item) }
            }
        }
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
                color = TextPrimary,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .widthIn(max = 320.dp)
                    .background(UserBubble, RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp))
                    .padding(horizontal = 14.dp, vertical = 9.dp),
            )
        }
        is ChatItem.Assistant -> Column {
            if (item.reasoning != null) Text(
                "💭 " + item.reasoning!!,
                color = TextTertiary, fontSize = 12.sp,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(bottom = 3.dp),
            )
            Text(item.text, color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
            if (!item.done) BlinkingCursor()
        }
        is ChatItem.Tool -> Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(Modifier.size(5.dp).background(if (item.status == "run") Running else Ok, RoundedCornerShape(3.dp)))
            Text(item.name, fontSize = 12.sp, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
            if (item.status == "run") Text("· jalan…", fontSize = 11.sp, color = TextTertiary)
            else if (item.detail != null) Text("· ${item.detail}", fontSize = 11.sp, color = TextTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        }
        is ChatItem.NoticeLine -> Text(item.text, fontSize = 12.sp, color = Warn)
    }
}

@Composable
private fun BlinkingCursor() {
    val alpha = rememberInfiniteTransition().animateFloat(
        initialValue = 0.15f, targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(550), repeatMode = RepeatMode.Reverse),
        label = "cursor",
    )
    Box(Modifier.padding(top = 4.dp).size(7.dp, 15.dp).background(Accent.copy(alpha = alpha.value)))
}

@Composable
private fun Composer(
    value: String, onValueChange: (String) -> Unit,
    running: Boolean, connected: Boolean,
    onSend: () -> Unit, onStop: () -> Unit,
) {
    Surface(color = BgElevated, tonalElevation = 0.dp) {
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = TextStyle(color = TextPrimary, fontSize = 15.sp),
                cursorBrush = SolidColor(Accent),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    Box(Modifier.background(BgCard, RoundedCornerShape(20.dp)).padding(horizontal = 14.dp, vertical = 10.dp)) {
                        if (value.isEmpty()) Text("Kirim pesan…", color = TextTertiary, fontSize = 15.sp)
                        inner()
                    }
                },
            )
            if (running) {
                IconButton(onClick = onStop, modifier = Modifier.size(40.dp)) {
                    Box(Modifier.size(12.dp).background(Danger, RoundedCornerShape(2.dp)))
                }
            } else {
                IconButton(onClick = onSend, enabled = connected && value.isNotBlank(), modifier = Modifier.size(40.dp)) {
                    Text("↑", color = if (connected && value.isNotBlank()) Accent else TextTertiary, fontSize = 20.sp)
                }
            }
        }
    }
}
