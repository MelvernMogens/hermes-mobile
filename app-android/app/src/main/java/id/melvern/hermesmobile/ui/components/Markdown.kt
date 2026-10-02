package id.melvern.hermesmobile.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import id.melvern.hermesmobile.ui.theme.Dim
import id.melvern.hermesmobile.ui.theme.Ink
import id.melvern.hermesmobile.ui.theme.JetBrainsMono
import id.melvern.hermesmobile.ui.theme.Radius
import id.melvern.hermesmobile.ui.theme.Type
import id.melvern.hermesmobile.ui.theme.hairline
import id.melvern.hermesmobile.ui.theme.pressClickable
import kotlinx.coroutines.delay

/**
 * Renderer markdown custom untuk jawaban assistant (M3.1) — tanpa dependency
 * markdown eksternal. Parser [MarkdownParser] = pure Kotlin (unit-testable),
 * komposit [MarkdownText] = render AnnotatedString + block layout.
 *
 * Support: **bold** / *italic* / `code`, fence code block + header bahasa,
 * heading #..####, list - dan * serta 1., link [t](url) + URL polos (linkify),
 * blockquote >.
 *
 * M5: (a) code block header punya ikon Copy → Check 1.5s (M8);
 * (b) inline code long-press = copy; (c) link long-press = salin tautan;
 * (d) baris yang persis path file gambar (.png/.jpg/…) → render [MarkdownImage].
 */

// ---------------------------------------------------------------------------
// Model + parser (pure Kotlin — jangan taruh import Compose di bawah garis ini)
// ---------------------------------------------------------------------------

/** Span inline di dalam satu baris/block. */
sealed interface MdSpan {
    val text: String
    data class Text(override val text: String) : MdSpan
    data class Bold(override val text: String) : MdSpan
    data class Italic(override val text: String) : MdSpan
    data class InlineCode(override val text: String) : MdSpan
    data class Link(override val text: String, val url: String) : MdSpan
}

/** Block level. */
sealed interface MdBlock {
    data class Paragraph(val spans: List<MdSpan>) : MdBlock
    data class Heading(val level: Int, val spans: List<MdSpan>) : MdBlock
    data class CodeBlock(val lang: String, val code: String) : MdBlock
    data class BulletList(val items: List<Pair<String, Int>>) : MdBlock
    data class NumberList(val items: List<List<MdSpan>>) : MdBlock
    data class Quote(val spans: List<MdSpan>) : MdBlock
    /** M5: baris path file gambar — render sebagai foto (via /api/media). */
    data class ImageRef(val path: String) : MdBlock
    /** M9 (item 3): baris path file video — render player (via proxy mobile-media). */
    data class VideoRef(val path: String) : MdBlock
    /** M9 (item 1): blok tabel markdown — header + baris body, sel = span inline. */
    data class Table(val header: List<List<MdSpan>>, val rows: List<List<List<MdSpan>>>) : MdBlock
}

object MarkdownParser {

    /** M9: marker sementara utk `|` yang di-escape (`\\|`) di dalam sel tabel. */
    private const val ESCAPED_PIPE = "\uE000"

    private val HEADING = Regex("^(#{1,6})\\s+(.*)$")
    private val BULLET = Regex("^(\\s*)[-*]\\s+(.*)$")
    private val ORDERED = Regex("^(\\s*)(\\d+)[.)]\\s+(.*)$")
    private val QUOTE = Regex("^\\s*>\\s?(.*)$")

    /** M9: level indent bullet — 2 spasi per level (tab = 4), max 3. */
    private fun bulletLevel(indent: String): Int {
        val spaces = indent.count { it == ' ' } + indent.count { it == '\t' } * 4
        return (spaces / 2).coerceIn(0, 3)
    }

    /** M9 (item 5): kolaps spasi ganda jadi satu — teks bot sering bawa "kata  kata". */
    private fun collapseSpaces(s: String): String = s.replace(Regex("[ \\t]{2,}"), " ")


    /**
     * M5: baris yang persis satu path file gambar — absolute unix path
     * berakhiran ekstensi gambar, opsional dibungkus markdown image/link
     * atau backtick/kutip. Contoh yang cocok:
     *   /Users/x/.hermes/images/upload_2026_0928_101010_1.png
     *   ![nama](/path/ke/file.jpg)
     */
    private val IMAGE_PATH_LINE = Regex(
        """^\s*(?:MEDIA:\s*|!\[[^\]]*\]\(|\[[^\]]*\]\()?\s*[`'"]?((?:/[\w.\-]+)+\.(?:png|jpe?g|webp|gif|bmp))[`'"]?\s*\)?\s*$""",
        RegexOption.IGNORE_CASE,
    )

