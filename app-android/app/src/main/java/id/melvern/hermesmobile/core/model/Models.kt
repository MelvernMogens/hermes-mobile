package id.melvern.hermesmobile.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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
) {
    val displayTitle: String get() = title?.takeIf { it.isNotBlank() } ?: "(${id})"
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
    data class User(val text: String, val rowId: Int? = null, val pending: Boolean = false) : ChatItem
    data class Assistant(val text: String, val done: Boolean, val reasoning: String? = null) : ChatItem
    data class Tool(val name: String, val status: String, val detail: String? = null) : ChatItem
    data class NoticeLine(val text: String) : ChatItem
}
