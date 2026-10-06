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
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import java.net.URLEncoder

/** v24: Mac control panel, code diff, web preview (endpoint proxy, cookie auth). */
class MacRepo(private val settings: ConnectionSettings) {
    data class Service(val label: String, val name: String, val running: Boolean)
    data class Status(
        val host: String, val batteryPct: Int?, val batteryState: String?, val onAc: Boolean,
        val load1: Double?, val cpus: Int, val uptimeDays: Double?, val diskFree: Long, val diskTotal: Long,
        val keepAwake: Boolean, val services: List<Service>, val botTabs: Int?,
    )
    data class Port(val port: Int, val process: String, val title: String, val html: Boolean)
    data class DiffFile(val path: String, val added: Int, val deleted: Int, val binary: Boolean, val untracked: Boolean)
    data class Commit(val hash: String, val subject: String)
    data class Diff(val repo: String, val name: String, val branch: String, val files: List<DiffFile>, val commits: List<Commit>, val patch: String, val truncated: Boolean)

    private val base get() = settings.baseUrl.trim().trimEnd('/')
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private suspend fun auth(): DashboardAuth? = withContext(Dispatchers.IO) {
        id.melvern.hermesmobile.core.auth.SharedAuth.get(base, settings.username, settings.password)
    }

    suspend fun status(): Status? = auth()?.getJson("$base/api/mobile-mac")?.let { parseStatus(it) }

    /** Return pesan dari server (ok / gagal). */
    suspend fun action(action: String, arg: String = ""): Pair<Boolean, String> {
        val a = auth() ?: return false to "Not signed in"
        val body = a.postJson("$base/api/mobile-mac", """{"action":${JsonPrimitive(action)},"arg":${JsonPrimitive(arg)}}""")
            ?: return false to "Mac didn't respond"
        val o = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return false to "Bad response"
        return ((o["ok"] as? JsonPrimitive)?.booleanOrNull == true) to ((o["message"] as? JsonPrimitive)?.contentOrNull ?: "")
    }

    suspend fun ports(): Pair<List<Port>, Int>? = auth()?.getJson("$base/api/mobile-ports")?.let { parsePorts(it) }

    suspend fun diff(path: String, since: Double? = null): Diff? =
        auth()?.getJson("$base/api/mobile-diff?path=${enc(path)}" + (since?.let { "&since=${it.toLong()}" } ?: ""))?.let { parseDiff(it) }

    /** URL preview di tailnet: host sama, port https preview; cookie login dibagi (per-host). */
    fun previewUrl(publicPort: Int, port: Int, path: String = "/"): String {
        val u = java.net.URI(base)
        // Tailnet (https) → port https publik (tailscale serve); http lokal/LAN → listener preview langsung.
        val origin = if (u.scheme == "https") "https://${u.host}:$publicPort" else "http://${u.host}:$LOCAL_PREVIEW_PORT"
        return "$origin/__hermes_preview?port=$port&path=${enc(path)}"
    }

    /** Cookie login untuk ditanam ke WebView (host sama dengan base). */
    suspend fun cookieHeader(): String? = auth()?.cookieHeaderFor(base)

    /** URL preview sekali-pakai (tiket 60 dtk) — origin preview menukarnya jadi cookie login sendiri. */
    suspend fun previewTicketUrl(port: Int, path: String = "/"): String? {
        val a = auth() ?: return null
        val (code, body) = a.request("POST", "$base/api/mobile-preview-ticket", "{}")
        if (code !in 200..299 || body == null) return null
        val o = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return null
        val tk = o.s("ticket") ?: return null
        val pub = (o["preview_port"] as? JsonPrimitive)?.intOrNull ?: 8443
        return previewUrl(pub, port, path) + "&ticket=" + enc(tk)
    }

