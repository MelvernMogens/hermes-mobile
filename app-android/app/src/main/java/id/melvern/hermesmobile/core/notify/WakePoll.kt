package id.melvern.hermesmobile.core.notify

import id.melvern.hermesmobile.core.auth.SharedAuth
import id.melvern.hermesmobile.core.store.ConnectionSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/**
 * v28 item D: long-poll /api/mobile-wait — HANYA membangunkan poller M14 (pollOnce tetap sumber
 * kebenaran notif). Saat app di background, `delay(20s)` diganti satu request yang ditahan server
 * maks 25 dtk dan pulang lebih cepat begitu ada jawaban asisten / status tugas bot berubah.
 * Error → backoff 20 → 40 → 60 dtk (tidak pernah loop rapat).
 */
class WakePoll(private val settings: ConnectionSettings) {
    sealed interface Outcome {
        /** Ada perubahan — jalankan pollOnce sekarang. [task] = ada event tugas bot. */
        data class Events(val count: Int, val task: Boolean) : Outcome
        data object Timeout : Outcome
        data object Error : Outcome
    }

    private var cursor: String? = null
    private val base get() = settings.baseUrl.trim().trimEnd('/')

    /** Satu long-poll (≤ ~26 dtk). Cursor kosong → ambil baseline dulu (jawab instan), lalu tunggu. */
    suspend fun await(timeoutSecs: Int = 25): Outcome {
        if (cursor == null) {
            val b = fetch(0) ?: return Outcome.Error
            cursor = b.cursor
        }
        val r = fetch(timeoutSecs) ?: return Outcome.Error
        cursor = r.cursor
        return if (r.events > 0) Outcome.Events(r.events, r.task) else Outcome.Timeout
    }

    private suspend fun fetch(timeoutSecs: Int): Parsed? {
        val auth = withContext(Dispatchers.IO) { SharedAuth.get(base, settings.username, settings.password) } ?: return null
        val cookie = auth.cookieHeaderFor(base) ?: return null
        val url = "$base/api/mobile-wait?timeout=$timeoutSecs" +
            (cursor?.let { "&since=" + java.net.URLEncoder.encode(it, "UTF-8") } ?: "")
        val call = http.newCall(Request.Builder().url(url).header("Cookie", cookie).build())
        val body = suspendCancellableCoroutine<String?> { cont ->
            cont.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) { if (cont.isActive) cont.resume(null) }
                override fun onResponse(call: Call, response: Response) {
                    val s = response.use {
                        if (it.code == 401) SharedAuth.invalidate()
                        if (it.isSuccessful) it.body?.string() else null
                    }
                    if (cont.isActive) cont.resume(s)
                }
            })
        } ?: return null
        return parse(body)
    }

    data class Parsed(val cursor: String, val events: Int, val task: Boolean)

    companion object {
        const val BASE_BACKOFF_MS = 20_000L
        const val MAX_BACKOFF_MS = 60_000L
        /** Jeda minimum antar bangun (event beruntun saat agent streaming tidak boleh jadi loop rapat). */
        const val MIN_GAP_MS = 5_000L

        private val json = Json { ignoreUnknownKeys = true }

        /** Klien khusus long-poll: read timeout 35 dtk (> timeout server 25 dtk). */
        private val http: OkHttpClient by lazy {
            OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(35, TimeUnit.SECONDS)
                .retryOnConnectionFailure(false).build()
        }

        fun parse(body: String): Parsed? = runCatching {
            val o = json.parseToJsonElement(body).jsonObject
            val c = (o["cursor"] as? JsonPrimitive)?.contentOrNull ?: return@runCatching null
            val ev = (o["events"] as? JsonArray).orEmpty()
            val task = ev.any { ((it as? JsonObject)?.get("kind") as? JsonPrimitive)?.contentOrNull == "task" }
            Parsed(c, ev.size, task)
        }.getOrNull()

        /** Pure: backoff berikutnya sesudah error (0 = belum pernah error). 20s → 40s → 60s (cap). */
        fun nextBackoff(prevMs: Long): Long =
            if (prevMs <= 0) BASE_BACKOFF_MS else (prevMs * 2).coerceAtMost(MAX_BACKOFF_MS)
    }
}
