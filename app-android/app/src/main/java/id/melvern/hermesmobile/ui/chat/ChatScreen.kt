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
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.melvern.hermesmobile.HermesApp
import id.melvern.hermesmobile.core.model.ChatItem
import id.melvern.hermesmobile.core.model.TranscriptMessage
import id.melvern.hermesmobile.core.repo.Fmt
import id.melvern.hermesmobile.core.repo.MetaRepo
import id.melvern.hermesmobile.core.repo.SessionRepo
import id.melvern.hermesmobile.core.rpc.ConnState
import id.melvern.hermesmobile.core.rpc.GatewayInbound
import id.melvern.hermesmobile.core.rpc.RpcException
import id.melvern.hermesmobile.ui.components.MarkdownText
import id.melvern.hermesmobile.ui.theme.F
import id.melvern.hermesmobile.ui.theme.JetBrainsMono
import id.melvern.hermesmobile.ui.theme.Shape
import id.melvern.hermesmobile.ui.theme.pressClickable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/**
 * Chat screen — "backstage": prose serif cream full-width, user pill kanan,
 * tool row kecil, composer = tiket strip (notch + outline vermillion).
 *
 * M3.2: title awal dari list (nav arg, encoded) — ID mentah gak pernah
 * tampil; timestamp per pesan; long-press salin; chip scroll-to-bottom.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(app: HermesApp, actualStoredId: String, preattachedRuntime: String?, initialTitle: String?) {
    val scope = rememberCoroutineScope()
    var runtimeId by remember { mutableStateOf(preattachedRuntime ?: actualStoredId) }
    var items by remember { mutableStateOf<List<ChatItem>>(emptyList()) }
    var title by remember { mutableStateOf(initialTitle ?: "") }
    var running by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(preattachedRuntime == null) }
    var input by remember { mutableStateOf("") }
    // M3.3: thinking indicator — event thinking.delta / reasoning.delta
    var thinking by remember { mutableStateOf(false) }
    var thinkingText by remember { mutableStateOf("") }
    // M4: model aktif — baris meta kecil di header (dari model.options).
    var activeModel by remember { mutableStateOf("") }
    // M4: approval + clarify card — server→client request.
    var approval by remember { mutableStateOf<AskApproval?>(null) }
    var clarify by remember { mutableStateOf<AskClarify?>(null) }
    var modelSheet by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val client = app.client
    val connState by client?.state?.collectAsState() ?: remember { mutableStateOf(ConnState.CLOSED) }

    // M3.2: pinned detection (toleransi 60dp) — autoscroll HANYA kalau user
    // dekat bottom; scroll up >60dp → chip muncul.
    val density = LocalDensity.current
    val bottomThresholdPx = with(density) { 60.dp.toPx() }
    val atBottom by remember(bottomThresholdPx) {
        derivedStateOf {
            // konten pendek / sudah di ujung — gak bisa scroll lagi = bottom
            if (!listState.canScrollForward) return@derivedStateOf true
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()
                ?: return@derivedStateOf true // kosong = dianggap bottom
            last.index >= info.totalItemsCount - 1 &&
                last.offset + last.size >= info.viewportEndOffset - bottomThresholdPx
        }
    }
    var hasNew by remember { mutableStateOf(false) }   // badge "baru" di chip
    var forceScroll by remember { mutableStateOf(false) } // user kirim pesan → selalu scroll
    var didInitialScroll by remember { mutableStateOf(false) }
    // M3.2: long-press salin teks
    var copyTarget by remember { mutableStateOf<String?>(null) }
    val clipboard = LocalClipboardManager.current

    fun nowEpoch(): Double = System.currentTimeMillis() / 1000.0

    fun mapTranscript(msgs: List<TranscriptMessage>): List<ChatItem> = msgs.flatMap { m ->
        when {
            m.isUser -> listOf(ChatItem.User(m.text ?: "", m.rowId, time = Fmt.clock(m.timestamp)))
            m.role == "tool" -> listOf(
                ChatItem.Tool(m.name ?: "tool", "done", (m.args ?: m.context ?: "").take(140))
            )
            else -> {
                val text = m.text ?: ""
                val reasoning = m.reasoning?.takeIf { it.isNotBlank() }
                if (text.isBlank() && reasoning == null) emptyList()
                else listOf(ChatItem.Assistant(text.ifBlank { "…" }, done = true, reasoning = reasoning?.take(400), time = Fmt.clock(m.timestamp)))
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
            val out = SessionRepo(c, app.profile.value).resume(actualStoredId)
            runtimeId = out.runtimeId
            running = out.running
            items = mapTranscript(out.messages)
            // M4: restored server→client request (replay open_requests) —
            // card dimunculkan lagi; jawab via request.answer RPC (frame id
            // dari socket lama sudah mati).
            out.openRequests.forEach { req ->
                when (req.method) {
                    "approval" -> approval = AskApproval(
                        id = req.id,
                        requestId = req.params?.get("request_id")?.jsonStr(),
                        title = req.params?.get("tool_name")?.jsonStr()?.takeIf { it.isNotBlank() } ?: "PERINTAH",
                        command = req.params?.get("command")?.jsonStr() ?: req.params?.get("description")?.jsonStr() ?: "",
                    )
                    "clarify" -> {
                        val qArr = req.params?.get("questions")?.jsonArray
                        clarify = AskClarify(
                            id = req.id,
                            question = req.params?.get("question")?.jsonStr()
                                ?: qArr?.firstOrNull()?.jsonObject?.get("question")?.jsonStr()
                                ?: "Ada yang mau dikonfirmasi",
                            questionId = qArr?.firstOrNull()?.jsonObject?.get("qid")?.jsonStr(),
                        )
                    }
                }
            }
        } catch (e: Throwable) {
            items = listOf(ChatItem.NoticeLine("Gagal memuat transcript: ${e.message}"))
        } finally { loading = false }
    }

    LaunchedEffect(runtimeId) {
        app.client?.inbound?.collect { ev ->
            when (ev) {
                is GatewayInbound.ServerAsk -> when (ev.method) {
                    "approval" -> {
                        val p = ev.params
                        approval = AskApproval(
                            id = ev.id,
                            requestId = p?.get("request_id")?.jsonStr(),
                            title = p?.get("tool_name")?.jsonStr()?.takeIf { it.isNotBlank() } ?: "PERINTAH",
                            command = p?.get("command")?.jsonStr() ?: p?.get("description")?.jsonStr() ?: "",
                            // respond frame return Boolean terkirim (sendRaw) — false = socket mati
                            respondRaw = { choice -> ev.respond(buildJsonObject { put("choice", choice) }) },
                        )
                        running = true // agent nunggu jawaban — jangan biarkan composer bunuh turn
                    }
                    "clarify" -> {
                        val p = ev.params
                        // batch: {questions:[{qid,question,...}]} — single: {question}
                        val qArr = p?.get("questions")?.jsonArray
                        val qid = qArr?.firstOrNull()?.jsonObject?.get("qid")?.jsonStr()
                        clarify = AskClarify(
                            id = ev.id,
                            question = p?.get("question")?.jsonStr()
                                ?: qArr?.firstOrNull()?.jsonObject?.get("question")?.jsonStr()
                                ?: "Ada yang mau dikonfirmasi",
                            questionId = qid,
                            respondRaw = { answer ->
                                if (qid != null) {
                                    // batch: lock jawaban pertanyaan ini
                                    ev.respond(buildJsonObject { put("answers", buildJsonObject { put(qid, answer) }) })
                                } else {
                                    ev.respond(buildJsonObject { put("answer", answer) })
                                }
                            },
                        )
                        running = true
                    }
                    else -> ev.fail(-32601, "no handler: ${ev.method}")
                }
                is GatewayInbound.RpcEvent -> if (ev.sessionId == runtimeId || ev.sessionId.isEmpty()) when (ev.type) {
                    "request.cancel" -> {
                        // server menarik request (timeout/withdraw) — buang card
                        // yang belum dijawab; card yang sudah dijawab (dim) biarkan.
                        val cid = ev.payload?.get("id")?.jsonStr()
                        if (approval?.id == cid && approval?.responded == null) { approval = null; running = false }
                        if (clarify?.id == cid && clarify?.responded == null) { clarify = null; running = false }
                    }
                    "thinking.delta", "reasoning.delta" -> {
                        // M3.3: stream reasoning — tampil SELAMA assistant belum mulai jawab
                        val chunk = ev.payload?.get("text")?.jsonStr() ?: ev.payload?.get("delta")?.jsonStr() ?: ""
                        if (chunk.isNotEmpty()) thinkingText = (thinkingText + chunk).takeLast(500)
                        thinking = true
                    }
                    "message.delta" -> {
                        thinking = false; thinkingText = ""
                        val delta = ev.payload?.get("delta")?.jsonStr() ?: ev.payload?.get("text")?.jsonStr() ?: ""
                        if (delta.isNotEmpty()) {
                            val last = items.lastOrNull()
                            items = if (last is ChatItem.Assistant && !last.done) {
                                items.dropLast(1) + last.copy(text = last.text + delta)
                            } else items + ChatItem.Assistant(delta, done = false, time = Fmt.clock(nowEpoch()))
                            if (!atBottom) hasNew = true
                        }
                    }
                    "message.complete" -> {
                        thinking = false
                        val text = ev.payload?.get("text")?.jsonStr() ?: ""
                        items = items.filterNot { it is ChatItem.Assistant && !it.done }
                        if (text.isNotEmpty()) items = items + ChatItem.Assistant(text, done = true, time = Fmt.clock(nowEpoch()))
                        running = false
                        if (!atBottom) hasNew = true
                    }
                    "message.start" -> { running = true; thinking = false; thinkingText = "" }
                    "message.interim" -> {
                        thinking = false
                        val text = ev.payload?.get("text")?.jsonStr() ?: ""
                        if (text.isNotEmpty()) {
                            items = items.filterNot { it is ChatItem.Assistant && !it.done }
                            items = items + ChatItem.Assistant(text, done = false, time = Fmt.clock(nowEpoch()))
                        }
                    }
                    "tool.start" -> {
                        running = true; thinking = false; thinkingText = ""
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

    // M3.2: scroll logic — initial jump (tanpa animasi) sekali setelah resume;
    // setelah itu autoscroll HANYA saat pinned di bottom / user kirim pesan.
    LaunchedEffect(items.size) {
        if (items.isEmpty()) return@LaunchedEffect
        if (!didInitialScroll) {
            listState.scrollToItem(items.size - 1)
            didInitialScroll = true
        } else if (forceScroll || atBottom) {
            listState.animateScrollToItem(items.size - 1)
        }
        forceScroll = false
    }
    // balik ke bottom manual → badge "baru" reset
    LaunchedEffect(atBottom) { if (atBottom) hasNew = false }
    // M3.3: thinking block muncul tanpa items berubah — scroll manual ke ujung
    LaunchedEffect(thinking, thinkingText) {
        if (thinking && (atBottom || forceScroll)) {
            listState.animateScrollToItem(listState.layoutInfo.totalItemsCount - 1)
        }
    }
    // M4: model aktif buat header meta — model.options layered over session live.
    LaunchedEffect(runtimeId) {
        val c = app.client ?: return@LaunchedEffect
        var waited = 0
        while (c.state.value != ConnState.OPEN && waited < 15000) { delay(250); waited += 250 }
        if (c.state.value != ConnState.OPEN) return@LaunchedEffect
        try {
            val prof = app.profile.value
            val opt = MetaRepo(c).modelOptions(sessionId = runtimeId, profile = prof)
            activeModel = listOfNotNull(
                opt.model.takeIf { it.isNotBlank() },
                opt.provider.takeIf { it.isNotBlank() },
            ).joinToString(" · ")
        } catch (_: Throwable) { /* header meta opsional */ }
    }

    Column(Modifier.fillMaxSize().background(F.Bg).statusBarsPadding()) {
        // Header tipis — bill type. M4: tap judul → sheet Model (read-only,
        // aktif + inventaris — contract gak punya set-model per-session).
        Column(
            Modifier
                .fillMaxWidth()
                .pressClickable { modelSheet = true }
                .padding(horizontal = 20.dp, vertical = 10.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
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
            if (activeModel.isNotBlank()) Text(
                activeModel,
                style = MaterialTheme.typography.labelSmall,
                color = F.Lavender,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
        HorizontalDivider(color = F.Stroke, thickness = 1.dp)

        Box(Modifier.weight(1f)) {
            if (loading) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = F.Vermillion, strokeWidth = 2.dp, modifier = Modifier.size(26.dp))
            } else if (items.isEmpty()) {
                // M3.2: empty state chat baru
                Column(
                    Modifier.fillMaxSize().padding(horizontal = 40.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("Kirim pesan pertama buat mulai", style = MaterialTheme.typography.bodyMedium, color = F.Lavender)
                    Spacer(Modifier.height(6.dp))
                    Text(Fmt.clock(nowEpoch()), style = MaterialTheme.typography.labelSmall, color = F.LavenderDim)
                }
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                items(items) { item -> ChatItemView(item, onLongPress = { copyTarget = it }) }
                // M3.3: thinking indicator — hanya kalau belum ada Assistant streaming
                // (Assistant done dari turn sebelumnya tidak menghalangi)
                val streamingAssistant = (items.lastOrNull() as? ChatItem.Assistant)?.done == false
                if (thinking && !streamingAssistant) {
                    item { ThinkingBlock(thinkingText) }
                }
                // M4: approval card — security boundary. Setelah respond → dim, bukan hilang.
                approval?.let { ap ->
                    item {
                        ApprovalCard(
                            ap,
                            onChoice = { choice ->
                                if (ap.responded != null) return@ApprovalCard
                                approval = ap.copy(responded = choice)
                                scope.launch {
                                    // rute 1: respond frame id-based (request hidup di socket ini)
                                    if (ap.respondRaw(choice)) return@launch
                                    // rute 2 fallback: request.answer — jawab open request
                                    // by frame id (bekerja utk restored + socket ganti).
                                    val c = app.client ?: return@launch
                                    try {
                                        c.call("request.answer", buildJsonObject {
                                            put("id", ap.id)
                                            put("result", buildJsonObject { put("choice", choice) })
                                            val prof = app.profile.value
                                            if (prof.isNotBlank() && prof != "default") put("profile", prof)
                                        })
                                    } catch (_: Throwable) { /* dim tetap — server kirim ulang kalau belum resolved */ }
                                }
                            },
                        )
                    }
                }
                // M4: clarify card — pertanyaan + input teks + Jawab.
                clarify?.let { cq ->
                    item { ClarifyCard(cq, onSubmit = { answer ->
                        if (cq.responded != null) return@ClarifyCard
                        clarify = cq.copy(responded = answer)
                        scope.launch {
                            // rute 1: respond frame id-based (request hidup di socket ini)
                            if (cq.respondRaw(answer)) return@launch
                            // rute 2 fallback: request.answer — sama untuk restored/lost frame
                            val c = app.client ?: return@launch
                            try {
                                c.call("request.answer", buildJsonObject {
                                    put("id", cq.id)
                                    put("result", buildJsonObject {
                                        if (cq.questionId != null) put("answers", buildJsonObject { put(cq.questionId, answer) })
                                        else put("answer", answer)
                                    })
                                    val prof = app.profile.value
                                    if (prof.isNotBlank() && prof != "default") put("profile", prof)
                                })
                            } catch (_: Throwable) {}
                        }
                    }) }
                }
            }
            // M3.2: chip scroll-to-bottom — muncul saat scroll up dari bottom
            if (!atBottom) {
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 12.dp)
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(F.Surface2)
                        .border(1.dp, F.StrokeBright, CircleShape)
                        .clickable {
                            hasNew = false
                            scope.launch { listState.animateScrollToItem(listState.layoutInfo.totalItemsCount - 1) }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("↓", color = F.Cream, fontSize = 18.sp)
                    if (hasNew) {
                        Box(
                            Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = 2.dp, y = 2.dp)
                                .size(9.dp)
                                .clip(CircleShape)
                                .background(F.Error)
                        )
                    }
                }
            }
        }

        // M3.2: long-press salin — bottom sheet kecil, satu aksi
        if (copyTarget != null) {
            ModalBottomSheet(
                onDismissRequest = { copyTarget = null },
                containerColor = F.Surface3,
                shape = Shape.Ticket,
            ) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 28.dp)) {
                    Text(
                        "SALIN TEKS",
                        style = MaterialTheme.typography.labelSmall,
                        color = F.LavenderDim,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                    Text(
                        copyTarget!!.take(120),
                        style = MaterialTheme.typography.bodySmall,
                        color = F.Lavender,
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(bottom = 14.dp),
                    )
                    Text(
                        "Salin teks",
                        style = MaterialTheme.typography.titleMedium,
                        color = F.Cream,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(Shape.S)
                            .clickable {
                                clipboard.setText(AnnotatedString(copyTarget!!))
                                copyTarget = null
                            }
                            .padding(vertical = 14.dp),
                    )
                }
            }
        }

        // M4: sheet Model — aktif (read-only; contract gak punya set-model
        // per-session, hanya session.create) + inventaris provider/model.
        if (modelSheet) {
            ModelSheet(
                app = app,
                sessionId = runtimeId,
                activeModel = activeModel,
                onDismiss = { modelSheet = false },
            )
        }

        TicketComposer(
            value = input, onValueChange = { input = it },
            running = running, connected = connState == ConnState.OPEN,
            onSend = {
                val text = input.trim(); if (text.isEmpty()) return@TicketComposer
                input = ""
                forceScroll = true
                items = items + ChatItem.User(text, pending = true, time = Fmt.clock(nowEpoch()))
                scope.launch {
                    try {
                        val repo = SessionRepo(app.client ?: return@launch, app.profile.value)
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
            onStop = { scope.launch { app.client?.let { SessionRepo(it, app.profile.value).interrupt(runtimeId) } } },
        )
    }
}

private fun kotlinx.serialization.json.JsonElement?.jsonStr(): String =
    (this as? kotlinx.serialization.json.JsonPrimitive)?.takeIf { it.isString }?.content
        ?: (this as? kotlinx.serialization.json.JsonPrimitive)?.content ?: ""

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun ChatItemView(item: ChatItem, onLongPress: (String) -> Unit = {}) {
    when (item) {
        is ChatItem.User -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    item.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = F.CreamDim,
                    modifier = Modifier
                        .widthIn(max = 300.dp)
                        .combinedClickable(onClick = {}, onLongClick = { onLongPress(item.text) })
                        .background(F.UserBubble, Shape.M)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                )
                // M3.2: jam kecil di bawah pesan — gaya WhatsApp
                if (item.time.isNotEmpty()) Text(
                    item.time,
                    style = MaterialTheme.typography.labelSmall,
                    color = F.LavenderDim,
                    fontSize = 9.sp,
                    modifier = Modifier.padding(top = 3.dp, end = 4.dp),
                )
            }
        }
        // animateContentSize HANYA saat done: transisi halus saat caret hilang,
        // tanpa churn re-layout per delta streaming (perf).
        is ChatItem.Assistant -> Column(
            Modifier
                .combinedClickable(onClick = {}, onLongClick = { onLongPress(item.text) })
                .then(if (item.done) Modifier.animateContentSize() else Modifier),
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
            // M3.2: SelectionContainer diganti combinedClickable — SelectionContainer
            // makan long-press buat seleksi teks, sheet "Salin" gak pernah ke-trigger.
            MarkdownText(item.text, style = MaterialTheme.typography.bodyLarge)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!item.done) VermillionCaret()
                if (item.time.isNotEmpty()) Text(
                    item.time,
                    style = MaterialTheme.typography.labelSmall,
                    color = F.LavenderDim,
                    fontSize = 9.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
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

// ── M4: approval / clarify / model sheet ────────────────────────────

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

/**
 * M4: approval card — SECURITY BOUNDARY. Tombol IZINKAN harus disengaja:
 * warna accent + label eksplisit, TOLAK netral, gak ada default-focus.
 * Setelah respond → dim (alpha rendah, tombol hilang) — jejak keputusan tetap ada.
 */
@Composable
private fun ApprovalCard(ap: AskApproval, onChoice: (String) -> Unit) {
    val answered = ap.responded != null
    Column(
        Modifier
            .fillMaxWidth()
            .background(F.Surface1, Shape.M)
            .border(1.dp, if (answered) F.Stroke else F.Warn.copy(alpha = 0.6f), Shape.M)
            .padding(16.dp),
    ) {
        Text(
            if (answered) "PERINTAH — ${if (ap.responded == "once") "DIIZINKAN" else "DITOLAK"}"
            else "MINTA IZIN — ${ap.title.uppercase()}",
            style = MaterialTheme.typography.labelSmall,
            color = if (answered) F.LavenderDim else F.Warn,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            ap.command.ifBlank { "—" },
            style = MaterialTheme.typography.bodySmall,
            color = if (answered) F.LavenderDim else F.CreamDim,
            fontFamily = JetBrainsMono,
            maxLines = 4, overflow = TextOverflow.Ellipsis,
        )
        if (!answered) {
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                // TOLAK dulu (kiri) — Izinkan = aksi berat, harus dicari.
                Button(
                    onClick = { onChoice("deny") },
                    modifier = Modifier.weight(1f).height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = F.Surface3, contentColor = F.Cream),
                    shape = Shape.S,
                ) { Text("Tolak", style = MaterialTheme.typography.labelLarge) }
                Button(
                    onClick = { onChoice("once") },
                    modifier = Modifier.weight(1f).height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = F.Vermillion, contentColor = F.BgDeep),
                    shape = Shape.S,
                ) { Text("Izinkan", style = MaterialTheme.typography.labelLarge) }
            }
        }
    }
}

