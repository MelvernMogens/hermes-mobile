package id.melvern.hermesmobile.ui.sessions

import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.outlined.AddComment
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.key
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import id.melvern.hermesmobile.HermesApp
import id.melvern.hermesmobile.core.model.SessionRow
import id.melvern.hermesmobile.core.repo.MetaRepo
import id.melvern.hermesmobile.core.repo.SessionRepo
import id.melvern.hermesmobile.core.rpc.ConnState
import id.melvern.hermesmobile.core.rpc.GatewayInbound
import id.melvern.hermesmobile.core.store.SettingsStore
import id.melvern.hermesmobile.ui.components.Hairline
import id.melvern.hermesmobile.ui.components.MonogramAvatar
import id.melvern.hermesmobile.ui.components.Pretty
import id.melvern.hermesmobile.ui.components.PulsingDot
import id.melvern.hermesmobile.ui.components.SectionHeader
import id.melvern.hermesmobile.ui.components.OneLine
import id.melvern.hermesmobile.ui.components.ProfileAvatar
import id.melvern.hermesmobile.ui.components.QuietIconButton
import id.melvern.hermesmobile.ui.components.QuietSheet
import id.melvern.hermesmobile.ui.components.RelTime
import id.melvern.hermesmobile.ui.components.SkeletonSessionRow
import id.melvern.hermesmobile.ui.components.StatusDot
import id.melvern.hermesmobile.ui.components.shimmerAlpha
import id.melvern.hermesmobile.ui.chat.ChatScreen
import id.melvern.hermesmobile.ui.layout.ChatRouteArg
import id.melvern.hermesmobile.ui.layout.isExpanded
import id.melvern.hermesmobile.ui.theme.Dim
import id.melvern.hermesmobile.ui.theme.Ink
import id.melvern.hermesmobile.ui.theme.Radius
import id.melvern.hermesmobile.ui.theme.Type
import id.melvern.hermesmobile.ui.theme.hairline
import id.melvern.hermesmobile.ui.theme.pressClickable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * M8 "Chats" — gaya list WhatsApp/iMessage.
 * Top bar: avatar profil 32 (dot status koneksi) + nama profil (meta), judul
 * kecil yang muncul saat large title "Chats" ter-scroll lewat, Search + Edit.
 * Row: avatar 44 · judul · preview/sumber · waktu relatif · "Running".
 * Hidden session dikelompokkan di bawah header "Hidden".
 */
