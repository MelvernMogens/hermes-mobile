package id.melvern.hermesmobile.ui.overview

import androidx.compose.material.icons.outlined.AddComment
import androidx.compose.material.icons.outlined.AssignmentTurnedIn
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.foundation.background
import id.melvern.hermesmobile.ui.theme.hairline
import id.melvern.hermesmobile.ui.components.StatusPill
import id.melvern.hermesmobile.ui.components.SectionHeader
import id.melvern.hermesmobile.ui.components.Pretty
import id.melvern.hermesmobile.ui.theme.pressClickable
import id.melvern.hermesmobile.ui.components.GroupSurface
import id.melvern.hermesmobile.ui.components.GroupDivider
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.CallSplit
import androidx.compose.material.icons.rounded.AddComment
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import id.melvern.hermesmobile.HermesApp
import id.melvern.hermesmobile.core.repo.BotCard
import id.melvern.hermesmobile.core.repo.BotFleet
import id.melvern.hermesmobile.core.repo.BotStatus
import id.melvern.hermesmobile.core.repo.OverviewRepo
import id.melvern.hermesmobile.core.repo.SessionRepo
import id.melvern.hermesmobile.core.repo.UsageBarUi
import id.melvern.hermesmobile.core.repo.UsageUi
import id.melvern.hermesmobile.core.rpc.ConnState
import id.melvern.hermesmobile.ui.components.Hairline
import id.melvern.hermesmobile.ui.components.OneLine
import id.melvern.hermesmobile.ui.components.ProfileAvatar
import id.melvern.hermesmobile.ui.components.PulsingDot
import id.melvern.hermesmobile.ui.components.QuietSheet
import id.melvern.hermesmobile.ui.components.SheetActionRow
import id.melvern.hermesmobile.ui.components.SkeletonBar
import id.melvern.hermesmobile.ui.components.SkeletonSessionRow
import id.melvern.hermesmobile.ui.components.shimmerAlpha
import id.melvern.hermesmobile.ui.layout.currentWinSize
import id.melvern.hermesmobile.ui.layout.isExpanded
import id.melvern.hermesmobile.ui.theme.Dim
import id.melvern.hermesmobile.ui.theme.Ink
import id.melvern.hermesmobile.ui.theme.Radius
import id.melvern.hermesmobile.ui.theme.Type
import android.net.Uri
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * M18 Overview — fleet dashboard (status seluruh bot) + usage.
 *
 * Data (semua RPC yang udah ada, tanpa poller baru):
 *  - profiles.list include_sessions → kartu bot + canonical Bot Chat id;
 *  - status live dari snapshot poller M14 (BotFleet, recompute tiap 20s);
 *  - usage.bars → kartu dolar (fail-open → "Usage data unavailable").
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun OverviewScreen(app: HermesApp, onOpenChat: (arg: String) -> Unit) {
    val scope = rememberCoroutineScope()
    var bots by remember { mutableStateOf<List<BotCard>?>(null) }
    var usage by remember { mutableStateOf<UsageUi?>(null) }
    var usageLoaded by remember { mutableStateOf(false) }
    var tokens by remember { mutableStateOf<id.melvern.hermesmobile.core.repo.InsightsRepo.Usage?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    // spinner HANYA untuk tarikan user — auto-refresh 20s diam (dulu spinner nempel di judul)
    var userPull by remember { mutableStateOf(false) }
    var actionBot by remember { mutableStateOf<BotCard?>(null) }
    var taskFor by remember { mutableStateOf<TaskTarget?>(null) }
    // v28: as-run log — every task given to a bot, newest first (from /api/mobile-tasks)
    var tasks by remember { mutableStateOf<List<id.melvern.hermesmobile.core.repo.BotTask>?>(null) }
    var showAllTasks by remember { mutableStateOf(false) }
    val connState by app.client?.state?.collectAsState()
        ?: remember { mutableStateOf(ConnState.CLOSED) }

    fun refresh() {
        val c = app.client ?: return
        if (c.state.value != ConnState.OPEN) return
        refreshing = true
        scope.launch {
            app.connection?.let { conn -> try { id.melvern.hermesmobile.core.repo.MacRepo(conn).botWork() } catch (_: Throwable) {} }
            try { bots = OverviewRepo(c).fetchBots() } catch (_: Throwable) { if (bots == null) bots = emptyList() }
            try { usage = OverviewRepo(c).usage() } catch (_: Throwable) { usage = UsageUi.UNAVAILABLE }
            app.connection?.let { conn ->
                id.melvern.hermesmobile.core.repo.InsightsRepo(conn).usage(app.profile.value)?.let { tokens = it }
                id.melvern.hermesmobile.core.repo.TasksRepo(conn).list(60)?.let { tasks = it }
            }
            usageLoaded = true
            refreshing = false
            userPull = false
        }
    }

    // Siklus refresh 20s (profiles.list ringan; status sendiri sudah live via
    // poller M14 → BotFleet). Koneksi OPEN (termasuk pasca-reconnect) → refresh.
    // Catatan: LaunchedEffect(connState) selalu jalan sekali saat entry dengan
    // nilai kini — refresh pertama tanpa double-call.
    LaunchedEffect(Unit) { while (true) { delay(20_000); refresh() } }
    LaunchedEffect(connState) { if (connState == ConnState.OPEN) refresh() }
    // Status live antar refresh: recompute poller M14.
    LaunchedEffect(Unit) {
        BotFleet.bots.collect { fleet -> if (fleet != null) bots = fleet }
    }

    /** Tap card = buka Bot Chat canonical bot itu (pola M5: profile param). */
    fun openBotChat(bot: BotCard) {
        val id = bot.botChatStoredId ?: run { actionBot = bot; return }
        // Uri.encode (bukan URLEncoder) — MainActivity decode pakai Uri.decode;
        // URLEncoder menghasilkan '+' untuk spasi yang tidak dikonversi balik.
        onOpenChat("$id|t=${Uri.encode("Bot Chat")}" + (if (bot.isDefault) "" else "|p=${bot.name}"))
    }

    val ptrState = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = userPull && refreshing,
        onRefresh = { userPull = true; refresh() },
        state = ptrState,
        indicator = {
            PullToRefreshDefaults.Indicator(
                state = ptrState, isRefreshing = userPull && refreshing,
                containerColor = Ink.Raised, color = Ink.Text,
                modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding(),
            )
        },
        modifier = Modifier.fillMaxSize().background(Ink.Bg),
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            OverviewHeader(onGiveTask = bots?.takeIf { it.isNotEmpty() }?.let { { taskFor = TaskTarget(null) } })
            val sections: @Composable () -> Unit = {
                when (val b = bots) {
                    null -> Column { repeat(5) { SkeletonSessionRow(shimmerAlpha()) } }
                    else -> {
                        MultiviewWall(app, b, tasks, onOpen = { openBotChat(it) }, onLongPress = { actionBot = it })
                        AsRunLog(tasks, showAll = showAllTasks, onToggleAll = { showAllTasks = !showAllTasks },
                            onOpen = { t -> onOpenChat(t.chatRoute) })
                        SchedulesSection(app)
                    }
                }
            }
            val usageBlock: @Composable () -> Unit = {
                bots?.let { SummaryStrip(it, tokens) }
                UsageSection(usage, usageLoaded, tokens)
            }
            val winSize = currentWinSize()
            if (winSize.isExpanded) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = Dim.ScreenH),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Box(Modifier.widthIn(max = 480.dp).weight(1f, fill = false)) {
                        Column(Modifier.verticalScroll(rememberScrollState())) { sections(); Spacer(Modifier.height(72.dp)) }
                    }
                    Spacer(Modifier.width(24.dp))
                    Box(Modifier.widthIn(max = 420.dp).weight(1f, fill = false)) {
                        Column(Modifier.verticalScroll(rememberScrollState())) { usageBlock(); Spacer(Modifier.height(72.dp)) }
                    }
                }
            } else {
                Box(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        Column(Modifier.weight(1f, fill = false).widthIn(max = 640.dp).verticalScroll(rememberScrollState())) {
                            sections(); usageBlock(); Spacer(Modifier.height(40.dp))
                        }
                    }
                    // fade di bawah judul — kartu masuk lembut, gak kepotong keras
                    Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().height(18.dp)
                        .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Ink.Bg, Ink.Bg.copy(alpha = 0f)))))
                }
            }
        }
    }

    actionBot?.let { bot ->
        BotActionSheet(
            app, bot,
            onOpenChat = { arg -> actionBot = null; onOpenChat(arg) },
            onDismiss = { actionBot = null },
            onGiveTask = { actionBot = null; taskFor = TaskTarget(bot) },
        )
    }
    taskFor?.let { t ->
        GiveTaskSheet(
            app, bots.orEmpty(), t.bot,
            onDismiss = { taskFor = null },
            onStarted = { arg -> taskFor = null; refresh(); onOpenChat(arg) },
        )
    }
}

