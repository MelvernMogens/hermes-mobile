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
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull

/** v28: satu file yang dikirim agent (baris MEDIA:) — dari /api/mobile-files. Isi via MediaRepo/mobile-media. */
data class AgentFile(
    val path: String,
    val name: String,
    /** image | video | audio | doc */
    val kind: String,
    val size: Long,
    /** epoch detik pesan yang menyebut file */
    val at: Double,
    val profile: String,
    val sessionId: String,
    val sessionTitle: String,
) {
    /** Route chat asal file — profile non-default ikut `|p=`. */
    val chatRoute: String get() =
        "$sessionId|t=" + java.net.URLEncoder.encode(sessionTitle.ifBlank { "Chat" }, "UTF-8").replace("+", "%20") +
            (if (profile == "default") "" else "|p=$profile")
}

/** v28: galeri semua file agent lintas chat + bot. */
class FilesRepo(private val settings: ConnectionSettings) {
    enum class Kind(val wire: String) { ALL("all"), IMAGE("image"), VIDEO("video"), AUDIO("audio"), DOC("doc") }

    private val base get() = settings.baseUrl.trim().trimEnd('/')

    /** null = gagal; emptyList = server menjawab kosong. */
    suspend fun list(kind: Kind = Kind.ALL, limit: Int = 120): List<AgentFile>? {
        val a = withContext(Dispatchers.IO) { SharedAuth.get(base, settings.username, settings.password) } ?: return null
        return a.getJson("$base/api/mobile-files?limit=$limit&kind=${kind.wire}")?.let { parse(it) }
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }
        private fun JsonObject.s(k: String) = (this[k] as? JsonPrimitive)?.contentOrNull

        fun parse(body: String): List<AgentFile>? = runCatching {
            val arr = json.parseToJsonElement(body).jsonObject["files"] as? JsonArray ?: return@runCatching null
            arr.mapNotNull { el ->
                val o = el as? JsonObject ?: return@mapNotNull null
                AgentFile(
                    path = o.s("path") ?: return@mapNotNull null,
                    name = o.s("name") ?: return@mapNotNull null,
                    kind = o.s("kind") ?: "doc",
                    size = (o["size"] as? JsonPrimitive)?.longOrNull ?: 0L,
                    at = (o["at"] as? JsonPrimitive)?.doubleOrNull ?: 0.0,
                    profile = o.s("profile") ?: "default",
                    sessionId = o.s("session_id").orEmpty(),
                    sessionTitle = o.s("session_title").orEmpty(),
                )
            }
        }.getOrNull()
    }
}
