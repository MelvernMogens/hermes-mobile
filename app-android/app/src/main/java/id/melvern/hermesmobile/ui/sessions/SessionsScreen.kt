package id.melvern.hermesmobile.ui.sessions

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import android.net.Uri
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.melvern.hermesmobile.HermesApp
import id.melvern.hermesmobile.core.model.SessionRow
import id.melvern.hermesmobile.core.repo.Fmt
import id.melvern.hermesmobile.core.repo.MetaRepo
import id.melvern.hermesmobile.core.repo.SessionRepo
import id.melvern.hermesmobile.core.rpc.ConnState
import id.melvern.hermesmobile.ui.theme.F
import id.melvern.hermesmobile.ui.theme.Shape
import id.melvern.hermesmobile.ui.theme.pressClickable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.LocalDate

/**
 * Session list — "kolom handbill": judul serif italic besar per gig,
 * meta tracked sans, hairline divider, running dot vermillion,
 * NEW GIG dashed row di bawah. Tanpa FAB (benci tombol gede).
 *
 * M4: tap wordmark "H E R M E S" → sheet profile switcher (profiles.list);
 * long-press row session → context menu (rename / branch / hide / delete).
 */
@Composable
fun SessionsScreen(app: HermesApp, onOpen: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var sessions by remember { mutableStateOf<List<SessionRow>>(emptyList()) }
    var active by remember { mutableStateOf<Set<String>>(emptySet()) }
    var loading by remember { mutableStateOf(true) }
    var profileSheet by remember { mutableStateOf(false) }
    var actionTarget by remember { mutableStateOf<SessionRow?>(null) }
    val client = app.client
    val connState by client?.state?.collectAsState() ?: remember { mutableStateOf(ConnState.CLOSED) }
    val profile by app.profile.collectAsState()

    fun refresh() {
        val c = app.client ?: return
        if (c.state.value != ConnState.OPEN) return
        scope.launch {
            try { sessions = SessionRepo(c, profile).listSessions() } catch (_: Throwable) {}
            try { active = SessionRepo(c, profile).activeStoredIds() } catch (_: Throwable) {}
            loading = false
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            refresh()
            delay(10_000)
        }
    }
    // M4: ganti profile → refresh list langsung (list profile beda).
    // Skip siklus pertama (Unit effect sudah refresh di atas — hindari double fetch).
    var sawInitialProfile by remember { mutableStateOf(false) }
    LaunchedEffect(profile) {
        if (!sawInitialProfile) { sawInitialProfile = true; return@LaunchedEffect }
        loading = true; sessions = emptyList(); refresh()
    }

    Column(Modifier.fillMaxSize().background(F.Bg).statusBarsPadding()) {
        // Header bill — M4: tap wordmark → sheet profile
        Column(
            Modifier
                .fillMaxWidth()
                .pressClickable { profileSheet = true }
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("H E R M E S", style = MaterialTheme.typography.labelLarge, color = F.Cream)
                Spacer(Modifier.weight(1f))
                Text(
                    billDate(),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (connState == ConnState.OPEN) F.Lavender else F.Error,
                )
            }
            // M4: nama profile aktif — labelSmall di bawah wordmark
            Text(
                profile.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = F.LavenderDim,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        HorizontalDivider(color = F.Stroke, thickness = 1.dp)

        if (loading && sessions.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = F.Vermillion, strokeWidth = 2.dp, modifier = Modifier.size(26.dp))
            }
        } else if (sessions.isEmpty()) {
            // Empty state — TONIGHT
            Column(
                Modifier.weight(1f).fillMaxWidth().padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text("tonight", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                Text("belum ada gig — mulai dari bawah", style = MaterialTheme.typography.bodySmall, color = F.LavenderDim)
            }
        } else {
            LazyColumn(Modifier.weight(1f)) {
                items(sessions, key = { it.id }) { s ->
                    SessionRowView(
                        s, running = s.id in active,
                        onClick = {
                            // M3.2: bawa displayTitle — ChatScreen gak boleh nampilin ID mentah
                            onOpen("${s.id}|t=${Uri.encode(s.displayTitle)}")
                        },
                        onLongPress = { actionTarget = s },
                    )
                    HorizontalDivider(color = F.Stroke, thickness = 1.dp, modifier = Modifier.padding(horizontal = 20.dp))
                }
            }
        }

        // NEW GIG row — dashed border vermillion (tiket kosong di bawah rak poster)
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 14.dp)
                .height(52.dp)
                .pressClickable {
                    scope.launch {
                        try {
                            val c = app.client ?: return@launch
                            val (runtimeId, storedId) = SessionRepo(c, profile).createSession()
                            onOpen("$storedId|$runtimeId")
                        } catch (_: Throwable) {}
                    }
                }
                // dashed border TERAKHIR: ikut ke-scale saat press (drawBehind setelah graphicsLayer)
                .dashedBorder(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Text("+  N E W   G I G", style = MaterialTheme.typography.labelLarge, color = F.Cream)
        }
    }

    // M4: sheet profile switcher
    if (profileSheet) {
        ProfileSheet(app, onDismiss = { profileSheet = false })
    }
    // M4: context menu session
    actionTarget?.let { target ->
        SessionActionSheet(
            app = app,
            row = target,
            onDone = { actionTarget = null; refresh() },
            onDismiss = { actionTarget = null },
            onOpenBranch = { runtimeId, storedId ->
                actionTarget = null
                onOpen("$storedId|$runtimeId")
            },
        )
    }
}

