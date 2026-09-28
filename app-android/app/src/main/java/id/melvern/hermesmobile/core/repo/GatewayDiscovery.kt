package id.melvern.hermesmobile.core.repo

import id.melvern.hermesmobile.core.store.ConnectionSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * M6: discovery gateway multi-surface. GET /api/desktop-port di base URL
 * (proxy 8790 di Mac, di-expose tailscale serve):
 *   {"port":57840,"pid":84269,"surface":"desktop"}  → desktop hidup → mode desktop
 *   {"port":8788,...,"surface":"mobile-serve"}      → fallback mobile-serve (backup)
 * Fail (desktop mati / proxy mati / belum upgrade) → mode mobile seperti M1.
 *
 * Desktop mode = koneksi dialihkan ke gateway process yang SAMA dengan desktop
 * app → prompt.submit tidak kena 4090 SESSION_NOT_OWNED (identity rule:
 * (pid, live_session_id) — resume via process sama = reuse live session).
 */
object GatewayDiscovery {
    private val json = Json { ignoreUnknownKeys = true }
    private val http = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    sealed interface Mode {
        /** Gateway desktop aktif — WS ke proxy tanpa tiket (proxy inject token loopback). */
        data class Desktop(val port: Int, val pid: Int) : Mode
        /** Fallback M1: mobile-serve 8788 (tiket password-login). */
        data object Mobile : Mode
    }

    /** GET /api/desktop-port — timeout pendek; apapun yang gagal = Mode.Mobile. */
    suspend fun resolve(baseUrl: String): Mode = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url("${baseUrl.trim().trimEnd('/')}/api/desktop-port").build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use Mode.Mobile
                val body = resp.body?.string() ?: return@use Mode.Mobile
                val obj = json.parseToJsonElement(body).jsonObject
                val surface = obj["surface"]?.jsonPrimitive?.content ?: ""
                if (surface != "desktop") return@use Mode.Mobile
                val port = obj["port"]?.jsonPrimitive?.content?.toIntOrNull() ?: return@use Mode.Mobile
                val pid = obj["pid"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                Mode.Desktop(port, pid)
            }
        } catch (_: Throwable) { Mode.Mobile }
    }
}