private data class TaskTarget(val bot: BotCard?)

@Composable
private fun OverviewHeader(onGiveTask: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().padding(start = Dim.ScreenH, end = 8.dp).padding(top = 6.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Agents", style = Type.Display, modifier = Modifier.weight(1f))
        // v28: the screen's one lit key
        if (onGiveTask != null) id.melvern.hermesmobile.ui.components.KeyCap(
            "Give task", icon = Icons.Outlined.AssignmentTurnedIn, lit = true, onClick = onGiveTask,
        )
    }
}

/** 3 angka yang gak diulang di bawah: token hari ini · 30 hari · jumlah agent. */
@Composable
private fun SummaryStrip(bots: List<BotCard>, tokens: id.melvern.hermesmobile.core.repo.InsightsRepo.Usage?) {
    val c = id.melvern.hermesmobile.core.repo.InsightsRepo
    Spacer(Modifier.height(16.dp))
    id.melvern.hermesmobile.ui.components.RackUnit("Today", Modifier.padding(horizontal = Dim.ScreenH)) {
        Row(Modifier.fillMaxWidth()) {
            Stat("Tokens today", tokens?.let { c.compact(it.todayTokens) } ?: "—", modifier = Modifier.weight(1f))
            Stat("Agents live", bots.count { it.status == BotStatus.RUNNING }.toString(), modifier = Modifier.weight(1f),
                live = bots.any { it.status == BotStatus.RUNNING })
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier, live: Boolean = false, unit: String? = null) {
    Column(modifier.padding(vertical = 4.dp)) {
        Text(label, style = Type.Caption.copy(color = Ink.Text3))
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (live) {
                id.melvern.hermesmobile.ui.components.TallyLamp(id.melvern.hermesmobile.ui.components.Tally.LIVE)
                Spacer(Modifier.width(8.dp))
            }
            Text(value, style = Type.Figure)
            if (unit != null) Text(" $unit", style = Type.MonoMeta.copy(color = Ink.Text4), modifier = Modifier.padding(bottom = 4.dp))
        }
    }
}

// ── Section Bots ────────────────────────────────────────────────────

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun BotsSection(
    app: HermesApp,
    bots: List<BotCard>,
    onOpen: (BotCard) -> Unit,
    onLongPress: (BotCard) -> Unit,
) {
    if (bots.isEmpty()) {
        Text(
            "No agents found",
            style = Type.Callout.copy(color = Ink.Text3),
            modifier = Modifier.padding(horizontal = Dim.ScreenH, vertical = 24.dp),
        )
        return
    }
    val active = bots.filter { it.status == BotStatus.RUNNING }
    val rest = bots.filter { it.status != BotStatus.RUNNING }
    if (active.isNotEmpty()) {
        SectionHeader("Working now", trailing = active.size.toString())
        GroupSurface {
            active.forEachIndexed { i, b ->
                BotCardRow(app, b, onOpen = { onOpen(b) }, onLongPress = { onLongPress(b) })
                if (i < active.lastIndex) GroupDivider(Dim.GroupPadH + Dim.AvatarSheet + 12.dp)
            }
        }
    }
    SectionHeader(if (active.isEmpty()) "All agents" else "Idle", trailing = rest.size.toString())
    GroupSurface {
        rest.forEachIndexed { i, b ->
            BotCardRow(app, b, onOpen = { onOpen(b) }, onLongPress = { onLongPress(b) })
            if (i < rest.lastIndex) GroupDivider(Dim.GroupPadH + Dim.AvatarSheet + 12.dp)
        }
    }
}

@androidx.compose.foundation.ExperimentalFoundationApi
@Composable
private fun BotCardRow(
    app: HermesApp,
    bot: BotCard,
    onOpen: () -> Unit,
    onLongPress: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onOpen, onLongClick = onLongPress)
            .heightIn(min = 64.dp)
            .padding(horizontal = Dim.GroupPadH, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProfileAvatar(app, bot.name, Dim.AvatarSheet)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            OneLine(Pretty.profile(bot.label), Type.RowTitle)
            val task = bot.task
            if (task != null) {
                Spacer(Modifier.height(2.dp))
                OneLine(
                    task + (bot.taskSecs?.let { " · " + id.melvern.hermesmobile.core.repo.FleetLogic.duration(it) } ?: ""),
                    Type.Meta.copy(color = Ink.Text2),
                )
            } else bot.model?.let {
                Spacer(Modifier.height(2.dp))
                OneLine(Pretty.model(it), Type.Meta.copy(color = Ink.Text3))
            }
        }
        when (bot.status) {
            BotStatus.RUNNING -> StatusPill("Running", live = true)
            BotStatus.IDLE -> StatusPill("Idle", live = false)
            BotStatus.OFFLINE -> {}
        }
    }
}

