package id.melvern.hermesmobile.ui.sessions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.melvern.hermesmobile.HermesApp
import id.melvern.hermesmobile.core.model.SessionRow
import id.melvern.hermesmobile.core.repo.Fmt
import id.melvern.hermesmobile.core.repo.SessionRepo
import id.melvern.hermesmobile.core.rpc.ConnState
import id.melvern.hermesmobile.ui.theme.F
import id.melvern.hermesmobile.ui.theme.pressClickable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Session list — "kolom handbill": judul serif italic besar per gig,
 * meta tracked sans, hairline divider, running dot vermillion,
 * NEW GIG dashed row di bawah. Tanpa FAB (benci tombol gede).
 */
@Composable
fun SessionsScreen(app: HermesApp, onOpen: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var sessions by remember { mutableStateOf<List<SessionRow>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    val client = app.client
    val connState by client?.state?.collectAsState() ?: remember { mutableStateOf(ConnState.CLOSED) }

    LaunchedEffect(Unit) {
        while (true) {
            app.client?.let { c ->
                if (c.state.value == ConnState.OPEN) {
                    try { sessions = SessionRepo(c).listSessions() } catch (_: Throwable) {}
                    // finally-style: jangan biarkan spinner gantung kalau list gagal
                    loading = false
                }
            }
            delay(10_000)
        }
    }

    Column(Modifier.fillMaxSize().background(F.Bg).statusBarsPadding()) {
        // Header bill
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("H E R M E S", style = MaterialTheme.typography.labelLarge, color = F.Cream)
            Spacer(Modifier.weight(1f))
            Text(
                billDate(),
                style = MaterialTheme.typography.labelSmall,
                color = if (connState == ConnState.OPEN) F.Lavender else F.Error,
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
                    SessionRowView(s, onClick = { onOpen(s.id) })
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
                            val (runtimeId, storedId) = SessionRepo(c).createSession()
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

@Composable
private fun SessionRowView(s: SessionRow, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().pressClickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                s.displayTitle,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "${s.messageCount} MSG · ${Fmt.timeAgo(s.updatedAt ?: s.startedAt)}".uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = F.Lavender,
            )
        }
        if (s.running == true) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(F.Vermillion))
        }
    }
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
