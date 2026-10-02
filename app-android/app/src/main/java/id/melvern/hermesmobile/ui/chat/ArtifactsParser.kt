package id.melvern.hermesmobile.ui.chat

import id.melvern.hermesmobile.core.model.ChatItem
import id.melvern.hermesmobile.ui.components.MarkdownParser
import id.melvern.hermesmobile.ui.components.RelTime

/**
 * M13: artifacts per session — semua link/foto/video/file yang pernah
 * dikirim di chat, pure client-side dari ChatItem list (TranscriptCache).
 * Tidak ada RPC baru; parse on-demand tiap ArtifactsScreen dibuka.
 *
 * Parser ini PURE (tanpa import Compose) supaya bisa di-unit-test.
 */

/** Satu artifact yang bisa di-tap di list. */
data class Artifact(
    val type: Type,
    /** URL utk Link, absolute unix path utk Image/Video/File. */
    val value: String,
    /** Domain utk link / nama file utk media+file. */
    val title: String,
    val fromUser: Boolean,
    /** Epoch detik pesan sumber (null = tidak diketahui → sort paling bawah harinya). */
    val at: Double?,
) {
    enum class Type { Link, Image, Video, File }
}

/** Baris list ArtifactsScreen: pemisah hari atau artifact. */
sealed interface ArtifactRow {
    val key: String
    data class Day(val label: String, override val key: String) : ArtifactRow
    data class Item(val artifact: Artifact, override val key: String) : ArtifactRow
}

object ArtifactsParser {

    /** Ekstensi non-media yang dianggap file generik (pdf, zip, txt, apk, json, md, dll). */
    private val FILE_EXTS = "pdf|zip|txt|apk|json|md|csv|xml|yaml|yml|html|py|kt|java|sh|ts|js|xlsx|docx|pptx|mp3|wav|log|toml|sql"

    /** Path file non-media di teks bebas (pola sama dengan image/video anywhere). */
    private val FILE_PATH_ANYWHERE = Regex(
        """(?:/[\w.\-]+)+\.($FILE_EXTS)""",
        RegexOption.IGNORE_CASE,
    )

    /** Regex URL + trim tanda baca — identik dengan MdSpan.Link parser. */
    private val URL_RE = Regex("""https?://[^\s<>()\[\]{}'"]+""")

    /**
     * URL yang dipakai sebagai artifact — buang backtick di ujung (URL di dalam
     * inline-code `` `http://x/y`. `` menelan backtick + titik), lalu tanda baca
     * kalimat. Backtick PEMBUKA di depan gak mungkin (bukan whitespace → regex
     * mulai dari h).
     */
    private fun cleanUrl(raw: String): String =
        raw.trimEnd('`', '.', ',', ';', ':', '!', '?')

    /** Chip attach di pesan user: "@file:nama.pdf" / "@image:foto.png" (harus di awal kata). */
    private val ATTACH_REF = Regex("""@(file|image):(\S+)""")

    /** Bersihkan trailing punctuation dari nama file chip (koma/titik di akhir kalimat). */
    private fun cleanChipName(raw: String): String = raw.trimEnd('.', ',', ';', ':', '!', '?', ')', ']', '}')

    /** Judul link: domain (+ jalur pendek kalau host-nya polos /ip/localhost). */
    private fun linkTitle(url: String): String {
        val host = Regex("""^https?://([^/]+)""").find(url)?.groupValues?.get(1)?.removePrefix("www.")
        if (host != null && host matches Regex("""^(\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}|ip|localhost)(:\d+)?$""", RegexOption.IGNORE_CASE)) {
            // host anonim — jalur lebih informatif
            val segs = url.split('#')[0].split('?')[0]
                .removePrefix("http://").removePrefix("https://").split('/')
                .drop(1).filter { it.isNotBlank() }
            val tail = segs.joinToString("/").take(36)
            return if (tail.isBlank()) (host ?: url) else tail
        }
        return host?.takeIf { it.isNotBlank() } ?: url
    }

    private fun pathTitle(path: String): String = path.substringAfterLast('/').ifBlank { path }