// ── Section Usage ───────────────────────────────────────────────────

@Composable
private fun UsageSection(usage: UsageUi?, loaded: Boolean, tokens: id.melvern.hermesmobile.core.repo.InsightsRepo.Usage?) {
    Column {
        when {
            usage?.available == true -> {
                SectionHeader("Billing")
                UsageCard(usage)
            }
            tokens != null && tokens.providers.isNotEmpty() -> {
                Spacer(Modifier.height(12.dp))
                TokenCard(tokens)
            }
            !loaded -> { SectionHeader("Usage"); SkeletonUsageCard(shimmerAlpha()) }
            else -> {}
        }
    }
}

/** Token per provider: angka total besar + bar proporsi (bukan dolar — key-based accounts). */
@Composable
private fun TokenCard(u: id.melvern.hermesmobile.core.repo.InsightsRepo.Usage) {
    val total = u.totalTokens.coerceAtLeast(1)
    val providers = u.providers.sortedByDescending { it.tokens }
    // tones step clearly apart (white → mid → dim) so legend + bar read without colour
    val shades = listOf(Ink.Text, Ink.Text2.copy(alpha = 0.75f), Ink.Text4, Ink.KeyBezel, Ink.LampOff)
    id.melvern.hermesmobile.ui.components.RackUnit(
        "Usage · ${u.days} days", Modifier.padding(horizontal = Dim.ScreenH),
        trailing = { Text("${u.totalSessions} chats", style = Type.Timecode.copy(color = Ink.Text3)) },
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(id.melvern.hermesmobile.core.repo.InsightsRepo.compact(u.totalTokens), style = Type.Figure)
            Text(" tokens", style = Type.Meta.copy(color = Ink.Text3), modifier = Modifier.padding(bottom = 4.dp))
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth().height(10.dp).clip(Radius.Led).background(Ink.LampOff)) {
            providers.forEachIndexed { i, p ->
                val f = p.tokens.toFloat() / total
                if (f > 0.004f) Box(Modifier.weight(f).fillMaxSize().padding(end = 2.dp).background(shades[i.coerceAtMost(shades.lastIndex)]))
            }
        }
        Spacer(Modifier.height(12.dp))
        providers.forEachIndexed { i, p ->
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(width = Dim.LampW, height = Dim.LampH).clip(Radius.Led)
                    .background(shades[i.coerceAtMost(shades.lastIndex)]).border(1.dp, Ink.KeyBezel, Radius.Led))
                Spacer(Modifier.width(10.dp))
                Text(id.melvern.hermesmobile.core.repo.InsightsRepo.providerName(p.provider), style = Type.Callout, modifier = Modifier.weight(1f))
                Text(id.melvern.hermesmobile.core.repo.InsightsRepo.compact(p.tokens), style = Type.FigureSmall)
                Text(
                    (p.tokens * 1000 / total).let { pm -> if (pm < 10) "<1%" else "${(pm + 5) / 10}%" },
                    style = Type.Timecode.copy(color = Ink.Text3),
                    modifier = Modifier.widthIn(min = 48.dp).padding(start = 10.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.End,
                )
            }
        }
    }
}

