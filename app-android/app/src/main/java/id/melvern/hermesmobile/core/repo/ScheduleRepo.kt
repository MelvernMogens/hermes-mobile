package id.melvern.hermesmobile.core.repo

import id.melvern.hermesmobile.core.auth.DashboardAuth
import id.melvern.hermesmobile.core.store.ConnectionSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.net.URLEncoder
import kotlinx.serialization.builtins.serializer

/** v25: jadwal bot (cron dashboard API) + cari isi chat (FTS /api/sessions/search). */
class ScheduleRepo(private val settings: ConnectionSettings) {
    data class Job(
        val id: String, val name: String, val prompt: String, val schedule: String, val scheduleLabel: String,
        val enabled: Boolean, val state: String, val nextRunAt: String?, val lastRunAt: String?, val lastStatus: String?, val profile: String?,
    ) { val paused get() = !enabled || state == "paused" }

    data class Hit(val sessionId: String, val title: String, val snippet: String, val role: String?, val at: Double?)

    private val base get() = settings.baseUrl.trim().trimEnd('/')
    private suspend fun auth(): DashboardAuth? = withContext(Dispatchers.IO) {
        id.melvern.hermesmobile.core.auth.SharedAuth.get(base, settings.username, settings.password)
    }
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    suspend fun jobs(): List<Job>? = auth()?.getJson("$base/api/cron/jobs?profile=all")?.let { parseJobs(it) }

    suspend fun create(name: String, prompt: String, schedule: String): Result<Unit> {
        val a = auth() ?: return Result.failure(Exception("Not signed in"))
        val body = buildJsonObject { put("name", name); put("prompt", prompt); put("schedule", schedule); put("deliver", "local") }.toString()
        val out = a.request("POST", "$base/api/cron/jobs", body)
        return if (out.first in 200..299) Result.success(Unit) else Result.failure(Exception(detail(out.second) ?: "Couldn't create (HTTP ${out.first})"))
    }

    suspend fun pause(id: String, pause: Boolean) = act("POST", "$base/api/cron/jobs/${enc(id)}/${if (pause) "pause" else "resume"}")
    suspend fun runNow(id: String) = act("POST", "$base/api/cron/jobs/${enc(id)}/trigger")
    suspend fun delete(id: String) = act("DELETE", "$base/api/cron/jobs/${enc(id)}")

    private suspend fun act(method: String, url: String): Result<Unit> {
        val a = auth() ?: return Result.failure(Exception("Not signed in"))
        val (code, body) = a.request(method, url, if (method == "DELETE") null else "{}")
        return if (code in 200..299) Result.success(Unit) else Result.failure(Exception(detail(body) ?: "HTTP $code"))
    }

