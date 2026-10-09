package id.melvern.hermesmobile.ui.chat

import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import id.melvern.hermesmobile.ui.components.dismissOnTap
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Share
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Reply
import androidx.compose.material.icons.rounded.MoreVert
import id.melvern.hermesmobile.ui.components.Pretty
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.shadow
import androidx.compose.material.icons.rounded.ArrowOutward
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.outlined.Folder
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
import androidx.compose.ui.unit.Dp
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
import id.melvern.hermesmobile.ui.layout.WinSize
import id.melvern.hermesmobile.ui.layout.isExpanded
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

/** v26: bubble "Send after" yang belum jalan selalu paling bawah — output turn yang sedang
 *  berjalan disisipkan DI ATAS-nya, bukan di bawahnya. */
internal fun splitQueuedTail(items: List<ChatItem>): Pair<List<ChatItem>, List<ChatItem>> {
    var i = items.size
    while (i > 0 && (items[i - 1] as? ChatItem.User)?.let { it.queued && it.rowId == null } == true) i--
    return items.subList(0, i) to items.subList(i, items.size)
}
internal fun liveAppend(items: List<ChatItem>, item: ChatItem): List<ChatItem> {
    val (head, tail) = splitQueuedTail(items)
    return head + item + tail
}

/** v26: batas lampiran per pesan (share / picker). */
private const val MAX_ATTACH = 10

