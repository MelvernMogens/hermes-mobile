package id.melvern.hermesmobile.ui.sessions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.melvern.hermesmobile.HermesApp
import id.melvern.hermesmobile.core.model.SessionRow
import id.melvern.hermesmobile.core.repo.Fmt
import id.melvern.hermesmobile.core.repo.SessionRepo
import id.melvern.hermesmobile.core.rpc.ConnState
import id.melvern.hermesmobile.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SessionsScreen(app: HermesApp, onOpen: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var sessions by remember { mutableStateOf<List<SessionRow>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val client = app.client
    val connState by client?.state?.collectAsState() ?: remember { mutableStateOf(ConnState.CLOSED) }

    fun refresh() {
        val c = app.client ?: return
        scope.launch {
            try { sessions = SessionRepo(c).listSessions(); error = null }
            catch (e: Throwable) { error = e.message }
            finally { loading = false }
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            app.client?.let { c ->
                if (c.state.value == ConnState.OPEN) {
                    try { sessions = SessionRepo(c).listSessions(); error = null } catch (_: Throwable) {}
                    loading = false
                }
            }
            delay(10_000)
        }
    }

    Scaffold(
        containerColor = Bg,
        topBar = {
            TopAppBar(
                title = { Text("Sessions", color = TextPrimary, style = MaterialTheme.typography.titleLarge) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Bg),
                actions = {
                    IconButton(onClick = { refresh() }) {
                        Text("⟳", color = TextSecondary, fontSize = 20.sp)
                    }
                },
            )
        },
        floatingActionButton = {
            SmallFloatingActionButton(
                onClick = {
                    scope.launch {
                        try {
                            val c = app.client ?: return@launch
                            val id = SessionRepo(c).createSession()
                            onOpen(id)
                        } catch (e: Throwable) { error = e.message }
                    }
                },
                containerColor = Accent, contentColor = Bg,
                modifier = Modifier.size(40.dp),
            ) { Text("+", fontSize = 20.sp) }
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            ConnBanner(connState, error)
            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Accent, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
                }
                sessions.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Belum ada session", color = TextTertiary)
                }
                else -> LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    items(sessions, key = { it.id }) { s ->
                        SessionRow(s, onClick = { onOpen(s.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnBanner(state: ConnState, error: String?) {
    if (state == ConnState.OPEN && error == null) return
    val (label, color) = when {
        error != null -> (error to Danger)
        state == ConnState.RECONNECTING -> ("Menyambung ulang…" to Warn)
        state == ConnState.CONNECTING -> ("Menyambung…" to Warn)
        else -> ("Terputus" to Danger)
    }
    Box(Modifier.fillMaxWidth().background(color.copy(alpha = 0.10f)).padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(label, color = color, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SessionRow(s: SessionRow, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    s.displayTitle, style = MaterialTheme.typography.bodyLarge, color = TextPrimary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                )
                if (s.running == true) Box(Modifier.size(6.dp).background(Running, RoundedCornerShape(3.dp)))
            }
            Spacer(Modifier.height(3.dp))
            Text(
                listOfNotNull(
                    s.preview?.take(80),
                    "${s.messageCount} pesan",
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = TextTertiary,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(Fmt.timeAgo(s.updatedAt ?: s.startedAt), style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            if (!s.source.isNullOrBlank() && s.source != "desktop") {
                Text(s.source!!, fontSize = 10.sp, color = TextTertiary)
            }
        }
    }
}