    suspend fun search(q: String, profile: String): List<Hit>? {
        if (q.isBlank()) return emptyList()
        val p = if (profile.isBlank() || profile == "default") "" else "&profile=${enc(profile)}"
        return auth()?.getJson("$base/api/sessions/search?q=${enc(q.trim())}&limit=30$p")?.let { parseHits(it) }
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }
        private fun JsonObject.s(k: String) = (this[k] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

        fun detail(body: String?): String? = body?.let { runCatching { json.parseToJsonElement(it).jsonObject.s("detail") }.getOrNull() }

        fun parseJobs(body: String): List<Job>? = runCatching {
            val el = json.parseToJsonElement(body)
            val arr = (el as? JsonArray) ?: (el.jsonObject["jobs"]?.jsonArray) ?: JsonArray(emptyList())
            arr.mapNotNull { e ->
                val o = e as? JsonObject ?: return@mapNotNull null
                val sched = o["schedule"]
                val schedExpr = (sched as? JsonPrimitive)?.contentOrNull
                    ?: (sched as? JsonObject)?.let { it.s("expr") ?: it.s("value") ?: it.s("display") } ?: ""
                Job(
                    id = o.s("id") ?: return@mapNotNull null,
                    name = o.s("name") ?: "",
                    prompt = o.s("prompt") ?: "",
                    schedule = schedExpr,
                    scheduleLabel = humanSchedule(schedExpr).takeIf { it != schedExpr }
                        ?: o.s("schedule_display") ?: (sched as? JsonObject)?.s("display") ?: schedExpr,
                    enabled = (o["enabled"] as? JsonPrimitive)?.booleanOrNull ?: true,
                    state = o.s("state") ?: "",
                    nextRunAt = o.s("next_run_at"),
                    lastRunAt = o.s("last_run_at"),
                    lastStatus = o.s("last_status"),
                    profile = o.s("profile"),
                )
            }
        }.getOrNull()

        fun parseHits(body: String): List<Hit>? = runCatching {
            (json.parseToJsonElement(body).jsonObject["results"] as? JsonArray).orEmpty().mapNotNull { e ->
                val o = e as? JsonObject ?: return@mapNotNull null
                Hit(
                    sessionId = o.s("lineage_root") ?: o.s("session_id") ?: o.s("id") ?: return@mapNotNull null,
                    title = o.s("title") ?: o.s("preview")?.take(60) ?: "Untitled chat",
                    snippet = cleanSnippet(o.s("snippet") ?: ""),
                    role = o.s("role"),
                    at = (o["last_active"] as? JsonPrimitive)?.doubleOrNull ?: (o["session_started"] as? JsonPrimitive)?.doubleOrNull,
                )
            }
        }.getOrNull()

        /** Snippet FTS: tool-call JSON → teks; penanda >>>hit<<< dipertahankan untuk highlight. */
        fun cleanSnippet(s: String): String {
            var t = s
            if (t.trimStart().startsWith("[{") || t.trimStart().startsWith("{")) {
                t = Regex("\"(?:command|content|text|query|path)\\\\*\":\\s*\\\\*\"(.*)").find(t)?.groupValues?.get(1) ?: t
            }
            // buang escape JSON berlapis: \\\\n → spasi, \\\\" → ", sisa backslash ganda → satu
            t = t.replace(Regex("\\\\+n"), " ").replace(Regex("\\\\+\""), "\"").replace(Regex("\\\\{2,}"), "\\\\")
            t = t.replace(Regex("\\\\(?=[*'])"), "")
            return t.replace(Regex("\\s+"), " ").trim()
        }

        /** Preset ramah manusia untuk pembuat jadwal → ekspresi yang dimengerti hermes cron. */
        val Presets: List<Pair<String, String>> = listOf(
            "Every morning 8:00" to "0 8 * * *",
            "Every evening 20:00" to "0 20 * * *",
            "Every hour" to "every 1h",
            "Every 30 minutes" to "every 30m",
            "Weekdays 9:00" to "0 9 * * 1-5",
            "Every Monday 9:00" to "0 9 * * 1",
        )

        fun humanSchedule(expr: String): String =
            Presets.firstOrNull { it.second == expr }?.first ?: expr
    }
}

/** v25: prompt favorit (lokal) + antrean offline (pesan diketik saat Mac tidak terjangkau). */
object PromptStore {
    private val StrList = kotlinx.serialization.builtins.ListSerializer(String.serializer())
    private const val PREF = "prompt_store"
    private val json = Json { ignoreUnknownKeys = true }

    @kotlinx.serialization.Serializable
    data class Queued(val storedId: String, val text: String, val at: Long)

    private fun sp(c: android.content.Context) = c.getSharedPreferences(PREF, android.content.Context.MODE_PRIVATE)

    fun favorites(c: android.content.Context): List<String> =
        runCatching { json.decodeFromString(StrList, sp(c).getString("fav", "[]")!!) }.getOrDefault(emptyList())

    fun toggleFavorite(c: android.content.Context, text: String): Boolean {
        val cur = favorites(c).toMutableList()
        val t = text.trim()
        val added = if (t in cur) { cur.remove(t); false } else { cur.add(0, t); true }
        sp(c).edit().putString("fav", json.encodeToString(StrList, cur.take(30))).apply()
        return added
    }

    fun queue(c: android.content.Context): List<Queued> =
        runCatching { json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(Queued.serializer()), sp(c).getString("queue", "[]")!!) }.getOrDefault(emptyList())

    fun enqueue(c: android.content.Context, storedId: String, text: String) = saveQueue(c, queue(c) + Queued(storedId, text, System.currentTimeMillis()))
    fun removeQueued(c: android.content.Context, q: Queued) = saveQueue(c, queue(c).filterNot { it == q })
    fun requeueFront(c: android.content.Context, q: Queued) = saveQueue(c, listOf(q) + queue(c).filterNot { it == q })
    private fun saveQueue(c: android.content.Context, l: List<Queued>) =
        sp(c).edit().putString("queue", json.encodeToString(kotlinx.serialization.builtins.ListSerializer(Queued.serializer()), l.takeLast(50))).apply()
}
