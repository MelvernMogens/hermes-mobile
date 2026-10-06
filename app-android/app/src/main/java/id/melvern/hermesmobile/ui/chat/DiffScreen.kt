package id.melvern.hermesmobile.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import id.melvern.hermesmobile.HermesApp
import id.melvern.hermesmobile.core.repo.MacRepo
import id.melvern.hermesmobile.ui.components.QuietIconButton
import id.melvern.hermesmobile.ui.theme.Dim
import id.melvern.hermesmobile.ui.theme.Ink
import id.melvern.hermesmobile.ui.theme.Type
import id.melvern.hermesmobile.ui.theme.pressClickable

/**
 * v24 Code changes: repo yang disentuh agent di chat ini → daftar file (+/−) dan
 * patch per file (hijau/merah redup, mono, scroll horizontal). Read-only.
 */
@Composable
fun DiffScreen(app: HermesApp, candidates: List<String>, since: Double?, onClose: () -> Unit) {
    var diffs by remember { mutableStateOf<List<MacRepo.Diff>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(candidates) {
        val conn = app.connection ?: run { error = "Not connected"; return@LaunchedEffect }
        val repo = MacRepo(conn)
        val seen = HashSet<String>()
        val out = ArrayList<MacRepo.Diff>()
        for (p in candidates.take(12)) {
            val d = try { repo.diff(p, since) } catch (_: Throwable) { null } ?: continue
            if (seen.add(d.repo)) out += d
            if (out.size >= 4) break
        }
        diffs = out
        if (out.isEmpty()) error = if (candidates.isEmpty()) "This chat didn't touch any files yet." else "No git repo found for the files in this chat."
    }
    Column(Modifier.fillMaxSize().background(Ink.Bg).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(Dim.TopBar).padding(end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            QuietIconButton(Icons.Rounded.Close, "Close", onClick = onClose)
            Text("Code changes", style = Type.Title, modifier = Modifier.weight(1f))
        }
        val ds = diffs
        when {
            ds == null -> Text("Loading…", style = Type.Callout.copy(color = Ink.Text3), modifier = Modifier.padding(Dim.ScreenH))
            ds.isEmpty() -> Text(error ?: "No changes", style = Type.Callout.copy(color = Ink.Text3), modifier = Modifier.padding(Dim.ScreenH))
            else -> LazyColumn(Modifier.fillMaxSize()) {
                ds.forEach { d ->
                    val split = MacRepo.splitPatch(d.patch).toMap()
                    item(key = "h-" + d.repo) {
                        Column(Modifier.padding(horizontal = Dim.ScreenH, vertical = 10.dp)) {
                            Text(d.name, style = Type.Title)
                            Text(
                                listOfNotNull(d.branch.ifBlank { null }, "${d.files.size} files",
                                    "+${d.files.sumOf { it.added }} −${d.files.sumOf { it.deleted }}",
                                    d.commits.size.takeIf { it > 0 }?.let { "$it commits" }).joinToString(" · "),
                                style = Type.Caption.copy(color = Ink.Text3),
                            )
                            d.commits.take(5).forEach { c ->
                                Text("${c.hash}  ${c.subject}", style = Type.MonoMeta.copy(color = Ink.Text2), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    if (d.files.isEmpty()) item { Text("Working tree clean", style = Type.Caption.copy(color = Ink.Text3), modifier = Modifier.padding(horizontal = Dim.ScreenH)) }
                    items(d.files, key = { d.repo + "/" + it.path }) { f -> FileBlock(f, split[f.path].orEmpty()) }
                    if (d.truncated) item { Text("Diff too large — showing the first part.", style = Type.Caption.copy(color = Ink.Warn), modifier = Modifier.padding(Dim.ScreenH)) }
                }
                item { Spacer(Modifier.height(40.dp)) }
            }
        }
    }
}

@Composable
private fun FileBlock(f: MacRepo.DiffFile, lines: List<String>) {
    var open by remember { mutableStateOf(lines.size in 1..80) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 3.dp)) {
        Row(
            Modifier.fillMaxWidth().pressClickable { open = !open }.padding(horizontal = 8.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(f.path, style = Type.Mono.copy(fontWeight = FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            when {
                f.untracked -> Text("new", style = Type.Caption.copy(color = AddInk))
                f.binary -> Text("binary", style = Type.Caption.copy(color = Ink.Text3))
                else -> {
                    Text("+${f.added}", style = Type.Caption.copy(color = AddInk))
                    Spacer(Modifier.width(6.dp))
                    Text("−${f.deleted}", style = Type.Caption.copy(color = DelInk))
                }
            }
            Spacer(Modifier.width(4.dp))
            Icon(if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, tint = Ink.Text4, modifier = Modifier.size(16.dp))
        }
        if (open && lines.isNotEmpty()) {
            Column(Modifier.fillMaxWidth().background(Ink.Surface1).horizontalScroll(rememberScrollState()).padding(vertical = 6.dp)) {
                lines.take(1500).forEach { l ->
                    val (bg, fg) = when {
                        l.startsWith("@@") -> Color.Transparent to Ink.Text3
                        l.startsWith("+") -> AddBg to AddInk
                        l.startsWith("-") -> DelBg to DelInk
                        else -> Color.Transparent to Ink.Text2
                    }
                    Text(
                        l.ifEmpty { " " }, style = Type.MonoMeta.copy(color = fg), softWrap = false,
                        modifier = Modifier.background(bg).padding(horizontal = 10.dp),
                    )
                }
            }
        }
    }
}

// Warna diff = status (hijau tambah, merah hapus) — redup, bukan neon.
private val AddInk = Color(0xFF7FCB95)
private val DelInk = Color(0xFFE48A84)
private val AddBg = Color(0x1A55BE7E)
private val DelBg = Color(0x1AE5675F)