@Composable
fun SessionsScreen(app: HermesApp, onOpen: (String) -> Unit, initialSelection: String? = null) {
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    // M15: adaptive — two-pane HANYA Expanded width (>=840dp); Compact/Medium
    // perilaku lama (nav ke ChatScreen). Selection di level screen ini, bukan
    // navigasi — rememberSaveable supaya selamat rotate (dan Compact↔Expanded
    // tidak menghapusnya).
    val winSize = id.melvern.hermesmobile.ui.layout.currentWinSize()
    var paneSelection by rememberSaveable { mutableStateOf(initialSelection) }
    // M18: buka Bot Chat dari Overview di expanded → selection inline di pane
    // kanan (bukan route full-screen yang menimpa two-pane M15).
    androidx.compose.runtime.LaunchedEffect(initialSelection) {
        if (initialSelection != null) paneSelection = initialSelection
    }
    fun openChat(arg: String) {
        // ada share yang menunggu → chat yang dibuka ini jadi tujuannya
        if (id.melvern.hermesmobile.core.share.ShareInbox.pending.value != null) {
            id.melvern.hermesmobile.core.share.ShareInbox.route(arg.substringBefore("|"))
        }
        if (winSize.isExpanded) paneSelection = arg else onOpen(arg)
    }
    // M15: back di expanded = tutup pane (bukan keluar app).
    androidx.activity.compose.BackHandler(enabled = winSize.isExpanded && paneSelection != null) {
        paneSelection = null
    }
    var sessions by remember { mutableStateOf<List<SessionRow>>(emptyList()) }
    // M8: id session yang hidden = ada di list include_hidden tapi tidak di list default
    // (SessionListRow di kontrak tidak punya field hidden).
    var hiddenIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var active by remember { mutableStateOf<Set<String>>(emptySet()) }
    var loading by remember { mutableStateOf(true) }
    var profileSheet by remember { mutableStateOf(false) }
    var actionTarget by remember { mutableStateOf<SessionRow?>(null) }
    // M5→M7: toggle session tersembunyi — persist di DataStore (default TRUE).
    var showHidden by remember { mutableStateOf(true) }
    var showHiddenLoaded by remember { mutableStateOf(false) }
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    // v25: cari ISI chat (FTS server) — debounce 350ms, tampil di bawah hasil judul
    var contentHits by remember { mutableStateOf<List<id.melvern.hermesmobile.core.repo.ScheduleRepo.Hit>>(emptyList()) }
    LaunchedEffect(query, searching) {
        val q = query.trim()
        if (!searching || q.length < 2) { contentHits = emptyList(); return@LaunchedEffect }
        kotlinx.coroutines.delay(350)
        val conn = app.connection ?: return@LaunchedEffect
        contentHits = id.melvern.hermesmobile.core.repo.ScheduleRepo(conn).search(q, app.profile.value) ?: emptyList()
    }
    // Pesan terakhir asli per session (proxy /api/mobile-last) — session.list
    // preview = prompt PERTAMA, jadi tanpa ini home selalu nunjukin chat lama.
    var lastMsgs by remember { mutableStateOf<Map<String, id.melvern.hermesmobile.core.repo.InsightsRepo.Last>>(emptyMap()) }
    var pinned by remember { mutableStateOf<Set<String>>(emptySet()) }
    LaunchedEffect(Unit) { pinned = SettingsStore.loadPinned(ctx) }
    LaunchedEffect(Unit) { id.melvern.hermesmobile.core.store.ChatGroups.load(ctx) }
    var groupTarget by remember { mutableStateOf<SessionRow?>(null) }
    // v23: permission inbox (lintas chat) + unread
    var inbox by remember { mutableStateOf<List<id.melvern.hermesmobile.core.repo.AgentWorkRepo.Pending>>(emptyList()) }
    var inboxOpen by remember { mutableStateOf(false) }
    var inboxKick by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) { id.melvern.hermesmobile.core.repo.UnreadStore.load(ctx) }
    val fleetLive by id.melvern.hermesmobile.core.notify.FleetBus.live.collectAsState()
    val waitingKeys = fleetLive.filter { it.status == "waiting" }.map { it.sessionKey }.sorted()
    LaunchedEffect(inboxOpen) { while (inboxOpen) { delay(30_000); inboxKick++ } }
    LaunchedEffect(waitingKeys, inboxKick) {
        val c = app.client ?: return@LaunchedEffect
        inbox = try { id.melvern.hermesmobile.core.repo.AgentWorkRepo(c, app.profile.value).inbox() } catch (_: Throwable) { inbox }
    }
    var editGroup by remember { mutableStateOf<id.melvern.hermesmobile.core.store.ChatGroups.Group?>(null) }
    var creating by remember { mutableStateOf(false) }
    val client = app.client
    val connState by client?.state?.collectAsState() ?: remember { mutableStateOf(ConnState.CLOSED) }
    val profile by app.profile.collectAsState()

    fun refresh() {
        val c = app.client ?: return
        if (c.state.value != ConnState.OPEN) return
        scope.launch {
            try {
                val repo = SessionRepo(c, profile)
                val visible = repo.listSessions(includeHidden = false)
                if (showHidden) {
                    val all = repo.listSessions(includeHidden = true)
                    val visibleIds = visible.map { it.id }.toSet()
                    hiddenIds = all.map { it.id }.filterNot { it in visibleIds }.toSet()
                    sessions = all
                } else {
                    hiddenIds = emptySet()
                    sessions = visible
                }
            } catch (_: Throwable) {}
            try { active = SessionRepo(c, profile).activeStoredIds() } catch (_: Throwable) {}
            loading = false
            app.connection?.let { conn ->
                val ids = sessions.map { it.id }
                id.melvern.hermesmobile.core.repo.InsightsRepo(conn).lastMessages(profile, ids)?.let { lastMsgs = it }
            }
        }
    }

    // M7: load showHidden dari DataStore SEBELUM loop fetch — fetch pertama
    // menunggu nilai persisted siap (hindari list pertama pakai default salah).
    LaunchedEffect(Unit) {
        showHidden = SettingsStore.loadShowHidden(ctx)
        showHiddenLoaded = true
    }
    LaunchedEffect(showHiddenLoaded) {
        if (!showHiddenLoaded) return@LaunchedEffect
        while (true) {
            refresh()
            delay(10_000)
        }
    }
    // M6: multi-surface live update — event dari session lain → refresh (debounce 2s).
    val liveClient by app.clientFlow.collectAsState()
    LaunchedEffect(liveClient) {
        val inbound = liveClient?.inbound ?: return@LaunchedEffect
        var lastRefresh = 0L
        scope.launch {
            inbound.collect { ev ->
                if (ev is GatewayInbound.RpcEvent) {
                    when (ev.type) {
                        "message.start", "tool.start", "message.complete",
                        "session.reclaimed", "session.title" -> {
                            val now = System.currentTimeMillis()
                            if (now - lastRefresh > 2_000) {
                                lastRefresh = now
                                refresh()
                            }
                        }
                    }
                }
            }
        }
    }
    // Koneksi baru OPEN (startup / reconnect) → refresh langsung, jangan tunggu poll 10s.
    LaunchedEffect(connState, showHiddenLoaded) {
        if (connState == ConnState.OPEN && showHiddenLoaded) refresh()
    }
    // M5/M7: toggle hidden → refresh; skip siklus pertama setelah load DataStore.
    var sawInitialHidden by remember { mutableStateOf(false) }
    LaunchedEffect(showHidden) {
        if (!showHiddenLoaded) return@LaunchedEffect
        if (!sawInitialHidden) { sawInitialHidden = true; return@LaunchedEffect }
        loading = true; sessions = emptyList(); refresh()
    }
    // M4: ganti profile → refresh (skip siklus pertama).
    var sawInitialProfile by remember { mutableStateOf(false) }
    LaunchedEffect(profile) {
        if (!sawInitialProfile) { sawInitialProfile = true; return@LaunchedEffect }
        loading = true; sessions = emptyList(); refresh()
    }

    fun newChat() {
        if (creating) return
        creating = true
        scope.launch {
            try {
                val c = app.client ?: return@launch
                val (runtimeId, storedId) = SessionRepo(c, profile).createSession()
                // M15: expanded → chat baru langsung tampil di ChatPane.
                openChat("$storedId|$runtimeId")
            } catch (_: Throwable) {
            } finally { creating = false }
        }
    }

    val listState = rememberLazyListState()
    val density = LocalDensity.current
    // Large title collapse: judul kecil di bar muncul saat item 0 (large title) lewat 60%.
    val collapsed by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 ||
                listState.firstVisibleItemScrollOffset > with(density) { 30.dp.toPx() }
        }
    }
    val scrolled by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0 }
    }
    val smallTitleAlpha by animateFloatAsState(if (collapsed || searching) 1f else 0f, label = "smallTitle")

    val filtered = remember(sessions, query) {
        val q = query.trim()
        if (q.isEmpty()) sessions else sessions.filter { it.displayTitle.contains(q, ignoreCase = true) }
    }
    fun lastAt(r: SessionRow): Double = lastMsgs[r.id]?.at ?: r.updatedAt ?: r.startedAt ?: 0.0
    val visibleAll = filtered.filterNot { it.id in hiddenIds }
    val pinnedRows = visibleAll.filter { it.id in pinned }.sortedByDescending { lastAt(it) }
    val unpinned = visibleAll.filterNot { it.id in pinned }.sortedByDescending { lastAt(it) }
    // Grup: chat ber-grup tampil di section grupnya (urut terbaru), sisanya di "All chats".
    val (groupSections, visibleRows) = id.melvern.hermesmobile.core.store.ChatGroups.partition(unpinned, { it.id })
    val hiddenRows = filtered.filter { it.id in hiddenIds }.sortedByDescending { lastAt(it) }


    // M15: isi sessions pane — dipakai Compact (full width + status bar) dan
    // expanded (340dp kolom kiri, tanpa status bar tambahan — Row di atas sudah
    // tidak menambah padding apa pun).
    val paneContent: @Composable () -> Unit = {
        Column(Modifier.fillMaxSize().background(Ink.Bg).then(if (!winSize.isExpanded) Modifier.statusBarsPadding() else Modifier)) {
        Box(Modifier.fillMaxWidth().height(Dim.TopBar)) {
            if (searching) {
                SearchBar(
                    query = query,
                    onQuery = { query = it },
                    onClose = { searching = false; query = "" },
                )
            } else {
                Row(
                    Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = Dim.ScreenH - 8.dp)
                        .clip(Radius.Full)
                        .pressClickable { profileSheet = true }
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AvatarWithStatus(app, profile, connState)
                    Spacer(Modifier.width(10.dp))
                    OneLine(Pretty.profile(profile), Type.MetaMedium.copy(color = Ink.Text))
                    Icon(Icons.Rounded.UnfoldMore, "Switch profile", tint = Ink.Text3, modifier = Modifier.padding(start = 2.dp).size(16.dp))
                }
                Text(
                    "Chats",
                    style = Type.Title,
                    modifier = Modifier.align(Alignment.Center).graphicsLayer { alpha = smallTitleAlpha },
                )
                Row(Modifier.align(Alignment.CenterEnd).padding(end = 8.dp)) {
                    QuietIconButton(Icons.Outlined.Search, "Search chats", onClick = { searching = true })
                    QuietIconButton(Icons.Outlined.AddComment, "New chat", onClick = { newChat() }, enabled = !creating)
                }
            }
        }
        if (scrolled || searching) Hairline() else Spacer(Modifier.height(hairline()))

        // M14: banner permission notif mati — cuma di API 33+ dan belum granted.
        val notifDenied = remember {
            android.os.Build.VERSION.SDK_INT >= 33 &&
                androidx.core.content.ContextCompat.checkSelfPermission(
                    ctx, android.Manifest.permission.POST_NOTIFICATIONS,
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        if (notifDenied) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .pressClickable {
                        // Buka settings app (dialog permission cuma bisa muncul sekali per install).
                        runCatching {
                            ctx.startActivity(
                                android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                    .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        }
                    }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Notifications disabled — tap to enable", style = Type.MetaMedium, color = Ink.Text2)
            }
        }

        InboxBanner(inbox.size, onOpen = { inboxOpen = true })

        // Share masuk → pilih chat tujuan (tap chat mana pun / New chat).
        val shared by id.melvern.hermesmobile.core.share.ShareInbox.pending.collectAsState()
        shared?.let { sh ->
            Row(
                Modifier.fillMaxWidth().padding(horizontal = Dim.GroupInset, vertical = 6.dp)
                    .clip(Radius.Card).background(Ink.Surface2).padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Share to which chat?", style = Type.Callout.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold))
                    Text(sh.summary, style = Type.Caption.copy(color = Ink.Text3), maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }
                QuietIconButton(Icons.Rounded.Close, "Cancel share", onClick = { id.melvern.hermesmobile.core.share.ShareInbox.dismiss() })
            }
        }

        // ── Isi ────────────────────────────────────────────────────────
        val problem = when (connState) {
            ConnState.OPEN -> null
            ConnState.CLOSED -> "Offline — tap to retry"
            else -> "Reconnecting…"
        }
        when {
            loading && sessions.isEmpty() -> {
                Column(Modifier.fillMaxSize()) {
                    LargeTitle(problem, onRetry = { app.client?.start() })
                    val a = shimmerAlpha()
                    repeat(3) { SkeletonSessionRow(a) }
                }
            }
            sessions.isEmpty() -> {
                Column(Modifier.fillMaxSize()) {
                    LargeTitle(problem, onRetry = { app.client?.start() })
                    EmptyChats(Modifier.weight(1f))
                }
            }
            else -> LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = WindowInsets.navigationBars.asPaddingValues(),
            ) {
                if (!searching) item(key = "title") { LargeTitle(problem, onRetry = { app.client?.start() }) }
                fun rowItem(s: SessionRow, keyPrefix: String, last: Boolean) {
                    item(key = keyPrefix + s.id) {
                        SessionRowView(
                            app, s,
                            running = s.id in active || s.running == true,
                            pinned = s.id in pinned,
                            groupColor = id.melvern.hermesmobile.core.store.ChatGroups.groupOf(s.id)?.color,
                            unread = id.melvern.hermesmobile.core.repo.UnreadStore.isUnread(s.id, lastMsgs[s.id]?.at, lastMsgs[s.id]?.role),
                            last = lastMsgs[s.id],
                            stamp = lastAt(s),
                            divider = !last,
                            onClick = { openChat("${s.id}|t=${Uri.encode(s.displayTitle)}") },
                            onLongPress = { actionTarget = s },
                        )
                    }
                }
                if (pinnedRows.isNotEmpty() && !searching) {
                    item(key = "pinned-header") { SectionHeader("Pinned") }
                    pinnedRows.forEachIndexed { i, s -> rowItem(s, "p-", i == pinnedRows.lastIndex) }
                }
                groupSections.filter { (_, rows) -> rows.isNotEmpty() || !searching }.forEach { (g, rows) ->
                    item(key = "g-header-" + g.id) {
                        GroupHeader(
                            g, count = rows.size,
                            onToggle = { scope.launch { id.melvern.hermesmobile.core.store.ChatGroups.update(ctx, g.id, collapsed = !g.collapsed) } },
                            onEdit = { editGroup = g },
                        )
                    }
                    if (!g.collapsed || searching) rows.forEachIndexed { i, s -> rowItem(s, "g-${g.id}-", i == rows.lastIndex) }
                }
                if ((pinnedRows.isNotEmpty() || groupSections.isNotEmpty()) && !searching && visibleRows.isNotEmpty()) {
                    item(key = "all-header") { SectionHeader("All chats") }
                }
                visibleRows.forEachIndexed { i, s -> rowItem(s, "", i == visibleRows.lastIndex) }
                if (searching) pinnedRows.forEachIndexed { i, s -> rowItem(s, "ps-", i == pinnedRows.lastIndex) }
                if (hiddenRows.isNotEmpty()) {
                    item(key = "hidden-header") { SectionHeader("Hidden", trailing = hiddenRows.size.toString()) }
                    hiddenRows.forEachIndexed { i, s -> rowItem(s, "h-", i == hiddenRows.lastIndex) }
                }
                if (searching && contentHits.isNotEmpty()) {
                    item(key = "content-header") { SectionHeader("In messages", trailing = contentHits.size.toString()) }
                    contentHits.forEachIndexed { i, h ->
                        item(key = "hit-${h.sessionId}-$i") { ContentHitRow(h, onClick = { openChat(h.sessionId) }) }
                    }
                }
                item(key = "end-space") { Spacer(Modifier.height(24.dp)) }
                if (searching && filtered.isEmpty() && contentHits.isEmpty()) item(key = "no-results") {
                    Text(
                        "No chats match \"${query.trim()}\"",
                        style = Type.Callout.copy(color = Ink.Text2),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 48.dp, start = Dim.ScreenH, end = Dim.ScreenH),
                    )
                }
            }
        }
    }
    }   // tutup val paneContent (M15)

    Column(Modifier.fillMaxSize().background(Ink.Bg)) {
        // M15: expanded → list-detail: SessionsPane 340dp + hairline + ChatPane.
        if (winSize.isExpanded) {
            Row(Modifier.fillMaxSize()) {
                Box(Modifier.width(Dim.PaneListW)) { paneContent() }
                Box(Modifier.fillMaxHeight().width(hairline()).background(Ink.Hairline))
                Box(Modifier.weight(1f)) {
                    val sel = paneSelection
                    if (sel == null) {
                        SelectAChatPane()
                    } else {
                        val parsed = remember(sel) { ChatRouteArg.parse(sel as String) }
                        // Review M15 H1: key per selection — tanpa ini state ChatScreen
                        // (draft, attachment, approval, scroll) bocor dari chat lama
                        // ke chat baru saat ganti row di pane.
                        key(sel) {
                        ChatScreen(
                            app = app,
                            actualStoredId = parsed.storedId,
                            preattachedRuntime = parsed.runtimeId,
                            initialTitle = parsed.title,
                            onBack = { paneSelection = null },
                        )
                        }
                    }
                }
            }
        } else {
            Box(Modifier.fillMaxSize()) { paneContent() }
        }
    }

    if (inboxOpen) InboxSheet(
        app, inbox,
        onDismiss = { inboxOpen = false; inboxKick++ },
        onOpenChat = { id -> inboxOpen = false; openChat(id) },
        onChanged = { },
    )
    groupTarget?.let { t -> GroupPickerSheet(t.id, t.displayTitle, onDismiss = { groupTarget = null }) }
    editGroup?.let { g -> GroupEditSheet(g, onDismiss = { editGroup = null }) }
    if (profileSheet) {
        ProfileSheet(
            app,
            showHidden = showHidden,
            onToggleHidden = {
                showHidden = it
                // M7: persist pilihan — bertahan setelah app ditutup.
                scope.launch { SettingsStore.saveShowHidden(ctx, it) }
            },
            onDismiss = { profileSheet = false },
        )
    }
    actionTarget?.let { target ->
        SessionActionSheet(
            app = app,
            row = target,
            hidden = target.id in hiddenIds,
            pinned = target.id in pinned,
            onTogglePin = { scope.launch { pinned = SettingsStore.togglePinned(ctx, target.id) } },
            onDone = {
                actionTarget = null
                // Review M15 M4: chat yang sedang terpilih dihapus dari list →
                // bersihkan ChatPane (jangan render chat mati).
                if (paneSelection != null && target.id == paneSelection!!.substringBefore("|")) {
                    paneSelection = null
                }
                refresh()
            },
            onDismiss = { actionTarget = null },
            onOpenBranch = { runtimeId, storedId ->
                actionTarget = null
                openChat("$storedId|$runtimeId")
            },
            onGroup = { groupTarget = target },
        )
    }
}

