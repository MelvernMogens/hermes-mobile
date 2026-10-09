package id.melvern.hermesmobile.ui.overview

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import id.melvern.hermesmobile.HermesApp
import id.melvern.hermesmobile.core.repo.AgentFile
import id.melvern.hermesmobile.core.repo.FilesRepo
import id.melvern.hermesmobile.core.repo.MediaRepo
import id.melvern.hermesmobile.ui.chat.ImagePreviewDialog
import id.melvern.hermesmobile.ui.chat.VideoPreviewDialog
import id.melvern.hermesmobile.ui.components.KeyCap
import id.melvern.hermesmobile.ui.components.MediaFetchSave
import id.melvern.hermesmobile.ui.components.OneLine
import id.melvern.hermesmobile.ui.components.Pretty
import id.melvern.hermesmobile.ui.components.QuietIconButton
import id.melvern.hermesmobile.ui.components.RelTime
import id.melvern.hermesmobile.ui.components.scanlines
import id.melvern.hermesmobile.ui.components.shimmerAlpha
import id.melvern.hermesmobile.ui.theme.Dim
import id.melvern.hermesmobile.ui.theme.Ink
import id.melvern.hermesmobile.ui.theme.Radius
import id.melvern.hermesmobile.ui.theme.Type
import id.melvern.hermesmobile.ui.theme.hairline
import id.melvern.hermesmobile.ui.theme.pressClickable
import kotlinx.coroutines.launch

/**
 * v28 "Clip bin" — every file the agent and the bots sent, across all chats,
 * newest first. Edge-to-edge 3-up thumbnails (no cards), each with a tiny
 * mono label: kind code + time. Tap = preview (image/video) or save (docs);
 * long-press = open the chat it came from.
 */