@Composable
fun ChatScreen(
    app: HermesApp,
    actualStoredId: String,
    preattachedRuntime: String?,
    initialTitle: String?,
    onBack: () -> Unit = {},
    onOpenChat: (String) -> Unit = {},
    /** M13: buka ArtifactsScreen (route terpisah, back = balik chat) — bawa live title. */
    onOpenArtifacts: (String) -> Unit = {},
    /** v27: profile pemilik chat (mis. chat tugas bot). null = profile aktif app. */
    profileOverride: String? = null,
) {
    val scope = rememberCoroutineScope()
    val activeProfile by app.profile.collectAsState()
    val chatProfile = profileOverride ?: activeProfile
    var runtimeId by remember { mutableStateOf(preattachedRuntime ?: actualStoredId) }
    // M5 fix: stored id aktif — bisa ganti pas "GIG baru dengan model ini"
    // (session.create balikin stored id baru; jangan resume session lama).
    var effectiveStoredId by remember { mutableStateOf(actualStoredId) }
    // M14: chat ini "yang dibuka di layar" — notif utk session ini di-mute.
    DisposableEffect(effectiveStoredId) {
        app.openChatStoredId = effectiveStoredId
        id.melvern.hermesmobile.core.repo.UnreadStore.markSeen(app, effectiveStoredId)
        onDispose {
            id.melvern.hermesmobile.core.repo.UnreadStore.markSeen(app, effectiveStoredId)
            if (app.openChatStoredId == effectiveStoredId) app.openChatStoredId = null
        }
    }
    var items by remember { mutableStateOf<List<ChatItem>>(emptyList()) }
    var title by remember { mutableStateOf(initialTitle ?: "") }
    var running by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(preattachedRuntime == null) }
    // v28 E: yang tampil baru tail HTTP (resume belum mendarat) — antrean offline jangan dikirim dulu.
    var tailOnly by remember { mutableStateOf(false) }
    // M9 (item 6): draft input selamat dari rotate (transcript via TranscriptCache).
    var input by rememberSaveable { mutableStateOf(id.melvern.hermesmobile.core.repo.DraftStore.get(app, actualStoredId)) }
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
    // v23: task board + subagent monitor
    var todos by remember { mutableStateOf<List<id.melvern.hermesmobile.core.repo.AgentWorkRepo.Todo>>(emptyList()) }
    var subagents by remember { mutableStateOf<List<id.melvern.hermesmobile.core.repo.AgentWorkRepo.Subagent>>(emptyList()) }
    var workSheet by remember { mutableStateOf(false) }
    var subPollKick by remember { mutableStateOf(0) }
    var clarify by remember { mutableStateOf<AskClarify?>(null) }
    // agent berhenti nunggu jawaban (clarify/approval belum dijawab) → header "Waiting for you"
    val waitingOnUser = (clarify?.responded == null && clarify != null) || (approval?.responded == null && approval != null)
    var modelSheet by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    var selectionActive by remember { mutableStateOf(false) }
    androidx.activity.compose.BackHandler(enabled = selectionActive) { focusManager.clearFocus(force = true) }
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
    // v24: edit & resend (user message), diff viewer
    var editTarget by remember { mutableStateOf<ChatItem.User?>(null) }
    var composerFocus by remember { mutableStateOf(0) }
    var userActionTarget by remember { mutableStateOf<ChatItem.User?>(null) }
    var diffOpen by remember { mutableStateOf(false) }
    // v25: prompt favorit + antrean offline
    var favorites by remember { mutableStateOf(id.melvern.hermesmobile.core.repo.PromptStore.favorites(app)) }
    var offlineQueued by remember { mutableStateOf(id.melvern.hermesmobile.core.repo.PromptStore.queue(app).filter { it.storedId == actualStoredId }) }
    // v26: draft per chat — disimpan (debounce) + saat keluar chat; mode edit tidak menimpa draft.
    val latestInput by rememberUpdatedState(input)
    val latestEditing by rememberUpdatedState(editTarget != null)
    LaunchedEffect(input, effectiveStoredId) {
        if (editTarget != null) return@LaunchedEffect
        delay(400)
        id.melvern.hermesmobile.core.repo.DraftStore.put(app, effectiveStoredId, input)
    }
    var screenDisposed by remember { mutableStateOf(false) }
    DisposableEffect(Unit) { onDispose { screenDisposed = true } }
    DisposableEffect(effectiveStoredId) {
        // review P2-4: id ditangkap saat effect dibuat (saat dispose state sudah id baru)
        val sid = effectiveStoredId
        onDispose { if (!latestEditing) id.melvern.hermesmobile.core.repo.DraftStore.put(app, sid, latestInput) }
    }
    // M11: reply — teks pesan yang di-quote, tampil sebagai chip di atas composer.
    var replyQuote by remember { mutableStateOf<String?>(null) }
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
    // v26: banyak lampiran sekaligus (share multi-file / pilih beberapa dari galeri)
    var attachments by remember { mutableStateOf<List<Attachment>>(emptyList()) }
    var attachThumbs by remember { mutableStateOf<Map<Attachment, androidx.compose.ui.graphics.ImageBitmap>>(emptyMap()) }
    var attachingCount by remember { mutableStateOf(0) }
    val attaching = attachingCount > 0
    val attachUploadLock = remember { kotlinx.coroutines.sync.Mutex() }
    var attachError by remember { mutableStateOf<String?>(null) }
    var attachSheet by remember { mutableStateOf(false) }
    // Voice input (mic di composer saat field kosong)
    val voice = rememberVoiceInput()
    val micPerm = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { ok -> if (ok) voice.start(input) { input = it } else voice.error = "Microphone permission needed" }
    /** Upload satu URI (foto / video / file) ke session → chip di composer. */
    fun attachUri(uri: android.net.Uri) {
        val c = app.client ?: return
        attachingCount++; attachError = null
        scope.launch {
            // upload satu-satu (urutan stabil, server image counter tidak balapan)
            attachUploadLock.lock()
            try {
                // balik dari picker → activity sempat background → WS bisa reconnect. Tunggu OPEN.
                val deadline = System.currentTimeMillis() + 20_000
                while (c.state.value != ConnState.OPEN && System.currentTimeMillis() < deadline) delay(500)
                if (c.state.value != ConnState.OPEN) throw IllegalStateException("still reconnecting — try again")
                val (name, mime) = withContext(Dispatchers.IO) { readUriMeta(context, uri) }
                val isImage = mime?.startsWith("image/") == true
                val isVideo = mime?.startsWith("video/") == true
                val bytes = withContext(Dispatchers.IO) {
                    val realSize = querySize(context, uri)
                    val cap = 25L * 1024 * 1024 // = batas server image.attach_bytes
                    if (realSize != null && realSize > cap) {
                        throw IllegalStateException("file is ${realSize / 1024 / 1024}MB — max is 25MB")
                    }
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: throw IllegalStateException("can't read file")
                }
                if (bytes.size > 25 * 1024 * 1024) throw IllegalStateException("file is ${bytes.size / 1024 / 1024}MB — max is 25MB")
                val b64 = withContext(Dispatchers.Default) { Base64.encodeToString(bytes, Base64.NO_WRAP) }
                val repo = MetaRepo(c)
                val att = if (isImage) {
                    // image.attach_bytes — gambar auto-queued (vision) untuk submit berikutnya
                    val out = repo.attachImageBytes(
                        runtimeId, b64,
                        filename = name ?: "photo.jpg",
                        ext = name?.substringAfterLast('.', "") ?: "",
                        profile = chatProfile,
                    )
                    if (!out.attached) throw RpcException(-1, "image.attach_bytes: failed")
                    Attachment(refText = "", name = name ?: "photo", isImage = true, path = out.path)
                } else {
                    // file.attach data_url — ref_text masuk prompt (video = stub path, agent tetap tahu filenya)
                    val out = repo.attachFileDataUrl(
                        runtimeId,
                        dataUrl = "data:${mime ?: "application/octet-stream"};base64,$b64",
                        name = name ?: if (isVideo) "video.mp4" else "file",
                        profile = chatProfile,
                    )
                    Attachment(refText = out.refText, name = out.name, isImage = false, path = out.path, isVideo = isVideo)
                }
                val thumb = withContext(Dispatchers.Default) {
                    when {
                        isImage -> {
                            val o = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                            android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
                            var sample = 1
                            while (o.outWidth / (sample * 2) >= 120 && o.outHeight / (sample * 2) >= 120) sample *= 2
                            android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size,
                                android.graphics.BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
                        }
                        isVideo -> try {
                            android.media.MediaMetadataRetriever().run {
                                setDataSource(context, uri)
                                val f = getFrameAtTime(0)
                                release()
                                f?.let { android.graphics.Bitmap.createScaledBitmap(it, 160, (160f * it.height / it.width).toInt().coerceAtLeast(1), true) }
                                    ?.asImageBitmap()
                            }
                        } catch (_: Throwable) { null }
                        else -> null
                    }
                }
                attachments = attachments + att
                if (thumb != null) attachThumbs = attachThumbs + (att to thumb)
            } catch (e: Throwable) {
                attachError = "Couldn't attach: ${e.message}"
            } finally { attachingCount--; attachUploadLock.unlock() }
        }
    }
    // v23: subagent monitor — poll saat turn jalan (atau ada event subagent), berhenti saat idle & semua selesai.
    LaunchedEffect(runtimeId, running, subPollKick) {
        if (runtimeId.isBlank()) return@LaunchedEffect
        val c = app.client ?: return@LaunchedEffect
        val repo = id.melvern.hermesmobile.core.repo.AgentWorkRepo(c, chatProfile)
        var failures = 0
        while (true) {
            try { subagents = repo.subagents(runtimeId); failures = 0 }
            catch (e: Throwable) {
                // 4001/4010 = session bukan milik koneksi ini (mis. dibuka desktop) — berhenti, jangan spam
                if ((e as? id.melvern.hermesmobile.core.rpc.RpcException)?.code in setOf(4001, 4010) || ++failures >= 3) break
            }
            if (!running && subagents.none { it.running }) break
            delay(4000)
        }
    }

    // v25: kirim antrean offline begitu koneksi OPEN + runtime siap (urut, satu per satu)
    LaunchedEffect(connState, runtimeId, offlineQueued.size, loading, tailOnly) {
        if (connState != ConnState.OPEN || runtimeId.isBlank() || offlineQueued.isEmpty() || loading || tailOnly) return@LaunchedEffect
        val q = offlineQueued.first()
        // Review fix: keluarkan dari antrean SEBELUM kirim (effect bisa restart di tengah kirim →
        // jangan kirim dobel); gagal → kembalikan ke antrean.
        id.melvern.hermesmobile.core.repo.PromptStore.removeQueued(app, q)
        try {
            val repo = SessionRepo(app.client ?: throw IllegalStateException("no client"), chatProfile)
            val nowE = System.currentTimeMillis() / 1000.0
            items = items + ChatItem.User(q.text, time = RelTime.clock(nowE), at = nowE)
            forceScroll = true
            launch { delay(150); runCatching { listState.animateScrollToItem((listState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0)) } }
            val (newRt, status) = repo.sendPromptResilient(effectiveStoredId, runtimeId, q.text)
            if (newRt != runtimeId) runtimeId = newRt
            if (status == SessionRepo.SubmitStatus.QUEUED) items = items.map { if (it is ChatItem.User && it.text == q.text) it.copy(queued = true) else it }
            running = true
        } catch (e: kotlinx.coroutines.CancellationException) {
            id.melvern.hermesmobile.core.repo.PromptStore.requeueFront(app, q); throw e
        } catch (_: Throwable) {
            items = items.filterNot { it is ChatItem.User && it.text == q.text && it.rowId == null && !it.queued }
            id.melvern.hermesmobile.core.repo.PromptStore.requeueFront(app, q)
            delay(5000)
        }
        offlineQueued = id.melvern.hermesmobile.core.repo.PromptStore.queue(app).filter { it.storedId == effectiveStoredId }
    }

    // Slash: saran saat mengetik token /xxx (debounce 150ms, sama sumber dengan desktop).
    var slashSuggestions by remember { mutableStateOf<List<id.melvern.hermesmobile.core.repo.SlashRepo.Suggestion>>(emptyList()) }
    LaunchedEffect(input) {
        if (!id.melvern.hermesmobile.core.repo.SlashRepo.wantsSuggestions(input)) { slashSuggestions = emptyList(); return@LaunchedEffect }
        kotlinx.coroutines.delay(150)
        slashSuggestions = try {
            id.melvern.hermesmobile.core.repo.SlashRepo(app.client ?: return@LaunchedEffect).suggest(input).take(40)
        } catch (_: Throwable) { emptyList() }
    }

    // v26.3: status outbox berubah (kirim selesai/gagal) → sinkron bubble
    val outboxVersion by id.melvern.hermesmobile.core.repo.Outbox.version.collectAsState()
    LaunchedEffect(outboxVersion, effectiveStoredId) {
        if (!loading) items = id.melvern.hermesmobile.core.repo.Outbox.reconcile(items, id.melvern.hermesmobile.core.repo.Outbox.forChat(app, effectiveStoredId), id.melvern.hermesmobile.core.repo.Outbox::isSending, { RelTime.clock(it) }, id.melvern.hermesmobile.core.repo.Outbox::isFailed)
    }
    // Share to Hermes: chat ini dipilih sebagai tujuan → teks ke draft, file pertama di-attach.
    val shareTarget by id.melvern.hermesmobile.core.share.ShareInbox.target.collectAsState()
    val sharePending by id.melvern.hermesmobile.core.share.ShareInbox.pending.collectAsState()
    LaunchedEffect(effectiveStoredId, runtimeId, shareTarget, sharePending?.ready) {
        if (runtimeId.isBlank()) return@LaunchedEffect
        val sh = id.melvern.hermesmobile.core.share.ShareInbox.take(effectiveStoredId) ?: return@LaunchedEffect
        sh.text?.let { t -> input = listOf(input.trim(), t).filter { it.isNotEmpty() }.joinToString("\n") }
        sh.uris.take(MAX_ATTACH).forEach { attachUri(it) }
        if (sh.uris.size > MAX_ATTACH) attachError = "Only $MAX_ATTACH files per message — attached the first $MAX_ATTACH"
    }
    val pickFile = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetMultipleContents()
    ) { uris -> uris.take(MAX_ATTACH - attachments.size).forEach { attachUri(it) } }
    // Galeri: foto + video (photo picker sistem, tanpa izin storage)
    val pickMedia = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.PickMultipleVisualMedia(MAX_ATTACH)
    ) { uris -> uris.take(MAX_ATTACH - attachments.size).forEach { attachUri(it) } }
    // Kamera: hasil ditulis ke cache via FileProvider
    var cameraUri by remember { mutableStateOf<android.net.Uri?>(null) }
    val takePhoto = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.TakePicture()
    ) { ok -> cameraUri?.let { if (ok) attachUri(it) } }
    fun launchCamera() {
        try {
            val dir = java.io.File(context.cacheDir, "camera").apply { mkdirs() }
            val f = java.io.File(dir, "photo_${System.currentTimeMillis()}.jpg")
            val u = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.files", f)
            cameraUri = u
            takePhoto.launch(u)
        } catch (e: Throwable) { attachError = "Camera unavailable: ${e.message}" }
    }
    // Lokasi: izin → lokasi terakhir/terkini → kirim link maps
    var locating by remember { mutableStateOf(false) }
    fun sendLocation() {
        locating = true
        scope.launch {
            val loc = withContext(Dispatchers.IO) { currentLocation(context) }
            locating = false
            if (loc == null) { attachError = "Couldn't get your location — is location on?"; return@launch }
            val (lat, lng) = loc
            val link = "https://maps.google.com/?q=%.6f,%.6f".format(java.util.Locale.US, lat, lng)
            input = (input.trim() + (if (input.isBlank()) "" else "\n") + "My location: $link").trim()
        }
    }
    val locationPerm = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { granted -> if (granted.values.any { it }) sendLocation() else attachError = "Location permission denied" }
    // M5: fetcher gambar buat MarkdownImage (auth /api/media → bitmap)
    val mediaFetch = remember(app.connection) { mediaFetcherFor(app.connection) }
    // M9 (item 3): fetcher video (proxy mobile-media → file cacheDir)
    val videoFetch = remember(app.connection) { videoFetcherFor(app.connection) { context.cacheDir } }
    // M16: MediaFetchSave.connection sekarang di-set GLOBAL di HermesApp
    // (onCreate + buildClient + disconnect) — bukan lagi per-ChatScreen,
    // supaya Artifacts dibuka duluan setelah fresh start tetap bisa save.

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
            "clarify" -> clarify = AskClarify.parse(req.id, req.params)
        }
    }

    fun cacheSnapshot() {
        id.melvern.hermesmobile.core.store.OfflineStore.saveChat(app, effectiveStoredId, items)
        // app.client (bukan val client lama) — instance bisa diganti HermesApp saat reconnect.
        TranscriptCache.snapshot(
            effectiveStoredId, items, runtimeId,
            app.client?.lastSeenSeq(runtimeId) ?: TranscriptCache.CURSOR_UNKNOWN, running,
        )
    }

    /**
     * Bubble user yang dikirim dari HP (optimistic) belum punya row_id DB — tanpa itu
     * "Edit & resend" menunjuk row lama / gagal. Setelah turn selesai, ambil row_id
     * dari Mac (user tail) dan cocokkan per teks (urut dari belakang).
     */
    fun backfillRowIds() {
        if (items.none { it is ChatItem.User && it.rowId == null }) return
        val conn = app.connection ?: return
        val sid = effectiveStoredId.takeIf { it.isNotBlank() } ?: return
        scope.launch {
            val since = (items.filterIsInstance<ChatItem.User>().filter { it.rowId == null }.mapNotNull { it.at }.minOrNull() ?: return@launch) - 300
            val tail = id.melvern.hermesmobile.core.repo.InsightsRepo(conn).userTail(chatProfile, sid, since) ?: return@launch
            items = id.melvern.hermesmobile.core.repo.InsightsRepo.backfillRowIds(items, tail)
            cacheSnapshot()
        }
    }


    /**
     * Turn baru mulai (message.start) — mungkin dari surface lain. Ambil prompt
     * user sejak pesan user terakhir yang kita tahu (minus 2 menit buat clock skew)
     * dan sisipkan yang belum ada. Retry singkat: row prompt kadang baru tercatat
     * sesaat setelah turn mulai.
     */
    fun pullForeignPrompts() {
        val conn = app.connection ?: return
        val sid = effectiveStoredId.takeIf { it.isNotBlank() } ?: return
        val lastKnown = items.filterIsInstance<ChatItem.User>().mapNotNull { it.at }.maxOrNull()
        val after = (lastKnown ?: (nowEpoch() - 600)) - 120
        scope.launch {
            for (attempt in 0..2) {
                if (attempt > 0) delay(1500L * attempt)
                val tail = id.melvern.hermesmobile.core.repo.InsightsRepo(conn)
                    .userTail(chatProfile, sid, after) ?: continue
                // v26: prompt "Send after" sudah jalan (ada row di DB) → bubble normal, lepas dari store
                id.melvern.hermesmobile.core.repo.PromptStore.settleServerQueued(app, sid, tail.map { it.text to it.at })
                val tailTexts = tail.map { id.melvern.hermesmobile.core.model.SteerText.unwrap(it.text).first.trim() }
                fun ran(u: ChatItem.User) = u.queued && tailTexts.any { t -> id.melvern.hermesmobile.core.model.UserMatch.same(u.text, t) }
                if (items.any { it is ChatItem.User && ran(it) }) {
                    items = items.map { if (it is ChatItem.User && ran(it)) it.copy(queued = false) else it }
                }
                val merged = id.melvern.hermesmobile.core.repo.InsightsRepo.mergeUserTail(items, tail) { RelTime.clock(it) }
                if (merged !== items) {
                    items = merged
                    forceScroll = atBottom
                    cacheSnapshot()
                    break
                }
            }
        }
    }


    /**
     * v26.3: kirim lewat Outbox di scope APLIKASI — keluar chat tidak membatalkan kirim,
     * gagal tidak menghilangkan pesan (bubble "Not sent · Tap to retry").
     */
    fun deliver(o: id.melvern.hermesmobile.core.repo.Outbox.Out) {
        // review P1-1: satu kiriman per entri — double-tap / race reconcile tidak boleh submit dua kali
        if (id.melvern.hermesmobile.core.repo.Outbox.isSending(o.id)) return
        id.melvern.hermesmobile.core.repo.Outbox.markRetried(o.id)
        id.melvern.hermesmobile.core.repo.Outbox.markSending(o.id)
        // retry: pindahkan bubble ke bawah (urutan = urutan server menerima)
        val idx = items.indexOfFirst { it is ChatItem.User && it.outboxId == o.id }
        if (idx >= 0) {
            val u = (items[idx] as ChatItem.User).copy(pending = true, failed = false)
            items = liveAppend(items.filterIndexed { i, _ -> i != idx }, u)
        }
        val turnStartSnapshot = turnStartCount
        app.appScope.launch {
            try {
                val c = app.client ?: throw IllegalStateException("not connected")
                // socket bisa sedang reconnect (balik dari background) — tunggu OPEN sebentar
                val deadline = System.currentTimeMillis() + 15_000
                while (c.state.value != ConnState.OPEN && System.currentTimeMillis() < deadline) delay(300)
                val repo = SessionRepo(c, chatProfile)
                var rt = runtimeId
                val status = if (o.queued) {
                    repo.submitQueued(rt, o.text); SessionRepo.SubmitStatus.QUEUED
                } else {
                    val (newRt, st) = repo.sendPromptResilient(o.storedId, rt, o.text); rt = newRt; st
                }
                id.melvern.hermesmobile.core.repo.Outbox.remove(app, o.id)
                if (o.queued) id.melvern.hermesmobile.core.repo.PromptStore.addServerQueued(app, o.storedId, o.display)
                withContext(Dispatchers.Main) {
                    // review P1-3: layar sudah ditutup → jangan tulis state/cache basi (layar baru rekonsiliasi via Outbox.version)
                    if (screenDisposed) return@withContext
                    sessionNotOwned = false
                    if (rt != runtimeId) runtimeId = rt
                    items = items.map {
                        if (it is ChatItem.User && it.outboxId == o.id)
                            it.copy(pending = false, failed = false, outboxId = null, queued = status == SessionRepo.SubmitStatus.QUEUED)
                        else it
                    }
                    if (status == SessionRepo.SubmitStatus.QUEUED) running = true
                    cacheSnapshot()
                }
                if (!o.queued && !screenDisposed) {
                    val watchdogRuntime = rt
                    withTimeoutOrNull(20_000) { while (turnStartCount == turnStartSnapshot && running) delay(500) }
                    if (turnStartCount == turnStartSnapshot) app.client?.replaySince(watchdogRuntime)
                }
            } catch (e: Throwable) {
                withContext(Dispatchers.Main + kotlinx.coroutines.NonCancellable) {
                    if (screenDisposed) return@withContext
                    if (e is id.melvern.hermesmobile.core.rpc.SessionNotOwnedException) sessionNotOwned = true
                    items = items.map { if (it is ChatItem.User && it.outboxId == o.id) it.copy(pending = false, failed = true) else it }
                    cacheSnapshot()
                }
            } finally {
                id.melvern.hermesmobile.core.repo.Outbox.markDone(o.id)
            }
        }
    }

    /** v26.3: tambahkan bubble outbox yang belum tampil (setelah keluar-masuk / app di-kill). */
    fun withOutbox(list: List<ChatItem>, serverFresh: Boolean = false): List<ChatItem> {
        // review P2-5: hanya row yang benar-benar dari server (rowId) — bukan bubble "Queued" sintetis
        id.melvern.hermesmobile.core.repo.Outbox.settle(app, effectiveStoredId,
            list.filterIsInstance<ChatItem.User>().filter { it.outboxId == null && it.rowId != null && !it.queued }.map { it.text to it.at })
        if (serverFresh) id.melvern.hermesmobile.core.repo.Outbox.markCheckedMissing(id.melvern.hermesmobile.core.repo.Outbox.forChat(app, effectiveStoredId).filterNot { id.melvern.hermesmobile.core.repo.Outbox.isSending(it.id) }.map { it.id })
        return id.melvern.hermesmobile.core.repo.Outbox.reconcile(list, id.melvern.hermesmobile.core.repo.Outbox.forChat(app, effectiveStoredId), id.melvern.hermesmobile.core.repo.Outbox::isSending, { RelTime.clock(it) }, id.melvern.hermesmobile.core.repo.Outbox::isFailed)
    }

    /** v26: bubble "Send after" yang belum jalan — buang yang sudah masuk transcript, sisanya tampil Queued. */
    fun withQueued(list: List<ChatItem>, serverFresh: Boolean = false): List<ChatItem> {
        val sid = effectiveStoredId
        id.melvern.hermesmobile.core.repo.PromptStore.settleServerQueued(app, sid, list.filterIsInstance<ChatItem.User>().filter { !it.queued }.map { it.text to it.at })
        val pending = id.melvern.hermesmobile.core.repo.PromptStore.serverQueued(app, sid)
        // bubble queued lama (cache) yang sudah tidak antre → dibuang; sisanya ditambah ulang dari store
        val base = list.filterNot { it is ChatItem.User && it.queued && it.rowId == null && pending.none { q -> q.text == it.text.trim() } }
        return withOutbox(id.melvern.hermesmobile.core.repo.PromptStore.withServerQueued(base, pending) { RelTime.clock(it) }, serverFresh)
    }

    fun mapTranscript(msgs: List<TranscriptMessage>): List<ChatItem> = msgs.flatMap { m ->
        when {
            m.isUser -> {
                val (txt, steered) = id.melvern.hermesmobile.core.model.SteerText.unwrap(m.text ?: "")
                listOf(ChatItem.User(txt, m.rowId, time = RelTime.clock(m.timestamp), at = m.timestamp, steered = steered))
            }
            m.role == "tool" -> listOf(
                ChatItem.Tool(m.name ?: "tool", "done", (m.argsText ?: m.context ?: "").take(2000))
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
    // v27: dibuka saat offline → begitu Mac terjangkau lagi, muat ulang transcript asli.
    var openedOffline by remember { mutableStateOf(false) }
    var flushOutboxAfterLoad by remember { mutableStateOf(false) }
    LaunchedEffect(connState) {
        if (connState == ConnState.OPEN && openedOffline) {
            openedOffline = false
            flushOutboxAfterLoad = true
            items = items.filterNot { it is ChatItem.NoticeLine && it.text.startsWith("Offline —") }
            TranscriptCache.drop(actualStoredId)
            reloadKey++
        }
    }
    LaunchedEffect(actualStoredId, preattachedRuntime, reloadKey) {
        loadFailed = false
        val cachedEntry = TranscriptCache.get(actualStoredId)
        if (preattachedRuntime != null && cachedEntry == null) {
            // NEW GIG: session baru kosong — gak ada transcript buat di-resume.
            // M13: tetap tulis entry kosong ke cache supaya ArtifactsScreen bisa
            // bedain "chat baru (Nothing shared yet)" vs "transcript belum
            // termuat (Nothing loaded yet)", dan back→reopen gak miss.
            cacheSnapshot()
            loading = false
            return@LaunchedEffect
        }
        // M9 (item 6): cache dulu — back→reopen / rotate render instan.
        if (!cacheHydrated) {
            TranscriptCache.get(actualStoredId)?.let { e ->
                items = withQueued(e.items)
                runtimeId = e.runtimeId
                running = e.running
                loading = false
            }
            cacheHydrated = true
        }
        // v28 E: belum ada cache → N pesan terakhir via HTTP dulu (chat raksasa tampil instan);
        // resume penuh di bawah MENGGANTI daftar ini seluruhnya (tidak pernah digabung).
        val tailJob = if (cachedEntry == null && items.isEmpty()) launch {
            val t = app.connection?.let { id.melvern.hermesmobile.core.repo.TailRepo(it).tail(actualStoredId, chatProfile) }
            if (t != null && t.messages.isNotEmpty() && loading && items.isEmpty()) {
                items = id.melvern.hermesmobile.core.repo.TailRepo.withPartialNotice(mapTranscript(t.messages), t.hasMore)
                tailOnly = true
                loading = false
            }
        } else null
        val c = app.client ?: return@LaunchedEffect
        var waited = 0
        // v27: HP tanpa internet → jangan tunggu 25s, langsung salinan tersimpan
        val limit = if (id.melvern.hermesmobile.core.store.NetState.hasInternet(app)) 25000 else 1500
        while (c.state.value != ConnState.OPEN && waited < limit) { delay(250); waited += 250 }
        if (c.state.value != ConnState.OPEN) {
            if (items.isEmpty()) {
                // v27: offline → tampilkan salinan tersimpan (bisa dibaca + ketik; kirim masuk antrean)
                val saved = id.melvern.hermesmobile.core.store.OfflineStore.loadChat(app, actualStoredId) { RelTime.clock(it) }
                items = if (saved.isNotEmpty()) withOutbox(saved) + ChatItem.NoticeLine("Offline — showing saved copy. New replies load when you're back online.")
                        else listOf(ChatItem.NoticeLine("Offline — this chat isn't saved on the phone yet."))
                loadFailed = saved.isEmpty()
                loading = false
            }
            openedOffline = true
            return@LaunchedEffect
        }
        try {
            val cached = TranscriptCache.get(actualStoredId)
            // M12 anti-stale: runtime id server bisa berganti TANPA restart gateway
            // (resume dari surface lain) — epoch sama, ring baru, delta count=0 palsu.
            // Percaya delta HANYA kalau runtime server == runtime cache.
            val serverRt = c.serverRuntimeFor(actualStoredId)
            val runtimeFresh = serverRt == null || serverRt == runtimeId
            if (cached != null && cached.runtimeId == runtimeId && runtimeFresh &&
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
                        val out = SessionRepo(c, chatProfile).resume(actualStoredId)
                        runtimeId = out.runtimeId
                        running = out.running
                        items = withQueued(mapTranscript(out.messages), serverFresh = !out.running)
                        cacheSnapshot()
                    }
                    loading = false
                }
            } else {
                val out = SessionRepo(c, chatProfile).resume(actualStoredId)
                tailJob?.cancel(); tailOnly = false
                runtimeId = out.runtimeId
                running = out.running
                items = withQueued(mapTranscript(out.messages), serverFresh = !out.running)
                cacheSnapshot()
                // M4 asli (review M9): open_requests SELALU direstore dari resume
                // penuh — approval pending server-side membuat running=true dan
                // justru di kasus ini card wajib muncul lagi.
                out.openRequests.forEach { req -> restoreOpenRequest(req) }
                todos = id.melvern.hermesmobile.core.repo.AgentWorkRepo.parseTodos(out.todoState)
            }
            // M4: restored server→client request (replay open_requests) —
            // card dimunculkan lagi; jawab via request.answer RPC (frame id
            // dari socket lama sudah mati). M9: diambil via resume lazy
            // (delta path tidak memanggil resume penuh) — HANYA kalau tidak
            // ada turn jalan (lazy attach bisa re-parent runtime aktif).
            // delta path: open_requests via lazy resume — HANYA kalau gak ada
            // turn jalan (lazy attach bisa re-parent runtime aktif).
            if (!running) SessionRepo(c, chatProfile).resumeOpenRequests(actualStoredId).forEach { req ->
                restoreOpenRequest(req)
            }
            // v27: balik online → kirim otomatis pesan yang diketik saat offline (≤30 menit, belum ada di server)
            if (flushOutboxAfterLoad) {
                flushOutboxAfterLoad = false
                items = withOutbox(items)
                val fresh = System.currentTimeMillis() - 30 * 60_000L
                id.melvern.hermesmobile.core.repo.Outbox.forChat(app, actualStoredId)
                    .filter { it.at >= fresh && !id.melvern.hermesmobile.core.repo.Outbox.isSending(it.id) }
                    .forEach { deliver(it) }
            }
        } catch (e: Throwable) {
            items = listOf(ChatItem.NoticeLine("Couldn't load this chat (${e.message})."))
            loadFailed = true
        } finally { tailJob?.cancel(); tailOnly = false; loading = false }
    }

    val liveClient by app.clientFlow.collectAsState()
    LaunchedEffect(runtimeId, liveClient) {
        liveClient?.inbound?.collect { ev ->
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
                        // single {question,choices} atau batch {questions:[{qid,question,choices}]}
                        clarify = AskClarify.parse(ev.id, ev.params).copy(respondRaw = { result -> ev.respond(result) })
                        running = true
                    }
                    else -> ev.fail(-32601, "no handler: ${ev.method}")
                }
                is GatewayInbound.RpcEvent -> if (ev.sessionId == runtimeId || ev.sessionId == actualStoredId || ev.sessionId.isEmpty()) when (ev.type) {
                    // M12: event server memakai RUNTIME id yang berganti tiap resume di surface
                    // lain (desktop/python) — cocokkan stored id juga supaya event gak dibuang.
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
                            val (head, tailQ) = splitQueuedTail(items)
                            val last = head.lastOrNull()
                            items = if (last is ChatItem.Assistant && !last.done) {
                                head.dropLast(1) + last.copy(text = last.text + delta) + tailQ
                            } else head + ChatItem.Assistant(
                                delta, done = false, time = RelTime.clock(nowEpoch()), at = nowEpoch(),
                                reasoning = pendingThought?.first?.takeIf { it.isNotBlank() }, thoughtSecs = pendingThought?.second,
                            ) + tailQ
                            if (!atBottom) hasNew = true
                        }
                    }
                    "message.complete" -> {
                        endThinking()
                        val text = ev.payload?.get("text")?.jsonStr() ?: ""
                        items = items.filterNot { it is ChatItem.Assistant && !it.done }
                        if (text.isNotEmpty()) items = liveAppend(items, ChatItem.Assistant(
                            text, done = true, time = RelTime.clock(nowEpoch()), at = nowEpoch(),
                            reasoning = pendingThought?.first?.takeIf { it.isNotBlank() }, thoughtSecs = pendingThought?.second,
                        ))
                        pendingThought = null; thoughtBuf = ""
                        running = false
                        cacheSnapshot()
                        backfillRowIds()
                        if (!atBottom) hasNew = true
                    }
                    "message.start" -> {
                        running = true; thinking = false; thinkingText = ""; turnStartCount++
                        thinkStartMs = 0L; thoughtBuf = ""; pendingThought = null
                        // Prompt yang diketik di desktop gak punya event sendiri — tarik dari Mac.
                        pullForeignPrompts()
                    }
                    "message.interim" -> {
                        endThinking()
                        val text = ev.payload?.get("text")?.jsonStr() ?: ""
                        if (text.isNotEmpty()) {
                            items = items.filterNot { it is ChatItem.Assistant && !it.done }
                            items = liveAppend(items, ChatItem.Assistant(text, done = false, time = RelTime.clock(nowEpoch()), at = nowEpoch()))
                        }
                    }
                    "tool.start" -> {
                        running = true; endThinking()
                        val p = ev.payload
                        val name = p?.get("tool")?.jsonStr()?.ifEmpty { null } ?: p?.get("name")?.jsonStr()?.ifEmpty { null } ?: "tool"
                        // M8: args (ToolStartPayload.args_text/preview/context) → isi code block saat row di-expand
                        val args = listOf("args_text", "preview", "context").firstNotNullOfOrNull { k -> p?.get(k)?.jsonStr()?.takeIf { it.isNotBlank() } }
                        items = liveAppend(items, ChatItem.Tool(name, "run", detail = args?.take(2000), toolId = p?.get("tool_id")?.jsonStr()?.ifEmpty { null }))
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
                            l[idx] = t.copy(status = if (toolFailed(p?.get("result"))) "error" else "done", detail = detail?.take(4000))
                        }
                        cacheSnapshot()
                    }
                    "session.title" -> {
                        ev.payload?.get("title")?.jsonStr()?.takeIf { it.isNotBlank() }?.let { title = it }
                    }
                    "todo.updated" -> {
                        todos = id.melvern.hermesmobile.core.repo.AgentWorkRepo.parseTodos(ev.payload)
                    }
                    "subagent.spawn_requested", "subagent.start", "subagent.complete" -> subPollKick++
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
            val prof = chatProfile
            val opt = MetaRepo(c).modelOptions(sessionId = runtimeId, profile = prof)
            // M8: header cuma nama model (provider ada di model sheet)
            activeModel = opt.model.ifBlank { opt.provider }
        } catch (_: Throwable) { /* header meta opsional */ }
    }


    val headerModel = activeModel
    val profileName by app.profile.collectAsState()
    val reduceMotion = rememberReduceMotion()
    val rows = remember(items) { buildRows(items, { RelTime.dayKey(it) }, { RelTime.dayLabel(it) }) }
    // Index assistant yang PENUTUP turn (berikutnya user / akhir) — cuma itu yang dapat baris aksi.
    val turnEnds = remember(items) {
        val out = HashSet<Int>()
        var pending = -1
        items.forEachIndexed { i, it ->
            when {
                it is ChatItem.Assistant && !it.isThoughtOnly() -> pending = i
                it is ChatItem.User -> { if (pending >= 0) out += pending; pending = -1 }
            }
        }
        if (pending >= 0) out += pending
        out
    }
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
    // M15: adaptive — konten max 640 center (gaya Discord) di Medium+ (tablet
    // portrait single-pane juga kena cap); Compact phone = full seperti biasa.
    val winSize = id.melvern.hermesmobile.ui.layout.currentWinSize()
    val wide = winSize != WinSize.Compact
    // M15: artifacts sebagai side sheet 380dp di expanded (bukan route full screen).
    var artifactsOpen by rememberSaveable { mutableStateOf(false) }
    // M13: jumlah artifacts (link/foto/video/file) dari items — badge di top bar.
    // Review M13 HIGH#2: parse di Dispatchers.Default (regex berat, transcript gede)
    // dan di-skip saat turn jalan (items berganti tiap delta streaming — full
    // reparse per delta = jank di main thread). Badge update saat stream selesai.
    var artifactCount by remember { mutableStateOf(0) }
    LaunchedEffect(items, running) {
        if (running) return@LaunchedEffect
        val snapshot = items
        artifactCount = withContext(Dispatchers.Default) { ArtifactsParser.parse(snapshot).size }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Ink.Bg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        // ── Top bar 56dp (M15: expanded → konten max 640 center) ────────
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Row(
            Modifier
                .then(if (wide) Modifier.widthIn(max = Dim.ChatMaxW) else Modifier)
                .fillMaxWidth().height(Dim.TopBar).padding(end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            QuietIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onClick = onBack)
            ProfileAvatar(app, profileName, 34.dp)
            Spacer(Modifier.width(12.dp))
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
                        running && waitingOnUser -> {
                            StatusDot(Ink.Warn)
                            Spacer(Modifier.width(6.dp))
                            Text("Waiting for you", style = Type.Meta.copy(color = Ink.Text2), maxLines = 1)
                            Text(" · " + Pretty.model(headerModel), style = Type.Meta.copy(color = Ink.Text3), maxLines = 1)
                        }
                        running -> {
                            PulsingDot(Ink.Live, size = 6.dp)
                            Spacer(Modifier.width(6.dp))
                            Text("Working", style = Type.Meta.copy(color = Ink.Text2), maxLines = 1)
                            Text(" · " + Pretty.model(headerModel), style = Type.Meta.copy(color = Ink.Text3), maxLines = 1)
                        }
                        else -> {
                            OneLine(Pretty.model(headerModel).ifBlank { "Choose model" }, Type.Meta, Modifier.weight(1f, fill = false))
                            Icon(Icons.Rounded.UnfoldMore, "Change model", tint = Ink.Text3, modifier = Modifier.padding(start = 2.dp).size(14.dp))
                        }
                    }
                }
            }
            // M13: artifacts — semua link/foto/video/file dari chat ini.
            // M15: expanded → side sheet 380dp di dalam pane (BUKAN route full screen).
            Box {
                QuietIconButton(Icons.Outlined.Folder, "Artifacts", tint = Ink.Text2, onClick = {
                    if (wide) artifactsOpen = true else onOpenArtifacts(title)
                })
                if (artifactCount > 0) {
                    Text(
                        if (artifactCount > 99) "99+" else "$artifactCount",
                        style = Type.Caption.copy(color = Ink.Text, fontSize = 10.sp, lineHeight = 14.sp),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = (-5).dp, y = 7.dp)
                            .clip(Radius.Full)
                            .background(Ink.Raised)
                            .padding(horizontal = 4.dp),
                    )
                }
            }
            QuietIconButton(Icons.Rounded.MoreHoriz, "Chat options", tint = Ink.Text2, onClick = { menuSheet = true })
        }
        }
        // M15: hairline juga ikut max width center di expanded.
        // (dulu hairline yang memotong teks saat scroll — sekarang fade di atas list)

        WorkStrip(todos, subagents, wide, onOpen = { workSheet = true })

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

        Box(Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
            when {
                loading -> ChatSkeleton()
                items.isEmpty() && approval == null && clarify == null -> EmptyChat(app, profileName, headerModel, onPrompt = { input = it })
            }
            // M15: Medium+ → list max 640 center (Discord style). Wrapper Box
            // fillMaxWidth + TopCenter menempatkan LazyColumn widthIn di tengah;
            // Compact tetap fillMaxSize (align tidak berlaku pada fill parent).
            Box(Modifier.fillMaxWidth().then(if (!wide) Modifier.fillMaxSize() else Modifier), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                state = listState,
                modifier = (if (wide) Modifier.widthIn(max = Dim.ChatMaxW).fillMaxHeight()
                            else Modifier.fillMaxSize())
                    // v26.1: seleksi teks aktif = SelectionContainer di list pegang fokus.
                    // Tap di mana pun (bubble lain / area kosong) atau Back → lepas fokus → seleksi batal.
                    .onFocusChanged { selectionActive = it.hasFocus }
                    .pointerInput(Unit) {
                        dismissOnTap({ selectionActive }) { focusManager.clearFocus(force = true) }
                    },
                contentPadding = PaddingValues(top = 12.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(rows, key = { it.key }) { row ->
                    val animate = seeded && row.key !in seenKeys && !reduceMotion
                    SideEffect { if (seeded) seenKeys += row.key }
                    Box(Modifier.enterOnce(animate)) {
                        when (row) {
                            is ChatRow.Day -> DayChip(row.label)
                            is ChatRow.Tools -> ToolGroup(row.tools)
                            is ChatRow.Thoughts -> ThoughtsRow(row.texts)
                            is ChatRow.Activity -> ActivityRow(row.tools, row.thoughts)
                            is ChatRow.Item -> when (val item = row.item) {
                                is ChatItem.User -> UserBubble(item, onLongPress = { userActionTarget = item }, mediaFetch = mediaFetch, videoFetch = videoFetch,
                                    onRetry = item.outboxId?.let { oid -> { id.melvern.hermesmobile.core.repo.Outbox.get(context, oid)?.let { deliver(it) } } })
                                is ChatItem.Assistant -> AssistantBlock(item, onLongPress = { copyTarget = it }, mediaFetch = mediaFetch, videoFetch = videoFetch, onReply = { replyQuote = it.take(280) }, showMeta = row.index in turnEnds)
                                is ChatItem.NoticeLine -> NoticeRow(
                                    item.text,
                                    onRetry = if (loadFailed) ({ loading = true; items = emptyList(); reloadKey++ }) else null,
                                )
                                is ChatItem.Tool -> ToolGroup(listOf(item))
                                is ChatItem.Command -> CommandCard(item)
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
                                            val prof = chatProfile
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
                        ClarifyCard(cq, onSubmit = { answers ->
                            if (cq.responded != null) return@ClarifyCard
                            clarify = cq.copy(responded = answers.joinToString(" · ") { it.ifBlank { "Skipped" } })
                            val result = cq.result(answers)
                            scope.launch {
                                if (cq.respondRaw(result)) return@launch
                                val c = app.client ?: return@launch
                                try {
                                    c.call("request.answer", buildJsonObject {
                                        put("id", cq.id)
                                        put("result", result)
                                        val prof = chatProfile
                                        if (prof.isNotBlank() && prof != "default") put("profile", prof)
                                    })
                                } catch (_: Throwable) {}
                            }
                        })
                    }
                }
            }
            }
            // Fade tepi atas & bawah list — teks masuk/keluar lembut, gak keiris garis.
            Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().height(20.dp)
                .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Ink.Bg, Ink.Bg.copy(alpha = 0f)))))
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(16.dp)
                .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Ink.Bg.copy(alpha = 0f), Ink.Bg))))
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
                            .shadow(8.dp, Radius.Full, ambientColor = Ink.Bg, spotColor = Ink.Bg)
                            .clip(Radius.Full)
                            .background(Ink.Raised)
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

            // M15: expanded → composer max 640 center.
            if (offlineQueued.isNotEmpty()) {
                Column(Modifier.fillMaxWidth().padding(horizontal = Dim.ScreenH, vertical = 4.dp)) {
                    offlineQueued.forEach { q ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Schedule, null, tint = Ink.Text3, modifier = Modifier.size(13.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(q.text, style = Type.Caption.copy(color = Ink.Text2), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            Text("Cancel", style = Type.Caption.copy(color = Ink.Text3), modifier = Modifier.clip(Radius.Chip).pressClickable {
                                id.melvern.hermesmobile.core.repo.PromptStore.removeQueued(context, q)
                                offlineQueued = id.melvern.hermesmobile.core.repo.PromptStore.queue(context).filter { it.storedId == effectiveStoredId }
                            }.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                    }
                    Text("Will send when your Mac is reachable", style = Type.Caption.copy(color = Ink.Text4))
                }
            }
            editTarget?.let { et ->
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                    Row(
                        Modifier.then(if (wide) Modifier.widthIn(max = Dim.ChatMaxW) else Modifier).fillMaxWidth()
                            .padding(start = Dim.ScreenH, end = 4.dp, top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.Edit, null, tint = Ink.Text3, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Editing — replies after this message will be replaced", style = Type.Caption.copy(color = Ink.Text2), modifier = Modifier.weight(1f))
                        QuietIconButton(Icons.Rounded.Close, "Cancel edit", tint = Ink.Text3, onClick = { editTarget = null; input = "" })
                    }
                }
            }
            if (running && input.isNotBlank() && attachments.isEmpty() && !readOnly && !input.trimStart().startsWith("/")) {
                BusySendBar(
                    wide = wide,
                    onSteer = {
                        val t = input.trim(); input = ""
                        items = items + ChatItem.User(t, time = RelTime.clock(nowEpoch()), at = nowEpoch(), steered = true)
                        forceScroll = true
                        scope.launch {
                            val ok = try { id.melvern.hermesmobile.core.repo.AgentWorkRepo(app.client ?: return@launch, chatProfile).steer(runtimeId, t) } catch (_: Throwable) { false }
                            if (!ok) items = items + ChatItem.NoticeLine("Couldn't steer — the turn may have just ended. Send it as a new message.")
                        }
                    },
                    onQueue = {
                        val t = input.trim(); input = ""
                        val o = id.melvern.hermesmobile.core.repo.Outbox.Out(java.util.UUID.randomUUID().toString(), effectiveStoredId, t, t, System.currentTimeMillis(), queued = true)
                        id.melvern.hermesmobile.core.repo.Outbox.add(context, o)
                        items = liveAppend(items, ChatItem.User(t, pending = true, time = RelTime.clock(nowEpoch()), at = nowEpoch(), outboxId = o.id))
                        forceScroll = true
                        deliver(o)
                    },
                )
            }
            Composer(
                value = input, onValueChange = { input = it },
                running = running, connected = connState == ConnState.OPEN,
                readOnly = readOnly,
                attachments = attachments,
                attachThumbs = attachThumbs,
                attaching = attaching,
                attachError = attachError,
                quote = replyQuote,
                onCancelQuote = { replyQuote = null },
                onAttach = { attachSheet = true },
                onRemoveAttachment = { a -> attachments = attachments - a; attachThumbs = attachThumbs - a },
                wide = wide,
                voice = voice.takeIf { it.available },
                slashSuggestions = slashSuggestions,
                focusKey = composerFocus,
                onPickSlash = { t -> input = (if (t.startsWith("/")) t else "/$t").trimEnd() + " "; slashSuggestions = emptyList() },
                onMic = {
                    val granted = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) ==
                        android.content.pm.PackageManager.PERMISSION_GRANTED
                    if (granted) voice.start(input) { input = it } else micPerm.launch(android.Manifest.permission.RECORD_AUDIO)
                },
        onSend = {
                voice.cancel()
                val typed = input.trim()
                // M11: reply — quote diselipkan sebagai konteks di atas pesan
                val quote = replyQuote
                replyQuote = null
                // file non-gambar WAJIB bawa ref; gambar auto-queued server
                val atts = attachments
                val att = atts.firstOrNull()
                // Lampiran: @file ref (agent baca isinya) + baris MEDIA:<path Mac> supaya
                // bubble (live & setelah reload) render foto/video/kartu file, bukan teks path.
                val mediaLine = atts.mapNotNull { a -> a.path.takeIf { it.isNotBlank() }?.let { "MEDIA:$it" } }
                    .joinToString("\n").takeIf { it.isNotEmpty() }
                val parts = listOfNotNull(
                    typed.takeIf { it.isNotEmpty() },
                    atts.filter { !it.isImage }.mapNotNull { it.refText.takeIf { r -> r.isNotBlank() } }
                        .joinToString("\n").takeIf { it.isNotEmpty() },
                    mediaLine,
                )
                if (typed.isEmpty() && att == null) return@Composer
                // v25 offline queue: Mac tidak terjangkau → simpan, kirim otomatis saat konek lagi
                if (connState != ConnState.OPEN && att == null && !typed.startsWith("/")) {
                    id.melvern.hermesmobile.core.repo.PromptStore.enqueue(context, effectiveStoredId, typed)
                    offlineQueued = id.melvern.hermesmobile.core.repo.PromptStore.queue(context).filter { it.storedId == effectiveStoredId }
                    input = ""; replyQuote = null
                    return@Composer
                }
                // v24: edit & resend — potong history mulai pesan ini lalu kirim versi baru
                editTarget?.let { et ->
                    val rid = et.rowId
                    editTarget = null
                    if (rid != null && att == null) {
                        input = ""
                        val idx = items.indexOfFirst { it === et || (it is ChatItem.User && it.rowId == rid) }
                        val isFirst = items.take(maxOf(idx, 0)).none { it is ChatItem.User }
                        if (idx >= 0) items = items.take(idx)
                        items = items + ChatItem.User(typed, pending = true, time = RelTime.clock(nowEpoch()), at = nowEpoch())
                        forceScroll = true
                        val oldText = et.text.trim()
                        val userOrdinal = items.take(maxOf(idx, 0)).count { it is ChatItem.User }
                        scope.launch {
                            try {
                                // row_id lokal bisa basi (cache / edit sebelumnya me-rewind row) —
                                // ambil row AKTIF terbaru dari transcript server sesuai urutan pesan user.
                                val repo = SessionRepo(app.client ?: return@launch, chatProfile)
                                val fresh = try { repo.activeUserRowIds(effectiveStoredId) } catch (_: Throwable) { emptyList() }
                                val target = fresh.getOrNull(userOrdinal)?.takeIf { it.second.trim() == oldText || it.second.trim().endsWith(oldText) }?.first
                                    ?: fresh.lastOrNull { it.second.trim() == oldText }?.first ?: rid
                                repo.submitEdit(runtimeId, typed, target, isFirst)
                                items = items.map { if (it is ChatItem.User && it.pending) it.copy(pending = false) else it }
                                running = true
                            } catch (e: Throwable) {
                                items = items.map { if (it is ChatItem.User && it.pending) it.copy(pending = false) else it } +
                                    ChatItem.NoticeLine("Edit failed: ${e.message ?: "unknown"} — pull to reload")
                            }
                        }
                        return@Composer
                    }
                }
                // Slash command (tanpa lampiran): jalanin lewat slash.exec, output tampil
                // sebagai kartu command. Skill/queue → balik jadi prompt dan dikirim normal.
                if (att == null && quote == null && typed.startsWith("/") && typed.length > 1 && runtimeId.isNotBlank()) {
                    input = ""
                    slashSuggestions = emptyList()
                    forceScroll = true
                    val card = ChatItem.Command(typed, null)
                    items = items + card
                    scope.launch {
                        val res = try {
                            id.melvern.hermesmobile.core.repo.SlashRepo(app.client ?: return@launch).run(runtimeId, typed)
                        } catch (e: Throwable) {
                            items = items.map { if (it === card) ChatItem.Command(typed, e.message ?: "Command failed", failed = true) else it }
                            return@launch
                        }
                        when (res) {
                            is id.melvern.hermesmobile.core.repo.SlashRepo.Result.Output ->
                                items = items.map { if (it === card) ChatItem.Command(typed, res.text) else it }
                            is id.melvern.hermesmobile.core.repo.SlashRepo.Result.Send -> {
                                items = items.filterNot { it === card } + ChatItem.User(typed, pending = true, time = RelTime.clock(nowEpoch()), at = nowEpoch())
                                try {
                                    val repo = SessionRepo(app.client ?: return@launch, chatProfile)
                                    if (res.queued) {
                                        repo.submitQueued(runtimeId, res.message)
                                        id.melvern.hermesmobile.core.repo.PromptStore.addServerQueued(context, effectiveStoredId, typed)
                                        items = items.map { if (it is ChatItem.User && it.pending) it.copy(pending = false, queued = true) else it }
                                        cacheSnapshot()
                                        return@launch
                                    }
                                    val (newRuntime, status) = repo.sendPromptResilient(effectiveStoredId, runtimeId, res.message)
                                    if (newRuntime != runtimeId) runtimeId = newRuntime
                                    items = items.map { if (it is ChatItem.User && it.pending) it.copy(pending = false, queued = status == SessionRepo.SubmitStatus.QUEUED) else it }
                                    running = true
                                } catch (e: Throwable) {
                                    items = items.map { if (it is ChatItem.User && it.pending) it.copy(pending = false) else it } + ChatItem.NoticeLine(e.message ?: "Send failed")
                                }
                            }
                        }
                    }
                    return@Composer
                }
                val sentQuote = quote?.let { q ->
                    val flat = q.lineSequence().joinToString(" ").trim()
                    if (flat.isEmpty()) null else "> ${flat.take(240)}"
                }
                val text = listOfNotNull(
                    sentQuote,
                    (if (typed.isEmpty() && atts.isNotEmpty() && atts.all { it.isImage } && mediaLine == null) "Sent ${atts.size} photo(s)" else parts.joinToString("\n")).takeIf { it.isNotEmpty() },
                ).joinToString("\n\n")
                input = ""
                attachments = emptyList()
                attachThumbs = emptyMap()
                forceScroll = true
                val display = listOfNotNull(typed.takeIf { it.isNotEmpty() }, mediaLine).joinToString("\n")
                    .ifEmpty { "Sent ${atts.joinToString { it.name }}" }
                val o = id.melvern.hermesmobile.core.repo.Outbox.Out(java.util.UUID.randomUUID().toString(), effectiveStoredId, display, text, System.currentTimeMillis(), quote = quote)
                id.melvern.hermesmobile.core.repo.Outbox.add(context, o)
                items = items + ChatItem.User(
                    display, pending = true, time = RelTime.clock(nowEpoch()), at = nowEpoch(),
                    quote = quote, outboxId = o.id,
                )
                deliver(o)
            },
            onStop = { scope.launch { app.client?.let { SessionRepo(it, chatProfile).interrupt(runtimeId) } } },
        )
    }

    // v24: pesan user — Copy / Reply / Edit & resend
    userActionTarget?.let { u ->
        QuietSheet(onDismiss = { userActionTarget = null }, title = "Your message") {
            Text(u.text.take(200), style = Type.Callout.copy(color = Ink.Text2), maxLines = 3, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = Dim.ScreenH).padding(bottom = 8.dp))
            if (u.rowId != null && !running && !readOnly) SheetActionRow("Edit & resend", Icons.Rounded.Edit) {
                editTarget = u; input = userDisplayText(u.text); userActionTarget = null; composerFocus++
            }
            val saved = userDisplayText(u.text).trim() in favorites
            SheetActionRow(if (saved) "Remove from saved prompts" else "Save as prompt", Icons.Rounded.BookmarkBorder) {
                id.melvern.hermesmobile.core.repo.PromptStore.toggleFavorite(context, userDisplayText(u.text))
                favorites = id.melvern.hermesmobile.core.repo.PromptStore.favorites(context); userActionTarget = null
            }
            SheetActionRow("Reply", Icons.Rounded.Reply) { replyQuote = u.text.take(280); userActionTarget = null }
            SheetActionRow("Copy text", Icons.Rounded.ContentCopy) { clipboard.setText(AnnotatedString(u.text)); userActionTarget = null }
        }
    }
    if (diffOpen) {
        val paths = remember(items) {
            id.melvern.hermesmobile.core.repo.MacRepo.touchedPaths(items.mapNotNull { (it as? ChatItem.Tool)?.detail } +
                items.mapNotNull { (it as? ChatItem.Assistant)?.text })
        }
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { diffOpen = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        ) {
            DiffScreen(app, paths, since = items.firstNotNullOfOrNull { (it as? ChatItem.User)?.at }?.minus(60), onClose = { diffOpen = false })
        }
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
            SheetActionRow("Reply", Icons.Rounded.Reply) {
                replyQuote = target.take(280)
                copyTarget = null
            }
            SheetActionRow("Copy text", Icons.Rounded.ContentCopy) {
                clipboard.setText(AnnotatedString(target))
                copyTarget = null
            }
            SheetActionRow("Share", Icons.Rounded.Share) {
                id.melvern.hermesmobile.core.share.ChatExport.share(context, title.ifBlank { "Hermes" }, target)
                copyTarget = null
            }
        }
    }
    if (attachSheet) {
        AttachSheet(
            onDismiss = { attachSheet = false },
            onCamera = { launchCamera() },
            onGallery = {
                pickMedia.launch(androidx.activity.result.PickVisualMediaRequest(
                    androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageAndVideo))
            },
            onFile = { pickFile.launch("*/*") },
            favorites = favorites,
            onPickFavorite = { f -> input = f },
            onRemoveFavorite = { f -> id.melvern.hermesmobile.core.repo.PromptStore.toggleFavorite(context, f); favorites = id.melvern.hermesmobile.core.repo.PromptStore.favorites(context) },
            onLocation = {
                val fine = android.Manifest.permission.ACCESS_FINE_LOCATION
                val coarse = android.Manifest.permission.ACCESS_COARSE_LOCATION
                val has = androidx.core.content.ContextCompat.checkSelfPermission(context, coarse) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
                if (has) sendLocation() else locationPerm.launch(arrayOf(fine, coarse))
            },
        )
    }
    // Model sheet: live chat → switch this chat's model in place (desktop parity).
    if (workSheet) {
        WorkSheet(
            todos = todos, subs = subagents,
            repo = { app.client?.let { id.melvern.hermesmobile.core.repo.AgentWorkRepo(it, chatProfile) } },
            runtimeId = runtimeId,
            onDismiss = { workSheet = false },
            onRefreshSubs = { subPollKick++ },
        )
    }
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
                id.melvern.hermesmobile.core.repo.DraftStore.put(context, effectiveStoredId, input)
                runtimeId = runtime
                effectiveStoredId = stored.takeIf { it.isNotBlank() } ?: actualStoredId
                items = emptyList(); title = ""; activeModel = ""
                input = ""; attachments = emptyList(); attachThumbs = emptyMap(); running = false
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
            onCodeChanges = { diffOpen = true },
            onShareChat = {
                val md = id.melvern.hermesmobile.core.share.ChatExport.markdown(title.ifBlank { "Hermes chat" }, items)
                id.melvern.hermesmobile.core.share.ChatExport.share(context, title.ifBlank { "Hermes chat" }, md)
            },
        )
    }
    // M15: artifacts side sheet (expanded) — 380dp slide dari kanan di dalam
    // ChatPane, tanpa navigasi. Konten = ArtifactsScreen (reuse penuh).
    if (artifactsOpen && winSize.isExpanded) {
        ArtifactsSideSheet(
            app = app,
            storedId = effectiveStoredId,
            chatTitle = title,
            onDismiss = { artifactsOpen = false },
        )
    }
}

