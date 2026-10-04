package id.melvern.hermesmobile.ui.chat

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import id.melvern.hermesmobile.HermesApp
import id.melvern.hermesmobile.core.repo.MediaRepo
import id.melvern.hermesmobile.core.repo.TranscriptCache
import id.melvern.hermesmobile.ui.components.Hairline
import id.melvern.hermesmobile.ui.components.MarkdownVideo
import id.melvern.hermesmobile.ui.components.MediaFetchSave
import id.melvern.hermesmobile.ui.components.OneLine
import id.melvern.hermesmobile.ui.components.QuietIconButton
import id.melvern.hermesmobile.ui.components.RelTime
import id.melvern.hermesmobile.ui.components.SkeletonBar
import id.melvern.hermesmobile.ui.components.openUrlExternal
import id.melvern.hermesmobile.ui.components.shimmerAlpha
import id.melvern.hermesmobile.ui.theme.Dim
import id.melvern.hermesmobile.ui.theme.Ink
import id.melvern.hermesmobile.ui.theme.Radius
import id.melvern.hermesmobile.ui.theme.Type
import id.melvern.hermesmobile.ui.theme.hairline
import id.melvern.hermesmobile.ui.theme.pressClickable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * M13: ArtifactsScreen — semua link/foto/video/file yang pernah dikirim
 * agent (dan user) di session ini. Parse on-demand dari TranscriptCache,
 * tanpa RPC baru. Quiet Mono konsisten M8.
 */
