package id.melvern.hermesmobile.ui.overview

import androidx.compose.foundation.background
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
    var refreshing by remember { mutableStateOf(false) }
    var actionBot by remember { mutableStateOf<BotCard?>(null) }
    val connState by app.client?.state?.collectAsState()
        ?: remember { mutableStateOf(ConnState.CLOSED) }

    fun refresh() {
        val c = app.client ?: return
        if (c.state.value != ConnState.OPEN) return
        refreshing = true
        scope.launch {
            try { bots = OverviewRepo(c).fetchBots() } catch (_: Throwable) { if (bots == null) bots = emptyList() }
            try { usage = OverviewRepo(c).usage() } catch (_: Throwable) { usage = UsageUi.UNAVAILABLE }
            usageLoaded = true
            refreshing = false
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
        onOpenChat("$id|t=${Uri.encode("Bot Chat")}")
    }

    val ptrState = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = { refresh() },
        state = ptrState,
        indicator = {
            PullToRefreshDefaults.Indicator(state = ptrState, isRefreshing = refreshing)
        },
        modifier = Modifier.fillMaxSize().background(Ink.Bg),
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            OverviewHeader()
            val content: @Composable (Modifier) -> Unit = { m ->
                Column(m.verticalScroll(rememberScrollState())) {
                    when (val b = bots) {
                        null -> Column { repeat(5) { SkeletonSessionRow(shimmerAlpha()) } }
                        else -> BotsSection(app, b, onOpen = { openBotChat(it) }, onLongPress = { actionBot = it })
                    }
                    UsageSection(usage, usageLoaded)
                    Spacer(Modifier.height(72.dp))
                }
            }
            val winSize = currentWinSize()
            if (winSize.isExpanded) {
                // Tablet/landscape lebar: Bots kiri + Usage kanan, max 900dp center.
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = Dim.ScreenH),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Box(Modifier.widthIn(max = 470.dp).weight(1f, fill = false)) {
                        Column(Modifier.verticalScroll(rememberScrollState())) {
                            when (val b = bots) {
                                null -> Column { repeat(5) { SkeletonSessionRow(shimmerAlpha()) } }
                                else -> BotsSection(app, b, onOpen = { openBotChat(it) }, onLongPress = { actionBot = it })
                            }
                            Spacer(Modifier.height(72.dp))
                        }
                    }
                    Spacer(Modifier.width(32.dp))
                    Box(Modifier.widthIn(max = 430.dp).weight(1f, fill = false)) {
                        Column(Modifier.verticalScroll(rememberScrollState())) {
                            UsageSection(usage, usageLoaded)
                            Spacer(Modifier.height(72.dp))
                        }
                    }
                }
            } else {
                // Compact/Medium: satu kolom scroll, max width 640 center.
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    content(Modifier.weight(1f, fill = false).widthIn(max = 640.dp))
                }
            }
        }
    }

    actionBot?.let { bot ->
        BotActionSheet(
            app, bot,
            onOpenChat = { arg -> actionBot = null; onOpenChat(arg) },
            onDismiss = { actionBot = null },
        )
    }
}

