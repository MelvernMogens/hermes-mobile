package id.melvern.hermesmobile.ui.chat

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.provider.OpenableColumns
import android.util.Base64
import id.melvern.hermesmobile.HermesApp
import id.melvern.hermesmobile.core.model.Attachment
import id.melvern.hermesmobile.core.model.ChatItem
import id.melvern.hermesmobile.core.model.SessionRow
import id.melvern.hermesmobile.core.model.TranscriptMessage
import id.melvern.hermesmobile.core.repo.MediaRepo
import id.melvern.hermesmobile.core.repo.MetaRepo
import id.melvern.hermesmobile.core.repo.SessionRepo
import id.melvern.hermesmobile.core.repo.TranscriptCache
import id.melvern.hermesmobile.core.rpc.ConnState
import id.melvern.hermesmobile.core.rpc.GatewayInbound
import id.melvern.hermesmobile.core.rpc.RpcException
import id.melvern.hermesmobile.ui.components.Hairline
import id.melvern.hermesmobile.ui.components.OneLine
import id.melvern.hermesmobile.ui.components.ProfileAvatar
import id.melvern.hermesmobile.ui.components.PulsingDot
import id.melvern.hermesmobile.ui.components.QuietIconButton
import id.melvern.hermesmobile.ui.components.QuietSheet
import id.melvern.hermesmobile.ui.components.RelTime
import id.melvern.hermesmobile.ui.components.SheetActionRow
import id.melvern.hermesmobile.ui.components.SkeletonBar
import id.melvern.hermesmobile.ui.components.StatusDot
import id.melvern.hermesmobile.ui.components.shimmerAlpha
import id.melvern.hermesmobile.ui.sessions.SessionActionSheet
import id.melvern.hermesmobile.ui.theme.Dim
import id.melvern.hermesmobile.ui.theme.Ink
import id.melvern.hermesmobile.ui.theme.Motion
import id.melvern.hermesmobile.ui.theme.Radius
import id.melvern.hermesmobile.ui.theme.Type
import id.melvern.hermesmobile.ui.theme.hairline
import id.melvern.hermesmobile.ui.theme.pressClickable
import id.melvern.hermesmobile.ui.theme.rememberReduceMotion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/**
 * M8 Chat — Quiet Mono. Top bar: back · avatar 32 · judul + baris model
 * (tap = model sheet) / "Working…" saat turn jalan · MoreVert (aksi session).
 * User = bubble kanan surface2; assistant = tanpa bubble full width markdown;
 * tool berurutan = grup collapsed; thinking = "Thinking…" → "Thought for Ns".
 * Logika M3–M7 (resume, event stream, approval/clarify, queue, watchdog,
 * read-only derived) dipertahankan apa adanya — pass ini visual.
 */