@Composable
private fun SkeletonUsageCard(alpha: Float) {
    Column(
        Modifier
            .padding(horizontal = Dim.GroupInset)
            .fillMaxWidth()
            .clip(Radius.Group)
            .background(Ink.Surface1)
            .padding(horizontal = 16.dp, vertical = 16.dp),
    ) {
        SkeletonBar(120.dp, 20.dp, alpha)
        Spacer(Modifier.height(10.dp))
        SkeletonBar(null, 6.dp, alpha)
    }
}

@Composable
private fun UsageCard(u: UsageUi) {
    Column(
        Modifier
            .padding(horizontal = Dim.GroupInset)
            .fillMaxWidth()
            .clip(Radius.Group)
            .background(Ink.Surface1)
            .padding(horizontal = 16.dp, vertical = 16.dp),
    ) {
        u.plan?.let { bar ->
            UsageBarBlock(bar, big = true)
        }
        u.topup?.let { t ->
            if (u.plan != null) Spacer(Modifier.height(14.dp))
            UsageBarBlock(t, big = u.plan == null)
        }
        val period = listOfNotNull(u.planName, u.renews).joinToString(" · ")
        if (period.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text(period, style = Type.Meta.copy(color = Ink.Text3))
        }
    }
}

/** Angka besar (title/display) + label + track bar surface2 (Quiet Mono). */
@Composable
private fun UsageBarBlock(bar: UsageBarUi, big: Boolean) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(bar.spent.ifBlank { "$0" }, style = if (big) Type.Figure else Type.FigureSmall)
        Spacer(Modifier.width(8.dp))
        Text("of ${bar.total}", style = Type.Meta.copy(color = Ink.Text2))
    }
    Spacer(Modifier.height(10.dp))
    UsageFill(bar.fill)
    Spacer(Modifier.height(6.dp))
    Row(Modifier.fillMaxWidth()) {
        Text(bar.label, style = Type.Meta.copy(color = Ink.Text2))
        bar.pctUsed?.let { p ->
            Spacer(Modifier.weight(1f))
            Text("$p%", style = Type.Meta.copy(color = Ink.Text2))
        }
    }
}

