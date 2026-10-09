package id.melvern.hermesmobile.core.repo

import id.melvern.hermesmobile.core.auth.SharedAuth
import id.melvern.hermesmobile.core.store.ConnectionSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject

/** v28: satu tugas bot (session source='tool' di profile bot) dari /api/mobile-tasks. */
data class BotTask(
    val profile: String,
    val id: String,
    val title: String,
    /** epoch detik */
    val startedAt: Double,
    val endedAt: Double?,
    val messageCount: Int,
    val lastActivity: Double,
    /** running | done | failed | stopped */
    val status: String,
    val result: String,
) {
    val isRunning: Boolean get() = status == "running"
    /** Route chat (ChatScreen) — selalu bawa profile pemilik. */
    val chatRoute: String get() = "$id|t=" + java.net.URLEncoder.encode(title, "UTF-8").replace("+", "%20") + "|p=$profile"
}

/** v28: riwayat tugas bot lintas profile (endpoint proxy, cookie auth bersama SharedAuth). */
class TasksRepo(private val settings: ConnectionSettings) {
    private val base get() = settings.baseUrl.trim().trimEnd('/')

    /** null = gagal (jaringan/auth/parse); emptyList hanya kalau server benar-benar kosong. */
    suspend fun list(limit: Int = 60): List<BotTask>? {
        val a = withContext(Dispatchers.IO) { SharedAuth.get(base, settings.username, settings.password) } ?: return null
        return a.getJson("$base/api/mobile-tasks?limit=$limit")?.let { parse(it) }
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }
        private fun JsonObject.s(k: String) = (this[k] as? JsonPrimitive)?.contentOrNull
        private fun JsonObject.d(k: String) = (this[k] as? JsonPrimitive)?.doubleOrNull

        fun parse(body: String): List<BotTask>? = runCatching {
            val arr = json.parseToJsonElement(body).jsonObject["tasks"] as? JsonArray ?: return@runCatching null
            arr.mapNotNull { el ->
                val o = el as? JsonObject ?: return@mapNotNull null
                val started = o.d("started_at") ?: return@mapNotNull null
                BotTask(
                    profile = o.s("profile") ?: return@mapNotNull null,
                    id = o.s("id") ?: return@mapNotNull null,
                    title = o.s("title").orEmpty().ifBlank { "Task" },
                    startedAt = started,
                    endedAt = o.d("ended_at"),
                    messageCount = (o["message_count"] as? JsonPrimitive)?.intOrNull ?: 0,
                    lastActivity = o.d("last_activity") ?: started,
                    status = o.s("status") ?: "stopped",
                    result = o.s("result").orEmpty(),
                )
            }
        }.getOrNull()

        /** "45 s", "32 min", "1 h 05 min" — durasi dari start sampai end (atau now kalau masih jalan). */
        fun durationLabel(start: Double, endOrNow: Double): String {
            val secs = (endOrNow - start).toLong().coerceAtLeast(0)
            return when {
                secs < 60 -> "$secs s"
                secs < 3600 -> "${secs / 60} min"
                else -> "${secs / 3600} h " + "%02d".format((secs % 3600) / 60) + " min"
            }
        }

        /** Ujung durasi tugas: ended_at, else aktivitas terakhir (selesai), else now (jalan). */
        fun durationLabel(t: BotTask, nowEpoch: Double): String =
            durationLabel(t.startedAt, if (t.isRunning) nowEpoch else (t.endedAt ?: t.lastActivity))
    }
}