    /** Path gambar dalam teks bebas (utk ekstrak dari jawaban panjang). */
    private val IMAGE_PATH_ANYWHERE = Regex(
        """(?:/[\w.\-]+)+\.(?:png|jpe?g|webp|gif|bmp)""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * M9 (item 3): ekstensi video yang dikenali (line-based + anywhere,
     * pola sama dengan gambar).
     */
    private val VIDEO_EXTS = "mp4|mov|webm|mkv|avi"

    /** M9: baris yang persis satu path file video (pola IMAGE_PATH_LINE). */
    private val VIDEO_PATH_LINE = Regex(
        """^\s*(?:MEDIA:\s*|!\[[^\]]*\]\(|\[[^\]]*\]\()?\s*[`'"]?((?:/[\w.\-]+)+\.(?:$VIDEO_EXTS))[`'"]?\s*\)?\s*$""",
        RegexOption.IGNORE_CASE,
    )

    /** Path video dalam teks bebas. */
    private val VIDEO_PATH_ANYWHERE = Regex(
        """(?:/[\w.\-]+)+\.(?:$VIDEO_EXTS)""",
        RegexOption.IGNORE_CASE,
    )


    /** M5: true kalau teks berisi setidaknya satu path file gambar. */
    fun containsImagePath(text: String): Boolean = IMAGE_PATH_ANYWHERE.containsMatchIn(text)

    /** M5: semua path gambar yang ketemu di teks (distinct, urutan kemunculan). */
    fun imagePathsIn(text: String): List<String> =
        IMAGE_PATH_ANYWHERE.findAll(text).map { it.value }.distinct().toList()

    /** M9: semua path video yang ketemu di teks (distinct, urutan kemunculan). */
    fun videoPathsIn(text: String): List<String> =
        VIDEO_PATH_ANYWHERE.findAll(text).map { it.value }.distinct().toList()


    // urutan alternatif PENTING: bold sebelum italic, markdown-link sebelum URL polos
    private val INLINE = Regex(
        """\*\*(.+?)\*\*""" +                      // **bold**
        """|\*([^*\n]+)\*""" +                     // *italic*
        """|`([^`\n]+)`""" +                       // `code`
        """|\[([^\]]+)\]\((https?://[^\s)]+)[^)]*\)""" + // [t](url)
        """|(?:^|[\s(])((?:https?://)[^\s<>()\[\]{}'\u0022]+)""" // url polos
    )

    /** Baris tabel markdown: mulai + akhir `|` (opsional), sel dipisah `|`. */
    private fun splitTableRow(line: String): List<String> {
        var s = line.trim()
        if (s.startsWith("|")) s = s.substring(1)
        if (s.endsWith("|")) s = s.substring(0, s.length - 1)
        return s.split("|").map { it.trim() }
    }

    /** Baris separator tabel: `|---|:---:|` — hanya dash/colon/spasi. */
    private fun isTableSeparator(line: String): Boolean {
        val cells = splitTableRow(line)
        if (cells.isEmpty()) return false
        return cells.all { it.matches(Regex(":?-{3,}:?")) }
    }

    /**
     * M9 (item 1): split inline menjadi span per sel — escaped pipe `\|`
     * dipulihkan jadi `|` setelah split baris (markernya <U+E000>).
     */
    fun parseInlineCell(src: String): List<MdSpan> =
        parseInline(src.replace(ESCAPED_PIPE, "|"))

    fun parse(src: String): List<MdBlock> {
        val blocks = mutableListOf<MdBlock>()
        val para = mutableListOf<String>()
        val bullet = mutableListOf<Pair<Int, String>>()
        var curLevel = -1
        val ordered = mutableListOf<String>()
        val quote = mutableListOf<String>()

        fun flushPara() {
            if (para.isNotEmpty()) {
                blocks += MdBlock.Paragraph(parseInline(para.joinToString("\n")))
                para.clear()
            }
        }
        fun flushBullet() {
            if (bullet.isNotEmpty()) {
                blocks += MdBlock.BulletList(bullet.map { it.second to it.first })
                bullet.clear()
            }
        }
        fun flushOrdered() {
            if (ordered.isNotEmpty()) {
                blocks += MdBlock.NumberList(ordered.map { parseInline(it) })
                ordered.clear()
            }
        }
        fun flushQuote() {
            if (quote.isNotEmpty()) {
                blocks += MdBlock.Quote(parseInline(quote.joinToString("\n")))
                quote.clear()
            }
        }
        fun flushAll() { flushPara(); flushBullet(); flushOrdered(); flushQuote() }

        val lines = src.lines()
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            val trimmed = line.trim()
            val trimmedCollapsed = collapseSpaces(trimmed)
            when {
                trimmed.startsWith("```") -> {
                    flushAll()
                    val lang = trimmed.removePrefix("```").trim()
                    val body = mutableListOf<String>()
                    i++
                    while (i < lines.size && !lines[i].trim().startsWith("```")) {
                        body += lines[i]; i++
                    }
                    // i sekarang di fence penutup (atau EOF — code block gak ditutup tetap dirender)
                    blocks += MdBlock.CodeBlock(lang, body.joinToString("\n"))
                }
                // M9 (item 1): blok tabel — baris | lalu separator |---|
                trimmed.startsWith("|") && i + 1 < lines.size && isTableSeparator(lines[i + 1]) -> {
                    flushAll()
                    val header = splitTableRow(trimmed.replace("\\|", ESCAPED_PIPE)).map { parseInlineCell(it) }
                    i += 2
                    val rows = mutableListOf<List<List<MdSpan>>>()
                    while (i < lines.size && lines[i].trim().startsWith("|") &&
                        !isTableSeparator(lines[i])
                    ) {
                        rows += splitTableRow(lines[i].trim().replace("\\|", ESCAPED_PIPE)).map { parseInlineCell(it) }
                        i++
                    }
                    blocks += MdBlock.Table(header, rows)
                }
                HEADING.containsMatchIn(trimmed) && trimmed.startsWith("#") -> {
                    flushAll()
                    val m = HEADING.find(trimmed)!!
                    blocks += MdBlock.Heading(m.groupValues[1].length, parseInline(m.groupValues[2].trim()))
                }
                // M9: match di baris MENTAH — indent dipakai utk level nested
                BULLET.containsMatchIn(trimmed) && !trimmed.startsWith("**") -> {
                    val m = BULLET.find(line)!!
                    val level = bulletLevel(m.groupValues[1])
                    if (level != curLevel) { flushPara(); flushOrdered(); flushQuote(); flushBullet(); curLevel = level }
                    if (para.isNotEmpty()) flushPara()
                    if (ordered.isNotEmpty()) flushOrdered()
                    if (quote.isNotEmpty()) flushQuote()
                    bullet += bulletLevel(m.groupValues[1]) to collapseSpaces(m.groupValues[2].trim())
                }
                ORDERED.containsMatchIn(trimmed) -> {
                    flushPara(); flushBullet(); flushQuote()
                    ordered += collapseSpaces(ORDERED.find(line)!!.groupValues[3].trim())
                }
                QUOTE.containsMatchIn(trimmed) -> {
                    flushPara(); flushBullet(); flushOrdered()
                    quote += QUOTE.find(trimmed)!!.groupValues[1]
                }
                trimmed.isEmpty() -> flushAll()
                VIDEO_PATH_LINE.containsMatchIn(trimmed) -> {
                    flushAll()
                    blocks += MdBlock.VideoRef(videoPathsIn(trimmed).first())
                }
                IMAGE_PATH_LINE.containsMatchIn(trimmed) && imagePathsIn(trimmed).isNotEmpty() -> {
                    // M5: baris path gambar → render foto (bukan paragraf teks)
                    flushAll()
                    blocks += MdBlock.ImageRef(imagePathsIn(trimmed).first())
                }
                else -> {
                    flushBullet(); flushOrdered(); flushQuote()
                    para += trimmedCollapsed
                }
            }
            i++
        }
        flushAll()
        return blocks
    }

