package id.melvern.hermesmobile.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Baris session.list — sesuai gateway-contract SessionListRow. */
@Serializable
data class SessionRow(
    val id: String,
    val title: String? = null,
    val preview: String? = null,
    @SerialName("started_at") val startedAt: Double? = null,
    @SerialName("updated_at") val updatedAt: Double? = null,
    @SerialName("message_count") val messageCount: Int = 0,
    val source: String? = null,
    val profile: String? = null,
    val running: Boolean? = null,
    val pinned: Boolean? = null,
    val resolvedId: String? = null,
) {
    /**
     * M3.2: JANGAN pernah nampilin ID mentah (angka) — complaint user 27 Sep.
     * Fallback chain: title → preview 40 char → "Sesi {d MMM HH:mm}" dari startedAt.
     */
    val displayTitle: String
        get() = title?.takeIf { it.isNotBlank() }
            ?: preview?.takeIf { it.isNotBlank() }?.let { p ->
                val flat = p.replace('\n', ' ').trim()
                if (flat.length > 40) flat.take(40).trimEnd() + "…" else flat
            }
            ?: fallbackSessionDate()

    private fun fallbackSessionDate(): String {
        val at = startedAt
        if (at == null || at <= 0) return "New session"
        return SimpleDateFormat("MMM d, HH:mm", Locale.US).format(Date((at * 1000).toLong()))
    }

    /** Label English untuk source session (desktop/cli/bot/dst) — M5b. */
    val sourceLabel: String
        get() = when (source?.trim()?.lowercase()) {
            "desktop", "desktop_app" -> "Desktop"
            "cli" -> "CLI"
            "bot", "gateway" -> "Bot"
            "tui" -> "TUI"
            "mobile" -> "Mobile"
            null, "" -> "Hermes"
            else -> source!!.trim().replaceFirstChar { it.uppercase() }.take(12)
        }
}

/** Pesan transcript dari session.resume — field sesuai TranscriptMessage contract. */
@Serializable
data class TranscriptMessage(
    val role: String,
    val text: String? = null,
    @SerialName("row_id") val rowId: Int? = null,
    val timestamp: Double? = null,
    val reasoning: String? = null,
    val name: String? = null,
    val context: String? = null,
    /** Server kirim object (dict args tool) — dulu dideklarasi String → decode gagal
     *  dan SEMUA baris tool di transcript lama hilang diam-diam. */
    val args: kotlinx.serialization.json.JsonElement? = null,
    val id: String? = null,
) {
    /** Args tool sebagai teks tampilan (pretty JSON / string mentah). */
    val argsText: String?
        get() = when (val a = args) {
            null, is kotlinx.serialization.json.JsonNull -> null
            is kotlinx.serialization.json.JsonPrimitive -> a.content
            else -> TranscriptJson.pretty.encodeToString(kotlinx.serialization.json.JsonElement.serializer(), a)
        }

    val isUser: Boolean get() = role == "user"
}

/** Formatter args tool — top-level (companion di @Serializable class bentrok dgn serializer()). */
internal object TranscriptJson {
    val pretty = kotlinx.serialization.json.Json { prettyPrint = true }
}

/** Item chat UI: user / assistant / tool-activity / thinking. */
sealed interface ChatItem {
    data class User(
        val text: String,
        val rowId: Int? = null,
        val pending: Boolean = false,
        /** M5: submit balik status "queued" — pesan dijamin server, muncul setelah turn jalan. */
        val queued: Boolean = false,
        val time: String = "",
        /** M8: epoch detik — pemisah hari di chat (Today / Yesterday). */
        val at: Double? = null,
        /** M11: teks pesan yang di-quote (reply) — tampil sebagai blok kutipan di atas isi. */
        val quote: String? = null,
        /** v23: dikirim sebagai steer (masuk ke turn yang sedang jalan). */
        val steered: Boolean = false,
        /** v26.3: gagal terkirim — tap untuk kirim ulang (pesan tersimpan di Outbox). */
        val failed: Boolean = false,
        /** v26.3: id entri Outbox (pesan yang dikirim dari HP ini, belum terkonfirmasi server). */
        val outboxId: String? = null,
    ) : ChatItem
    data class Assistant(
        val text: String,
        val done: Boolean,
        val reasoning: String? = null,
        val time: String = "",
        /** M8: epoch detik — pemisah hari. */
        val at: Double? = null,
        /** M8: durasi thinking live (detik) → "Thought for 12s"; null = tidak diketahui. */
        val thoughtSecs: Int? = null,
    ) : ChatItem
    /** M8: [toolId] = tool_id event live (match start↔complete); [detail] = args + output → code block saat expand. */
    data class Tool(val name: String, val status: String, val detail: String? = null, val toolId: String? = null) : ChatItem
    data class NoticeLine(val text: String) : ChatItem
    /** Output slash command (lokal, tidak masuk transcript agent). */
    data class Command(val command: String, val output: String?, val failed: Boolean = false) : ChatItem
}

/**
 * M5: hasil attach di composer — `refText` diselipin ke prompt saat kirim
 * (file: "@file:..." — image gak butuh ref, auto-queued server, tapi tetap
 * dicatat biar chip-nya jelas dan prompt bisa nyebut namanya).
 */
data class Attachment(
    val refText: String,
    val name: String,
    val isImage: Boolean,
    /** Path absolut di Mac — ikut dikirim sebagai baris MEDIA: supaya bubble bisa render foto/video/kartu file. */
    val path: String = "",
    val isVideo: Boolean = false,
)


/**
 * Steer/redirect disimpan server sebagai baris user yang dibungkus penanda internal
 * "[OUT-OF-BAND USER MESSAGE …] teks [/OUT-OF-BAND USER MESSAGE]". Untuk tampilan:
 * buka bungkusnya → (teks asli, true). Teks biasa → (teks, false).
 */
object SteerText {
    private val rx = Regex("""^\s*\[OUT-OF-BAND USER MESSAGE[^\]]*]\s*(.*?)\s*\[/OUT-OF-BAND USER MESSAGE]\s*$""", RegexOption.DOT_MATCHES_ALL)
    fun unwrap(text: String): Pair<String, Boolean> =
        rx.find(text)?.let { it.groupValues[1].trim() to true } ?: (text to false)
}

/**
 * v26.2: kunci pencocokan pesan user lokal (bubble optimistis) vs row DB.
 * Server menambah baris `@file:` / `@image:` dan blok "--- Attached Context ---" ke prompt
 * berlampiran — tanpa normalisasi, bubble lokal tidak dikenali dan tampil dobel.
 */
object UserMatch {
    private val CTX = Regex("""\n*-{3}\s*Attached Context\s*-{3}""")
    fun key(raw: String): String {
        val cut = CTX.find(raw)?.let { raw.substring(0, it.range.first) } ?: raw
        return cut.lineSequence().map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("@file:") && !it.startsWith("@image:") }
            .joinToString("\n")
    }
    /** [local] (tanpa quote) cocok dengan [server] (bisa diawali "> quote"). */
    fun same(local: String, server: String): Boolean {
        val a = key(local); val b = key(server)
        if (a == b) return true
        // review P1-2: suffix hanya pada batas baris (server bisa menambah "> quote" di atas) —
        // "ok" tidak boleh cocok dengan "book"/"hook".
        return a.isNotEmpty() && b.endsWith("\n" + a)
    }
}
