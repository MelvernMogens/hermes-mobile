package id.melvern.hermesmobile.core.repo

import id.melvern.hermesmobile.core.auth.DashboardAuth
import id.melvern.hermesmobile.core.store.ConnectionSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import java.net.URLEncoder

/**
 * Read-only insights dari proxy Mac (server/mobile_insights.py):
 *  - /api/mobile-last  → pesan TERAKHIR per session (session.list preview =
 *    prompt pertama — itu yang bikin home "gak nunjukin chat terakhir");
 *  - /api/mobile-usage → token per provider 30 hari (usage.bars cuma Nous portal).
 * Cookie auth sama dengan MediaRepo. Gagal = null → UI fallback ke data RPC.
 */
class InsightsRepo(private val settings: ConnectionSettings) {

    data class Last(val role: String, val text: String, val at: Double?, val turnAt: Double? = null, val tool: String? = null)

    data class ProviderUsage(val provider: String, val sessions: Int, val tokens: Long, val input: Long, val output: Long)
    data class Usage(
        val totalTokens: Long,
        val todayTokens: Long,
        val totalSessions: Int,
        val todaySessions: Int,
        val days: Int,
        val providers: List<ProviderUsage>,
    )

    private fun base() = settings.baseUrl.trim().trimEnd('/')
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    suspend fun lastMessages(profile: String, ids: List<String>): Map<String, Last>? = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext emptyMap()
        val auth = id.melvern.hermesmobile.core.auth.SharedAuth.get(base(), settings.username, settings.password) ?: return@withContext null
        val body = auth.getJson("${base()}/api/mobile-last?profile=${enc(profile)}&ids=${enc(ids.take(120).joinToString(","))}")
            ?: return@withContext null
        parseLast(body)
    }

    data class UserMsg(val rowId: Int?, val text: String, val at: Double, val kind: String? = null, val displayText: String? = null)

    /** Prompt user di session ini setelah [after] (epoch s) — termasuk yang diketik di desktop. */
    suspend fun userTail(profile: String, storedId: String, after: Double): List<UserMsg>? = withContext(Dispatchers.IO) {
        val auth = id.melvern.hermesmobile.core.auth.SharedAuth.get(base(), settings.username, settings.password) ?: return@withContext null
        val body = auth.getJson("${base()}/api/mobile-user-tail?profile=${enc(profile)}&id=${enc(storedId)}&after=$after")
            ?: return@withContext null
        parseUserTail(body)
    }

    suspend fun usage(profile: String): Usage? = withContext(Dispatchers.IO) {
        val auth = id.melvern.hermesmobile.core.auth.SharedAuth.get(base(), settings.username, settings.password) ?: return@withContext null
        val body = auth.getJson("${base()}/api/mobile-usage?profile=${enc(profile)}") ?: return@withContext null
        parseUsage(body)
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun parseLast(body: String): Map<String, Last>? = try {
            val root = json.parseToJsonElement(body).jsonObject["last"]?.jsonObject ?: JsonObject(emptyMap())
            root.mapValues { (_, v) ->
                val o = v.jsonObject
                Last(
                    role = (o["role"] as? JsonPrimitive)?.content.orEmpty(),
                    text = (o["text"] as? JsonPrimitive)?.content.orEmpty(),
                    at = (o["at"] as? JsonPrimitive)?.doubleOrNull,
                    turnAt = (o["turn_at"] as? JsonPrimitive)?.doubleOrNull,
                    tool = (o["tool"] as? JsonPrimitive)?.contentOrNull,
                )
            }.filterValues { it.text.isNotBlank() }
        } catch (_: Throwable) { null }

        fun parseUserTail(body: String): List<UserMsg>? = try {
            json.parseToJsonElement(body).jsonObject["messages"]?.jsonArray?.mapNotNull { e ->
                val o = e.jsonObject
                val text = (o["text"] as? JsonPrimitive)?.content.orEmpty()
                val at = (o["at"] as? JsonPrimitive)?.doubleOrNull ?: return@mapNotNull null
                if (text.isBlank()) null else UserMsg((o["row_id"] as? JsonPrimitive)?.intOrNull, text, at,
                    (o["display_kind"] as? JsonPrimitive)?.content, (o["display_text"] as? JsonPrimitive)?.content)
            } ?: emptyList()
        } catch (_: Throwable) { null }

        /**
         * Sisipkan prompt user yang belum ada di transcript lokal. Pesan dari HP
         * sendiri sudah ada (optimistic) → dedupe by rowId lalu teks. Disisip
         * SEBELUM assistant yang sedang streaming supaya urutan tetap user → balasan.
         */
        fun mergeUserTail(items: List<id.melvern.hermesmobile.core.model.ChatItem>, tail: List<UserMsg>,
                          clock: (Double) -> String): List<id.melvern.hermesmobile.core.model.ChatItem> {
            if (tail.isEmpty()) return items
            val users = items.filterIsInstance<id.melvern.hermesmobile.core.model.ChatItem.User>()
            val haveIds = users.mapNotNull { it.rowId }.toSet() +
                items.mapNotNull { (it as? id.melvern.hermesmobile.core.model.ChatItem.Event)?.rowId }
            val recentTexts = users.takeLast(12).map { it.text.trim() }.toMutableList()
            val missing = tail.filter { m ->
                if (m.rowId != null && m.rowId in haveIds) return@filter false
                if (id.melvern.hermesmobile.core.model.SystemEvent.kindOf(m.kind, m.text) != null) return@filter true
                // pesan dari HP: teks yang dikirim bisa diawali "> quote" / berisi ref lampiran — cocokkan kunci
                val t = id.melvern.hermesmobile.core.model.SteerText.unwrap(m.text).first.trim()
                val hit = recentTexts.indexOfFirst { id.melvern.hermesmobile.core.model.UserMatch.same(it, t) }
                if (hit >= 0) { recentTexts.removeAt(hit); false } else true
            }
            if (missing.isEmpty()) return items
            val add = missing.map {
                val kind = id.melvern.hermesmobile.core.model.SystemEvent.kindOf(it.kind, it.text)
                if (kind != null) id.melvern.hermesmobile.core.model.ChatItem.Event(kind,
                    id.melvern.hermesmobile.core.model.SystemEvent.label(kind, it.displayText, it.text), it.text, it.rowId, it.at)
                else {
                    val (txt, steered) = id.melvern.hermesmobile.core.model.SteerText.unwrap(it.text)
                    id.melvern.hermesmobile.core.model.ChatItem.User(txt, it.rowId, time = clock(it.at), at = it.at, steered = steered)
                }
            }
            val streamingIdx = items.indexOfLast { it is id.melvern.hermesmobile.core.model.ChatItem.Assistant && !it.done }
            return if (streamingIdx >= 0) items.take(streamingIdx) + add + items.drop(streamingIdx) else items + add
        }

        /** Pure: isi rowId bubble user yang kosong dari tail DB — cocok per teks, dari yang terbaru. */
        fun backfillRowIds(items: List<id.melvern.hermesmobile.core.model.ChatItem>, tail: List<UserMsg>): List<id.melvern.hermesmobile.core.model.ChatItem> {
            val used = items.mapNotNull { (it as? id.melvern.hermesmobile.core.model.ChatItem.User)?.rowId }.toMutableSet()
            val pool = tail.filter { it.rowId != null && it.rowId !in used }.toMutableList()
            val out = items.toMutableList()
            for (i in out.indices.reversed()) {
                val u = out[i] as? id.melvern.hermesmobile.core.model.ChatItem.User ?: continue
                if (u.rowId != null) continue
                val want = u.text.trim()
                val k = pool.indexOfLast { m ->
                    val t = id.melvern.hermesmobile.core.model.SteerText.unwrap(m.text).first.trim()
                    id.melvern.hermesmobile.core.model.UserMatch.same(want, t)
                }
                if (k >= 0) { out[i] = u.copy(rowId = pool[k].rowId); pool.removeAt(k) }
            }
            return out
        }

        fun parseUsage(body: String): Usage? = try {
            val o = json.parseToJsonElement(body).jsonObject
            if ((o["available"] as? JsonPrimitive)?.booleanOrNull != true) null
            else Usage(
                totalTokens = (o["total_tokens"] as? JsonPrimitive)?.longOrNull ?: 0,
                todayTokens = (o["today_tokens"] as? JsonPrimitive)?.longOrNull ?: 0,
                totalSessions = (o["total_sessions"] as? JsonPrimitive)?.intOrNull ?: 0,
                todaySessions = (o["today_sessions"] as? JsonPrimitive)?.intOrNull ?: 0,
                days = (o["days"] as? JsonPrimitive)?.intOrNull ?: 30,
                providers = o["providers"]?.jsonArray?.map { pe ->
                    val p = pe.jsonObject
                    ProviderUsage(
                        provider = (p["provider"] as? JsonPrimitive)?.content.orEmpty(),
                        sessions = (p["sessions"] as? JsonPrimitive)?.intOrNull ?: 0,
                        tokens = (p["tokens"] as? JsonPrimitive)?.longOrNull ?: 0,
                        input = (p["input"] as? JsonPrimitive)?.longOrNull ?: 0,
                        output = (p["output"] as? JsonPrimitive)?.longOrNull ?: 0,
                    )
                }.orEmpty(),
            )
        } catch (_: Throwable) { null }

        /** 45_317_902 → "45.3M" · 276_582 → "277K" · 980 → "980". */
        fun compact(n: Long): String = when {
            n >= 1_000_000_000 -> String.format(java.util.Locale.US, "%.1fB", n / 1e9)
            n >= 10_000_000 -> String.format(java.util.Locale.US, "%.0fM", n / 1e6)
            n >= 1_000_000 -> String.format(java.util.Locale.US, "%.1fM", n / 1e6)
            n >= 1_000 -> String.format(java.util.Locale.US, "%.0fK", n / 1e3)
            else -> n.toString()
        }

        /** "zai" → "Z.ai" dll — nama provider manusiawi. */
        fun providerName(slug: String): String = when (slug.lowercase()) {
            "zai" -> "Z.ai"
            "anthropic" -> "Anthropic"
            "openai", "openai-codex" -> "OpenAI"
            "openrouter" -> "OpenRouter"
            "google", "gemini" -> "Google"
            "nous" -> "Nous"
            "other", "" -> "Other"
            else -> slug.replaceFirstChar { it.uppercase() }
        }
    }
}