@Composable
fun ChatScreen(
    app: HermesApp,
    actualStoredId: String,
    preattachedRuntime: String?,
    initialTitle: String?,
    onBack: () -> Unit = {},
    onOpenChat: (String) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    var runtimeId by remember { mutableStateOf(preattachedRuntime ?: actualStoredId) }
    // M5 fix: stored id aktif — bisa ganti pas "GIG baru dengan model ini"
    // (session.create balikin stored id baru; jangan resume session lama).
    var effectiveStoredId by remember { mutableStateOf(actualStoredId) }
    var items by remember { mutableStateOf<List<ChatItem>>(emptyList()) }
    var title by remember { mutableStateOf(initialTitle ?: "") }
    var running by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(preattachedRuntime == null) }
    // M9 (item 6): draft input selamat dari rotate (transcript via TranscriptCache).
    var input by rememberSaveable { mutableStateOf("") }
    // M3.3: thinking indicator — event thinking.delta / reasoning.delta
    var thinking by remember { mutableStateOf(false) }
    var thinkingText by remember { mutableStateOf("") }
    // M8: durasi + isi thinking live → "Thought for 12s" di jawaban yang menyusul.
    var thinkStartMs by remember { mutableStateOf(0L) }
    var thoughtBuf by remember { mutableStateOf("") }
    var pendingThought by remember { mutableStateOf<Pair<String, Int>?>(null) }
    fun endThinking() {
        if (thinkStartMs > 0L) {
            val secs = ((System.currentTimeMillis() - thinkStartMs) / 1000L).toInt().coerceAtLeast(1)
            pendingThought = thoughtBuf to secs
            thinkStartMs = 0L
        }
        thinking = false; thinkingText = ""
    }
    // M4: model aktif — baris meta kecil di header (dari model.options).
    var activeModel by remember { mutableStateOf("") }
    // M4: approval + clarify card — server→client request.
    var approval by remember { mutableStateOf<AskApproval?>(null) }
    var clarify by remember { mutableStateOf<AskClarify?>(null) }
    var modelSheet by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val client = app.client
    val connState by client?.state?.collectAsState() ?: remember { mutableStateOf(ConnState.CLOSED) }
    // M7: mode gateway aktif — dipakai derive readOnly (banner 4090 hanya
    // relevan kalau error terjadi saat gateway mobile).
    val gatewayMode by app.gatewayMode.collectAsState()

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
    var hasNew by remember { mutableStateOf(false) }   // ada pesan baru saat user di atas
    // M8: badge jumlah pesan baru — baseline dicatat saat user meninggalkan bottom
    val messageCount = items.count { it is ChatItem.User || (it is ChatItem.Assistant && it.done) }
    var baselineCount by remember { mutableStateOf(0) }
    var forceScroll by remember { mutableStateOf(false) } // user kirim pesan → selalu scroll
    var didInitialScroll by remember { mutableStateOf(false) }
    // M3.2: long-press salin teks
    var copyTarget by remember { mutableStateOf<String?>(null) }
    val clipboard = LocalClipboardManager.current
    // M6→M7: read-only — HANYA setelah error 4090 NYATA saat gateway mobile
    // (desktop-linked = multi-surface normal, banner tidak boleh muncul
    // preventively). Derived: sessionNotOwned && mode != Desktop.
    var sessionNotOwned by remember { mutableStateOf(false) }
    // M7 watchdog: prompt.submit sukses tapi message.start belum datang →
    // setelah 20s refresh via session.events.since (bukan spinner selamanya).
    // Counter monoton, BUKAN boolean: snapshot diambil SEBELUM submit — event
    // yang datang duluan / reset dari send lain tidak bisa menimpa (race
    // review M7: boolean di-reset setelah submit menimpa flag yang sudah true).
    var turnStartCount by remember { mutableStateOf(0) }
    // M7: derive banner — 4090 NYATA (sessionNotOwned) DAN gateway mobile.
    // Desktop-linked = multi-surface, session bisa dipakai bareng → jangan
    // tampilkan banner read-only preventive.
    val readOnly by remember {
        derivedStateOf {
            sessionNotOwned && gatewayMode !is id.melvern.hermesmobile.core.repo.GatewayDiscovery.Mode.Desktop
        }
    }

    // M5: attach — picker Android, hasil jadi chip di atas composer.
    val context = androidx.compose.ui.platform.LocalContext.current
    var attachment by remember { mutableStateOf<Attachment?>(null) }
    var attaching by remember { mutableStateOf(false) }
    var attachError by remember { mutableStateOf<String?>(null) }
    var attachThumb by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    var attachSheet by remember { mutableStateOf(false) }
    val pickFile = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val c = app.client ?: return@rememberLauncherForActivityResult
        attaching = true; attachError = null
        scope.launch {
            try {
                // M5 fix: balik dari file picker → activity sempat background → WS drop
                // & reconnect (backoff sampai ~15s). Tunggu ConnState.OPEN dulu (deadline
                // 20s) sebelum attach — kalau gak, RPC pasti gagal "not connected".
                val deadline = System.currentTimeMillis() + 20_000
                while (c.state.value != ConnState.OPEN && System.currentTimeMillis() < deadline) {
                    delay(500)
                }
                if (c.state.value != ConnState.OPEN) {
                    throw IllegalStateException("still reconnecting — try again")
                }
                val (name, mime) = withContext(Dispatchers.IO) { readUriMeta(context, uri) }
                val bytes = withContext(Dispatchers.IO) {
                    // M5 fix: guard ukuran — file gede (video dll) bikin OOM/ANR
                    // kalau dibaca full ke memori + base64 (1.33x ukuran file).
                    context.contentResolver.openInputStream(uri)?.use { st ->
                        val size = st.available().toLong().coerceAtLeast(0)
                        val realSize = querySize(context, uri) ?: size
                        if (realSize > 8L * 1024 * 1024) {
                            throw IllegalStateException("file is ${(realSize / 1024 / 1024)}MB — max is 8MB")
                        }
                        st.readBytes()
                    } ?: throw IllegalStateException("can't read file")
                }
                val isImage = mime?.startsWith("image/") == true
                val b64 = withContext(Dispatchers.Default) {
                    Base64.encodeToString(bytes, Base64.NO_WRAP)
                }
                val repo = MetaRepo(c)
                val att = if (isImage) {
                    // image.attach_bytes — gambar auto-queued server utk submit berikutnya
                    val out = repo.attachImageBytes(
                        runtimeId, b64,
                        filename = name ?: "foto.jpg",
                        ext = name?.substringAfterLast('.', "") ?: "",
                        profile = app.profile.value,
                    )
                    if (!out.attached) throw RpcException(-1, "image.attach_bytes: failed")
                    // path balikan = lokasi di Mac → simpan biar bisa dirender ulang
                    Attachment(refText = "", name = name ?: "foto", isImage = true)
                } else {
                    // file.attach data_url — ref_text HARUS masuk prompt
                    val fileMime = mime ?: "application/octet-stream"
                    val out = repo.attachFileDataUrl(
                        runtimeId,
                        dataUrl = "data:$fileMime;base64,$b64",
                        name = name ?: "file",
                        profile = app.profile.value,
                    )
                    Attachment(refText = out.refText, name = out.name, isImage = false)
                }
                attachment = att
                // M8: thumbnail 40dp di chip — decode downsampled (~120px)
                attachThumb = if (isImage) withContext(Dispatchers.Default) {
                    val o = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
                    var sample = 1
                    while (o.outWidth / (sample * 2) >= 120 && o.outHeight / (sample * 2) >= 120) sample *= 2
                    android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, android.graphics.BitmapFactory.Options().apply { inSampleSize = sample })
                        ?.asImageBitmap()
                } else null
            } catch (e: Throwable) {
                attachError = "Couldn't attach: ${e.message}"
            } finally { attaching = false }
        }
    }
    // M5: fetcher gambar buat MarkdownImage (auth /api/media → bitmap)
    val mediaFetch = remember(app.connection) { mediaFetcherFor(app.connection) }
    // M9 (item 3): fetcher video (proxy mobile-media → file cacheDir)
    val videoFetch = remember(app.connection) { videoFetcherFor(app.connection) { context.cacheDir } }
    // M10: save helper butuh connection aktif (unduh via media proxy)
    androidx.compose.runtime.LaunchedEffect(app.connection) {
        id.melvern.hermesmobile.ui.components.MediaFetchSave.connection = app.connection
    }

    fun nowEpoch(): Double = System.currentTimeMillis() / 1000.0

    /**
     * M9 (item 6): sinkron transcript ke cache level-app — dipanggil di titik
     * mutasi items/running (delta, complete, send, tool) supaya back→reopen
     * dan rotate memulai dari state terakhir, bukan reload penuh.
     */
    /** M4/M9: replay server→client request (approval/clarify) yang belum dijawab. */
    fun restoreOpenRequest(req: SessionRepo.OpenRequest) {
        when (req.method) {
            "approval" -> approval = AskApproval(
                id = req.id,
                requestId = req.params?.get("request_id")?.jsonStr(),
                title = req.params?.get("tool_name")?.jsonStr()?.takeIf { it.isNotBlank() } ?: "COMMAND",
                command = req.params?.get("command")?.jsonStr() ?: req.params?.get("description")?.jsonStr() ?: "",
            )
            "clarify" -> {
                val qArr = req.params?.get("questions")?.jsonArray
                clarify = AskClarify(
                    id = req.id,
                    question = req.params?.get("question")?.jsonStr()
                        ?: qArr?.firstOrNull()?.jsonObject?.get("question")?.jsonStr()
                        ?: "Something needs confirmation",
                    questionId = qArr?.firstOrNull()?.jsonObject?.get("qid")?.jsonStr(),
                )
            }
        }
    }

    fun cacheSnapshot() {
        // app.client (bukan val client lama) — instance bisa diganti HermesApp saat reconnect.
        TranscriptCache.snapshot(
            effectiveStoredId, items, runtimeId,
            app.client?.lastSeenSeq(runtimeId) ?: TranscriptCache.CURSOR_UNKNOWN, running,
        )
    }

    fun mapTranscript(msgs: List<TranscriptMessage>): List<ChatItem> = msgs.flatMap { m ->
        when {
            m.isUser -> listOf(ChatItem.User(m.text ?: "", m.rowId, time = RelTime.clock(m.timestamp), at = m.timestamp))
            m.role == "tool" -> listOf(
                ChatItem.Tool(m.name ?: "tool", "done", (m.args ?: m.context ?: "").take(2000))
            )
            else -> {
                val text = m.text ?: ""
                val reasoning = m.reasoning?.takeIf { it.isNotBlank() }
                if (text.isBlank() && reasoning == null) emptyList()
                else listOf(ChatItem.Assistant(text.ifBlank { "…" }, done = true, reasoning = reasoning?.take(4000), time = RelTime.clock(m.timestamp), at = m.timestamp))
            }
        }
    }

    // M8: gagal load transcript (mis. socket flap di tengah resume) → notice + Retry.
    var reloadKey by remember { mutableStateOf(0) }
    var loadFailed by remember { mutableStateOf(false) }
    // M9 (item 6): satu flag yang menahan restore cache SEBELUM transcript
    // server dipakai — mencegah flicker kosong dan cache menimpa hasil server.
    var cacheHydrated by remember { mutableStateOf(false) }
    LaunchedEffect(actualStoredId, preattachedRuntime, reloadKey) {
        loadFailed = false
        if (preattachedRuntime != null) {
            // NEW GIG: session baru kosong — gak ada transcript buat di-resume
            loading = false
            return@LaunchedEffect
        }
        // M9 (item 6): cache dulu — back→reopen / rotate render instan.
        if (!cacheHydrated) {
            TranscriptCache.get(actualStoredId)?.let { e ->
                items = e.items
                runtimeId = e.runtimeId
                running = e.running
                loading = false
            }
            cacheHydrated = true
        }
        val c = app.client ?: return@LaunchedEffect
        var waited = 0
        while (c.state.value != ConnState.OPEN && waited < 25000) { delay(250); waited += 250 }
        if (c.state.value != ConnState.OPEN) {
            if (items.isEmpty()) {
                items = listOf(ChatItem.NoticeLine("Not connected."))
                loadFailed = true
                loading = false
            }
            return@LaunchedEffect
        }
        try {
            val cached = TranscriptCache.get(actualStoredId)
            if (cached != null && cached.runtimeId == runtimeId &&
                cached.cursor != TranscriptCache.CURSOR_UNKNOWN
            ) {
                // DELTA PATH: transcript dipegang cache; ambil hanya event sejak
                // watermark. Kalau ada event / server bilang truncated → cache
                // basi (gap) → fallback full resume (satu kali).
                val replay = c.replaySince(runtimeId, lastSeen = cached.cursor)
                TranscriptCache.setCursor(actualStoredId, client?.lastSeenSeq(runtimeId) ?: cached.cursor)
                // M10b: epoch replay-ring — kalau gateway restart, runtime id + ring baru
                // (count=0 palsu). Epoch beda dari cache = WAJIB full resume, jangan
                // percaya "tidak ada event baru" (bug: chat gak ngikutin pesan terakhir).
                replay?.let { TranscriptCache.setEpoch(actualStoredId, it.epoch) }
                val epochFresh = replay != null && (cached.epoch == 0 || replay.epoch == cached.epoch)
                if (replay != null && replay.count > 0 && items.isNotEmpty()) {
                    // event masuk lewat inbound flow — beri jendela kecil lalu
                    // snapshot; fallback resume penuh kalau ternyata ada gap.
                    delay(600)
                    cacheSnapshot()
                    if ((items.lastOrNull() as? ChatItem.Assistant)?.done != false) loading = false
                } else {
                    if (replay == null || replay.truncated || !epochFresh) {
                        val out = SessionRepo(c, app.profile.value).resume(actualStoredId)
                        runtimeId = out.runtimeId
                        running = out.running
                        items = mapTranscript(out.messages)
                        cacheSnapshot()
                    }
                    loading = false
                }
            } else {
                val out = SessionRepo(c, app.profile.value).resume(actualStoredId)
                runtimeId = out.runtimeId
                running = out.running
                items = mapTranscript(out.messages)
                cacheSnapshot()
                // M4 asli (review M9): open_requests SELALU direstore dari resume
                // penuh — approval pending server-side membuat running=true dan
                // justru di kasus ini card wajib muncul lagi.
                out.openRequests.forEach { req -> restoreOpenRequest(req) }
            }
            // M4: restored server→client request (replay open_requests) —
            // card dimunculkan lagi; jawab via request.answer RPC (frame id
            // dari socket lama sudah mati). M9: diambil via resume lazy
            // (delta path tidak memanggil resume penuh) — HANYA kalau tidak
            // ada turn jalan (lazy attach bisa re-parent runtime aktif).
            // delta path: open_requests via lazy resume — HANYA kalau gak ada
            // turn jalan (lazy attach bisa re-parent runtime aktif).
            if (!running) SessionRepo(c, app.profile.value).resumeOpenRequests(actualStoredId).forEach { req ->
                restoreOpenRequest(req)
            }
        } catch (e: Throwable) {
            items = listOf(ChatItem.NoticeLine("Couldn't load this chat (${e.message})."))
            loadFailed = true
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
                            title = p?.get("tool_name")?.jsonStr()?.takeIf { it.isNotBlank() } ?: "COMMAND",
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
                                ?: "Something needs confirmation",
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
                        if (chunk.isNotEmpty()) {
                            thinkingText = (thinkingText + chunk).takeLast(500)
                            thoughtBuf = (thoughtBuf + chunk).takeLast(4000)
                        }
                        if (thinkStartMs == 0L) thinkStartMs = System.currentTimeMillis()
                        thinking = true
                    }
                    "message.delta" -> {
                        endThinking()
                        val delta = ev.payload?.get("delta")?.jsonStr() ?: ev.payload?.get("text")?.jsonStr() ?: ""
                        if (delta.isNotEmpty()) {
                            val last = items.lastOrNull()
                            items = if (last is ChatItem.Assistant && !last.done) {
                                items.dropLast(1) + last.copy(text = last.text + delta)
                            } else items + ChatItem.Assistant(
                                delta, done = false, time = RelTime.clock(nowEpoch()), at = nowEpoch(),
                                reasoning = pendingThought?.first?.takeIf { it.isNotBlank() }, thoughtSecs = pendingThought?.second,
                            )
                            if (!atBottom) hasNew = true
                        }
                    }
                    "message.complete" -> {
                        endThinking()
                        val text = ev.payload?.get("text")?.jsonStr() ?: ""
                        items = items.filterNot { it is ChatItem.Assistant && !it.done }
                        if (text.isNotEmpty()) items = items + ChatItem.Assistant(
                            text, done = true, time = RelTime.clock(nowEpoch()), at = nowEpoch(),
                            reasoning = pendingThought?.first?.takeIf { it.isNotBlank() }, thoughtSecs = pendingThought?.second,
                        )
                        pendingThought = null; thoughtBuf = ""
                        running = false
                        cacheSnapshot()
                        if (!atBottom) hasNew = true
                    }
                    "message.start" -> {
                        running = true; thinking = false; thinkingText = ""; turnStartCount++
                        thinkStartMs = 0L; thoughtBuf = ""; pendingThought = null
                    }
                    "message.interim" -> {
                        endThinking()
                        val text = ev.payload?.get("text")?.jsonStr() ?: ""
                        if (text.isNotEmpty()) {
                            items = items.filterNot { it is ChatItem.Assistant && !it.done }
                            items = items + ChatItem.Assistant(text, done = false, time = RelTime.clock(nowEpoch()), at = nowEpoch())
                        }
                    }
                    "tool.start" -> {
                        running = true; endThinking()
                        val p = ev.payload
                        val name = p?.get("tool")?.jsonStr()?.ifEmpty { null } ?: p?.get("name")?.jsonStr()?.ifEmpty { null } ?: "tool"
                        // M8: args (ToolStartPayload.args_text/preview/context) → isi code block saat row di-expand
                        val args = listOf("args_text", "preview", "context").firstNotNullOfOrNull { k -> p?.get(k)?.jsonStr()?.takeIf { it.isNotBlank() } }
                        items = items + ChatItem.Tool(name, "run", detail = args?.take(2000), toolId = p?.get("tool_id")?.jsonStr()?.ifEmpty { null })
                    }
                    "tool.complete" -> {
                        val p = ev.payload
                        val name = p?.get("tool")?.jsonStr() ?: p?.get("name")?.jsonStr() ?: ""
                        val tid = p?.get("tool_id")?.jsonStr()?.ifEmpty { null }
                        // non-verbose: output ada di `result` (string / objek {output|content|...}); verbose: result_text
                        val out = p?.get("result_text")?.jsonStr()?.takeIf { it.isNotBlank() }
                            ?: toolResultText(p?.get("result"))
                            ?: p?.get("summary")?.jsonStr()?.takeIf { it.isNotBlank() }
                        // match by tool_id kalau ada; fallback nama (perilaku lama). Hanya SATU row yang di-complete.
                        val idx = items.indexOfFirst {
                            it is ChatItem.Tool && it.status == "run" &&
                                (if (tid != null && it.toolId != null) it.toolId == tid else (name.isEmpty() || it.name == name))
                        }
                        if (idx >= 0) items = items.toMutableList().also { l ->
                            val t = l[idx] as ChatItem.Tool
                            val detail = listOfNotNull(t.detail, out).joinToString("\n\n").ifBlank { null }
                            l[idx] = t.copy(status = "done", detail = detail?.take(4000))
                        }
                        cacheSnapshot()
                    }
                    "session.title" -> {
                        ev.payload?.get("title")?.jsonStr()?.takeIf { it.isNotBlank() }?.let { title = it }
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
    LaunchedEffect(atBottom, messageCount) { if (atBottom) { hasNew = false; baselineCount = messageCount } }
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
            // M8: header cuma nama model (provider ada di model sheet)
            activeModel = opt.model.ifBlank { opt.provider }
        } catch (_: Throwable) { /* header meta opsional */ }
    }


    val headerModel = activeModel
    val profileName by app.profile.collectAsState()
    val reduceMotion = rememberReduceMotion()
    val rows = remember(items) { buildRows(items, { RelTime.dayKey(it) }, { RelTime.dayLabel(it) }) }
    // M8: animasi masuk hanya untuk baris yang muncul SETELAH load awal.
    val seenKeys = remember { mutableSetOf<String>() }
    var seeded by remember { mutableStateOf(false) }
    LaunchedEffect(loading, rows.size) {
        if (!loading && !seeded) { rows.forEach { seenKeys += it.key }; seeded = true }
    }
    val scrolledContent by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0 }
    }
    // scroll-to-bottom muncul hanya kalau user naik > 1 layar
    val farFromBottom by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull() ?: return@derivedStateOf false
            val below = info.totalItemsCount - 1 - last.index
            !atBottom && below >= info.visibleItemsInfo.size.coerceAtLeast(1)
        }
    }
    var menuSheet by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .background(Ink.Bg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        // ── Top bar 56dp ───────────────────────────────────────────────
        Row(
            Modifier.fillMaxWidth().height(Dim.TopBar).padding(end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            QuietIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onClick = onBack)
            ProfileAvatar(app, profileName, Dim.AvatarBar)
            Spacer(Modifier.width(10.dp))
            Column(
                Modifier
                    .weight(1f)
                    .clip(Radius.Chip)
                    .pressClickable { modelSheet = true }
                    .padding(vertical = 4.dp, horizontal = 2.dp),
            ) {
                OneLine(title.ifBlank { "New chat" }, Type.Title)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    when {
                        connState != ConnState.OPEN -> {
                            StatusDot(if (connState == ConnState.CLOSED) Ink.Danger else Ink.Warn)
                            Spacer(Modifier.width(6.dp))
                            Text(if (connState == ConnState.CLOSED) "Offline" else "Reconnecting…", style = Type.Meta, maxLines = 1)
                        }
                        running -> {
                            PulsingDot(Ink.Live)
                            Spacer(Modifier.width(6.dp))
                            Text("Working…", style = Type.Meta, maxLines = 1)
                        }
                        else -> {
                            OneLine(headerModel.ifBlank { "Model" }, Type.Meta, Modifier.weight(1f, fill = false))
                            Icon(Icons.Rounded.ExpandMore, "Change model", tint = Ink.Text2, modifier = Modifier.size(Dim.IconSmall))
                        }
                    }
                }
            }
            QuietIconButton(Icons.Rounded.MoreVert, "Chat options", onClick = { menuSheet = true })
        }
        if (scrolledContent) Hairline() else Spacer(Modifier.height(hairline()))

        // ── Read-only banner (M7 derived) ──────────────────────────────
        if (readOnly) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(Dim.BannerH)
                    .background(Ink.Surface1)
                    .padding(start = Dim.ScreenH, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatusDot(Ink.Warn)
                Spacer(Modifier.width(10.dp))
                Text("Open on desktop — view only", style = Type.Callout, modifier = Modifier.weight(1f), maxLines = 1)
                TextButton(onClick = { sessionNotOwned = false }) {
                    Text("Retry", style = Type.Callout.copy(fontWeight = FontWeight.SemiBold))
                }
            }
        }

        Box(Modifier.weight(1f)) {
            when {
                loading -> ChatSkeleton()
                items.isEmpty() && approval == null && clarify == null -> EmptyChat(app, profileName, headerModel)
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(rows, key = { it.key }) { row ->
                    val animate = seeded && row.key !in seenKeys && !reduceMotion
                    SideEffect { if (seeded) seenKeys += row.key }
                    Box(Modifier.enterOnce(animate)) {
                        when (row) {
                            is ChatRow.Day -> DayChip(row.label)
                            is ChatRow.Tools -> ToolGroup(row.tools)
                            is ChatRow.Thoughts -> ThoughtsRow(row.texts)
                            is ChatRow.Item -> when (val item = row.item) {
                                is ChatItem.User -> UserBubble(item, onLongPress = { copyTarget = it })
                                is ChatItem.Assistant -> AssistantBlock(item, onLongPress = { copyTarget = it }, mediaFetch = mediaFetch, videoFetch = videoFetch)
                                is ChatItem.NoticeLine -> NoticeRow(
                                    item.text,
                                    onRetry = if (loadFailed) ({ loading = true; items = emptyList(); reloadKey++ }) else null,
                                )
                                is ChatItem.Tool -> ToolGroup(listOf(item))
                            }
                        }
                    }
                }
                // M3.3: thinking — hanya kalau belum ada Assistant streaming
                val streamingAssistant = (items.lastOrNull() as? ChatItem.Assistant)?.done == false
                if (thinking && !streamingAssistant) {
                    item(key = "thinking") { ThinkingRow(thinkingText) }
                }
                // M4: approval card — security boundary. Setelah respond → dim, bukan hilang.
                approval?.let { ap ->
                    item(key = "approval-${ap.id}") {
                        ApprovalCard(
                            ap,
                            onChoice = { choice ->
                                if (ap.responded != null) return@ApprovalCard
                                approval = ap.copy(responded = choice)
                                scope.launch {
                                    // rute 1: respond frame id-based (request hidup di socket ini)
                                    if (ap.respondRaw(choice)) return@launch
                                    // rute 2 fallback: request.answer by frame id (restored / socket ganti).
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
                // M4: clarify card
                clarify?.let { cq ->
                    item(key = "clarify-${cq.id}") {
                        ClarifyCard(cq, onSubmit = { answer ->
                            if (cq.responded != null) return@ClarifyCard
                            clarify = cq.copy(responded = answer)
                            scope.launch {
                                if (cq.respondRaw(answer)) return@launch
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
                        })
                    }
                }
            }
            // scroll-to-bottom: 36dp surface2 bulat kanan bawah + badge jumlah pesan baru
            if (farFromBottom) {
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = Dim.ScreenH, bottom = 12.dp),
                ) {
                    Box(
                        Modifier
                            .size(Dim.ScrollFab)
                            .clip(Radius.Full)
                            .background(Ink.Surface2)
                            .border(hairline(), Ink.HairlineStrong, Radius.Full)
                            .pressClickable {
                                hasNew = false
                                scope.launch { listState.animateScrollToItem((listState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0)) }
                            }
                            .semantics { contentDescription = "Scroll to latest" },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.KeyboardArrowDown, null, tint = Ink.Text, modifier = Modifier.size(Dim.Icon))
                    }
                    val newCount = (messageCount - baselineCount).coerceAtLeast(0)
                    if (hasNew && newCount > 0) {
                        Text(
                            if (newCount > 99) "99+" else "$newCount",
                            style = Type.Caption.copy(color = Ink.OnAccent),
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = 6.dp, y = (-6).dp)
                                .clip(Radius.Full)
                                .background(Ink.Accent)
                                .padding(horizontal = 5.dp, vertical = 0.dp),
                        )
                    }
                }
            }
        }

        Composer(
            value = input, onValueChange = { input = it },
            running = running, connected = connState == ConnState.OPEN,
            readOnly = readOnly,
            attachment = attachment,
            attachThumb = attachThumb,
            attaching = attaching,
            attachError = attachError,
            onAttach = { attachSheet = true },
            onRemoveAttachment = { attachment = null; attachThumb = null },
        onSend = {
                val typed = input.trim()
                // file non-gambar WAJIB bawa ref; gambar auto-queued server
                val att = attachment
                val parts = listOfNotNull(
                    att?.takeIf { !it.isImage }?.refText?.takeIf { it.isNotBlank() },
                    typed.takeIf { it.isNotEmpty() },
                )
                // M5: chip foto tanpa teks tetap bisa dikirim — gambar sudah
                // auto-queued server-side; teks "Sent a photo: nama" cuma trigger turn.
                if (parts.isEmpty() && att?.isImage != true) return@Composer
                val text = if (parts.isEmpty()) "Sent a photo: ${att?.name}" else parts.joinToString("\n")
                input = ""
                attachment = null
                attachThumb = null
                forceScroll = true
                items = items + ChatItem.User(
                    if (typed.isEmpty() && att != null) "Sent a photo: ${att.name}" else typed.ifEmpty { "Sent a photo: ${att?.name}" },
                    pending = true, time = RelTime.clock(nowEpoch()), at = nowEpoch(),
                )
                scope.launch {
                    try {
                        val repo = SessionRepo(app.client ?: return@launch, app.profile.value)
                        // M7: snapshot counter SEBELUM submit — kalau server kirim
                        // message.start sebelum response RPC submit balik, event itu
                        // sudah terhitung (tidak dimakan reset).
                        val turnStartSnapshot = turnStartCount
                        val (newRuntime, status) = repo.sendPromptResilient(effectiveStoredId, runtimeId, text)
                        // M7 fix: kondisi normal terbukti lewat submit sukses —
                        // reset fakta 4090 lama (readOnly di-derive → false).
                        sessionNotOwned = false
                        if (newRuntime != runtimeId) runtimeId = newRuntime
                        // M5: status "queued" → server bilang pesan diantrekan/steered —
                        // bubble user tampil "QUEUED" (bukan pending forever).
                        items = items.map {
                            if (it is ChatItem.User && it.pending)
                                it.copy(pending = false, queued = status == SessionRepo.SubmitStatus.QUEUED)
                            else it
                        }
                        if (status == SessionRepo.SubmitStatus.QUEUED) running = true // turn lanjut/drain queued
                        cacheSnapshot()
                        // M7 watchdog: submit sukses tapi event streaming belum pasti
                        // sampai. Kalau message.start (counter naik) gak datang dalam
                        // 20s, refresh transcript via session.events.since (replay
                        // RPC) — bukan spinner "running" tak berujung. Loop juga
                        // berhenti kalau turn tamat (message.complete → running=false)
                        // tanpa start (mis. jawaban kosong/turn direject) — tidak
                        // menunggu penuh 20s sia-sia. (Review M7: versi boolean +
                        // kondisi `running` exit instan saat submit turn baru.)
                        val watchdogRuntime = runtimeId
                        withTimeoutOrNull(20_000) {
                            while (turnStartCount == turnStartSnapshot && running) delay(500)
                        }
                        if (turnStartCount == turnStartSnapshot && runtimeId == watchdogRuntime) {
                            app.client?.replaySince(watchdogRuntime)
                        }
                        cacheSnapshot()
                    } catch (e: Throwable) {
                        items = items.map { if (it is ChatItem.User && it.pending) it.copy(pending = false) else it }
                        if (e is id.melvern.hermesmobile.core.rpc.SessionNotOwnedException) {
                            // M6: read-only yang bener — transcript tetap, banner + composer mati.
                            // M7: simpan FAKTA error; readOnly (banner) di-derive dari
                            // gateway mode — 4090 saat gateway mobile = session benar2
                            // dipegang surface lain, tapi saat desktop-linked itu lease
                            // stale yang sembuh sendiri lewat retry.
                            sessionNotOwned = true
                        } else {
                            val msg = "Couldn't send: ${e.message}"
                            items = items + ChatItem.NoticeLine(msg)
                        }
                        // M5 fix (review): gak nimpa draft baru yang user lagi ketik —
                        // restore cuma kalau input masih kosong.
                        if (input.isBlank()) input = text
                    }
                }
            },
            onStop = { scope.launch { app.client?.let { SessionRepo(it, app.profile.value).interrupt(runtimeId) } } },
        )
    }

    // M3.2: long-press salin pesan
    copyTarget?.let { target ->
        QuietSheet(onDismiss = { copyTarget = null }, title = "Message") {
            Text(
                target.take(200),
                style = Type.Callout.copy(color = Ink.Text2),
                maxLines = 3, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = Dim.ScreenH).padding(bottom = 8.dp),
            )
            SheetActionRow("Copy text", Icons.Rounded.ContentCopy) {
                clipboard.setText(AnnotatedString(target))
                copyTarget = null
            }
        }
    }
    if (attachSheet) {
        AttachSheet(
            onDismiss = { attachSheet = false },
            onPhoto = { pickFile.launch("image/*") },
            onFile = { pickFile.launch("*/*") },
        )
    }
    // Model sheet: live chat → switch this chat's model in place (desktop parity).
    if (modelSheet) {
        ModelSheet(
            app = app,
            sessionId = runtimeId,
            onDismiss = { modelSheet = false },
            onSwitched = { m, deferred ->
                activeModel = m
                if (deferred) items = items + ChatItem.NoticeLine("Switching to $m after this reply")
            },
            // M5 fix (review HIGH#3): stored id session baru WAJIB ikut — draft/attachment direset.
            onNewChat = { runtime, stored ->
                runtimeId = runtime
                effectiveStoredId = stored.takeIf { it.isNotBlank() } ?: actualStoredId
                items = emptyList(); title = ""; activeModel = ""
                input = ""; attachment = null; attachThumb = null; running = false
                cacheSnapshot()
            },
        )
    }
    // M8: MoreVert → aksi session yang sama dengan long-press di Chats.
    if (menuSheet) {
        SessionActionSheet(
            app = app,
            row = SessionRow(id = effectiveStoredId, title = title.ifBlank { null }),
            onDone = { menuSheet = false },
            onDismiss = { menuSheet = false },
            onOpenBranch = { rt, stored -> menuSheet = false; onOpenChat("$stored|$rt") },
            onDeleted = { menuSheet = false; onBack() },
        )
    }
}

