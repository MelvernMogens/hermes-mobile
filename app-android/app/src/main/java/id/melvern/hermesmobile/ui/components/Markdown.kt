package id.melvern.hermesmobile.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.melvern.hermesmobile.ui.theme.F
import id.melvern.hermesmobile.ui.theme.JetBrainsMono
import id.melvern.hermesmobile.ui.theme.Shape
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
 * M5: (a) code block header ada teks SALIN → copy isi (TERSALIN ✓ 1.5s);
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
    data class BulletList(val items: List<List<MdSpan>>) : MdBlock
    data class NumberList(val items: List<List<MdSpan>>) : MdBlock
    data class Quote(val spans: List<MdSpan>) : MdBlock
    /** M5: baris path file gambar — render sebagai foto (via /api/media). */
    data class ImageRef(val path: String) : MdBlock
}

object MarkdownParser {

    private val HEADING = Regex("^(#{1,6})\\s+(.*)$")
    private val BULLET = Regex("^\\s*[-*]\\s+(.*)$")
    private val ORDERED = Regex("^\\s*(\\d+)[.)]\\s+(.*)$")
    private val QUOTE = Regex("^\\s*>\\s?(.*)$")

    /**
     * M5: baris yang persis satu path file gambar — absolute unix path
     * berakhiran ekstensi gambar, opsional dibungkus markdown image/link
     * atau backtick/kutip. Contoh yang cocok:
     *   /Users/x/.hermes/images/upload_2026_0928_101010_1.png
     *   ![nama](/path/ke/file.jpg)
     */
    private val IMAGE_PATH_LINE = Regex(
        """^\s*(?:!\[[^\]]*\]\(|\[[^\]]*\]\()?\s*[`'"]?((?:/[\w.\-]+)+\.(?:png|jpe?g|webp|gif|bmp))[`'"]?\s*\)?\s*$""",
        RegexOption.IGNORE_CASE,
    )

    /** Path gambar dalam teks bebas (utk ekstrak dari jawaban panjang). */
    private val IMAGE_PATH_ANYWHERE = Regex(
        """(?:/[\w.\-]+)+\.(?:png|jpe?g|webp|gif|bmp)""",
        RegexOption.IGNORE_CASE,
    )

    /** M5: true kalau teks berisi setidaknya satu path file gambar. */
    fun containsImagePath(text: String): Boolean = IMAGE_PATH_ANYWHERE.containsMatchIn(text)

    /** M5: semua path gambar yang ketemu di teks (distinct, urutan kemunculan). */
    fun imagePathsIn(text: String): List<String> =
        IMAGE_PATH_ANYWHERE.findAll(text).map { it.value }.distinct().toList()

    // urutan alternatif PENTING: bold sebelum italic, markdown-link sebelum URL polos
    private val INLINE = Regex(
        """\*\*(.+?)\*\*""" +                      // **bold**
        """|\*([^*\n]+)\*""" +                     // *italic*
        """|`([^`\n]+)`""" +                       // `code`
        """|\[([^\]]+)\]\((https?://[^\s)]+)[^)]*\)""" + // [t](url)
        """|(?:^|[\s(])((?:https?://)[^\s<>()\[\]{}'\u0022]+)""" // url polos
    )