    fun parseInline(src: String): List<MdSpan> {
        val spans = mutableListOf<MdSpan>()
        var cursor = 0
        for (m in INLINE.findAll(src)) {
            // karakter sebelum match (untuk URL polos, match group mulai setelah boundary char)
            val groups = m.groupValues
            val bold = groups[1]; val italic = groups[2]; val code = groups[3]
            val linkText = groups[4]; val linkUrl = groups[5]
            // URL polos: buang tanda baca di ujung (titik/koma titik dua dsb — sering
            // nempel di akhir kalimat, bukan bagian dari URL)
            val bareUrlRaw = groups[6]
            val bareUrl = bareUrlRaw.trimEnd('.', ',', ';', ':', '!', '?')
            val consumedFrom = when {
                bareUrlRaw.isNotEmpty() -> m.range.first + (m.value.length - bareUrlRaw.length)
                else -> m.range.first
            }
            if (consumedFrom > cursor) spans += MdSpan.Text(src.substring(cursor, consumedFrom))
            when {
                bold.isNotEmpty() -> spans += MdSpan.Bold(bold)
                italic.isNotEmpty() -> spans += MdSpan.Italic(italic)
                code.isNotEmpty() -> spans += MdSpan.InlineCode(code)
                linkUrl.isNotEmpty() -> spans += MdSpan.Link(linkText, linkUrl)
                bareUrl.isNotEmpty() -> spans += MdSpan.Link(bareUrl, bareUrl)
            }
            val consumedTo = when {
                bareUrlRaw.isNotEmpty() -> consumedFrom + bareUrl.length
                else -> m.range.last + 1
            }
            cursor = consumedTo
        }
        if (cursor < src.length) spans += MdSpan.Text(src.substring(cursor))
        return spans.ifEmpty { listOf(MdSpan.Text(src)) }
    }
}