/** Track bar dolar: container hairline + fill surface2. */
@Composable
private fun UsageFill(fraction: Float) {
    Box(Modifier.fillMaxWidth().height(6.dp).clip(Radius.Full).background(Ink.Hairline)) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(6.dp)
                .clip(Radius.Full)
                .background(Ink.Surface2),
        )
    }
}

// ── Sheet aksi bot (long-press) ─────────────────────────────────────

/**
 * Long-press kartu bot: New chat / Open Bot Chat / Switch to this profile.
 * New chat = session.create dengan params.profile (M5); switch = setProfile
 * (persist DataStore, semua RPC berikutnya pakai profile itu).
 */
@Composable
private fun BotActionSheet(
    app: HermesApp,
    bot: BotCard,
    onOpenChat: (arg: String) -> Unit,
    onDismiss: () -> Unit,
    onGiveTask: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val current by app.profile.collectAsState()
    var busy by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }

    fun run(action: suspend () -> Unit) {
        busy = true; notice = null
        scope.launch {
            try { action() } catch (e: Throwable) { notice = e.message ?: "Something went wrong" }
            busy = false
        }
    }

    fun botChatArg(): String =
        // Uri.encode — konsisten dengan decoder MainActivity (Uri.decode).
        "${bot.botChatStoredId}|t=${Uri.encode("Bot Chat")}" + (if (bot.isDefault) "" else "|p=${bot.name}")

    QuietSheet(onDismiss = onDismiss, title = Pretty.profile(bot.label)) {
        notice?.let {
            Text(it, style = Type.Meta.copy(color = Ink.Danger), modifier = Modifier.padding(horizontal = Dim.ScreenH).padding(bottom = 8.dp))
        }
        SheetActionRow("Give task", Icons.Outlined.AssignmentTurnedIn, enabled = !busy) { onGiveTask() }
        SheetActionRow("New chat", Icons.Outlined.AddComment, enabled = !busy) {
            run {
                val c = app.client ?: return@run
                // chat baru DI profile bot itu (params.profile — pola SessionRepo M4)
                val (runtimeId, storedId) = SessionRepo(c, bot.name).createSession()
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main.immediate) {
                    onDismiss()
                    onOpenChat("$storedId|$runtimeId" + (if (bot.isDefault) "" else "|p=${bot.name}"))
                }
            }
        }
        SheetActionRow(
            "Open Bot Chat",
            Icons.AutoMirrored.Outlined.Chat,
            enabled = !busy && bot.botChatStoredId != null,
        ) {
            onDismiss()
            onOpenChat(botChatArg())
        }
        SheetActionRow("Switch to this profile", Icons.Outlined.SwapHoriz, enabled = !busy && bot.name != current) {
            run {
                app.setProfile(bot.name)
                // Main.immediate: setProfile selesai SEBELUM dismiss — dismiss
                // membuang coroutineScope sheet, switch bisa silent-fail.
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main.immediate) {
                    onDismiss()
                }
            }
        }
    }
}


