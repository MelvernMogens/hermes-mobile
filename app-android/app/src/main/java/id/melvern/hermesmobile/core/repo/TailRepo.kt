package id.melvern.hermesmobile.core.repo

import id.melvern.hermesmobile.core.auth.SharedAuth
import id.melvern.hermesmobile.core.model.ChatItem
import id.melvern.hermesmobile.core.model.TranscriptMessage
import id.melvern.hermesmobile.core.store.ConnectionSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import java.net.URLEncoder

/**
 * v28 item E: N pesan aktif terakhir via HTTP (/api/mobile-tail) — bentuk = session.resume `messages`,
 * di-decode dengan serializer TranscriptMessage yang sama. Chat raksasa (3000+ pesan) tampil instan;
 * resume penuh menyusul dan MENGGANTI daftar ini seluruhnya.
 */
class TailRepo(private val settings: ConnectionSettings) {
    data class Tail(val messages: List<TranscriptMessage>, val hasMore: Boolean)

    private val base get() = settings.baseUrl.trim().trimEnd('/')

    suspend fun tail(storedId: String, profile: String?, limit: Int = 120): Tail? {
        val a = withContext(Dispatchers.IO) { SharedAuth.get(base, settings.username, settings.password) } ?: return null
        val p = profile?.takeIf { it.isNotBlank() && it != "default" }?.let { "&profile=" + URLEncoder.encode(it, "UTF-8") } ?: ""
        return a.getJson("$base/api/mobile-tail?id=${URLEncoder.encode(storedId, "UTF-8")}&limit=$limit$p")?.let { parse(it) }
    }

    companion object {
        const val PARTIAL_NOTICE = "Loading earlier messages…"
        private val json = Json { ignoreUnknownKeys = true }

        fun parse(body: String): Tail? = runCatching {
            val o = json.parseToJsonElement(body).jsonObject
            val arr = o["messages"] as? JsonArray ?: return@runCatching null
            val msgs = arr.mapNotNull { el ->
                try { json.decodeFromJsonElement(TranscriptMessage.serializer(), el.jsonObject) } catch (_: Throwable) { null }
            }
            Tail(msgs, (o["has_more"] as? JsonPrimitive)?.booleanOrNull ?: false)
        }.getOrNull()

        /** Daftar sementara: baris "Loading earlier messages…" di atas kalau masih ada pesan lebih lama. */
        fun withPartialNotice(items: List<ChatItem>, hasMore: Boolean): List<ChatItem> =
            if (hasMore) listOf(ChatItem.NoticeLine(PARTIAL_NOTICE)) + items else items
    }
}