/** M4: clarify card — pertanyaan + input + Jawab; setelah jawab → dim. */
@Composable
private fun ClarifyCard(cq: AskClarify, onSubmit: (String) -> Unit) {
    var answer by remember(cq.id) { mutableStateOf("") }
    val answered = cq.responded != null
    Column(
        Modifier
            .fillMaxWidth()
            .background(F.Surface1, Shape.M)
            .border(1.dp, if (answered) F.Stroke else F.StrokeBright, Shape.M)
            .padding(16.dp),
    ) {
        Text(
            if (answered) "DRAFT — TERJAWAB" else "KONFIRMASI",
            style = MaterialTheme.typography.labelSmall,
            color = if (answered) F.LavenderDim else F.Lavender,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            cq.question,
            style = MaterialTheme.typography.bodyMedium,
            color = if (answered) F.LavenderDim else F.Cream,
        )
        if (answered) {
            Spacer(Modifier.height(6.dp))
            Text(cq.responded ?: "", style = MaterialTheme.typography.bodySmall, color = F.LavenderDim)
        } else {
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                BasicTextField(
                    value = answer,
                    onValueChange = { answer = it },
                    textStyle = TextStyle(color = F.Cream, fontSize = 15.sp),
                    cursorBrush = SolidColor(F.Vermillion),
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .background(F.BgDeep, Shape.S)
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    decorationBox = { inner ->
                        Box {
                            if (answer.isEmpty()) Text("jawaban…", style = MaterialTheme.typography.bodySmall, color = F.LavenderDim)
                            inner()
                        }
                    },
                )
                Button(
                    onClick = { if (answer.isNotBlank()) onSubmit(answer.trim()) },
                    modifier = Modifier.height(44.dp),
                    enabled = answer.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = F.Vermillion, contentColor = F.BgDeep,
                        disabledContainerColor = F.Surface3, disabledContentColor = F.LavenderDim,
                    ),
                    shape = Shape.S,
                ) { Text("Jawab", style = MaterialTheme.typography.labelLarge) }
            }
        }
    }
}