@Composable
fun ArtifactsScreen(
    app: HermesApp,
    storedId: String,
    chatTitle: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // parse di background: transcript gede (187+ msg) gak boleh jank di main thread.
    // Review M13 MED#3: TranscriptCache.get() dipanggil SEKALI saja (counter
    // observability hits/misses gak boleh dobel per buka screen).
    var artifacts by remember { mutableStateOf<List<Artifact>?>(null) }
    var cachePresent by remember { mutableStateOf(false) }
    LaunchedEffect(storedId) {
        val entry = withContext(Dispatchers.Default) { TranscriptCache.get(storedId) }
        cachePresent = entry != null
        val parsed = withContext(Dispatchers.Default) { ArtifactsParser.parse(entry?.items ?: emptyList()) }
        // jendela skeleton minimal supaya gak flash kosong→isi di cache kecil
        if ((entry?.items?.size ?: 0) < 8) delay(150)
        artifacts = parsed
    }

    // preview fullscreen foto + player video
    var previewImage by remember { mutableStateOf<String?>(null) }
    var previewVideo by remember { mutableStateOf<String?>(null) }

    // M16: section tab (All default). rememberSaveable supaya rotate/ganti
    // tab gak reset saat side sheet M15 re-compose.
    var section by rememberSaveable { mutableStateOf(ArtifactSection.All) }

    Column(
        Modifier
            .fillMaxSize()
            .background(Ink.Bg)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        // ── Top bar 56dp: back + judul + subtitle nama chat ────────────
        Row(
            Modifier.fillMaxWidth().height(Dim.TopBar).padding(end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            QuietIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onClick = onBack)
            Column(Modifier.weight(1f)) {
                OneLine("Artifacts", Type.Title)
                if (chatTitle.isNotBlank()) OneLine(chatTitle, Type.Meta)
            }
        }
        Hairline()

        val list = artifacts
        // M16: tab section — selalu tampil begitu parse selesai (All default).
        // Badge ikon top bar chat TETAP total semua jenis (tidak difilter).
        if (list != null) SectionTabs(section) { section = it }
        when {
            list == null -> ArtifactSkeleton()
            // Review M13 MED#4: cache gak ada (ter-evict / app baru start) = TIDAK
            // boleh bilang "Nothing shared yet" — transcript memang belum di-memori.
            list.isEmpty() && !cachePresent -> NotLoadedArtifacts()
            else -> {
                // M16: filter pure client per section sebelum grouping hari.
                val filtered = remember(list, section) { ArtifactsParser.filter(list, section) }
                if (filtered.isEmpty()) {
                    SectionEmptyArtifacts(section)
                } else {
                val rows = remember(filtered) { ArtifactsParser.rows(filtered) { RelTime.dayLabel(it) } }
                // note hydrated: transcript yang ada di cache belum tentu full
                // (session jarang dibuka → resume lazy cuma nambah delta).
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp),
                ) {
                    items(rows, key = { it.key }) { row ->
                        when (row) {
                            is ArtifactRow.Day -> DayChip(row.label)
                            is ArtifactRow.Item -> ArtifactRowLine(row.artifact) { a ->
                                when (a.type) {
                                    Artifact.Type.Link -> openUrlExternal(context, a.value)
                                    Artifact.Type.Image -> previewImage = a.value
                                    Artifact.Type.Video -> previewVideo = a.value
                                    Artifact.Type.File -> {
                                        scope.launch {
                                            val ok = MediaFetchSave.saveAny(context, a.value)
                                            Toast.makeText(
                                                context,
                                                if (ok) "Saved to Downloads" else "Couldn't save — try again",
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                }
            }
        }
    }

    // ── Tap foto → fullscreen preview + Save ────────────────────────────
    previewImage?.let { path ->
        ImagePreviewDialog(path = path, conn = app.connection, onDismiss = { previewImage = null })
    }
    // ── Tap video → player fullwidth (reuse MarkdownVideo) ─────────────
    previewVideo?.let { path ->
        VideoPreviewDialog(path = path, conn = app.connection, onDismiss = { previewVideo = null })
    }
}

/** Baris artifact 56dp: ikon per type dalam lingkaran surface1 36dp. */
@Composable
private fun ArtifactRowLine(a: Artifact, onTap: (Artifact) -> Unit) {
    val icon: ImageVector = when (a.type) {
        Artifact.Type.Link -> Icons.Rounded.Link
        Artifact.Type.Image -> Icons.Rounded.Image
        Artifact.Type.Video -> Icons.Rounded.PlayCircle
        Artifact.Type.File -> Icons.Rounded.Description
    }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .pressClickable { onTap(a) }
            .padding(horizontal = Dim.ScreenH, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(36.dp).clip(Radius.Full).background(Ink.Surface1),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = Ink.Text2, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(a.title, style = Type.Title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            Text(shortMeta(a), style = Type.Meta, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (a.at != null) {
            Spacer(Modifier.width(8.dp))
            Text(RelTime.listStamp(a.at), style = Type.Meta.copy(color = Ink.Text3))
        }
    }
}

/** Sub meta: domain utk link / path pendek utk media+file (2 segmen terakhir + …). */
private fun shortMeta(a: Artifact): String {
    if (a.type == Artifact.Type.Link) return a.value
    val segs = a.value.split('/').filter { it.isNotBlank() }
    return when {
        segs.size <= 2 -> a.value
        else -> "…/" + segs.takeLast(2).joinToString("/")
    }
}

/** Empty state: ikon 48 text3 + judul + sub. */
@Composable
private fun EmptyArtifacts() {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Rounded.Inventory2, null, tint = Ink.Text3, modifier = Modifier.size(Dim.EmptyIcon))
        Spacer(Modifier.height(12.dp))
        Text("Nothing shared yet", style = Type.Title)
        Spacer(Modifier.height(4.dp))
        Text(
            "Links, photos, videos, and files from this chat appear here",
            style = Type.Meta,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
    }
}

/**
 * M16: empty state kecil per section — teks dari ArtifactSection.emptyText.
 * Ringkas (bukan empty state besar All) karena section lain pasti menampilkan
 * konten di tab lain — ini cuma penanda "belum ada jenis ini".
 */
@Composable
private fun SectionEmptyArtifacts(section: ArtifactSection) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = Dim.ScreenH, vertical = 48.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(section.emptyText, style = Type.Title)
        Spacer(Modifier.height(4.dp))
        Text("Switch tabs to see the rest", style = Type.Meta.copy(color = Ink.Text3))
    }
}

/**
 * M16: tab section — segmented 5 tombol teks meta (gaya Effort chips M9):
 * track Surface1 + hairline border, aktif bg putih/teks hitam. Pure client,
 * All = gabungan tanpa filter.
 */
@Composable
private fun SectionTabs(section: ArtifactSection, onPick: (ArtifactSection) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Dim.ScreenH, vertical = 10.dp)
            .clip(Radius.Chip)
            .background(Ink.Surface1)
            .border(hairline(), Ink.Hairline, Radius.Chip)
            .padding(3.dp),
    ) {
        ArtifactSection.entries.forEach { s ->
            val active = s == section
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 32.dp)
                    .clip(Radius.Inline)
                    .then(if (active) Modifier.background(Ink.Accent) else Modifier)
                    .pressClickable { onPick(s) }
                    .padding(vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    s.label,
                    style = Type.Meta.copy(
                        color = if (active) Ink.OnAccent else Ink.Text2,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    ),
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * Review M13 MED#4: transcript belum ada di memori (cache ter-evict / app baru
 * start) — JANGAN bilang "Nothing shared yet". Buka chatnya dulu supaya
 * transcript ke-hydrate, lalu balik ke sini.
 */
@Composable
private fun NotLoadedArtifacts() {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Rounded.Inventory2, null, tint = Ink.Text3, modifier = Modifier.size(Dim.EmptyIcon))
        Spacer(Modifier.height(12.dp))
        Text("Nothing loaded yet", style = Type.Title)
        Spacer(Modifier.height(4.dp))
        Text(
            "Open this chat once, then come back — its links, photos, videos, and files appear here",
            style = Type.Meta,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
    }
}

/** Skeleton 4 row saat parse. */
@Composable
private fun ArtifactSkeleton() {
    val a = shimmerAlpha()
    Column(Modifier.fillMaxSize().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        repeat(4) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = Dim.ScreenH),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SkeletonBar(36.dp, 36.dp, a)
                Spacer(Modifier.width(12.dp))
                Column {
                    SkeletonBar(null, 14.dp, a)
                    Spacer(Modifier.height(6.dp))
                    SkeletonBar(180.dp, 12.dp, a)
                }
            }
        }
    }
}

/**
 * Fullscreen foto: fetch via MediaRepo (auth proxy), pinch-zoom sederhana
 * (transform gesture: pinch 1x–5x + drag pan), tombol Save reuse MediaFetchSave.
 */
@Composable
private fun ImagePreviewDialog(
    path: String,
    conn: id.melvern.hermesmobile.core.store.ConnectionSettings?,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var image by remember(path) { mutableStateOf<ImageBitmap?>(null) }
    var failed by remember(path) { mutableStateOf(false) }
    var saved by remember(path) { mutableStateOf(false) }
    LaunchedEffect(path) {
        if (conn == null) { failed = true; return@LaunchedEffect }
        val bmp = try { MediaRepo(conn).fetchImage(path) } catch (_: Throwable) { null }
        if (bmp != null) image = bmp else failed = true
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.92f))) {
            when {
                image != null -> ZoomableImage(image!!)
                failed -> Column(
                    Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(Icons.Rounded.Image, null, tint = Ink.Text3, modifier = Modifier.size(Dim.EmptyIcon))
                    Spacer(Modifier.height(8.dp))
                    Text("Couldn't load this image", style = Type.Meta)
                }
                else -> CircularProgressIndicator(
                    color = Ink.Text2, strokeWidth = 2.dp,
                    modifier = Modifier.align(Alignment.Center).size(22.dp),
                )
            }
            // close kiri-atas
            Box(Modifier.align(Alignment.TopStart).statusBarsPadding()) {
                QuietIconButton(Icons.Rounded.Close, "Close", onClick = onDismiss, tint = Color.White)
            }
            // Save kanan-atas
            Row(
                Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(end = 12.dp)
                    .clip(Radius.Chip)
                    .background(Ink.Scrim)
                    .pressClickable(enabled = !saved) {
                        scope.launch {
                            val res = MediaFetchSave.saveImage(context, path)
                            if (res != null) saved = true
                        }
                    }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Download, if (saved) "Saved" else "Save", tint = Color.White, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (saved) "Saved" else "Save", style = Type.Meta, color = Color.White)
            }
        }
    }
}