/** M15: empty state ChatPane — belum ada chat terpilih (expanded). */
@Composable
private fun SelectAChatPane() {
    Column(
        Modifier.fillMaxSize().statusBarsPadding(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Rounded.ChatBubbleOutline, null, tint = Ink.Text3, modifier = Modifier.size(Dim.EmptyIcon))
        Spacer(Modifier.height(12.dp))
        Text("Select a chat", style = Type.Title)
        Spacer(Modifier.height(4.dp))
        Text(
            "Pick a conversation from the list",
            style = Type.Meta, textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
    }
}

/** Large title "Chats" (display 28) + subtitle kecil HANYA kalau ada masalah koneksi. */
@Composable
private fun LargeTitle(problem: String?, onRetry: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = Dim.ScreenH).padding(top = 6.dp, bottom = 6.dp)) {
        Text("Chats", style = Type.Display)
        if (problem != null) {
            Row(
                Modifier
                    .padding(top = 2.dp)
                    .then(if (problem.startsWith("Offline")) Modifier.pressClickable(onClick = onRetry) else Modifier)
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatusDot(if (problem.startsWith("Offline")) Ink.Danger else Ink.Warn)
                Spacer(Modifier.width(8.dp))
                Text(problem, style = Type.Meta)
            }
        }
    }
}

