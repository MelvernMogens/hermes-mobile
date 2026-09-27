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
        if (at == null || at <= 0) return "Sesi baru"
        return SimpleDateFormat("Sesi d MMM HH:mm", Locale("id", "ID")).format(Date((at * 1000).toLong()))
    }

    /** Label Indonesia untuk source session (desktop/cli/bot/dst). */
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
    val args: String? = null,
    val id: String? = null,
) {
    val isUser: Boolean get() = role == "user"
}

/** Item chat UI: user / assistant / tool-activity / thinking. */
sealed interface ChatItem {
    data class User(
        val text: String,
        val rowId: Int? = null,
        val pending: Boolean = false,
        val time: String = "",
    ) : ChatItem
    data class Assistant(
        val text: String,
        val done: Boolean,
        val reasoning: String? = null,
        val time: String = "",
    ) : ChatItem
    data class Tool(val name: String, val status: String, val detail: String? = null) : ChatItem
    data class NoticeLine(val text: String) : ChatItem
}