/** Animasi masuk pesan baru: fade + translateY 8dp→0, 180ms, sekali. */
private fun Modifier.enterOnce(animate: Boolean): Modifier = composed {
    if (!animate) return@composed this
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(Motion.MessageInMs, easing = Motion.EmphasizedDecelerate)) }
    val rise = with(LocalDensity.current) { Motion.MessageRise.toPx() }
    graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * rise
    }
}

/** Empty chat: avatar 56 + "Chat with {profile}" + model line, tengah vertikal. */
@Composable
private fun EmptyChat(app: HermesApp, profile: String, model: String) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ProfileAvatar(app, profile, Dim.AvatarEmpty)
        Spacer(Modifier.height(16.dp))
        Text("Chat with $profile", style = Type.Title)
        if (model.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(model, style = Type.Meta)
        }
        Spacer(Modifier.height(48.dp))
    }
}

/** Skeleton transcript: 3 blok (bubble kanan, 2 paragraf kiri). */
@Composable
private fun ChatSkeleton() {
    val a = shimmerAlpha()
    Column(Modifier.fillMaxSize().padding(horizontal = Dim.ScreenH, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) { SkeletonBar(180.dp, 40.dp, a) }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SkeletonBar(null, 14.dp, a); SkeletonBar(null, 14.dp, a); SkeletonBar(220.dp, 14.dp, a)
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SkeletonBar(null, 14.dp, a); SkeletonBar(260.dp, 14.dp, a)
        }
    }
}

