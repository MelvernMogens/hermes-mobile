package id.melvern.hermesmobile.core.repo

import id.melvern.hermesmobile.core.rpc.GatewayClient
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.put

/**
 * v23 — fitur "agent lagi kerja":
 *  - Todo/task board: snapshot `todo.updated` / `todo_state` (resume).
 *  - Subagent monitor: subagent.list / tail / steer / interrupt (per runtime session).
 *  - Steer: session.steer — sisipkan arahan ke turn yang jalan tanpa stop.
 *  - Permission inbox: semua approval/clarify yang menunggu di semua chat live.
 */
class AgentWorkRepo(private val c: GatewayClient, private val profile: String = "") {

    // ── Todo ──────────────────────────────────────────────────────────
    data class Todo(val id: String, val content: String, val status: String, val parent: String? = null) {
        val done get() = status == "completed"
        val active get() = status == "in_progress"
        val cancelled get() = status == "cancelled"
    }

    // ── Subagents ─────────────────────────────────────────────────────
    data class Subagent(
        val id: String, val goal: String, val status: String, val model: String,
        val toolCount: Int, val lastTool: String, val startedAt: Double?, val depth: Int, val acceptingSteer: Boolean,
    ) {
        val running get() = status in setOf("running", "starting", "queued", "") 
    }

    suspend fun subagents(runtimeId: String): List<Subagent> {
        val r = c.call("subagent.list", buildJsonObject { put("session_id", runtimeId) }, timeoutMs = 15_000)
        return parseSubagents(r)
    }

    suspend fun tail(runtimeId: String, subagentId: String): String {
        val r = c.call("subagent.tail", buildJsonObject { put("session_id", runtimeId); put("subagent_id", subagentId) })
        return (r["text"] as? JsonPrimitive)?.contentOrNull.orEmpty()
    }

    /** Return true kalau server menerima (status queued). */
    suspend fun steerSubagent(runtimeId: String, subagentId: String, text: String): Boolean {
        val r = c.call("subagent.steer", buildJsonObject {
            put("session_id", runtimeId); put("subagent_id", subagentId); put("text", text)
        })
        return (r["status"] as? JsonPrimitive)?.contentOrNull == "queued"
    }

    suspend fun stopSubagent(runtimeId: String, subagentId: String): Boolean {
        val r = c.call("subagent.interrupt", buildJsonObject { put("session_id", runtimeId); put("subagent_id", subagentId) })
        return (r["found"] as? JsonPrimitive)?.booleanOrNull == true
    }

    /** Steer turn yang jalan: teks masuk ke hasil tool berikutnya, turn TIDAK berhenti. */
    suspend fun steer(runtimeId: String, text: String): Boolean {
        val r = c.call("session.steer", buildJsonObject {
            put("session_id", runtimeId); put("text", text)
            if (profile.isNotBlank() && profile != "default") put("profile", profile)
        })
        return (r["status"] as? JsonPrimitive)?.contentOrNull == "queued"
    }

    // ── Inbox ────────────────────────────────────────────────────────
    data class Pending(
        val storedId: String, val chatTitle: String, val requestId: String, val kind: String,
        val title: String, val detail: String, val params: JsonObject?,
    )