/**
 * Dashed border tiket NEW GIG: vermillion 1.5dp, dash 8dp gap 6dp.
 * drawBehind + PathEffect — rounded rect stroke dashed di tepi luar row.
 */
private fun Modifier.dashedBorder(): Modifier = drawBehind {
    val stroke = 1.5.dp.toPx()
    val dash = 8.dp.toPx()
    val gap = 6.dp.toPx()
    val r = 8.dp.toPx() // radius kecil, sejajar Shape.Xs
    val path = Path().apply {
        addRoundRect(
            androidx.compose.ui.geometry.RoundRect(
                left = stroke / 2,
                top = stroke / 2,
                right = size.width - stroke / 2,
                bottom = size.height - stroke / 2,
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r),
            )
        )
    }
    drawPath(
        path,
        color = F.Vermillion,
        style = Stroke(
            width = stroke,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, gap)),
        ),
    )
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun SessionRowView(s: SessionRow, running: Boolean, onClick: () -> Unit, onLongPress: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongPress)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                s.displayTitle,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            // M3.2: meta lengkap — source (Desktop/CLI/Bot) · msg · waktu
            Text(
                "${s.sourceLabel} · ${s.messageCount} MSG · ${Fmt.timeAgo(s.updatedAt ?: s.startedAt)}".uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = F.Lavender,
            )
            if (running) {
                Spacer(Modifier.height(5.dp))
                Text(
                    "● SEDANG JALAN",
                    style = MaterialTheme.typography.labelSmall,
                    color = F.Warn,
                )
            }
        }
    }
}