@Composable
fun FilesScreen(app: HermesApp, onBack: () -> Unit, onOpenChat: (String) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var kind by remember { mutableStateOf(FilesRepo.Kind.ALL) }
    var files by remember { mutableStateOf<List<AgentFile>?>(null) }
    var failed by remember { mutableStateOf(false) }
    var kick by remember { mutableStateOf(0) }
    var previewImage by remember { mutableStateOf<String?>(null) }
    var previewVideo by remember { mutableStateOf<String?>(null) }
    var focus by remember { mutableStateOf<AgentFile?>(null) }

    LaunchedEffect(kind, kick) {
        failed = false
        val conn = app.connection ?: run { failed = true; return@LaunchedEffect }
        val got = try { FilesRepo(conn).list(kind, limit = 240) } catch (_: Throwable) { null }
        if (got == null) { if (files == null) failed = true } else files = got
    }

    Column(Modifier.fillMaxSize().background(Ink.Bg).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(Dim.TopBar).padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            QuietIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onClick = onBack)
            Column(Modifier.weight(1f)) {
                OneLine("Files", Type.Title)
                Text("Everything your agents sent", style = Type.Meta.copy(color = Ink.Text3), maxLines = 1)
            }
            files?.let { Text("${it.size}", style = Type.Timecode.copy(color = Ink.Text2), modifier = Modifier.padding(end = 8.dp)) }
        }
        // source selector — a row of key caps, the active one lit
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = Dim.ScreenH, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(
                FilesRepo.Kind.ALL to "All", FilesRepo.Kind.IMAGE to "Images", FilesRepo.Kind.VIDEO to "Videos",
                FilesRepo.Kind.DOC to "Docs", FilesRepo.Kind.AUDIO to "Audio",
            ).forEach { (k, label) ->
                KeyCap(label, selected = kind == k) { if (kind != k) { kind = k; files = null } }
            }
        }
        val list = files
        when {
            list == null && failed -> Column(Modifier.fillMaxWidth().padding(Dim.ScreenH)) {
                Text("Couldn't reach your Mac", style = Type.Callout)
                Spacer(Modifier.height(10.dp))
                KeyCap("Try again") { kick++ }
            }
            list == null -> {
                val a = shimmerAlpha()
                LazyVerticalGrid(GridCells.Fixed(3), Modifier.fillMaxSize(), userScrollEnabled = false,
                    horizontalArrangement = Arrangement.spacedBy(2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    items(12) { Box(Modifier.aspectRatio(1f).background(Ink.Surface1.copy(alpha = a))) }
                }
            }
            list.isEmpty() -> Column(Modifier.fillMaxWidth().padding(horizontal = Dim.ScreenH, vertical = 32.dp)) {
                Text("Nothing here yet", style = Type.Title)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Photos, videos and documents your agents send in any chat show up here.",
                    style = Type.Callout.copy(color = Ink.Text2),
                )
            }
            else -> LazyVerticalGrid(
                GridCells.Adaptive(116.dp),
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = Dim.ScreenH, end = Dim.ScreenH, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                var lastDay: String? = null
                list.forEach { f ->
                    val day = RelTime.dayLabel(java.time.Instant.ofEpochMilli((f.at * 1000).toLong()).atZone(java.time.ZoneId.systemDefault()).toLocalDate())
                    if (day != lastDay) {
                        lastDay = day
                        item(key = "d-$day-${f.path}", span = { GridItemSpan(maxLineSpan) }) {
                            Text(day, style = Type.Section.copy(color = Ink.Text2),
                                modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))
                        }
                    }
                    item(key = f.path) {
                        BinCell(app, f, onTap = {
                            when (f.kind) {
                                "image" -> previewImage = f.path
                                "video" -> previewVideo = f.path
                                else -> scope.launch {
                                    val ok = MediaFetchSave.saveAny(ctx, f.path)
                                    Toast.makeText(ctx, if (ok) "Saved to Downloads" else "Couldn't save — try again", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }, onLong = { focus = f })
                    }
                }
            }
        }
    }

    previewImage?.let { ImagePreviewDialog(path = it, conn = app.connection, onDismiss = { previewImage = null }) }
    previewVideo?.let { VideoPreviewDialog(path = it, conn = app.connection, onDismiss = { previewVideo = null }) }
    focus?.let { f ->
        id.melvern.hermesmobile.ui.components.QuietSheet(onDismiss = { focus = null }, title = f.name) {
            Text(
                "${Pretty.profile(f.profile)} · ${f.sessionTitle.ifBlank { "Chat" }} · ${RelTime.listStamp(f.at)} · ${sizeLabel(f.size)}",
                style = Type.Meta.copy(color = Ink.Text3),
                modifier = Modifier.padding(horizontal = Dim.ScreenH).padding(bottom = 8.dp),
            )
            id.melvern.hermesmobile.ui.components.SheetActionRow("Open the chat it came from", Icons.Outlined.Description) {
                focus = null; onOpenChat(f.chatRoute)
            }
            id.melvern.hermesmobile.ui.components.SheetActionRow("Save to phone", Icons.Outlined.AudioFile) {
                focus = null
                scope.launch {
                    val ok = MediaFetchSave.saveAny(ctx, f.path)
                    Toast.makeText(ctx, if (ok) "Saved to Downloads" else "Couldn't save — try again", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun BinCell(app: HermesApp, f: AgentFile, onTap: () -> Unit, onLong: () -> Unit) {
    var thumb by remember(f.path) { mutableStateOf<ImageBitmap?>(ThumbCache.get(f.path)) }
    if (f.kind == "image" && thumb == null) LaunchedEffect(f.path) {
        val conn = app.connection ?: return@LaunchedEffect
        thumb = try {
            MediaRepo(conn).fetchBytes(f.path)?.first?.let { bytes ->
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { decodeThumb(bytes) }
            }?.also { ThumbCache.put(f.path, it) }
        } catch (_: Throwable) { null }
    }
    Box(
        Modifier
            .aspectRatio(1f)
            .clip(Radius.Thumb)
            .background(Ink.Glass)
            .border(hairline(), Ink.KeyBezel, Radius.Thumb)
            .scanlines()
            .androidx_combined(onTap, onLong),
    ) {
        val t = thumb
        if (t != null) {
            // the picture sits ABOVE the slate strip (not under it), so nothing in the photo is hidden
            Image(t, f.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().padding(bottom = 20.dp))
        } else {
            Column(Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.Center) {
                Icon(
                    when (f.kind) { "video" -> Icons.Outlined.Movie; "audio" -> Icons.Outlined.AudioFile; else -> Icons.Outlined.Description },
                    null, tint = Ink.Text3, modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.height(8.dp))
                Text(binName(f.name), style = Type.Caption.copy(color = Ink.Text2), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        if (f.kind == "video") {
            Box(
                Modifier.align(Alignment.TopEnd).padding(6.dp).size(26.dp).clip(Radius.Key).background(Ink.Bg.copy(alpha = 0.6f))
                    .border(hairline(), Ink.KeyBezel, Radius.Key),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.PlayArrow, "Video", tint = Ink.Text, modifier = Modifier.size(16.dp)) }
        }
        // slate label: kind code + clock on a SOLID strip (never a see-through band over a photo's
        // own burned-in caption)
        Row(
            Modifier.align(Alignment.BottomStart).fillMaxWidth().background(Ink.Bg)
                .padding(horizontal = 6.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                when (f.kind) { "image" -> "IMG"; "video" -> "VID"; "audio" -> "AUD"; else -> f.name.substringAfterLast('.', "DOC").take(4).uppercase() },
                style = Type.Catalog.copy(color = Ink.Text2),
            )
            Spacer(Modifier.width(6.dp))
            Text(RelTime.clock(f.at), style = Type.Catalog.copy(color = Ink.Text3))
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
private fun Modifier.androidx_combined(onTap: () -> Unit, onLong: () -> Unit): Modifier =
    this.combinedClickable(onClick = onTap, onLongClick = onLong)

/** Downsampled thumbnails shared across grid cells — scrolling back never re-downloads. ~24 MB cap. */
private object ThumbCache {
    private val cache = object : android.util.LruCache<String, ImageBitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
    }
    fun get(k: String): ImageBitmap? = cache.get(k)
    fun put(k: String, v: ImageBitmap) { cache.put(k, v) }
}

/** Decode at ~360px on the short side (inSampleSize power of two) — never a full-res bitmap per cell. */
private fun decodeThumb(bytes: ByteArray, target: Int = 360): ImageBitmap? {
    val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
    android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (minOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= target) sample *= 2
    val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
    return android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)?.asImageBitmap()
}

/**
 * File names in a narrow tile: give the line breaker an explicit break opportunity (zero-width
 * space) right after the name's own separators (`-`, `_`, `.`), so a wrap lands between parts
 * ("Sesi-2-Tangan-dan- / Mata.pptx") instead of splitting a word ("…Tangan-da / n-Mata").
 */
internal fun binName(name: String): String = buildString(name.length + 8) {
    name.forEachIndexed { i, ch ->
        append(ch)
        if ((ch == '-' || ch == '_' || ch == '.') && i < name.lastIndex) append('\u200B')
    }
}

private fun sizeLabel(b: Long): String = when {
    b <= 0 -> "—"
    b < 1024 -> "$b B"
    b < 1024 * 1024 -> "${b / 1024} KB"
    else -> "%.1f MB".format(b / 1048576.0)
}