    companion object {
        const val LOCAL_PREVIEW_PORT = 8791
        private val json = Json { ignoreUnknownKeys = true }
        private fun JsonObject.s(k: String) = (this[k] as? JsonPrimitive)?.contentOrNull

        fun parseStatus(body: String): Status? = runCatching {
            val o = json.parseToJsonElement(body).jsonObject
            val bat = o["battery"] as? JsonObject
            val disk = o["disk"] as? JsonObject
            Status(
                host = o.s("host") ?: "Mac",
                batteryPct = (bat?.get("percent") as? JsonPrimitive)?.intOrNull,
                batteryState = bat?.s("state"),
                onAc = (bat?.get("ac") as? JsonPrimitive)?.booleanOrNull ?: true,
                load1 = ((o["load"] as? JsonArray)?.firstOrNull() as? JsonPrimitive)?.doubleOrNull,
                cpus = (o["cpu_count"] as? JsonPrimitive)?.intOrNull ?: 8,
                uptimeDays = (o["uptime_days"] as? JsonPrimitive)?.doubleOrNull,
                diskFree = (disk?.get("free_bytes") as? JsonPrimitive)?.longOrNull ?: 0,
                diskTotal = (disk?.get("total_bytes") as? JsonPrimitive)?.longOrNull ?: 0,
                keepAwake = (o["keep_awake"] as? JsonPrimitive)?.booleanOrNull ?: false,
                services = (o["services"] as? JsonArray).orEmpty().mapNotNull { el ->
                    val so = el as? JsonObject ?: return@mapNotNull null
                    Service(so.s("label") ?: return@mapNotNull null, so.s("name") ?: "", (so["running"] as? JsonPrimitive)?.booleanOrNull == true)
                },
                botTabs = (o["bot_tabs"] as? JsonPrimitive)?.intOrNull,
            )
        }.getOrNull()

        fun parsePorts(body: String): Pair<List<Port>, Int>? = runCatching {
            val o = json.parseToJsonElement(body).jsonObject
            val ports = (o["ports"] as? JsonArray).orEmpty().mapNotNull { el ->
                val p = el as? JsonObject ?: return@mapNotNull null
                Port((p["port"] as? JsonPrimitive)?.intOrNull ?: return@mapNotNull null, p.s("process") ?: "", p.s("title") ?: "", p.s("kind") == "html")
            }
            ports to ((o["preview_port"] as? JsonPrimitive)?.intOrNull ?: 8443)
        }.getOrNull()

        fun parseDiff(body: String): Diff? = runCatching {
            val o = json.parseToJsonElement(body).jsonObject
            if (o["error"] != null) return@runCatching null
            Diff(
                repo = o.s("repo") ?: "", name = o.s("name") ?: "", branch = o.s("branch") ?: "",
                files = (o["files"] as? JsonArray).orEmpty().mapNotNull { el ->
                    val f = el as? JsonObject ?: return@mapNotNull null
                    DiffFile(f.s("path") ?: return@mapNotNull null, (f["added"] as? JsonPrimitive)?.intOrNull ?: 0,
                        (f["deleted"] as? JsonPrimitive)?.intOrNull ?: 0, (f["binary"] as? JsonPrimitive)?.booleanOrNull == true,
                        (f["untracked"] as? JsonPrimitive)?.booleanOrNull == true)
                },
                commits = (o["commits"] as? JsonArray).orEmpty().mapNotNull { el ->
                    val c = el as? JsonObject ?: return@mapNotNull null
                    Commit(c.s("hash") ?: "", c.s("subject") ?: "")
                },
                patch = o.s("patch") ?: "",
                truncated = (o["truncated"] as? JsonPrimitive)?.booleanOrNull == true,
            )
        }.getOrNull()

        /** Split unified patch per file → (path, lines). */
        fun splitPatch(patch: String): List<Pair<String, List<String>>> {
            val out = ArrayList<Pair<String, MutableList<String>>>()
            for (line in patch.lineSequence()) {
                if (line.startsWith("diff --git ")) {
                    val path = line.substringAfter(" b/", line.removePrefix("diff --git "))
                    out += path to mutableListOf()
                } else out.lastOrNull()?.second?.add(line)
            }
            return out.map { (p, l) -> p to l.filterNot { it.startsWith("index ") || it.startsWith("--- ") || it.startsWith("+++ ") || it == "new file" || it.startsWith("new file mode") } }
        }

        /** Repo paths yang disentuh tool di transcript (write_file/patch path args) — tebakan target diff. */
        fun touchedPaths(texts: List<String>): List<String> {
            val rx = Regex("""(/Users/[^\s"'`,)\]}]+)""")
            return texts.flatMap { t -> rx.findAll(t).map { it.value.trimEnd('.', ':') } }
                .filter { p -> listOf(".kt", ".py", ".ts", ".tsx", ".js", ".md", ".json", ".html", ".css", ".swift", ".xml", ".yaml", ".yml", ".toml", ".gradle", ".kts", ".sh").any { p.endsWith(it) } }
                .distinct()
        }
    }
}