/**
 * M4: sheet profile — profiles.list; pilih → app.setProfile (semua RPC
 * selanjutnya bawa params.profile) + list direfresh.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun ProfileSheet(app: HermesApp, onDismiss: () -> Unit) {
    var rows by remember { mutableStateOf<List<MetaRepo.ProfileRow>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val current by app.profile.collectAsState()
    LaunchedEffect(Unit) {
        val c = app.client ?: return@LaunchedEffect
        try { rows = MetaRepo(c).profiles() } catch (e: Throwable) { error = e.message }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = F.Surface3,
        shape = Shape.Ticket,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 28.dp)) {
            Text("PROFILE", style = MaterialTheme.typography.labelSmall, color = F.LavenderDim)
            Spacer(Modifier.height(10.dp))
            when {
                rows == null && error == null -> Box(Modifier.fillMaxWidth().height(60.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = F.Vermillion, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
                }
                error != null -> Text("Gagal memuat: $error", style = MaterialTheme.typography.bodySmall, color = F.Error)
                else -> LazyColumn(Modifier.fillMaxWidth().heightIn(max = 460.dp)) {
                    items(rows!!, key = { it.name }) { p ->
                        val selected = p.name == current
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(Shape.S)
                                .background(if (selected) F.Surface2 else androidx.compose.ui.graphics.Color.Transparent)
                                .clickable {
                                    app.setProfile(p.name)
                                    onDismiss()
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    (p.displayName.ifBlank { p.name }),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (selected) F.Cream else F.CreamDim,
                                )
                                val meta = listOfNotNull(
                                    p.name.takeIf { it != "default" },
                                    p.model?.takeIf { it.isNotBlank() },
                                    if (p.isDefault) "utama" else null,
                                ).joinToString(" · ")
                                if (meta.isNotBlank()) Text(meta, style = MaterialTheme.typography.labelSmall, color = F.Lavender)
                                if (p.description.isNotBlank()) Text(
                                    p.description, style = MaterialTheme.typography.labelSmall, color = F.LavenderDim,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (selected) Text("●", color = F.Vermillion)
                        }
                    }
                }
            }
        }
    }
}

/**
 * M4: context menu session — rename (session.title), branch (session.branch),
 * hide (session.set_hidden), delete (session.delete + konfirmasi merah).
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun SessionActionSheet(
    app: HermesApp,
    row: SessionRow,
    onDone: () -> Unit,
    onDismiss: () -> Unit,
    onOpenBranch: (String, String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var renaming by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf(row.displayTitle) }
    var confirmingDelete by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    val profile = app.profile.value

    fun run(action: suspend () -> Unit) {
        busy = true; notice = null
        scope.launch {
            try { action(); onDone() }
            catch (e: Throwable) { notice = e.message; busy = false }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = F.Surface3,
        shape = Shape.Ticket,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 28.dp)) {
            Text(row.displayTitle, style = MaterialTheme.typography.titleMedium, color = F.Cream, maxLines = 1, overflow = TextOverflow.Ellipsis)
            notice?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, style = MaterialTheme.typography.labelSmall, color = F.Error)
            }
            Spacer(Modifier.height(12.dp))

            if (renaming) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BasicTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        textStyle = TextStyle(color = F.Cream, fontSize = 15.sp),
                        cursorBrush = SolidColor(F.Vermillion),
                        singleLine = true,
                        keyboardActions = KeyboardActions(onDone = { if (newName.isNotBlank() && !busy) run { rename(app, row, newName.trim(), profile) } }),
                        modifier = Modifier
                            .weight(1f)
                            .background(F.BgDeep, Shape.S)
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                    )
                    Button(
                        onClick = { if (newName.isNotBlank() && !busy) run { rename(app, row, newName.trim(), profile) } },
                        enabled = newName.isNotBlank() && !busy,
                        colors = ButtonDefaults.buttonColors(containerColor = F.Vermillion, contentColor = F.BgDeep, disabledContainerColor = F.Surface2, disabledContentColor = F.LavenderDim),
                        shape = Shape.S,
                    ) { Text("Simpan") }
                }
            } else {
                SheetAction("Ganti nama", enabled = !busy) { renaming = true }
                SheetAction("Cabang baru", enabled = !busy) {
                    run {
                        // branch butuh session live — attach lazy dulu (session.resume lazy)
                        val c = app.client!!
                        val live = try {
                            SessionRepo(c, profile).resumeAttachLazy(row.id)
                        } catch (_: Throwable) { null }
                        val target = live ?: row.id
                        val out = MetaRepo(c).branchSession(target, profile)
                        onOpenBranch(out.runtimeId, out.storedId)
                    }
                }
                SheetAction("Sembunyikan", enabled = !busy) { run { MetaRepo(app.client!!).hideSession(row.id, profile) } }
                SheetAction("Hapus", danger = true, enabled = !busy) { confirmingDelete = true }
                if (confirmingDelete) {
                    Spacer(Modifier.height(12.dp))
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .background(F.Error.copy(alpha = 0.08f), Shape.M)
                            .border(1.dp, F.Error.copy(alpha = 0.5f), Shape.M)
                            .padding(14.dp),
                    ) {
                        Text(
                            "Hapus \"${row.displayTitle}\" beserta transcript? Gak bisa dibatalkan.",
                            style = MaterialTheme.typography.bodySmall, color = F.Cream,
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = { confirmingDelete = false },
                                modifier = Modifier.weight(1f).height(44.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = F.Surface2, contentColor = F.Cream),
                                shape = Shape.S,
                            ) { Text("Batal") }
                            Button(
                                onClick = {
                                    run {
                                        val c = app.client!!
                                        try { MetaRepo(c).deleteSession(row.id, profile) }
                                        catch (e: id.melvern.hermesmobile.core.rpc.RpcException) {
                                            if (e.code == 4023) {
                                                // session live di backend — close pakai RUNTIME id
                                                // (session.close cuma resolve runtime id, bukan stored)
                                                val runtime = SessionRepo(c, profile).resumeAttachLazy(row.id)
                                                c.call("session.close", kotlinx.serialization.json.buildJsonObject {
                                                    put("session_id", runtime ?: row.id)
                                                    if (profile.isNotBlank() && profile != "default") put("profile", profile)
                                                })
                                                MetaRepo(c).deleteSession(row.id, profile)
                                            } else throw e
                                        }
                                    }
                                },
                                enabled = !busy,
                                modifier = Modifier.weight(1f).height(44.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = F.Error, contentColor = F.BgDeep),
                                shape = Shape.S,
                            ) { Text("Hapus") }
                        }
                    }
                }
            }
        }
    }
}

/** Item aksi di sheet context menu — bill type, teks besar, tanpa ikon. */
@Composable
private fun SheetAction(label: String, danger: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.titleMedium,
        color = when {
            !enabled -> F.LavenderDim
            danger -> F.Error
            else -> F.Cream
        },
        modifier = Modifier
            .fillMaxWidth()
            .clip(Shape.S)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 14.dp),
    )
}

/**
 * Rename: session.title SET butuh runtime live — attach lazy via session.resume
 * (gak build agent, gak transfer history) lalu set title di runtime itu.
 */
private suspend fun rename(app: HermesApp, row: SessionRow, newTitle: String, profile: String) {
    val c = app.client!!
    val live = try { SessionRepo(c, profile).resumeAttachLazy(row.id) } catch (_: Throwable) { null }
    MetaRepo(c).renameSession(live ?: row.id, newTitle, profile)
}

private fun billDate(): String {
    val today = LocalDate.now()
    val dayName = DayNames[today.dayOfWeek.value - 1]
    val monthName = MonthNames[today.monthValue - 1]
    return "$dayName · ${today.dayOfMonth} $monthName"
}

// Locale("id") constructor deprecated (warning build) — hardcode nama pendek Indonesia.
private val DayNames = listOf("SEN", "SEL", "RAB", "KAM", "JUM", "SAB", "MIN")
private val MonthNames = listOf(
    "JAN", "FEB", "MAR", "APR", "MEI", "JUN", "JUL", "AGU", "SEP", "OKT", "NOV", "DES",
)
