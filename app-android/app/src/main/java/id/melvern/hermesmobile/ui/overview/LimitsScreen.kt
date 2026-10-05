package id.melvern.hermesmobile.ui.overview

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import id.melvern.hermesmobile.HermesApp
import id.melvern.hermesmobile.core.repo.LimitsRepo
import id.melvern.hermesmobile.ui.theme.Dim
import id.melvern.hermesmobile.ui.theme.Ink
import id.melvern.hermesmobile.ui.theme.Radius
import id.melvern.hermesmobile.ui.theme.Type
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Tab "Limits": limit plan per provider (sesi 5 jam / mingguan — angka sama dengan
 * /usage di Hermes) + RAM Mac. Auto-refresh 30 dtk selama tab terbuka.
 * Bar putih; >= 80% oranye, >= 95% merah (warna = status).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LimitsScreen(app: HermesApp) {
    val scope = rememberCoroutineScope()
    var data by remember { mutableStateOf<LimitsRepo.Limits?>(null) }
    var failed by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }

    suspend fun load() {
        val conn = app.connection ?: run { failed = true; return }
        val got = try { LimitsRepo(conn).fetch() } catch (_: Throwable) { null }
        if (got != null) { data = got; failed = false } else if (data == null) failed = true
        now = System.currentTimeMillis()
    }
    LaunchedEffect(Unit) { while (true) { load(); delay(30_000) } }

    val ptr = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = { refreshing = true; scope.launch { load(); refreshing = false } },
        state = ptr,
        indicator = {
            PullToRefreshDefaults.Indicator(
                state = ptr, isRefreshing = refreshing, containerColor = Ink.Raised, color = Ink.Text,
                modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding(),
            )
        },
        modifier = Modifier.fillMaxSize().background(Ink.Bg),
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Text("Limits", style = Type.Display,
                modifier = Modifier.padding(horizontal = Dim.ScreenH).padding(top = 6.dp, bottom = 4.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                Column(
                    Modifier.weight(1f, fill = false).widthIn(max = 640.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = Dim.GroupInset).padding(top = 14.dp, bottom = 40.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    val d = data
                    when {
                        d == null && failed -> Text("Couldn't reach your Mac", style = Type.Meta,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 24.dp))
                        d == null -> repeat(3) { SkeletonCard() }
                        else -> {
                            d.ram?.let { RamCard(it) }
                            if (d.plans.isEmpty()) {
                                Text("No plan limits reported", style = Type.Meta, modifier = Modifier.padding(8.dp))
                            }
                            d.plans.forEach { PlanCard(it, now) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(Radius.Card).background(Ink.Surface1)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) { content() }
}

@Composable
private fun RamCard(r: LimitsRepo.Ram) {
    Card {
        CardTitle("Mac memory", null)
        Spacer(Modifier.height(12.dp))
        Meter(
            label = "${LimitsRepo.gb(r.usedBytes)} of ${LimitsRepo.gb(r.totalBytes)}",
            percent = r.usedPercent,
            sub = r.swapUsedBytes?.takeIf { it > 256L * 1024 * 1024 }?.let { "Swap ${LimitsRepo.gb(it)}" },
        )
    }
}

@Composable
private fun PlanCard(p: LimitsRepo.Plan, now: Long) {
    Card {
        CardTitle(p.label, p.plan)
        p.windows.forEach { w ->
            Spacer(Modifier.height(12.dp))
            Meter(label = windowLabel(w.label), percent = w.usedPercent, sub = LimitsRepo.resetIn(w.resetsAtEpochMs, now))
        }
        p.note?.let {
            Spacer(Modifier.height(10.dp))
            // "use /usage reset" = perintah terminal → di HP cukup infonya
            Text(it.replace(Regex("""\s*-\s*use /usage reset.*$"""), "").replace(" - ", " — "),
                style = Type.Caption.copy(color = Ink.Text3))
        }
    }
}

@Composable
private fun CardTitle(title: String, badge: String?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = Type.Callout.copy(fontWeight = FontWeight.SemiBold))
        badge?.let {
            Spacer(Modifier.padding(start = 8.dp))
            Text(it, style = Type.Caption.copy(color = Ink.Text2),
                modifier = Modifier.clip(Radius.Full).background(Ink.Surface3).padding(horizontal = 8.dp, vertical = 2.dp))
        }
    }
}

/** Baris: label kiri, % kanan, bar 6dp, keterangan reset di bawah. */
@Composable
private fun Meter(label: String, percent: Double, sub: String?) {
    val pct = percent.coerceIn(0.0, 100.0)
    val color = when {
        pct >= 95 -> Ink.Danger
        pct >= 80 -> Ink.Warn
        else -> Ink.Text
    }
    val anim by animateFloatAsState((pct / 100.0).toFloat(), tween(500), label = "meter")
    Column {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(label, style = Type.Callout.copy(color = Ink.Text2), modifier = Modifier.weight(1f))
            Text("${Math.round(pct)}%", style = Type.Callout.copy(color = if (pct >= 80) color else Ink.Text, fontWeight = FontWeight.SemiBold))
        }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().height(6.dp).clip(Radius.Full).background(Color(0xFF34343A))) {
            Box(Modifier.fillMaxWidth(anim.coerceAtLeast(if (pct > 0) 0.01f else 0f)).height(6.dp).clip(Radius.Full).background(color))
        }
        sub?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, style = Type.Caption.copy(color = Ink.Text3))
        }
    }
}

@Composable
private fun SkeletonCard() {
    Box(Modifier.fillMaxWidth().height(112.dp).clip(Radius.Card).background(Ink.Surface1))
}

/** Label jendela seragam antar provider: Session / Week (+ varian model). */
private fun windowLabel(raw: String): String = when (raw.lowercase()) {
    "current session", "session", "5h", "five hour" -> "Session (5h)"
    "current week", "weekly", "week" -> "Week"
    else -> raw.removePrefix("Current ").replaceFirstChar { it.uppercase() }
}