// ---------------------------------------------------------------------------
// Render Compose (M8 Quiet Mono)
// ---------------------------------------------------------------------------

/** Buka URL di browser eksternal: Custom Tabs kalau ada, fallback ACTION_VIEW. */
fun openUrlExternal(context: Context, url: String) {
    val uri = Uri.parse(url)
    try {
        CustomTabsIntent.Builder()
            .setColorScheme(CustomTabsIntent.COLOR_SCHEME_DARK)
            .setShowTitle(true)
            .build()
            .launchUrl(context, uri)
    } catch (_: Exception) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (_: Exception) {
            // gak ada yang bisa buka URL — diam saja (link mati > crash)
        }
    }
}

private val BlockGap = 12.dp
private val ListIndent = 20.dp

/**
 * Body markdown assistant. Parse di-remember per teks supaya streaming delta
 * gak bikin parse penuh setiap recomposition.
 */
@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = Type.Body,
    /** M5: fetcher gambar (path di Mac → bitmap via /api/media); null = selalu chip fallback. */
    imageFetch: (suspend (String) -> androidx.compose.ui.graphics.ImageBitmap?)? = null,
    /** M9: fetcher video (path di Mac → file cache via proxy mobile-media); null = chip fallback. */
    videoFetch: (suspend (String) -> java.io.File?)? = null,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val blocks = remember(text) { MarkdownParser.parse(text) }
    // M5: slot long-press — null = tutup. Isi = (kind, nilai yang disalin).
    var copySheet by remember { mutableStateOf<Pair<String, String>?>(null) }
    val actions = remember { MdActions() }
    actions.onCopyCode = { code -> copySheet = "code" to code }
    actions.onCopyLink = { url -> copySheet = "link" to url }
    Column(modifier = modifier) {
        blocks.forEachIndexed { idx, block ->
            val gap = if (idx == blocks.lastIndex) Modifier else Modifier.padding(bottom = BlockGap)
            when (block) {
                is MdBlock.ImageRef -> MarkdownImage(block.path, modifier = gap, fetch = imageFetch)
                is MdBlock.VideoRef -> MarkdownVideo(block.path, modifier = gap, fetch = videoFetch)
                is MdBlock.Table -> MdTable(block, modifier = gap)
                is MdBlock.Paragraph -> InlineAwareText(
                    buildMd(block.spans, context), style = style, spans = block.spans, actions = actions, modifier = gap,
                )
                is MdBlock.Heading -> InlineAwareText(
                    buildMd(block.spans, context),
                    style = Type.Title,
                    spans = block.spans, actions = actions,
                    modifier = gap.then(if (idx > 0) Modifier.padding(top = 8.dp) else Modifier),
                )
                is MdBlock.CodeBlock -> CodeBox(block.lang, block.code, modifier = gap)
                is MdBlock.BulletList -> Column(gap) {
                    block.items.forEachIndexed { n, (raw, level) ->
                        Row(Modifier.padding(top = if (n == 0) 0.dp else 4.dp)) {
                            Box(Modifier.padding(start = ListIndent * level).width(ListIndent).height(with(LocalDensity.current) { style.lineHeight.toDp() }), contentAlignment = Alignment.CenterStart) {
                                Box(Modifier.padding(start = 6.dp).size(if (level > 0) 4.dp else 5.dp).clip(Radius.Full).background(Ink.Text2))
                            }
                            val item = remember(raw) { MarkdownParser.parseInline(raw) }
                            InlineAwareText(buildMd(item, context), style = style, spans = item, actions = actions, modifier = Modifier.weight(1f))
                        }
                    }
                }
                is MdBlock.NumberList -> Column(gap) {
                    block.items.forEachIndexed { n, item ->
                        Row(Modifier.padding(top = if (n == 0) 0.dp else 4.dp)) {
                            Text("${n + 1}.", style = style.copy(color = Ink.Text2), modifier = Modifier.widthIn(min = ListIndent).padding(end = 4.dp))
                            InlineAwareText(buildMd(item, context), style = style, spans = item, actions = actions, modifier = Modifier.weight(1f))
                        }
                    }
                }
                is MdBlock.Quote -> Row(gap.height(IntrinsicSize.Min)) {
                    Box(Modifier.width(2.dp).fillMaxHeight().background(Ink.HairlineStrong))
                    InlineAwareText(
                        buildMd(block.spans, context),
                        style = style.copy(color = Ink.Text2),
                        spans = block.spans, actions = actions,
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }
            }
        }
    }
    // M5: long-press inline code / link → sheet satu aksi salin.
    copySheet?.let { (kind, value) ->
        QuietSheet(onDismiss = { copySheet = null }, title = if (kind == "link") "Link" else "Code") {
            Text(
                value,
                style = if (kind == "link") Type.Callout.copy(color = Ink.Text2) else Type.Mono.copy(color = Ink.Text2),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = Dim.ScreenH).padding(bottom = 8.dp),
            )
            SheetActionRow(if (kind == "link") "Copy link" else "Copy code", Icons.Rounded.ContentCopy) {
                clipboard.setText(AnnotatedString(value))
                copySheet = null
            }
            if (kind == "link") SheetActionRow("Open in browser", Icons.AutoMirrored.Rounded.OpenInNew) {
                openUrlExternal(context, value)
                copySheet = null
            }
        }
    }
}