/** Avatar profil 32dp + dot status koneksi 8dp di pojok kanan bawah (ring bg 2dp). */
@Composable
private fun AvatarWithStatus(app: HermesApp, profile: String, state: ConnState) {
    val dot = when (state) {
        ConnState.OPEN -> null   // online = default diam; dot hanya untuk masalah
        ConnState.CLOSED -> Ink.Danger
        else -> Ink.Warn
    }
    Box {
        ProfileAvatar(app, profile, Dim.AvatarBar)
        if (dot != null) Box(
            Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 2.dp, y = 2.dp)
                .size(Dim.Dot + 4.dp)
                .clip(Radius.Full)
                .background(Ink.Bg),
            contentAlignment = Alignment.Center,
        ) { StatusDot(dot!!) }
    }
}

@Composable
private fun SearchBar(query: String, onQuery: (String) -> Unit, onClose: () -> Unit) {
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) { fr.requestFocus() }
    Row(
        Modifier.fillMaxSize().padding(start = Dim.ScreenH, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .weight(1f)
                .height(40.dp)
                .clip(Radius.Chip)
                .background(Ink.Surface1)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Search, null, tint = Ink.Text3, modifier = Modifier.size(Dim.Icon))
            Spacer(Modifier.width(8.dp))
            BasicTextField(
                value = query,
                onValueChange = onQuery,
                singleLine = true,
                textStyle = Type.Callout,
                cursorBrush = SolidColor(Ink.Text),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.weight(1f).focusRequester(fr),
                decorationBox = { inner ->
                    Box {
                        if (query.isEmpty()) Text("Search", style = Type.Callout.copy(color = Ink.Text3))
                        inner()
                    }
                },
            )
        }
        QuietIconButton(Icons.Rounded.Close, "Close search", onClick = onClose)
    }
}

