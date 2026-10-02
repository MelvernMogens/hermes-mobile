package id.melvern.hermesmobile.core.rpc

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*
import okhttp3.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/** State koneksi yang di-render UI. */
enum class ConnState { CONNECTING, OPEN, RECONNECTING, CLOSED }

/** Event inbound selain RPC response. */
sealed interface GatewayInbound {
    data class RpcEvent(val type: String, val sessionId: String, val payload: JsonObject?) : GatewayInbound
    /**
     * Server→client request. respond/fail balikin Boolean dari socket send —
     * false = frame gak terkirim (socket mati) → pemanggil harus fallback RPC.
     */
    data class ServerAsk(
        val id: String,
        val method: String,
        val params: JsonObject?,
        val respond: (JsonObject) -> Boolean,
        val fail: (Int, String) -> Boolean,
    ) : GatewayInbound
    data class Ready(val payload: JsonObject?) : GatewayInbound
}

/** Supplier tiket WS — DashboardAuth minta tiket baru sebelum tiap upgrade. */
fun interface TicketSupplier { suspend fun freshTicket(): String }

/**
 * Client JSON-RPC 2.0 over WebSocket ke backend `hermes serve` (gated auth).
 *
 * Port dari apps/shared/src/json-rpc-gateway.ts (desktop):
 *  - heartbeat `gateway.ping` tiap 15s, deadline 45s, liveness = any-inbound.
 *  - reconnect full-jitter 300ms→15s.
 *  - setelah reconnect: replay `session.events.since` untuk session aktif.
 *  - server→client request dijawab otomatis -32601 kalau gak ada handler.
 */