/** M5: fetcher gambar (path di Mac → bitmap) — null connection = selalu null (chip fallback). */
private fun mediaFetcherFor(conn: id.melvern.hermesmobile.core.store.ConnectionSettings?): suspend (String) -> androidx.compose.ui.graphics.ImageBitmap? =
    if (conn == null) { _ -> null } else { path -> MediaRepo(conn).fetchImage(path) }

/** M9 (item 3): fetcher video (path di Mac → file cacheDir) — null connection = chip fallback. */
private fun videoFetcherFor(conn: id.melvern.hermesmobile.core.store.ConnectionSettings?, cacheDir: () -> java.io.File?): suspend (String) -> java.io.File? =
    if (conn == null) { _ -> null } else { path -> cacheDir()?.let { MediaRepo(conn).fetchVideo(path, it) } }

/** M8: teks output tool dari ToolCompletePayload.result — string apa adanya; objek → field output umum, fallback JSON. */
internal fun toolResultText(r: kotlinx.serialization.json.JsonElement?): String? {
    val s = when (r) {
        null, is kotlinx.serialization.json.JsonNull -> null
        is kotlinx.serialization.json.JsonPrimitive -> r.content
        is kotlinx.serialization.json.JsonObject -> listOf("output", "content", "stdout", "text", "result", "error")
            .firstNotNullOfOrNull { k -> (r[k] as? kotlinx.serialization.json.JsonPrimitive)?.content?.takeIf { it.isNotBlank() } }
            ?: r.toString()
        else -> r.toString()
    }
    return s?.trim()?.takeIf { it.isNotEmpty() }
}

