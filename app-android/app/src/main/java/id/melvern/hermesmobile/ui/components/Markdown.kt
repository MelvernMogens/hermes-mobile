package id.melvern.hermesmobile.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
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
import id.melvern.hermesmobile.ui.theme.Shape

/**
 * Renderer markdown custom untuk jawaban assistant (M3.1) — tanpa dependency
 * markdown eksternal. Parser [MarkdownParser] = pure Kotlin (unit-testable),
 * komposit [MarkdownText] = render AnnotatedString + block layout.
 *
 * Support: **bold** / *italic* / `code`, fence code block + header bahasa,
 * heading #..####, list - dan * serta 1., link [t](url) + URL polos (linkify),
 * blockquote >.
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
}

object MarkdownParser {

    private val HEADING = Regex("^(#{1,6})\\s+(.*)$")
    private val BULLET = Regex("^\\s*[-*]\\s+(.*)$")
    private val ORDERED = Regex("^\\s*(\\d+)[.)]\\s+(.*)$")
    private val QUOTE = Regex("^\\s*>\\s?(.*)$")

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
) {
    val context = LocalContext.current
    val blocks = remember(text) { MarkdownParser.parse(text) }
    Column(modifier = modifier) {
        blocks.forEachIndexed { idx, block ->
            val last = idx == blocks.lastIndex
            when (block) {
                is MdBlock.Paragraph -> Text(
                    buildMd(block.spans, context),
                    style = style,
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
                            Text(buildMd(item, context), style = style)
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
}

@Composable
private fun CodeBlockView(block: MdBlock.CodeBlock, last: Boolean) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = if (last) 0.dp else 10.dp)
            .background(F.CodeBg, Shape.S)
            .border(1.dp, F.Stroke, Shape.S)
            .padding(vertical = 10.dp),
    ) {
        if (block.lang.isNotBlank()) {
            Text(
                block.lang.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = F.LavenderDim,
                modifier = Modifier.padding(horizontal = 14.dp).padding(bottom = 6.dp),
            )
        }
        SelectionContainer {
            Text(
                block.code,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                color = F.CreamDim,
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp),
            )
        }
    }
}

/** Gabung span jadi AnnotatedString — link pakai LinkAnnotation.Clickable. */
private fun buildMd(spans: List<MdSpan>, context: Context): AnnotatedString = buildAnnotatedString {
    spans.forEach { span ->
        when (span) {
            is MdSpan.Text -> append(span.text)
            is MdSpan.Bold -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(span.text) }
            is MdSpan.Italic -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(span.text) }
            is MdSpan.InlineCode -> withStyle(
                SpanStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
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
