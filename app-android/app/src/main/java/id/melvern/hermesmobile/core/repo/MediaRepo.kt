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

    /** null = gagal (403/404/network) — pemanggil tampilin chip fallback. */
    suspend fun fetchImage(path: String): ImageBitmap? = withContext(Dispatchers.IO) {
        val auth = DashboardAuth(settings.baseUrl.trim().trimEnd('/'))
        // cookie login: login ringan (idempotent — server 409 kalau sudah login)
        val ok = auth.ensureLogin(settings.username, settings.password)
        if (!ok) return@withContext null
        val body = auth.getJson(mediaUrl(settings.baseUrl, path)) ?: return@withContext null
        parseImageDataUrl(body)
    }

    companion object {
        /** Pure: URL /api/media — dipakai test verifikasi encoding path. */
        fun mediaUrl(baseUrl: String, path: String): String {
            val base = baseUrl.trim().trimEnd('/')
            return "$base/api/media?path=" + URLEncoder.encode(path, "UTF-8")
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
    }
}