/** M15: side sheet artifacts — width 380dp, slide-in dari kanan, scrim none. */
@Composable
private fun ArtifactsSideSheet(app: HermesApp, storedId: String, chatTitle: String, onDismiss: () -> Unit) {
    val reduce = rememberReduceMotion()
    // Review M15 M5: exit animation — turunkan visible SEBELUM keluar composition.
    var closing by remember { mutableStateOf(false) }
    androidx.activity.compose.BackHandler(onBack = { closing = true })
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = !closing,
            // Review M15 M6: sheet dipakai hanya Expanded (Medium tidak scrim-less).
            enter = if (reduce) EnterTransition.None else slideInHorizontally(tween(Motion.NavMs, easing = Motion.EmphasizedDecelerate)) { it } + fadeIn(tween(Motion.NavMs)),
            exit = if (reduce) ExitTransition.None else slideOutHorizontally(tween(Motion.NavMs / 2, easing = Motion.EmphasizedAccelerate)) { it } + fadeOut(tween(Motion.NavMs / 2)),
            modifier = Modifier.align(Alignment.CenterEnd),
        ) {
            Box(
                Modifier
                    .width(Dim.ArtifactSheetW)
                    .fillMaxHeight()
                    .background(Ink.Bg),
            ) {
                ArtifactsScreen(app, storedId, chatTitle, onBack = onDismiss)
            }
        }
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