/** Pinch-zoom sederhana: pinch 1x–5x + drag pan; lepas di 1x = reset. */
@Composable
private fun ZoomableImage(bitmap: ImageBitmap) {
    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }
    Image(
        bitmap = bitmap,
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                scaleX = scale; scaleY = scale
                translationX = offsetX; translationY = offsetY
            }
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val newScale = (scale * zoom).coerceIn(1f, 5f)
                    if (newScale > 1.02f) {
                        scale = newScale
                        offsetX += pan.x
                        offsetY += pan.y
                    } else {
                        scale = 1f; offsetX = 0f; offsetY = 0f
                    }
                }
            },
    )
}

/** M13: fetcher video (path di Mac → file cacheDir) — null connection = selalu null (chip fallback). */
private fun videoFetcherFor(conn: id.melvern.hermesmobile.core.store.ConnectionSettings?, cacheDir: () -> File?): suspend (String) -> File? =
    if (conn == null) { _ -> null } else { path -> cacheDir()?.let { MediaRepo(conn).fetchVideo(path, it) } }

/** Video fullscreen dialog — reuse MarkdownVideo (player media3 16:9). */
@Composable
private fun VideoPreviewDialog(
    path: String,
    conn: id.melvern.hermesmobile.core.store.ConnectionSettings?,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val context = LocalContext.current
        val videoFetch = remember(conn, context) { videoFetcherFor(conn) { context.cacheDir } }
        Column(
            Modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.95f)),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                QuietIconButton(Icons.Rounded.Close, "Close", onClick = onDismiss, tint = Color.White)
                Text(
                    path.substringAfterLast('/'),
                    style = Type.Meta, color = Color.White,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                // M16: Save langsung dari preview video (download video ke Movies).
                var saved by remember(path) { mutableStateOf(false) }
                var saving by remember(path) { mutableStateOf(false) }
                val vScope = rememberCoroutineScope()
                val vCtx = LocalContext.current
                Row(
                    Modifier
                        .clip(Radius.Chip)
                        .pressClickable(enabled = !saved && !saving) {
                            saving = true
                            vScope.launch {
                                val ok = MediaFetchSave.saveAny(vCtx, path)
                                saved = ok
                                saving = false
                                Toast.makeText(vCtx, if (ok) "Saved to Movies" else "Couldn't save — try again", Toast.LENGTH_SHORT).show()
                            }
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (saving) CircularProgressIndicator(Modifier.size(14.dp), color = Color.White, strokeWidth = 2.dp)
                    else Icon(if (saved) Icons.Rounded.Check else Icons.Rounded.Download, if (saved) "Saved" else "Save", tint = Color.White, modifier = Modifier.size(16.dp))
                    if (!saved) {
                        Spacer(Modifier.width(4.dp))
                        Text("Save", style = Type.Meta, color = Color.White)
                    }
                }
            }
            MarkdownVideo(path = path, fetch = videoFetch)
        }
    }
}
