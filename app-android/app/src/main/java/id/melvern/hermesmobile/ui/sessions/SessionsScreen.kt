package id.melvern.hermesmobile.ui.sessions

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
import androidx.compose.material.icons.rounded.Edit
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
fun SessionsScreen(app: HermesApp, onOpen: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    // M15: adaptive — two-pane HANYA Expanded width (>=840dp); Compact/Medium
    // perilaku lama (nav ke ChatScreen). Selection di level screen ini, bukan
    // navigasi — rememberSaveable supaya selamat rotate (dan Compact↔Expanded
    // tidak menghapusnya).
    val winSize = id.melvern.hermesmobile.ui.layout.currentWinSize()
    var paneSelection by rememberSaveable { mutableStateOf<String?>(null) }
    fun openChat(arg: String) {
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
    var creating by remember { mutableStateOf(false) }
    val client = app.client
    val connState by client?.state?.collectAsState() ?: remember { mutableStateOf(ConnState.CLOSED) }
    val profile by app.profile.collectAsState()
    val ctx = LocalContext.current

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
    LaunchedEffect(Unit) {
        val inbound = app.client?.inbound ?: return@LaunchedEffect
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
    val visibleRows = filtered.filterNot { it.id in hiddenIds }
    val hiddenRows = filtered.filter { it.id in hiddenIds }


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
                        .padding(start = 8.dp)
                        .clip(Radius.Full)
                        .pressClickable { profileSheet = true }
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AvatarWithStatus(app, profile, connState)
                    Spacer(Modifier.width(8.dp))
                    OneLine(profile, Type.MetaMedium)
                }
                Text(
                    "Chats",
                    style = Type.Title,
                    modifier = Modifier.align(Alignment.Center).graphicsLayer { alpha = smallTitleAlpha },
                )
                Row(Modifier.align(Alignment.CenterEnd).padding(end = 4.dp)) {
                    QuietIconButton(Icons.Rounded.Search, "Search chats", onClick = { searching = true })
                    QuietIconButton(Icons.Rounded.Edit, "New chat", onClick = { newChat() }, enabled = !creating)
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
                items(visibleRows, key = { it.id }) { s ->
                    SessionRowView(
                        app, s,
                        running = s.id in active || s.running == true,
                        onClick = { openChat("${s.id}|t=${Uri.encode(s.displayTitle)}") },
                        onLongPress = { actionTarget = s },
                    )
                }
                if (hiddenRows.isNotEmpty()) {
                    item(key = "hidden-header") {
                        Text(
                            "Hidden",
                            style = Type.MetaMedium,
                            modifier = Modifier.padding(start = Dim.ScreenH, end = Dim.ScreenH, top = 20.dp, bottom = 6.dp),
                        )
                    }
                    items(hiddenRows, key = { "h-" + it.id }) { s ->
                        SessionRowView(
                            app, s,
                            running = s.id in active || s.running == true,
                            onClick = { openChat("${s.id}|t=${Uri.encode(s.displayTitle)}") },
                            onLongPress = { actionTarget = s },
                        )
                    }
                }
                if (searching && filtered.isEmpty()) item(key = "no-results") {
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
    Column(Modifier.fillMaxWidth().padding(horizontal = Dim.ScreenH).padding(top = 4.dp, bottom = 8.dp)) {
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
        ConnState.OPEN -> Ink.Live
        ConnState.CLOSED -> Ink.Danger
        else -> Ink.Warn
    }
    Box {
        ProfileAvatar(app, profile, Dim.AvatarBar)
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 2.dp, y = 2.dp)
                .size(Dim.Dot + 4.dp)
                .clip(Radius.Full)
                .background(Ink.Bg),
            contentAlignment = Alignment.Center,
        ) { StatusDot(dot) }
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
        Text("Start one from the pencil icon", style = Type.Callout.copy(color = Ink.Text2), textAlign = TextAlign.Center)
        Spacer(Modifier.height(64.dp)) // optik: sedikit di atas tengah
    }
}

/** Baris ke-2: preview pesan terakhir; kalau kosong/sama dengan judul → "Desktop · 509 messages". */
internal fun secondLine(s: SessionRow): String {
    val preview = s.preview?.replace('\n', ' ')?.trim().orEmpty()
    val title = s.displayTitle.removeSuffix("…").trim()
    val usable = preview.isNotEmpty() && !preview.startsWith(title)
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
            // avatar 44 — ring 2dp live kalau running
            Box(
                Modifier
                    .size(Dim.AvatarRow)
                    .then(if (running) Modifier.border(Dim.RunningRing, Ink.Live, Radius.Full) else Modifier),
                contentAlignment = Alignment.Center,
            ) {
                ProfileAvatar(
                    app, s.profile?.takeIf { it.isNotBlank() } ?: app.profile.value,
                    if (running) Dim.AvatarRow - 8.dp else Dim.AvatarRow,
                )
            }
            Spacer(Modifier.width(Dim.RowGap))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OneLine(s.displayTitle, Type.Title, Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    Text(RelTime.listStamp(s.updatedAt ?: s.startedAt), style = Type.Meta.copy(color = Ink.Text3), maxLines = 1)
                }
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OneLine(secondLine(s), Type.Callout.copy(color = Ink.Text2), Modifier.weight(1f))
                    if (running) {
                        Spacer(Modifier.width(8.dp))
                        Text("Running", style = Type.Meta.copy(color = Ink.Live), maxLines = 1)
                    }
                }
            }
        }
        Hairline(Modifier.align(Alignment.BottomStart).padding(start = Dim.RowDividerInset))
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
            else -> LazyColumn(Modifier.fillMaxWidth().heightIn(max = 440.dp)) {
                items(rows!!, key = { it.name }) { p ->
                    val selected = p.name == current
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = Dim.SheetRow)
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
                            OneLine(p.displayName.ifBlank { p.name }, Type.Title)
                            p.model?.takeIf { it.isNotBlank() }?.let { OneLine(it, Type.Meta) }
                        }
                        if (selected) Icon(Icons.Rounded.Check, "Active profile", tint = Ink.Text, modifier = Modifier.size(Dim.Icon))
                    }
                }
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
                    checkedThumbColor = Ink.Text,
                    checkedTrackColor = Ink.Text.copy(alpha = 0.3f),
                    checkedBorderColor = Ink.Transparent,
                    uncheckedThumbColor = Ink.Text2,
                    uncheckedTrackColor = Ink.Surface2,
                    uncheckedBorderColor = Ink.HairlineStrong,
                ),
            )
        }
    }
}