    /**
     * Parse seluruh ChatItem list jadi artifact (terbaru dulu, distinct per
     * (type,value), grup hari). Tool/notice/thinking di-skip — cuma pesan
     * user + assistant yang bisa bawa konten.
     */
    fun parse(items: List<ChatItem>): List<Artifact> {
        val out = mutableListOf<Artifact>()
        items.forEach { item ->
            when (item) {
                is ChatItem.User -> {
                    collect(item.text, fromUser = true, at = item.at, out = out)
                    // chip attach: "@file:nama.pdf" / "@image:foto.png" di prompt user.
                    // Review M13 MED#5: mask URL dulu (chip di dalam URL = palsu),
                    // require awal kata, buang tanda baca di ujung.
                    val chipText = URL_RE.replace(item.text) { " ".repeat(it.value.length) }
                    ATTACH_REF.findAll(chipText).forEach { m ->
                        val (kind, name) = m.groupValues[1] to cleanChipName(m.groupValues[2])
                        val boundaryOk = m.range.first == 0 || item.text[m.range.first - 1].isWhitespace()
                        val path = name.trim()
                        if (boundaryOk && path.isNotBlank() && out.none { it.value == path }) {
                            out += Artifact(
                                type = if (kind == "image") Artifact.Type.Image else Artifact.Type.File,
                                value = path,
                                title = pathTitle(path),
                                fromUser = true,
                                at = item.at,
                            )
                        }
                    }
                }
                is ChatItem.Assistant -> {
                    if (item.done) collect(item.text, fromUser = false, at = item.at, out = out)
                }
                else -> {}
            }
        }
        // distinct per (type,value) — pertahankan kemunculan TERAKHIR (timestamp paling baru)
        val seen = HashSet<Pair<Artifact.Type, String>>()
        val dedup = mutableListOf<Artifact>()
        for (a in out.asReversed()) {
            if (seen.add(a.type to a.value)) dedup += a
        }
        // terbaru dulu; at null = paling bawah
        return dedup.sortedWith(compareByDescending<Artifact> { it.at ?: 0.0 })
    }

    /** Ekstrak konten dari satu teks pesan (assistant/user). */
    private fun collect(text: String, fromUser: Boolean, at: Double?, out: MutableList<Artifact>) {
        if (text.isBlank()) return
        // Review M13 HIGH#1: substring path di DALAM URL ("https://x.com/a.pdf" →
        // phantom File "/x.com/a.pdf"; "https://cdn.site/p.png" → phantom Image).
        // Solusi: mask semua URL dengan spasi (panjang tetap) lalu match path di
        // teks yang sudah bersih; link diambil dari teks asli.
        val pathText = URL_RE.replace(text) { " ".repeat(it.value.length) }

        MarkdownParser.imagePathsIn(pathText).forEach { p ->
            out += Artifact(Artifact.Type.Image, p, pathTitle(p), fromUser, at)
        }
        MarkdownParser.videoPathsIn(pathText).forEach { p ->
            out += Artifact(Artifact.Type.Video, p, pathTitle(p), fromUser, at)
        }
        FILE_PATH_ANYWHERE.findAll(pathText).forEach { m ->
            out += Artifact(Artifact.Type.File, m.value, pathTitle(m.value), fromUser, at)
        }
        URL_RE.findAll(text).forEach { m ->
            val url = cleanUrl(m.value)
            // bare "https://" (backtick dibuang) → bukan URL nyata
            val host = Regex("""^https?://([^/?#]+)""").find(url)?.groupValues?.get(1)
            if (url.startsWith("http") && !host.isNullOrBlank()) {
                out += Artifact(Artifact.Type.Link, url, linkTitle(url), fromUser, at)
            }
        }
    }

    /**
     * Susun baris list terbaru-dulu dengan pemisah hari (gaya chat:
     * Today / Yesterday / Monday / 3 October). Artifact tanpa `at` masuk
     * grup paling akhir tanpa pemisah.
     */
    fun rows(artifacts: List<Artifact>, label: (java.time.LocalDate) -> String): List<ArtifactRow> {
        val out = mutableListOf<ArtifactRow>()
        var lastDay: java.time.LocalDate? = null
        artifacts.forEach { a ->
            val day = a.at?.let { RelTime.dayKey(it) }
            if (day != null && day != lastDay) {
                out += ArtifactRow.Day(label(day), "d$day")
                lastDay = day
            }
            // key stabil dari (type,value) — indeks tidak dipakai supaya item
            // LazyColumn gak kehilangan state saat list berubah.
            out += ArtifactRow.Item(a, "a-${a.type}-${a.value}")
        }
        return out
    }
}