@Composable
private fun OverviewHeader() {
    Column(Modifier.fillMaxWidth().padding(horizontal = Dim.ScreenH).padding(top = 4.dp, bottom = 8.dp)) {
        Text("Overview", style = Type.Display)
        Text(
            SimpleDateFormat("EEEE, d MMMM", Locale.US).format(Date()),
            style = Type.Meta.copy(color = Ink.Text2),
        )
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
    Column {
        SectionLabel("Bots")
        if (bots.isEmpty()) {
            Text(
                "No profiles found",
                style = androidx.compose.ui.text.TextStyle(color = Ink.Text3),
                modifier = Modifier.padding(horizontal = Dim.ScreenH, vertical = 12.dp),
            )
        }
        bots.forEach { bot -> BotCardRow(app, bot, onOpen = { onOpen(bot) }, onLongPress = { onLongPress(bot) }) }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = Type.Meta.copy(color = Ink.Text2),
        modifier = Modifier.padding(horizontal = Dim.ScreenH, vertical = 8.dp),
    )
}

@androidx.compose.foundation.ExperimentalFoundationApi
@Composable
private fun BotCardRow(
    app: HermesApp,
    bot: BotCard,
    onOpen: () -> Unit,
    onLongPress: () -> Unit,
) {
    Box(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onOpen, onLongClick = onLongPress)
                .heightIn(min = 64.dp)
                .padding(horizontal = Dim.ScreenH, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProfileAvatar(app, bot.name, Dim.AvatarSheet)
            Spacer(Modifier.width(Dim.RowGap))
            Column(Modifier.weight(1f)) {
                OneLine(bot.label, Type.Title)
                bot.model?.let {
                    Spacer(Modifier.height(2.dp))
                    OneLine(it, Type.Meta)
                }
            }
            when (bot.status) {
                BotStatus.RUNNING -> Row(verticalAlignment = Alignment.CenterVertically) {
                    PulsingDot(Ink.Live)
                    Spacer(Modifier.width(6.dp))
                    Text("Running", style = Type.Meta.copy(color = Ink.Live))
                }
                BotStatus.IDLE -> Text("Idle", style = Type.Meta.copy(color = Ink.Text2))
                BotStatus.OFFLINE -> Text("—", style = Type.Meta.copy(color = Ink.Text3))
            }
        }
        Hairline(Modifier.align(Alignment.BottomStart))
    }
}

// ── Section Usage ───────────────────────────────────────────────────

@Composable
private fun UsageSection(usage: UsageUi?, loaded: Boolean) {
    Column(Modifier.padding(top = 16.dp)) {
        SectionLabel("Usage")
        when {
            !loaded && usage == null -> SkeletonUsageCard(shimmerAlpha())
            usage?.available == true -> UsageCard(usage)
            else -> Text(
                "Usage data unavailable",
                style = Type.Meta.copy(color = Ink.Text3),
                modifier = Modifier.padding(horizontal = Dim.ScreenH, vertical = 12.dp),
            )
        }
    }
}

@Composable
private fun SkeletonUsageCard(alpha: Float) {
    Column(
        Modifier
            .padding(horizontal = Dim.ScreenH)
            .fillMaxWidth()
            .clip(Radius.Card)
            .background(Ink.Surface1)
            .padding(horizontal = 16.dp, vertical = 14.dp),
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
            .padding(horizontal = Dim.ScreenH)
            .fillMaxWidth()
            .clip(Radius.Card)
            .background(Ink.Surface1)
            .padding(horizontal = 16.dp, vertical = 14.dp),
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
        Text(bar.spent.ifBlank { "$0" }, style = if (big) Type.Display else Type.Title)
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
        "${bot.botChatStoredId}|t=${Uri.encode("Bot Chat")}"

    QuietSheet(onDismiss = onDismiss, title = bot.label) {
        notice?.let {
            Text(it, style = Type.Meta.copy(color = Ink.Danger), modifier = Modifier.padding(horizontal = Dim.ScreenH).padding(bottom = 8.dp))
        }
        SheetActionRow("New chat", Icons.Rounded.AddComment, enabled = !busy) {
            run {
                val c = app.client ?: return@run
                // chat baru DI profile bot itu (params.profile — pola SessionRepo M4)
                val (runtimeId, storedId) = SessionRepo(c, bot.name).createSession()
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main.immediate) {
                    onDismiss()
                    onOpenChat("$storedId|$runtimeId")
                }
            }
        }
        SheetActionRow(
            "Open Bot Chat",
            Icons.AutoMirrored.Rounded.CallSplit,
            enabled = !busy && bot.botChatStoredId != null,
        ) {
            onDismiss()
            onOpenChat(botChatArg())
        }
        SheetActionRow("Switch to this profile", Icons.Rounded.SwapHoriz, enabled = !busy && bot.name != current) {
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