/** Empty chat: avatar bot besar + sapaan + model pill + 3 starter prompt. */
@Composable
private fun EmptyChat(app: HermesApp, profile: String, model: String, onPrompt: (String) -> Unit = {}) {
    val starters = remember(profile) {
        when (profile) {
            "coder" -> listOf("Review my latest commit", "Fix the failing build", "Explain this repo's structure")
            "research" -> listOf("Summarize today's AI news", "Compare two tools for me", "Find sources on…")
            "content" -> listOf("Draft a 55s TikTok script", "Give me 5 hook ideas", "Rewrite this caption")
            else -> listOf("What's running right now?", "Summarize my last session", "Plan my next task")
        }
    }
    Column(
        Modifier.fillMaxSize().padding(horizontal = 28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ProfileAvatar(app, profile, Dim.AvatarEmpty)
        Spacer(Modifier.height(18.dp))
        Text(Pretty.profile(profile), style = Type.Headline)
        if (model.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(Pretty.model(model), style = Type.MonoMeta.copy(color = Ink.Text3))
        }
        Spacer(Modifier.height(28.dp))
        starters.forEach { p ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .widthIn(max = 420.dp)
                    .padding(vertical = 4.dp)
                    .clip(Radius.Field)
                    .background(Ink.Surface1)
                    .border(hairline(), Ink.Hairline, Radius.Field)
                    .pressClickable { onPrompt(p) }
                    .padding(horizontal = 16.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(p, style = Type.Callout.copy(color = Ink.Text2), modifier = Modifier.weight(1f))
                Icon(Icons.Rounded.ArrowOutward, null, tint = Ink.Text4, modifier = Modifier.size(16.dp))
            }
        }
        Spacer(Modifier.height(56.dp))
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


/** Hasil tool gagal: objek dengan `error` non-kosong, `success:false`, atau exit_code ≠ 0. */
internal fun toolFailed(result: kotlinx.serialization.json.JsonElement?): Boolean {
    val o = result as? kotlinx.serialization.json.JsonObject ?: return false
    val err = o["error"]
    if (err != null && err !is kotlinx.serialization.json.JsonNull &&
        !(err is kotlinx.serialization.json.JsonPrimitive && (err.content.isBlank() || err.content == "false"))) return true
    (o["success"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.let { if (it == "false") return true }
    val code = (o["exit_code"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toIntOrNull()
    return code != null && code != 0
}


/** Lokasi terkini (≤10 dtk) atau last-known. null = gagal/izin tidak ada. */
@android.annotation.SuppressLint("MissingPermission")
private fun currentLocation(context: android.content.Context): Pair<Double, Double>? {
    val lm = context.getSystemService(android.content.Context.LOCATION_SERVICE) as? android.location.LocationManager ?: return null
    val providers = listOf(android.location.LocationManager.FUSED_PROVIDER, android.location.LocationManager.GPS_PROVIDER,
        android.location.LocationManager.NETWORK_PROVIDER).filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
    return try {
        if (android.os.Build.VERSION.SDK_INT >= 30 && providers.isNotEmpty()) {
            val latch = java.util.concurrent.CountDownLatch(1)
            var got: android.location.Location? = null
            val cancel = android.os.CancellationSignal()
            lm.getCurrentLocation(providers.first(), cancel, context.mainExecutor) { got = it; latch.countDown() }
            if (!latch.await(10, java.util.concurrent.TimeUnit.SECONDS)) cancel.cancel()
            got?.let { return it.latitude to it.longitude }
        }
        providers.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }?.let { it.latitude to it.longitude }
    } catch (_: Throwable) { null }
}