class GatewayClient(
    private val wsBase: String,          // wss://host/api/ws
    private val ticketSupplier: TicketSupplier,
    private val scope: CoroutineScope,
    /**
     * M6: mode multi-surface — konek ke gateway desktop via proxy. Proxy inject
     * ?token= (loopback) server-side, jadi client TIDAK minta tiket. Dispatch
     * logic tidak berubah (method-first).
     */
    private val desktopMode: Boolean = false,
) {
    companion object {
        const val HEARTBEAT_INTERVAL_MS = 15_000L
        const val HEARTBEAT_DEADLINE_MS = 45_000L
        const val REQUEST_TIMEOUT_MS = 120_000L
        const val REPLAY_TIMEOUT_MS = 10_000L
        const val WS_SUBPROTOCOL = "hermes-gateway-v1"
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val client = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .connectTimeout(15, TimeUnit.SECONDS)
        .build()

    private val _state = MutableStateFlow(ConnState.CLOSED)
    val state: StateFlow<ConnState> = _state.asStateFlow()

    private val _inbound = MutableSharedFlow<GatewayInbound>(
        extraBufferCapacity = 256, onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val inbound: SharedFlow<GatewayInbound> = _inbound.asSharedFlow()

    /** Di-set oleh pemanggil agar replay otomatis setelah reconnect. */
    @Volatile var activeSessionId: String? = null

    private val idCounter = AtomicLong(0)
    private val pending = java.util.concurrent.ConcurrentHashMap<String, CompletableDeferred<JsonObject>>()
    private var webSocket: WebSocket? = null
    private var connectJob: Job? = null
    private var heartbeatJob: Job? = null
    @Volatile private var lastInboundAt = 0L
    private val lastSeenBySession = java.util.concurrent.ConcurrentHashMap<String, Int>()
    @Volatile private var stopped = false

    fun start() {
        if (connectJob?.isActive == true) return
        stopped = false
        connectJob = scope.launch { connectLoop() }
    }

    fun stop() {
        stopped = true
        connectJob?.cancel()
        heartbeatJob?.cancel()
        webSocket?.close(1000, "client stop")
        failAllPending("connection stopped")
        _state.value = ConnState.CLOSED
    }

    private suspend fun connectLoop() {
        var attempt = 0
        while (!stopped && scope.isActive) {
            _state.value = if (attempt == 0) ConnState.CONNECTING else ConnState.RECONNECTING
            val closed = CompletableDeferred<Unit>()
            // Both modes send a single-use ticket from /api/auth/ws-ticket. In desktop
            // mode the proxy validates it against mobile-serve before injecting the
            // desktop loopback token (security fix 28 Sep — no unauthenticated relay).
            val queryCred: String? =
                try { "ticket=${ticketSupplier.freshTicket()}" } catch (e: Throwable) {
                    Log.w(TAG, "ticket mint failed: ${e.message}")
                    null
                }
            if (queryCred != null) {
                val dialUrl = if (queryCred.isEmpty()) wsBase else "$wsBase?$queryCred"
                val socket = client.newWebSocket(
                    Request.Builder()
                        .url(dialUrl)
                        .header("Sec-WebSocket-Protocol", WS_SUBPROTOCOL)
                        .build(),
                    Listener(closed)
                )
                webSocket = socket
                // TUNGGU sampai socket benar-benar mati (onFailure/onClosed).
                // Jangan pernah selesaikan `closed` di onOpen — loop ini hanya
                // boleh membuat socket BARU setelah yang lama mati total.
                closed.await()
            }
            if (stopped) break
            val delayMs = ReconnectBackoff.delayMs(attempt)
            attempt += 1
            kotlinx.coroutines.delay(delayMs)
        }
    }

    private inner class Listener(private val closed: CompletableDeferred<Unit>) : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            Log.i(TAG, "WS open")
            lastInboundAt = System.currentTimeMillis()
            _state.value = ConnState.OPEN
            startHeartbeat()
            // replay event yang ketinggalan untuk session aktif
            val sid = activeSessionId
            if (sid != null) scope.launch { replaySince(sid) }
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            
            lastInboundAt = System.currentTimeMillis()
            scope.launch(Dispatchers.Default) { handleFrame(text) }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.w(TAG, "WS failure: ${t.message} code=${response?.code}")
            heartbeatJob?.cancel()
            failAllPending("connection lost: ${t.message ?: "network error"}")
            _state.value = ConnState.RECONNECTING
            closed.complete(Unit)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            Log.i(TAG, "WS closed: $code $reason")
            heartbeatJob?.cancel()
            failAllPending("connection closed ($code)")
            _state.value = ConnState.RECONNECTING
            closed.complete(Unit)
        }
    }

    /** Tunggu socket mati — dipanggil connectLoop setelah open. */
    private suspend fun awaitClosedSignal() {}

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (isActive && _state.value == ConnState.OPEN) {
                delay(HEARTBEAT_INTERVAL_MS)
                val silent = System.currentTimeMillis() - lastInboundAt > HEARTBEAT_DEADLINE_MS
                if (silent) { webSocket?.cancel(); break }
                try {
                    call("gateway.ping", timeoutMs = HEARTBEAT_INTERVAL_MS)
                } catch (_: Throwable) { webSocket?.cancel(); break }
            }
        }
    }

    private suspend fun handleFrame(text: String) {
        val obj = try { json.parseToJsonElement(text).jsonObject } catch (_: Throwable) { return }
        val method = obj["method"]?.jsonPrimitive?.contentOrNull
        val id = obj["id"]?.jsonPrimitive?.contentOrNull
        when {
            // Notification / server→client request: punya `method`, tanpa atau dengan id.
            method != null -> {
                if (method == "event") {
                    val params = obj["params"]?.jsonObject
                    val type = params?.get("type")?.jsonPrimitive?.content ?: return
                    val sid = params?.get("session_id")?.jsonPrimitive?.contentOrNull ?: ""
                    val payload = params?.get("payload")?.jsonObject
                    trackSeq(sid, params)
                    if (type == "gateway.ready") _inbound.tryEmit(GatewayInbound.Ready(payload))
                    else _inbound.tryEmit(GatewayInbound.RpcEvent(type, sid, payload))
                } else if (id != null) {
                    // server→client request (approval/clarify/...)
                    val params = obj["params"]?.jsonObject
                    if (_inbound.subscriptionCount.value == 0) {
                        sendRaw(buildJsonObject {
                            put("jsonrpc", "2.0"); put("id", id)
                            put("error", buildJsonObject { put("code", -32601); put("message", "no handler") })
                        })
                        return
                    }
                    _inbound.tryEmit(
                        GatewayInbound.ServerAsk(
                            id, method, params,
                            respond = { result -> sendRaw(buildJsonObject {
                                put("jsonrpc", "2.0"); put("id", id); put("result", result)
                            }) },
                            fail = { code, message -> sendRaw(buildJsonObject {
                                put("jsonrpc", "2.0"); put("id", id)
                                put("error", buildJsonObject { put("code", code); put("message", message) })
                            }) },
                        )
                    )
                }
                // method tanpa id dan bukan "event": unknown notification — abaikan.
            }
            // Response untuk request kita.
            id != null -> {
                val error = obj["error"]?.jsonObject
                val deferred = pending[id]
                if (deferred != null) {
                    if (error != null) deferred.completeExceptionally(
                        RpcException((error["code"]?.jsonPrimitive?.intOrNull ?: -1), (error["message"]?.jsonPrimitive?.contentOrNull ?: "rpc error"))
                    ) else deferred.complete(obj["result"]?.jsonObject ?: buildJsonObject {})
                    pending.remove(id)
                }
            }
        }
    }

    private fun trackSeq(sid: String, params: JsonObject?) {
        val seq = params?.get("seq")?.jsonPrimitive?.intOrNull ?: return
        if (sid.isNotEmpty()) lastSeenBySession[sid] = seq
    }

    private fun sendRaw(obj: JsonObject): Boolean = webSocket?.send(obj.toString()) ?: false

    suspend fun call(method: String, params: JsonObject? = null, timeoutMs: Long = REQUEST_TIMEOUT_MS): JsonObject {
        if (_state.value != ConnState.OPEN) throw RpcException(-1, "not connected (state=${_state.value})")
        val id = "c${idCounter.incrementAndGet()}"
        val deferred = CompletableDeferred<JsonObject>()
        pending[id] = deferred
        val frame = buildJsonObject {
            put("jsonrpc", "2.0"); put("id", id); put("method", method)
            if (params != null) put("params", params)
        }
        if (!sendRaw(frame)) {
            pending.remove(id)
            throw RpcException(-1, "socket closed")
        }
        return try {
            withTimeout(timeoutMs) { deferred.await() }
        } catch (e: TimeoutCancellationException) {
            pending.remove(id)
            throw RpcException(-1, "timeout: $method")
        }
    }

    suspend fun replaySince(sessionId: String): ReplayResult? =
        replaySince(sessionId, lastSeen = null)

    /**
     * M9 (item 6): replay eksplisit sejak watermark (TranscriptCache) —
     * client lama tanpa last_seen dapat snapshot penuh server.
     */
    suspend fun replaySince(sessionId: String, lastSeen: Int?): ReplayResult? {
        if (_state.value != ConnState.OPEN) return null
        val last = lastSeen ?: lastSeenBySession[sessionId]
        return try {
            val res = call(
                "session.events.since",
                buildJsonObject {
                    put("session_id", sessionId)
                    if (last != null) put("last_seen", last)
                },
                timeoutMs = REPLAY_TIMEOUT_MS,
            )
            val events = res["events"]?.jsonArray ?: JsonArray(emptyList())
            events.forEach { el ->
                val p = el.jsonObject
                val type = p["type"]?.jsonPrimitive?.contentOrNull ?: return@forEach
                val sid = p["session_id"]?.jsonPrimitive?.contentOrNull ?: sessionId
                val payload = p["payload"]?.jsonObject
                _inbound.tryEmit(GatewayInbound.RpcEvent(type, sid, payload))
            }
            (res["latest_seq"]?.jsonPrimitive?.intOrNull)?.let { lastSeenBySession[sessionId] = it }
            ReplayResult(
                res["count"]?.jsonPrimitive?.intOrNull ?: 0,
                res["truncated"]?.jsonPrimitive?.booleanOrNull ?: false,
                res["epoch"]?.jsonPrimitive?.intOrNull ?: 0,
            )
        } catch (_: Throwable) { null }
    }

    data class ReplayResult(val count: Int, val truncated: Boolean, val epoch: Int = 0)

    /** M9: watermark seq terakhir yang diterima utk session (dipakai TranscriptCache). */
    fun lastSeenSeq(sessionId: String): Int? = lastSeenBySession[sessionId]

    private fun failAllPending(reason: String) {
        val entries = pending.keys().toList()
        entries.forEach { id -> pending.remove(id)?.completeExceptionally(RpcException(-1, reason)) }
    }
}

class RpcException(val code: Int, message: String) : Exception(message)

/** 4090: session di-hold surface lain (desktop app buka session itu). */
class SessionNotOwnedException(message: String) : Exception(message)

private const val TAG = "HermesGateway"