@Composable
private fun EmptyChats(modifier: Modifier) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Rounded.ChatBubbleOutline, null, tint = Ink.Text3, modifier = Modifier.size(Dim.EmptyIcon))
        Spacer(Modifier.height(16.dp))
        Text("No chats yet", style = Type.Title)
        Spacer(Modifier.height(4.dp))
        Text("Tap the new-chat icon to start one", style = Type.Callout.copy(color = Ink.Text2), textAlign = TextAlign.Center)
        Spacer(Modifier.height(64.dp)) // optik: sedikit di atas tengah
    }
}

/**
 * Baris ke-2: pesan TERAKHIR (proxy) dengan prefix "You: " kalau dari user;
 * fallback ke preview RPC (prompt pertama) → "Desktop · 509 messages".
 */
internal fun secondLine(s: SessionRow, last: id.melvern.hermesmobile.core.repo.InsightsRepo.Last? = null): String {
    if (last != null && last.text.isNotBlank()) {
        val t = Pretty.preview(last.text)
        return if (last.role == "user") "You: $t" else t
    }
    val preview = Pretty.preview(s.preview)
    val title = s.displayTitle.removeSuffix("…").trim()
    val usable = preview.isNotEmpty() && !preview.startsWith(title) && preview.lowercase() != "new project"
    if (usable) return preview
    val n = s.messageCount
    return "${s.sourceLabel} · $n ${if (n == 1) "message" else "messages"}"
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SessionRowView(
    app: HermesApp,
    s: SessionRow,
    running: Boolean,
    pinned: Boolean,
    groupColor: Int? = null,
    unread: Boolean = false,
    last: id.melvern.hermesmobile.core.repo.InsightsRepo.Last?,
    stamp: Double,
    divider: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
) {
    Box(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onClick, onLongClick = onLongPress)
                .heightIn(min = Dim.RowMin)
                .padding(horizontal = Dim.ScreenH, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // monogram per chat (identitas), bot avatar kecil di pojok kalau bukan profile aktif
            Box(Modifier.size(Dim.AvatarRow)) {
                MonogramAvatar(s.id, s.displayTitle, Dim.AvatarRow, groupColor = groupColor)
                if (running) {
                    Box(
                        Modifier
                            .align(Alignment.BottomEnd)
                            .offset(x = 2.dp, y = 2.dp)
                            .size(16.dp)
                            .clip(Radius.Full)
                            .background(Ink.Bg),
                        contentAlignment = Alignment.Center,
                    ) { PulsingDot(Ink.Live, size = 10.dp) }
                }
            }
            Spacer(Modifier.width(Dim.RowGap))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OneLine(s.displayTitle, Type.RowTitle, Modifier.weight(1f))
                    Spacer(Modifier.width(10.dp))
                    if (pinned) {
                        Icon(Icons.Rounded.PushPin, "Pinned", tint = Ink.Text4, modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        if (running) "now" else RelTime.listStamp(stamp.takeIf { it > 0 }),
                        style = if (unread) Type.Caption.copy(color = Ink.Text, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold) else Type.Caption,
                        maxLines = 1,
                    )
                }
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OneLine(
                        if (running) "Working…" else secondLine(s, last),
                        Type.Preview.copy(color = if (running || unread) Ink.Text else Ink.Text3),
                        Modifier.weight(1f),
                    )
                    if (unread && !running) {
                        Spacer(Modifier.width(8.dp))
                        Box(Modifier.size(9.dp).clip(Radius.Full).background(Ink.Text))
                    }
                }
            }
        }
        if (divider) Hairline(Modifier.align(Alignment.BottomStart).padding(start = Dim.RowDividerInset, end = Dim.ScreenH))
    }
}