private fun kotlinx.serialization.json.JsonElement?.jsonStr(): String =
    (this as? kotlinx.serialization.json.JsonPrimitive)?.takeIf { it.isString }?.content
        ?: (this as? kotlinx.serialization.json.JsonPrimitive)?.content ?: ""


/** M5 fix: ukuran file dari OpenableColumns.SIZE — null kalau provider gak kasih. */
private fun querySize(context: android.content.Context, uri: android.net.Uri): Long? = try {
    context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.SIZE), null, null, null)?.use { cur ->
        if (cur.moveToFirst()) {
            val idx = cur.getColumnIndex(android.provider.OpenableColumns.SIZE)
            if (idx >= 0 && !cur.isNull(idx)) cur.getLong(idx) else null
        } else null
    }
} catch (_: Throwable) { null }

/** M5: nama + mime dari content resolver (OpenableColumns; fallback dari ekstensi). */
private fun readUriMeta(context: android.content.Context, uri: android.net.Uri): Pair<String?, String?> {
    var name: String? = null
    var mime: String? = context.contentResolver.getType(uri)
    try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cur ->
            if (cur.moveToFirst()) {
                val idx = cur.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) name = cur.getString(idx)
            }
        }
    } catch (_: Throwable) {}
    if (name == null) name = uri.lastPathSegment
    if (mime == null) {
        val ext = name?.substringAfterLast('.', "")?.lowercase()
        mime = when (ext) {
            "png" -> "image/png"; "jpg", "jpeg" -> "image/jpeg"; "webp" -> "image/webp"
            "gif" -> "image/gif"; "txt" -> "text/plain"; "md" -> "text/markdown"
            "json" -> "application/json"; "pdf" -> "application/pdf"
            else -> "application/octet-stream"
        }
    }
    return name to mime
}
