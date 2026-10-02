package id.melvern.hermesmobile.core.repo

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import id.melvern.hermesmobile.core.auth.DashboardAuth
import id.melvern.hermesmobile.core.store.ConnectionSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URLEncoder

/**
 * M5: ambil gambar dari Mac backend via `GET /api/media?path=` (web_routers/files.py —
 * auth cookie, roots: images/screenshots/cache di hermes home) → data URL base64 →
 * BitmapFactory. Dipakai renderer markdown utk foto yang agent kirim/balas.
 *
 * Path di luar media roots ditolak server (403) → pemanggil fallback ke chip teks.
 * Tanpa dependency image baru (Coil dll) — BitmapFactory + parse JSON manual.
 */
class MediaRepo(private val settings: ConnectionSettings) {

    /** null = gagal (403/404/network/too-large) — pemanggil tampilin chip fallback. */
    suspend fun fetchImage(path: String): ImageBitmap? = withContext(Dispatchers.IO) {
        val auth = DashboardAuth(settings.baseUrl.trim().trimEnd('/'))
        // cookie login: login ringan (idempotent — server 409 kalau sudah login)
        val ok = auth.ensureLogin(settings.username, settings.password)
        if (!ok) return@withContext null
        // Hermes media roots first; images elsewhere (agent MEDIA:/path) via the
        // proxy's guarded /api/mobile-media (path must appear in a chat).
        auth.getJson(mediaUrl(settings.baseUrl, path))?.let { parseImageDataUrl(it) }?.let { return@withContext it }
        val body = auth.getJson(mobileMediaUrl(settings.baseUrl, path)) ?: return@withContext null
        parseImageDataUrl(body)
    }

    /**
     * M9 (item 3): download video dari Mac via proxy /api/mobile-media
     * (auth cookie + path-must-appear-in-chat) → cacheDir. Body {data_url}
     * base64 — server cap 20MB, client cap 50MB. Null = gagal/kegedean →
     * pemanggil render chip fallback.
     */
    suspend fun fetchVideo(path: String, cacheDir: java.io.File): java.io.File? = withContext(Dispatchers.IO) {
        val auth = DashboardAuth(settings.baseUrl.trim().trimEnd('/'))
        val ok = auth.ensureLogin(settings.username, settings.password)
        if (!ok) return@withContext null
        val body = auth.getJson(mobileMediaUrl(settings.baseUrl, path)) ?: return@withContext null
        val file = parseDataUrlToFile(body, cacheDir, path.substringAfterLast('/'))
        file
    }

    companion object {
        /** Pure: URL /api/media — dipakai test verifikasi encoding path. */
        fun mediaUrl(baseUrl: String, path: String): String {
            val base = baseUrl.trim().trimEnd('/')
            return "$base/api/media?path=" + URLEncoder.encode(path, "UTF-8")
        }

        fun mobileMediaUrl(baseUrl: String, path: String): String {
            val base = baseUrl.trim().trimEnd('/')
            return "$base/api/mobile-media?path=" + URLEncoder.encode(path, "UTF-8")
        }

        /** Pure: body {"data_url": "data:image/png;base64,...."} → ImageBitmap. */
        fun parseImageDataUrl(body: String): ImageBitmap? {
            return try {
                val el = kotlinx.serialization.json.Json.parseToJsonElement(body)
                val obj = el as? kotlinx.serialization.json.JsonObject ?: return null
                val dataUrl = (obj["data_url"] as? kotlinx.serialization.json.JsonPrimitive)
                    ?.takeIf { it.isString }?.content ?: return null
                val b64 = dataUrl.substringAfter("base64,", "")
                if (b64.isEmpty()) return null
                val bytes = Base64.decode(b64, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            } catch (_: Throwable) { null }
        }

        /** M9: cap video 50MB (server proxy cap 20MB). */
        const val VIDEO_MAX_BYTES: Long = 50L * 1024 * 1024

        /**
         * M9: body {"data_url": "data:video/mp4;base64,..."} -> file di cacheDir.
         * Nama di-sanitize; ekstensi asli dipertahankan (ExoPlayer sniff dari
         * extension utk beberapa container). Null kalau bukan video / kosong /
         * > cap.
         */
        fun parseDataUrlToFile(body: String, cacheDir: java.io.File, name: String): java.io.File? {
            return try {
                val el = kotlinx.serialization.json.Json.parseToJsonElement(body)
                val obj = el as? kotlinx.serialization.json.JsonObject ?: return null
                val dataUrl = (obj["data_url"] as? kotlinx.serialization.json.JsonPrimitive)
                    ?.takeIf { it.isString }?.content ?: return null
                if (!dataUrl.startsWith("data:video/")) return null
                val b64 = dataUrl.substringAfter("base64,", "")
                if (b64.isEmpty()) return null
                // estimasi ukuran decoded sebelum decode (base64 ~ 4/3 dari raw)
                if (b64.length.toLong() * 3L / 4L > VIDEO_MAX_BYTES) return null
                val bytes = Base64.decode(b64, Base64.DEFAULT)
                if (bytes.isEmpty()) return null
                val safe = name.replace(Regex("[^\\w.\\-]"), "_").takeLast(80).ifEmpty { "video.mp4" }
                val f = java.io.File(cacheDir, "mdvideo_$safe")
                f.writeBytes(bytes)
                f
            } catch (_: Throwable) { null }
        }
    }
}