    fun parse(src: String): List<MdBlock> {
        val blocks = mutableListOf<MdBlock>()
        val para = mutableListOf<String>()
        val bullet = mutableListOf<String>()
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
                blocks += MdBlock.BulletList(bullet.map { parseInline(it) })
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
                HEADING.containsMatchIn(trimmed) && trimmed.startsWith("#") -> {
                    flushAll()
                    val m = HEADING.find(trimmed)!!
                    blocks += MdBlock.Heading(m.groupValues[1].length, parseInline(m.groupValues[2].trim()))
                }
                BULLET.containsMatchIn(trimmed) && !trimmed.startsWith("**") -> {
                    flushPara(); flushOrdered(); flushQuote()
                    if (ordered.isNotEmpty()) flushOrdered()
                    bullet += BULLET.find(trimmed)!!.groupValues[1].trim()
                }
                ORDERED.containsMatchIn(trimmed) -> {
                    flushPara(); flushBullet(); flushQuote()
                    ordered += ORDERED.find(trimmed)!!.groupValues[2].trim()
                }
                QUOTE.containsMatchIn(trimmed) -> {
                    flushPara(); flushBullet(); flushOrdered()
                    quote += QUOTE.find(trimmed)!!.groupValues[1]
                }
                trimmed.isEmpty() -> flushAll()
                IMAGE_PATH_LINE.containsMatchIn(trimmed) && imagePathsIn(trimmed).isNotEmpty() -> {
                    // M5: baris path gambar → render foto (bukan paragraf teks)
                    flushAll()
                    blocks += MdBlock.ImageRef(imagePathsIn(trimmed).first())
                }
                else -> {
                    flushBullet(); flushOrdered(); flushQuote()
                    para += trimmed
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
// Render Compose
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

/**
 * Body markdown assistant. Parse di-remember per teks supaya streaming delta
 * gak bikin parse penuh setiap recomposition.
 */
@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyLarge,
    /** M5: fetcher gambar (path di Mac → bitmap via /api/media); null = selalu chip fallback. */
    imageFetch: (suspend (String) -> androidx.compose.ui.graphics.ImageBitmap?)? = null,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val blocks = remember(text) { MarkdownParser.parse(text) }
    // M5: slot dialog long-press — null = tutup. Isi = (label, nilai yang disalin).
    var copyDialog by remember { mutableStateOf<Pair<String, String>?>(null) }
    val actions = remember { MdActions() }
    actions.onCopyCode = { code -> copyDialog = "code" to code }
    actions.onCopyLink = { url -> copyDialog = "link" to url }
    Column(modifier = modifier) {
        blocks.forEachIndexed { idx, block ->
            val last = idx == blocks.lastIndex
            when (block) {
                is MdBlock.ImageRef -> MarkdownImage(block.path, modifier = if (last) Modifier else Modifier.padding(bottom = 10.dp), fetch = imageFetch)
                is MdBlock.Paragraph -> InlineAwareText(
                    buildMd(block.spans, context),
                    style = style,
                    spans = block.spans,
                    actions = actions,
                    modifier = if (last) Modifier else Modifier.padding(bottom = 10.dp),
                )
                is MdBlock.Heading -> Text(
                    buildMd(block.spans, context),
                    style = when (block.level) {
                        1 -> androidx.compose.ui.text.TextStyle(
                            fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold,
                            fontSize = 24.sp, lineHeight = 30.sp, color = F.Cream,
                        )
                        2 -> androidx.compose.ui.text.TextStyle(
                            fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold,
                            fontSize = 20.sp, lineHeight = 26.sp, color = F.Cream,
                        )
                        else -> androidx.compose.ui.text.TextStyle(
                            fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold,
                            fontSize = 17.sp, lineHeight = 23.sp, color = F.Cream,
                        )
                    },
                    modifier = if (last) Modifier else Modifier.padding(bottom = 10.dp),
                )
                is MdBlock.CodeBlock -> CodeBlockView(block, last)
                is MdBlock.BulletList -> Column(
                    if (last) Modifier else Modifier.padding(bottom = 10.dp)
                ) {
                    block.items.forEach { item ->
                        Row {
                            Text("–", style = style, color = F.Lavender, modifier = Modifier.width(16.dp))
                            InlineAwareText(buildMd(item, context), style = style, spans = item, actions = actions)
                        }
                    }
                }
                is MdBlock.NumberList -> Column(
                    if (last) Modifier else Modifier.padding(bottom = 10.dp)
                ) {
                    block.items.forEachIndexed { n, item ->
                        Row {
                            Text("${n + 1}.", style = style, color = F.Lavender, modifier = Modifier.width(24.dp))
                            Text(buildMd(item, context), style = style)
                        }
                    }
                }
                is MdBlock.Quote -> Row(
                    Modifier
                        .padding(bottom = if (last) 0.dp else 10.dp)
                        .height(IntrinsicSize.Min)
                ) {
                    Box(Modifier.width(2.dp).fillMaxHeight().background(F.LavenderDim))
                    Text(
                        buildMd(block.spans, context),
                        style = style.copy(color = F.Lavender),
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }
            }
        }
    }
    // M5: dialog kecil hasil long-press inline code / link — satu aksi salin.
    copyDialog?.let { (kind, value) ->
        androidx.compose.ui.window.Dialog(onDismissRequest = { copyDialog = null }) {
            Column(
                Modifier
                    .clip(Shape.M)
                    .background(F.Surface3, Shape.M)
                    .border(1.dp, F.StrokeBright, Shape.M)
                    .padding(horizontal = 20.dp, vertical = 16.dp)
                    .widthIn(max = 320.dp),
            ) {
                Text(
                    if (kind == "link") "COPY LINK" else "COPY CODE",
                    style = MaterialTheme.typography.labelSmall,
                    color = F.LavenderDim,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    value,
                    style = MaterialTheme.typography.bodySmall,
                    color = F.Lavender,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Cancel",
                        style = MaterialTheme.typography.labelLarge,
                        color = F.Lavender,
                        modifier = Modifier
                            .weight(1f)
                            .clip(Shape.S)
                            .pressClickable { copyDialog = null }
                            .padding(vertical = 10.dp),
                    )
                    Text(
                        "Copy",
                        style = MaterialTheme.typography.labelLarge,
                        color = F.Vermillion,
                        modifier = Modifier
                            .weight(1f)
                            .clip(Shape.S)
                            .pressClickable {
                                clipboard.setText(AnnotatedString(value))
                                copyDialog = null
                            }
                            .padding(vertical = 10.dp),
                    )
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun CodeBlockView(block: MdBlock.CodeBlock, last: Boolean) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    // M5: tombol SALIN di header — copy isi block, label "TERSALIN ✓" 1.5 detik.
    var copied by remember(block.code) { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) { delay(1500); copied = false }
    }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = if (last) 0.dp else 10.dp)
            .background(F.CodeBg, Shape.S)
            .border(1.dp, F.Stroke, Shape.S)
            .padding(vertical = 10.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp)
                .padding(bottom = 6.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Text(
                (block.lang.ifBlank { "CODE" }).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = F.LavenderDim,
                modifier = Modifier.weight(1f),
            )
            Text(
                if (copied) "COPIED ✓" else "COPY",
                style = MaterialTheme.typography.labelSmall,
                color = if (copied) F.Ok else F.Lavender,
                modifier = Modifier
                    .clip(Shape.Xs)
                    .pressClickable {
                        clipboard.setText(AnnotatedString(block.code))
                        copied = true
                    }
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
        SelectionContainer {
            Text(
                block.code,
                fontFamily = JetBrainsMono,
                fontSize = 13.sp,
                lineHeight = 20.sp,
                color = F.CreamDim,
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp),
            )
        }
    }
}

/**
 * Gabung span jadi AnnotatedString — link pakai LinkAnnotation.Clickable.
 * M5: inline code & link punya aksi long-press (copy) — state slot disimpen
 * di [CopySlotRegistry] lokal per-MarkdownText; buildMd jadi non-composable
 * jadi aksinya dikirim lewat callback dari pemanggil (Compose side).
 */
private fun buildMd(spans: List<MdSpan>, context: Context, actions: MdActions? = null): AnnotatedString = buildAnnotatedString {
    spans.forEach { span ->
        when (span) {
            is MdSpan.Text -> append(span.text)
            is MdSpan.Bold -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(span.text) }
            is MdSpan.Italic -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(span.text) }
            is MdSpan.InlineCode -> withStyle(
                SpanStyle(
                    fontFamily = JetBrainsMono,
                    fontSize = 13.sp,
                    background = F.CodeBg,
                    color = F.CreamDim,
                )
            ) { append(" ${span.text} ") }
            is MdSpan.Link -> withLink(
                LinkAnnotation.Clickable(
                    tag = span.url,
                    styles = TextLinkStyles(
                        SpanStyle(color = F.Vermillion, textDecoration = TextDecoration.Underline)
                    ),
                    linkInteractionListener = { openUrlExternal(context, span.url) },
                )
            ) { append(span.text) }
        }
    }
}

// ---------------------------------------------------------------------------
// M5: aksi long-press (copy inline code / salin tautan) + render gambar path
// ---------------------------------------------------------------------------

/**
 * State visual buat long-press copy: satu slot global per MarkdownText
 * (cukup — user cuma sentuh satu elemen pada satu waktu).
 */
class MdActions {
    var onCopyCode: ((String) -> Unit)? = null
    var onCopyLink: ((String) -> Unit)? = null
}

/**
 * M5: render path file gambar dari jawaban agent.
 * Fetch via MediaRepo (GET /api/media → data URL → BitmapFactory, auth cookie).
 * Gagal / di luar media roots → chip teks "Gambar: nama (terkunci di Mac)".
 * `fetch` disuntik pemanggil supaya file ini gak bergantung HermesApp.
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
    when {
        image != null -> androidx.compose.foundation.Image(
            bitmap = image!!,
            contentDescription = name,
            contentScale = ContentScale.FillWidth,
            modifier = modifier
                .fillMaxWidth()
                .clip(Shape.M)
                .border(1.dp, F.Stroke, Shape.M),
        )
        failed -> Row(
            modifier
                .fillMaxWidth()
                .clip(Shape.M)
                .background(F.Surface1, Shape.M)
                .border(1.dp, F.Stroke, Shape.M)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp),
        ) {
            Box(
                Modifier
                    .size(10.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(F.LavenderDim)
            )
            Column {
                Text("Image: $name", style = MaterialTheme.typography.bodySmall, color = F.CreamDim)
                Text(
                    "can't render on this phone (outside the Mac media folder) — open on Mac",
                    style = MaterialTheme.typography.labelSmall,
                    color = F.LavenderDim,
                )
            }
        }
        else -> Box(
            modifier
                .fillMaxWidth()
                .height(160.dp)
                .clip(Shape.M)
                .background(F.Surface1, Shape.M)
                .border(1.dp, F.Stroke, Shape.M),
            contentAlignment = androidx.compose.ui.Alignment.Center,
        ) {
            Text("loading image…", style = MaterialTheme.typography.labelSmall, color = F.LavenderDim)
        }
    }
}

/**
 * M5: Text dengan long-press per-span: tahan di atas inline code → copy code;
 * di atas link → salin tautan. Offset karakter dari pointer position via
 * TextLayoutResult.getOffsetForPosition, dicocokkan ke range span
 * (span dirender linear — offset dihitung dari panjang append tiap span).
 */
@Composable
private fun InlineAwareText(
    annotated: AnnotatedString,
    style: androidx.compose.ui.text.TextStyle,
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
    // M5 fix: pointerInput(Unit) nangkap annotated/hitRanges komposisi pertama —
    // pas streaming, teks berubah tapi lambda lama masih pegang range STALE
    // (long-press nyalin kode/tautan yang salah). rememberUpdatedState biar
    // lambda selalu baca versi terbaru.
    val currentAnnotated by rememberUpdatedState(annotated)
    val currentHits by rememberUpdatedState(hitRanges)
    Text(
        annotated,
        style = style,
        onTextLayout = { layout = it },
        modifier = modifier.pointerInput(Unit) {
            detectTapGestures(
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
