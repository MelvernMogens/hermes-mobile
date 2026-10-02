package id.melvern.hermesmobile.core.notify

import android.util.Log
import id.melvern.hermesmobile.HermesApp
import id.melvern.hermesmobile.core.rpc.GatewayClient
import id.melvern.hermesmobile.core.rpc.ConnState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put

private const val TAG = "HermesNotifRouter"

/**
 * M14: notifikasi background via POLLING (spec: "foreground service + WebSocket keepalive…
 * polling service"). Terverifikasi E2E: server hanya mengirim event ring ke transport yang
 * attach ke session itu (viewer) — koneksi WS app (non-viewer) hanya menerima
 * gateway.ready + sessions.changed. Jadi deteksi aktivitas agent lewat poll ringan:
 *
 *  - session.active_list tiap 20s (1 RPC kecil): diff status/message_count.
 *    working→idle + count naik → "Agent replied"; status waiting → approval notif.
 *  - WS tetap dipertahankan untuk: sessions.changed → poll segera (latensi turun),
 *    dan ServerAsk approval/clarify kalau app kebetulan viewer (ChatScreen terbuka
 *    session lain tak mungkin — tapi gratis kalau ada).
 *
 * subscriptionCount inbound >0 permanen → server-request tanpa handler gak di-auto-fail;
 * aman: server memperlakukan no-response == error-response (server_requests.py).
 */
fun GatewayClient.startNotifPoller(app: HermesApp, scope: CoroutineScope): Job = scope.launch {
    val router = NotifRouter(app)
    var prev: Map<String, NotifPolicy.LiveRow> = emptyMap()
    // Poll pertama setelah koneksi OPEN; lanjut tiap 20s selalu (WS cuma keepalive).
    while (isActive) {
        val c = app.client ?: break
        if (c.state.value == ConnState.OPEN) {
            prev = router.pollOnce(prev)
        } else {
            prev = emptyMap() // reset baseline saat putus — hindari notif palsu pas reconnect
        }
        delay(POLL_INTERVAL_MS)
    }
    Log.i(TAG, "poller exit")
}

private const val POLL_INTERVAL_MS = 20_000L

/**
 * State + aksi poll. Judul utk notif dari active_list (server sudah resolve title).
 */
class NotifRouter(private val app: HermesApp) {

    private val lastNotifAt = HashMap<String, Long>()

    /** Satu siklus poll: active_list → diff → notif. Return snapshot baru. */
    suspend fun pollOnce(prev: Map<String, NotifPolicy.LiveRow>): Map<String, NotifPolicy.LiveRow> {
        val c = app.client ?: return prev
        val now = try {
            val res = c.call("session.active_list", buildJsonObject { }, timeoutMs = 15_000)
            res["sessions"]?.jsonArray?.mapNotNull { el ->
                val o = el.jsonObject
                val sid = o["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                NotifPolicy.LiveRow(
                    sid = sid,
                    sessionKey = o["session_key"]?.jsonPrimitive?.contentOrNull ?: "",
                    status = o["status"]?.jsonPrimitive?.contentOrNull ?: "idle",
                    messageCount = o["message_count"]?.jsonPrimitive?.intOrNull ?: 0,
                    preview = o["preview"]?.jsonPrimitive?.contentOrNull ?: "",
                    title = o["title"]?.jsonPrimitive?.contentOrNull ?: "",
                )
            }?.associateBy { it.sessionKey.ifEmpty { it.sid } } ?: return prev
            // Key = sessionKey (stored id — stabil antar runtime restart). Kalau pakai
            // runtime sid, resume di surface lain bikin sid baru → baseline hilang →
            // diff miss / notif palsu (terbukti saat verify: sid berganti 650ad4c2→49fada2d).
        } catch (e: Throwable) {
            Log.w(TAG, "active_list gagal: ${e.message}")
            return prev
        }
        val ctx = NotifPolicy.Ctx(
            now = System.currentTimeMillis(),
            foreground = app.isForeground,
            openStoredId = app.openChatStoredId,
            runtimeToStored = emptyMap(),
            lastNotifAt = lastNotifAt,
        )
        for (d in NotifPolicy.onPollDiff(ctx, prev, now)) {
            if (d is NotifPolicy.Decision.Notify) {
                lastNotifAt[d.storedId] = System.currentTimeMillis()
                Log.i(TAG, "notif: ${d.title} | ${d.preview.take(40)}")
                try { app.notifier.postAgent(d) } catch (e: Throwable) {
                    Log.w(TAG, "post notif gagal: ${e.message}")
                }
            }
        }
        return now
    }
}