// ── v28 Multiview wall ──────────────────────────────────────────────

/**
 * One monitor per agent, 2-up. Live monitors are lit (lamp + live timecode +
 * the task on screen); idle monitors sit dim with the model and last task.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun MultiviewWall(
    app: HermesApp,
    bots: List<BotCard>,
    tasks: List<id.melvern.hermesmobile.core.repo.BotTask>?,
    onOpen: (BotCard) -> Unit,
    onLongPress: (BotCard) -> Unit,
) {
    if (bots.isEmpty()) {
        Text("No agents found", style = Type.Callout.copy(color = Ink.Text3),
            modifier = Modifier.padding(horizontal = Dim.ScreenH, vertical = 24.dp))
        return
    }
    val live = bots.count { it.status == BotStatus.RUNNING }
    id.melvern.hermesmobile.ui.components.LogHeader("Multiview", trailing = if (live > 0) "$live live · ${bots.size}" else "${bots.size}")
    // live first, then idle — the wall reads left→right, top→bottom by activity
    val ordered = bots.sortedWith(compareBy({ it.status != BotStatus.RUNNING }, { !it.isDefault }, { it.label }))
    Column(Modifier.padding(horizontal = Dim.ScreenH), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ordered.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                pair.forEach { b ->
                    val lastTask = tasks?.firstOrNull { it.profile == b.name }
                    BotMonitor(app, b, lastTask, Modifier.weight(1f), onOpen = { onOpen(b) }, onLongPress = { onLongPress(b) })
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun BotMonitor(
    app: HermesApp,
    b: BotCard,
    lastTask: id.melvern.hermesmobile.core.repo.BotTask?,
    modifier: Modifier,
    onOpen: () -> Unit,
    onLongPress: () -> Unit,
) {
    val running = b.status == BotStatus.RUNNING
    val tally = when {
        running -> id.melvern.hermesmobile.ui.components.Tally.LIVE
        b.status == BotStatus.OFFLINE -> id.melvern.hermesmobile.ui.components.Tally.OFF
        else -> id.melvern.hermesmobile.ui.components.Tally.OFF
    }
    id.melvern.hermesmobile.ui.components.MonitorTile(
        modifier = modifier.height(Dim.WallTileH),
        tally = tally,
        onClick = onOpen,
        onLongClick = onLongPress,
        umd = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                id.melvern.hermesmobile.ui.components.TallyLamp(tally)
                Spacer(Modifier.width(5.dp))
                OneLine(Pretty.profile(b.label), Type.Umd, Modifier.weight(1f))
                Spacer(Modifier.width(6.dp))
                if (running) {
                    val start = b.taskSecs?.let { System.currentTimeMillis() / 1000.0 - it }
                    if (start != null) id.melvern.hermesmobile.ui.components.LiveTimecode(start, Type.Timecode.copy(color = Ink.Text))
                    else Text("On air", style = Type.Caption.copy(color = Ink.Live))
                } else {
                    Text("Idle", style = Type.Caption.copy(color = Ink.Text3))
                }
            }
        },
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProfileAvatar(app, b.name, 26.dp)
                Spacer(Modifier.weight(1f))
                b.model?.let { Text(Pretty.model(it), style = Type.Catalog, maxLines = 1) }
            }
            Spacer(Modifier.height(8.dp))
            val body = when {
                running && b.task != null -> b.task
                running -> "Working in a chat"
                lastTask != null -> "Last: " + lastTask.title
                else -> "Ready for a task"
            }
            Text(
                body,
                style = Type.Meta.copy(color = if (running) Ink.Text else Ink.Text2),
                maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        }
    }
}

// ── v28 As-run log (bot task history) ───────────────────────────────

@Composable
private fun AsRunLog(
    tasks: List<id.melvern.hermesmobile.core.repo.BotTask>?,
    showAll: Boolean,
    onToggleAll: () -> Unit,
    onOpen: (id.melvern.hermesmobile.core.repo.BotTask) -> Unit,
) {
    val list = tasks ?: return
    val shown = if (showAll) list else list.take(6)
    id.melvern.hermesmobile.ui.components.LogHeader(
        "Task log", trailing = list.size.toString(),
        action = if (list.size > 6) (if (showAll) "Less" else "All") else null,
        onAction = if (list.size > 6) onToggleAll else null,
    )
    if (list.isEmpty()) {
        Text(
            "Tasks you give a bot show up here with their result.",
            style = Type.Callout.copy(color = Ink.Text3),
            modifier = Modifier.padding(horizontal = Dim.ScreenH, vertical = 8.dp),
        )
        return
    }
    val now = System.currentTimeMillis() / 1000.0
    Column(
        Modifier
            .padding(horizontal = Dim.ScreenH)
            .fillMaxWidth()
            .clip(Radius.Rack)
            .border(1.dp, Ink.Bezel, Radius.Rack),
    ) {
        shown.forEachIndexed { i, t ->
            val tally = when (t.status) {
                "running" -> id.melvern.hermesmobile.ui.components.Tally.LIVE
                "failed" -> id.melvern.hermesmobile.ui.components.Tally.FAULT
                "stopped" -> id.melvern.hermesmobile.ui.components.Tally.WAIT
                else -> id.melvern.hermesmobile.ui.components.Tally.OFF
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .pressClickable { onOpen(t) }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.Top,
            ) {
                // time column (mono) — when it went on air
                Column(Modifier.width(54.dp)) {
                    Text(id.melvern.hermesmobile.ui.components.RelTime.clock(t.startedAt), style = Type.Timecode.copy(color = Ink.Text2))
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (t.isRunning) id.melvern.hermesmobile.ui.components.Timecode.short((now - t.startedAt).toLong())
                        else id.melvern.hermesmobile.ui.components.Timecode.short(((t.endedAt ?: t.lastActivity) - t.startedAt).toLong()),
                        style = Type.Catalog,
                    )
                }
                Box(Modifier.padding(top = 4.dp)) { id.melvern.hermesmobile.ui.components.TallyLamp(tally) }
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(Pretty.profile(t.profile), style = Type.MetaMedium.copy(color = Ink.Text))
                        Text(
                            "  " + when (t.status) { "running" -> "On air"; "done" -> "Done"; "failed" -> "Failed"; else -> "Stopped" },
                            style = Type.Caption.copy(color = when (t.status) {
                                "running" -> Ink.Live; "failed" -> Ink.Danger; "stopped" -> Ink.Warn; else -> Ink.Text3
                            }),
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(t.title, style = Type.Callout, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    if (t.result.isNotBlank()) {
                        Spacer(Modifier.height(3.dp))
                        Text(t.result, style = Type.Meta.copy(color = Ink.Text3), maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    }
                }
            }
            if (i < shown.lastIndex) Hairline(Modifier.padding(start = 12.dp))
        }
    }
}