/**
 * Kotak kode M8: surface1 radius 10 border hairline; header 32dp surface3
 * (bahasa lowercase mono kiri, ikon Copy kanan → Check 1.5s); body mono 13/19
 * horizontal scroll padding 12. Dipakai juga untuk output tool.
 */
@Composable
fun CodeBox(lang: String, code: String, modifier: Modifier = Modifier) {
    val clipboard = LocalClipboardManager.current
    var copied by remember(code) { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) { delay(1500); copied = false }
    }
    val hl = hairline()
    Column(
        modifier
            .fillMaxWidth()
            .clip(Radius.Chip)
            .background(Ink.Surface1)
            .border(hl, Ink.Hairline, Radius.Chip),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(Dim.CodeHeader)
                .background(Ink.Surface3)
                .padding(start = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(lang.ifBlank { "text" }.lowercase(), style = Type.MonoMeta, modifier = Modifier.weight(1f), maxLines = 1)
            Box(
                Modifier
                    .size(Dim.CodeHeader)
                    .pressClickable {
                        clipboard.setText(AnnotatedString(code))
                        copied = true
                    }
                    .semantics { contentDescription = if (copied) "Copied" else "Copy code" },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (copied) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                    contentDescription = null,
                    tint = if (copied) Ink.Text else Ink.Text2,
                    modifier = Modifier.size(Dim.IconSmall),
                )
            }
        }
        SelectionContainer {
            Text(
                code,
                style = Type.Mono,
                softWrap = false,
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(12.dp),
            )
        }
    }
}

/**
 * Gabung span jadi AnnotatedString — link pakai LinkAnnotation.Clickable
 * (underline, warna teks — bukan biru). Inline code: mono 14; background
 * rounded digambar di [InlineAwareText] (SpanStyle.background tidak bisa radius).
 */