/** M4: sheet "Model" — read-only: model aktif + inventaris model.options. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun ModelSheet(app: HermesApp, sessionId: String, activeModel: String, onDismiss: () -> Unit) {
    var options by remember { mutableStateOf<MetaRepo.ModelOptions?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        val c = app.client ?: return@LaunchedEffect
        try { options = MetaRepo(c).modelOptions(sessionId = sessionId, profile = app.profile.value) }
        catch (e: Throwable) { error = e.message }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = F.Surface3,
        shape = Shape.Ticket,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 28.dp)) {
            Text("MODEL", style = MaterialTheme.typography.labelSmall, color = F.LavenderDim)
            Spacer(Modifier.height(10.dp))
            Text(
                activeModel.ifBlank { options?.let { listOfNotNull(it.model.ifBlank { null }, it.provider.ifBlank { null }).joinToString(" · ") } ?: "—" },
                style = MaterialTheme.typography.titleMedium, color = F.Cream,
            )
            Text(
                "aktif di sesi ini — penggantian model per-sesi belum ada di contract (hanya via sesi baru)",
                style = MaterialTheme.typography.labelSmall, color = F.LavenderDim,
                modifier = Modifier.padding(top = 4.dp),
            )
            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = F.Stroke, thickness = 1.dp)
            Spacer(Modifier.height(10.dp))
            when {
                options == null && error == null -> Box(Modifier.fillMaxWidth().height(60.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = F.Vermillion, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
                }
                error != null -> Text("Gagal memuat: $error", style = MaterialTheme.typography.bodySmall, color = F.Error)
                else -> LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                    options!!.providers.forEach { p ->
                        item(key = p.slug) {
                            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        p.name.ifBlank { p.slug },
                                        style = MaterialTheme.typography.labelLarge,
                                        color = F.Cream,
                                        modifier = Modifier.weight(1f),
                                    )
                                    if (p.isCurrent == true) Text(
                                        "AKTIF", style = MaterialTheme.typography.labelSmall, color = F.Vermillion,
                                    )
                                }
                                Text(
                                    "${p.models.size} model · ${p.slug}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = F.Lavender,
                                )
                                // daftar model: featured dulu (kalau ada), sisanya
                                val models = (p.featuredModels.orEmpty() + p.models.filter { it !in (p.featuredModels ?: emptyList()) }).distinct()
                                models.take(12).forEach { m -> Text("· $m", style = MaterialTheme.typography.bodySmall, color = F.Lavender, modifier = Modifier.padding(start = 8.dp, top = 2.dp)) }
                                if (models.size > 12) Text("+${models.size - 12} lagi", style = MaterialTheme.typography.labelSmall, color = F.LavenderDim, modifier = Modifier.padding(start = 8.dp, top = 2.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * M3.3: blok "Berpikir…" — tampil saat reasoning.delta/thinking.delta stream
 * dan assistant belum mulai menjawab. Dot accent 6dp pulse alpha, teks reasoning
 * 2 baris max ellipsis (tail 500 char sudah di-cap di event handler).
 */