    /** Semua request yang menunggu: chat live berstatus waiting → open_requests (resume lazy, tanpa history). */
    suspend fun inbox(): List<Pending> {
        val live = c.call("session.active_list", buildJsonObject { }, timeoutMs = 15_000)
        val rows = (live["sessions"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
            .filter { (it["status"] as? JsonPrimitive)?.contentOrNull == "waiting" }
        val repo = SessionRepo(c, profile)
        return rows.flatMap { row ->
            val stored = (row["session_key"] as? JsonPrimitive)?.contentOrNull?.ifBlank { null }
                ?: (row["id"] as? JsonPrimitive)?.contentOrNull ?: return@flatMap emptyList()
            val chatTitle = (row["title"] as? JsonPrimitive)?.contentOrNull?.ifBlank { null } ?: stored.take(14)
            repo.resumeOpenRequests(stored).mapNotNull { req -> toPending(stored, chatTitle, req) }
        }
    }

    suspend fun answer(requestId: String, result: JsonObject): Boolean {
        val r = c.call("request.answer", buildJsonObject {
            put("id", requestId); put("result", result)
            if (profile.isNotBlank() && profile != "default") put("profile", profile)
        })
        return (r["status"] as? JsonPrimitive)?.contentOrNull == "ok"
    }

    companion object {
        private fun JsonElement?.str() = (this as? JsonPrimitive)?.contentOrNull

        fun parseTodos(state: JsonElement?): List<Todo> {
            val arr = when (state) {
                is JsonObject -> state["todos"] as? JsonArray
                is JsonArray -> state
                else -> null
            } ?: return emptyList()
            return arr.mapIndexedNotNull { i, el ->
                val o = el as? JsonObject ?: return@mapIndexedNotNull null
                val content = o["content"].str()?.trim().orEmpty().ifEmpty { return@mapIndexedNotNull null }
                Todo(o["id"].str() ?: "t$i", content, o["status"].str()?.lowercase() ?: "pending", o["parent"].str()?.ifBlank { null })
            }
        }

        fun parseSubagents(r: JsonObject): List<Subagent> =
            (r["subagents"] as? JsonArray).orEmpty().mapNotNull { el ->
                val o = el as? JsonObject ?: return@mapNotNull null
                val id = o["subagent_id"].str() ?: return@mapNotNull null
                Subagent(
                    id = id,
                    goal = o["goal"].str().orEmpty(),
                    status = o["status"].str().orEmpty(),
                    model = o["model"].str().orEmpty(),
                    toolCount = (o["tool_count"] as? JsonPrimitive)?.intOrNull ?: 0,
                    lastTool = o["last_tool"].str().orEmpty(),
                    startedAt = (o["started_at"] as? JsonPrimitive)?.doubleOrNull,
                    depth = (o["depth"] as? JsonPrimitive)?.intOrNull ?: 1,
                    acceptingSteer = (o["accepting_steer"] as? JsonPrimitive)?.booleanOrNull ?: true,
                )
            }

        fun toPending(stored: String, chatTitle: String, req: SessionRepo.OpenRequest): Pending? {
            val p = req.params
            return when (req.method) {
                "approval" -> Pending(
                    stored, chatTitle, req.id, "approval",
                    title = p?.get("tool_name").str()?.ifBlank { null }?.replace('_', ' ') ?: "Command",
                    detail = p?.get("command").str() ?: p?.get("description").str() ?: "",
                    params = p,
                )
                "clarify" -> {
                    val qs = p?.get("questions") as? JsonArray
                    val first = (qs?.firstOrNull() as? JsonObject)
                    Pending(
                        stored, chatTitle, req.id, "clarify",
                        title = if ((qs?.size ?: 0) > 1) "${qs!!.size} questions" else "Question",
                        detail = p?.get("question").str() ?: first?.get("question").str() ?: "",
                        params = p,
                    )
                }
                else -> null
            }
        }
    }
}

/**
 * Unread: jumlah balasan agent baru sejak chat terakhir dibuka. Lokal, dari
 * jejak `last at` (mobile-last) vs waktu buka terakhir — tanpa server baru.
 */
object UnreadStore {
    private val seenAt = java.util.concurrent.ConcurrentHashMap<String, Double>()
    @Volatile private var loaded = false
    private const val PREF = "unread_seen"

    fun load(context: android.content.Context) {
        if (loaded) return
        val sp = context.getSharedPreferences(PREF, android.content.Context.MODE_PRIVATE)
        sp.all.forEach { (k, v) -> (v as? Float)?.let { seenAt[k] = it.toDouble() } ?: (v as? String)?.toDoubleOrNull()?.let { seenAt[k] = it } }
        loaded = true
    }

    fun markSeen(context: android.content.Context, storedId: String, at: Double = System.currentTimeMillis() / 1000.0) {
        seenAt[storedId] = at
        context.getSharedPreferences(PREF, android.content.Context.MODE_PRIVATE).edit().putString(storedId, at.toString()).apply()
    }

    /** Belum pernah dibuka di HP ini = tidak dianggap unread (hindari 100 badge saat install pertama). */
    fun isUnread(storedId: String, lastAt: Double?, lastRole: String?): Boolean =
        isUnreadPure(seenAt[storedId], lastAt, lastRole)

    fun isUnreadPure(seen: Double?, lastAt: Double?, lastRole: String?): Boolean =
        seen != null && lastAt != null && lastRole == "assistant" && lastAt > seen + 1.0
}