private fun buildMd(spans: List<MdSpan>, context: Context): AnnotatedString = buildAnnotatedString {
    spans.forEach { span ->
        when (span) {
            is MdSpan.Text -> append(span.text)
            is MdSpan.Bold -> withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(span.text) }
            is MdSpan.Italic -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(span.text) }
            is MdSpan.InlineCode -> withStyle(
                SpanStyle(fontFamily = JetBrainsMono, fontSize = Type.InlineMonoSize, color = Ink.Text)
            ) { append(" ${span.text} ") }
            is MdSpan.Link -> withLink(
                LinkAnnotation.Clickable(
                    tag = span.url,
                    styles = TextLinkStyles(SpanStyle(color = Ink.Text, textDecoration = TextDecoration.Underline)),
                    linkInteractionListener = { openUrlExternal(context, span.url) },
                )
            ) { append(span.text) }
        }
    }
}

// ---------------------------------------------------------------------------
// M9 (item 1): render tabel markdown
// ---------------------------------------------------------------------------

private val TableCellMaxW = 200.dp

/**
 * M9 (item 1): tabel markdown — Surface1 radius 10, header bold (Title) di
 * atas divider hairline, kolom dibagi rata weight(1f), gap antar sel 12dp.
 * Kolom > 4 ATAU ada sel panjang (> 40 char) → horizontal scroll.
 * Tap sel yang terpotong (ellipsis) → sheet teks penuh.
 */