@Composable
private fun ThinkingBlock(text: String) {
    val alpha by rememberInfiniteTransition().animateFloat(
        initialValue = 1f, targetValue = 0.2f,
        animationSpec = infiniteRepeatable(tween(750), RepeatMode.Reverse),
        label = "thinkDot",
    )
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
        Box(
            Modifier
                .padding(top = 4.dp)
                .size(6.dp)
                .clip(CircleShape)
                .background(F.Vermillion.copy(alpha = alpha))
        )
        Column {
            Text(
                "BERPIKIR…",
                style = MaterialTheme.typography.labelSmall,
                color = F.Lavender,
            )
            if (text.isNotBlank()) Text(
                text,
                style = MaterialTheme.typography.bodySmall,
                color = F.Lavender,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Composer tiket: outline vermillion + notch kiri-kanan, send button = satu-satunya
 * lingkaran vermillion penuh di layar; morph send→stop satu slot.
 * M3.2: multiline auto-grow (max 4 baris) + IME action Send di keyboard.
 */
@Composable
private fun TicketComposer(
    value: String, onValueChange: (String) -> Unit,
    running: Boolean, connected: Boolean,
    onSend: () -> Unit, onStop: () -> Unit,
) {
    // auto-grow: 64dp (1 baris) → 128dp (4 baris), tick tiap 22dp
    val fieldHeight = (64 + minOf(value.count { it == '\n' }, 3) * 22).dp
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .height(fieldHeight)
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
                // M3.2: IME Send — cukup satu baris; multiline di-entry via paste
                // atau Shift+Enter (keyboard yang support). Enter polos = kirim.
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { if (!running && connected && value.isNotBlank()) onSend() }),
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