/**
 * M4→M8: sheet "Profiles" — row 64dp avatar 40 lingkaran, nama, model; aktif
 * = Check. Toggle "Show hidden chats" (Switch) di bawah dengan divider.
 */
@Composable
private fun ProfileSheet(app: HermesApp, showHidden: Boolean, onToggleHidden: (Boolean) -> Unit, onDismiss: () -> Unit) {
    var rows by remember { mutableStateOf<List<MetaRepo.ProfileRow>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val current by app.profile.collectAsState()
    LaunchedEffect(Unit) {
        val c = app.client ?: return@LaunchedEffect
        try { rows = MetaRepo(c).profiles() } catch (e: Throwable) { error = e.message }
    }
    QuietSheet(onDismiss = onDismiss, title = "Profiles") {
        when {
            rows == null && error == null -> {
                val a = shimmerAlpha()
                repeat(3) { SkeletonSessionRow(a) }
            }
            error != null -> Text(
                "Couldn't load profiles: $error",
                style = Type.Callout.copy(color = Ink.Danger),
                modifier = Modifier.padding(horizontal = Dim.ScreenH, vertical = 12.dp),
            )
            else -> Box { LazyColumn(Modifier.fillMaxWidth().heightIn(max = 560.dp)) {
                items(rows!!, key = { it.name }) { p ->
                    val selected = p.name == current
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = Dim.SheetRow)
                            .then(if (selected) Modifier.background(Ink.Surface3) else Modifier)
                            .pressClickable {
                                app.setProfile(p.name)
                                onDismiss()
                            }
                            .padding(horizontal = Dim.ScreenH, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ProfileAvatar(app, p.name, Dim.AvatarSheet)
                        Spacer(Modifier.width(Dim.RowGap))
                        Column(Modifier.weight(1f)) {
                            OneLine(Pretty.profile(p.displayName.ifBlank { p.name }), Type.RowTitle)
                            p.model?.takeIf { it.isNotBlank() }?.let { OneLine(Pretty.model(it), Type.Meta.copy(color = Ink.Text3)) }
                        }
                        if (selected) Icon(Icons.Rounded.Check, "Active profile", tint = Ink.Text, modifier = Modifier.size(Dim.Icon))
                    }
                }
            }
                // fade bawah — baris terakhir yang kepotong kebaca "masih bisa di-scroll"
                Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(28.dp)
                    .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Ink.Surface2.copy(alpha = 0f), Ink.Surface2))))
            }
        }
        Hairline(Modifier.padding(top = 8.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = Dim.SheetRow)
                .pressClickable { onToggleHidden(!showHidden) }
                .padding(horizontal = Dim.ScreenH),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Show hidden chats", style = Type.Callout, modifier = Modifier.weight(1f))
            Switch(
                checked = showHidden,
                onCheckedChange = { onToggleHidden(it) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Ink.Bg,
                    checkedTrackColor = Ink.Text,
                    checkedBorderColor = Ink.Transparent,
                    uncheckedThumbColor = Ink.Text2,
                    uncheckedTrackColor = Ink.Surface2,
                    uncheckedBorderColor = Ink.HairlineStrong,
                ),
            )
        }
    }
}