@Composable
fun MdTable(table: MdBlock.Table, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val cols = table.header.size
    val scrollable = cols > 4 || (table.rows.maxOfOrNull { r -> r.maxOfOrNull { spans -> spans.sumOf { it.text.length } } ?: 0 } ?: 0) > 40
    val hl = hairline()
    var cellSheet by remember { mutableStateOf<String?>(null) }
    Column(
        modifier
            .fillMaxWidth()
            .clip(Radius.Chip)
            .background(Ink.Surface1)
            .border(hl, Ink.Hairline, Radius.Chip)
            .then(if (scrollable) Modifier.horizontalScroll(rememberScrollState()) else Modifier),
    ) {
        // header
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp)) {
            table.header.forEachIndexed { c, spans ->
                val cell = buildMd(spans, context)
                val overflow = cell.length > 24
                Text(
                    cell,
                    style = Type.Title.copy(fontSize = Type.Callout.fontSize, lineHeight = Type.Callout.lineHeight),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .widthIn(max = TableCellMaxW)
                        .padding(horizontal = 6.dp)
                        .then(if (overflow) Modifier.pressClickable { cellSheet = cell.text } else Modifier),
                )
            }
        }
        HorizontalDivider(color = Ink.Hairline, thickness = hl)
        // body
        table.rows.forEachIndexed { r, row ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.Top,
            ) {
                row.forEachIndexed { c, spans ->
                    val cell = buildMd(spans, context)
                    val overflow = cell.length > 40
                    Text(
                        cell,
                        style = Type.Callout,
                        color = Ink.Text2,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .widthIn(max = TableCellMaxW)
                            .padding(horizontal = 6.dp)
                            .then(if (overflow) Modifier.pressClickable { cellSheet = cell.text } else Modifier),
                )
            }
            }
            if (r != table.rows.lastIndex) HorizontalDivider(color = Ink.Hairline, thickness = hairline())
        }
    }
    cellSheet?.let { full ->
        QuietSheet(onDismiss = { cellSheet = null }, title = "Cell") {
            Text(
                full,
                style = Type.Callout.copy(color = Ink.Text2),
                modifier = Modifier.padding(horizontal = Dim.ScreenH).padding(bottom = 12.dp),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// M9 (item 3): render video path dari agent
// ---------------------------------------------------------------------------

/**
 * M9 (item 3): render path file video dari jawaban agent.
 * Download via proxy /api/mobile-media (auth + path-in-chat guard) ke cacheDir,
 * mainkan pakai media3 ExoPlayer di AndroidView 16:9 radius 10; kontrol ON.
 * > 50MB / gagal → chip nama file + "Open on your Mac" (pola fallback image).
 */
@Composable
fun MarkdownVideo(
    path: String,
    modifier: Modifier = Modifier,
    fetch: (suspend (String) -> java.io.File?)? = null,
) {
    var file by remember(path) { mutableStateOf<java.io.File?>(null) }
    var failed by remember(path) { mutableStateOf(false) }
    LaunchedEffect(path) {
        if (fetch == null) { failed = true; return@LaunchedEffect }
        val f = try { fetch(path) } catch (_: Throwable) { null }
        if (f != null && f.exists() && f.length() > 0) file = f else failed = true
    }
    val name = path.substringAfterLast('/')
    val hl = hairline()
    val f = file
    when {
        f != null -> Box(
            modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(Radius.Chip)
                .border(hl, Ink.Hairline, Radius.Chip),
        ) {
            VideoPlayer(file = f, name = name)
        }
        failed -> Row(
            modifier
                .fillMaxWidth()
                .clip(Radius.Chip)
                .background(Ink.Surface1)
                .border(hl, Ink.Hairline, Radius.Chip)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.PlayArrow, null, tint = Ink.Text2, modifier = Modifier.size(Dim.Icon))
            Spacer(Modifier.width(12.dp))
            Column {
                Text(name, style = Type.Callout, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("Open on your Mac", style = Type.Meta)
            }
        }
        else -> Box(
            modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .graphicsLayer { alpha = 0.8f }
                .clip(Radius.Chip)
                .background(Ink.Surface1),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(color = Ink.Text2, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
        }
    }
}

/**
 * AndroidView wrapper media3 ExoPlayer — lifecycle-aware (play saat RESUMED,
 * release saat dispose). Controller ON, 16:9, background hitam.
 */
@android.annotation.SuppressLint("UnsafeOptInUsageError")
@Composable
private fun VideoPlayer(file: java.io.File, name: String) {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val exo = remember(file) {
        androidx.media3.exoplayer.ExoPlayer.Builder(context).build().apply {
            setMediaItem(androidx.media3.common.MediaItem.fromUri(android.net.Uri.fromFile(file)))
            prepare()
        }
    }
    DisposableEffect(lifecycleOwner) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> exo.play()
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> exo.pause()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(obs)
            exo.release()
        }
    }
    AndroidView(
        factory = { ctx ->
            androidx.media3.ui.PlayerView(ctx).apply {
                player = exo
                setUseController(true)
                setBackgroundColor(android.graphics.Color.BLACK)
                contentDescription = name
            }
        },
        modifier = Modifier.fillMaxSize(),
    )
}


/** Slot aksi long-press per MarkdownText (user cuma sentuh satu elemen sekaligus). */
class MdActions {
    var onCopyCode: ((String) -> Unit)? = null
    var onCopyLink: ((String) -> Unit)? = null
}

/**
 * M5: render path file gambar dari jawaban agent.
 * Fetch via MediaRepo (GET /api/media → bitmap, auth cookie).
 * Gagal / di luar media roots → baris ikon Image + nama + keterangan.
 */
@Composable
fun MarkdownImage(
    path: String,
    modifier: Modifier = Modifier,
    fetch: (suspend (String) -> androidx.compose.ui.graphics.ImageBitmap?)? = null,
) {
    var image by remember(path) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    var failed by remember(path) { mutableStateOf(false) }
    LaunchedEffect(path) {
        if (fetch == null) { failed = true; return@LaunchedEffect }
        val bmp = try { fetch(path) } catch (_: Throwable) { null }
        if (bmp != null) image = bmp else failed = true
    }
    val name = path.substringAfterLast('/')
    val hl = hairline()
    when {
        image != null -> androidx.compose.foundation.Image(
            bitmap = image!!,
            contentDescription = name,
            contentScale = ContentScale.FillWidth,
            modifier = modifier
                .fillMaxWidth()
                .clip(Radius.Chip)
                .border(hl, Ink.Hairline, Radius.Chip),
        )
        failed -> Row(
            modifier
                .fillMaxWidth()
                .clip(Radius.Chip)
                .background(Ink.Surface1)
                .border(hl, Ink.Hairline, Radius.Chip)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Image, null, tint = Ink.Text2, modifier = Modifier.size(Dim.Icon))
            Spacer(Modifier.width(12.dp))
            Column {
                Text(name, style = Type.Callout, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("Only viewable on your Mac", style = Type.Meta)
            }
        }
        else -> Box(
            modifier
                .fillMaxWidth()
                .height(160.dp)
                .graphicsLayer { alpha = 0.8f }
                .clip(Radius.Chip)
                .background(Ink.Surface1),
        )
    }
}

/**
 * M5: Text dengan long-press per-span: inline code → copy code; link → salin
 * tautan. Offset karakter dari posisi pointer (getOffsetForPosition) dicocokkan
 * ke range span. M8: background inline code digambar rounded (radius 6,
 * surface2) dari posisi glyph — per baris kalau span terpotong wrap.
 */
@Composable
private fun InlineAwareText(
    annotated: AnnotatedString,
    style: TextStyle,
    spans: List<MdSpan>,
    actions: MdActions,
    modifier: Modifier = Modifier,
) {
    // range karakter tiap span inline-code / link — dihitung SEKALI per teks.
    // InlineCode dirender dengan padding spasi (" code ") = len+2, span lain len.
    val hitRanges = remember(annotated, spans) {
        val out = mutableListOf<Triple<IntRange, String, String>>() // range, kind, value
        var cursor = 0
        spans.forEach { span ->
            val len = (if (span is MdSpan.InlineCode) span.text.length + 2 else span.text.length)
            when (span) {
                is MdSpan.InlineCode -> out += Triple(cursor until cursor + len, "code", span.text)
                is MdSpan.Link -> out += Triple(cursor until cursor + len, "link", span.url)
                else -> {}
            }
            cursor += len
        }
        out
    }
    var layout by remember { mutableStateOf<androidx.compose.ui.text.TextLayoutResult?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current
    // M5 fix: lambda pointerInput(Unit) harus baca versi terbaru (streaming).
    val currentAnnotated by rememberUpdatedState(annotated)
    val currentHits by rememberUpdatedState(hitRanges)
    val codeBg = Ink.Surface2
    Text(
        annotated,
        style = style,
        onTextLayout = { layout = it },
        modifier = modifier
            .drawBehind {
                val l = layout ?: return@drawBehind
                val r = 6.dp.toPx()
                val inset = 1.dp.toPx() // spasi pembungkus ≈ padding 4h
                currentHits.forEach { (range, kind, _) ->
                    if (kind != "code") return@forEach
                    val start = range.first.coerceIn(0, l.layoutInput.text.length)
                    val endEx = (range.last + 1).coerceIn(0, l.layoutInput.text.length)
                    if (endEx <= start) return@forEach
                    val firstLine = l.getLineForOffset(start)
                    val lastLine = l.getLineForOffset(endEx - 1)
                    for (line in firstLine..lastLine) {
                        val s = if (line == firstLine) l.getHorizontalPosition(start, true) else l.getLineLeft(line)
                        val e = if (line == lastLine) l.getHorizontalPosition(endEx, true) else l.getLineRight(line)
                        val top = l.getLineTop(line) + 2.dp.toPx()
                        val bottom = l.getLineBottom(line) - 2.dp.toPx()
                        drawRoundRect(
                            color = codeBg,
                            topLeft = Offset(minOf(s, e) + inset, top),
                            size = Size((kotlin.math.abs(e - s) - inset * 2).coerceAtLeast(0f), bottom - top),
                            cornerRadius = CornerRadius(r, r),
                        )
                    }
                }
            }
            .pointerInput(Unit) {
                // Tap pada link = buka di browser (LinkAnnotation di buildMd gak
                // ke-fire karena pointerInput ini mengonsumsi tap). Long-press =
                // copy (code / URL) seperti sebelumnya.
                detectTapGestures(
                    onTap = { pos ->
                        val l = layout ?: return@detectTapGestures
                        val a = currentAnnotated
                        val offset = l.getOffsetForPosition(pos).coerceIn(0, (a.length - 1).coerceAtLeast(0))
                        val hit = currentHits.firstOrNull { offset in it.first } ?: return@detectTapGestures
                        val (_, kind, value) = hit
                        if (kind == "link") openUrlExternal(context, value)
                    },
                    onLongPress = { pos ->
                        val l = layout ?: return@detectTapGestures
                        val a = currentAnnotated
                        val offset = l.getOffsetForPosition(pos).coerceIn(0, (a.length - 1).coerceAtLeast(0))
                        val hit = currentHits.firstOrNull { offset in it.first } ?: return@detectTapGestures
                        val (_, kind, value) = hit
                        if (kind == "code") actions.onCopyCode?.invoke(value)
                        else actions.onCopyLink?.invoke(value)
                    },
                )
            },
    )
}