/** Header grup: dot warna + nama + jumlah; tap = lipat/buka, long-press = edit. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GroupHeader(g: id.melvern.hermesmobile.core.store.ChatGroups.Group, count: Int, onToggle: () -> Unit, onEdit: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .combinedClickable(onClick = onToggle, onLongClick = onEdit)
            .padding(start = Dim.ScreenH, end = Dim.ScreenH, top = 18.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(Radius.Full).background(id.melvern.hermesmobile.core.store.ChatGroups.color(g.color)))
        Spacer(Modifier.width(8.dp))
        Text(g.name.uppercase(), style = Type.Caption.copy(color = Ink.Text2, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium))
        Spacer(Modifier.width(6.dp))
        Text(count.toString(), style = Type.Caption.copy(color = Ink.Text4))
        Spacer(Modifier.weight(1f))
        Icon(if (g.collapsed) Icons.Rounded.ExpandMore else Icons.Rounded.ExpandLess, if (g.collapsed) "Expand" else "Collapse",
            tint = Ink.Text4, modifier = Modifier.size(16.dp))
    }
}


/** v25: hasil cari isi pesan — judul chat + cuplikan dengan kata yang cocok ditebalkan. */
@Composable
private fun ContentHitRow(h: id.melvern.hermesmobile.core.repo.ScheduleRepo.Hit, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().pressClickable(onClick = onClick).padding(horizontal = Dim.ScreenH, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(h.title, style = Type.Callout.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium), maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text(RelTime.listStamp(h.at), style = Type.Caption)
        }
        Spacer(Modifier.height(2.dp))
        val snippet = remember(h.snippet) {
            androidx.compose.ui.text.buildAnnotatedString {
                var rest = h.snippet
                while (true) {
                    val a = rest.indexOf(">>>"); val b = rest.indexOf("<<<", a + 3)
                    if (a < 0 || b < 0) { append(rest.replace(">>>", "").replace("<<<", "")); break }
                    append(rest.substring(0, a))
                    pushStyle(androidx.compose.ui.text.SpanStyle(color = Ink.Text, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold))
                    append(rest.substring(a + 3, b)); pop()
                    rest = rest.substring(b + 3)
                }
            }
        }
        Text(snippet, style = Type.Preview.copy(color = Ink.Text3), maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
    }
}
