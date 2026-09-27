package id.melvern.hermesmobile.core.repo

import id.melvern.hermesmobile.core.model.SessionRow
import id.melvern.hermesmobile.core.model.TranscriptMessage
import id.melvern.hermesmobile.core.rpc.GatewayClient
import id.melvern.hermesmobile.core.rpc.SessionNotOwnedException
import id.melvern.hermesmobile.core.rpc.RpcException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SessionRepo(private val client: GatewayClient) {

    /** Lenient decode — server kirim field ekstra (mis. reasoning_content); strict default buang pesan senyap. */
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun listSessions(limit: Int = 60): List<SessionRow> {
        val res = client.call("session.list", buildJsonObject { put("limit", limit) })
        val arr = res["sessions"]?.jsonArray ?: return emptyList()
        return arr.mapNotNull { el ->
            try { json.decodeFromJsonElement(SessionRow.serializer(), el.jsonObject) } catch (_: Throwable) { null }
        }
    }

    /**
     * M3.2: stored-id session yang lagi live — dari `session.active_list`.
     * PENTING: `sessions[].id` itu RUNTIME id (uuid acak per resume), yang
     * cocok sama session.list itu `sessions[].session_key` (stored id) —
     * dibuktikan desktop app (use-background-sync.ts rehydrateLiveSessionStatuses:
     * runtimeSessionId=session.id, storedSessionId=session.session_key).
     * Filter status != idle (idle = cuma nempel, gak jalan).
     * Fail-open: kosong.
     */
    suspend fun activeStoredIds(): Set<String> = try {
        val res = client.call("session.active_list", buildJsonObject { })
        res["sessions"]?.jsonArray
            ?.mapNotNull { el ->
                val o = el.jsonObject
                val stored = o["session_key"]?.jsonPrimitive?.contentOrNull?.trim()
                val status = o["status"]?.jsonPrimitive?.contentOrNull ?: ""
                stored?.takeIf { it.isNotEmpty() && status.isNotEmpty() && status != "idle" }
            }
            ?.toSet() ?: emptySet()
    } catch (_: Throwable) { emptySet() }

    suspend fun resume(sessionId: String): ResumeOutcome {
        // defer_history HARUS false — true mengembalikan messages kosong (server
        // menganggap history di-hydrate terpisah). 694KB/187 msg terverifikasi.
        val res = client.call(
            "session.resume",
            buildJsonObject { put("session_id", sessionId) },
            timeoutMs = 45_000,
        )
        val messages = res["messages"]?.jsonArray?.mapNotNull { el ->
            try { json.decodeFromJsonElement(TranscriptMessage.serializer(), el.jsonObject) } catch (_: Throwable) { null }
        } ?: emptyList()
        return ResumeOutcome(
            runtimeId = res["session_id"]?.jsonPrimitive?.contentOrNull ?: sessionId,
            messages = messages,
            running = res["running"]?.jsonPrimitive?.booleanOrNull ?: false,
            hydrated = !(res["hydrating"]?.jsonPrimitive?.booleanOrNull ?: false),
        )
    }

    data class ResumeOutcome(
        val runtimeId: String,
        val messages: List<TranscriptMessage>,
        val running: Boolean,
        val hydrated: Boolean,
    )

    suspend fun sendPrompt(sessionId: String, text: String) {
        client.call(
            "prompt.submit",
            buildJsonObject { put("session_id", sessionId); put("text", text) },
            timeoutMs = 120_000,
        )
    }

    /**
     * Submit dengan jaminan runtime hidup: kalau prompt.submit kena error session
     * (4001/4006/not-found/"live owner"/"already active"), re-resume sekali lalu
     * ulangi submit dengan runtime id baru. Return runtime id yang dipakai.
     *
     * 4090 SESSION_NOT_OWNED (session masih di-hold surface lain — desktop app
     * buka session itu) TIDAK bisa diambil alih by design (#106217): dilempar
     * ke UI dengan reason utk pesan yang jelas.
     */
    suspend fun sendPromptResilient(storedSessionId: String, lastKnownRuntimeId: String, text: String): String {
        fun retryable(e: RpcException): Boolean {
            val m = (e.message ?: "").lowercase()
            return listOf("live owner", "already", "active", "owner", "409", "4001", "4006", "not found")
                .any { it in m }
        }
        return try {
            client.call(
                "prompt.submit",
                buildJsonObject { put("session_id", lastKnownRuntimeId); put("text", text) },
                timeoutMs = 120_000,
            )
            lastKnownRuntimeId
        } catch (e: RpcException) {
            if (e.code == 4090) throw SessionNotOwnedException(e.message ?: "session dipegang surface lain")
            if (!retryable(e)) throw e
            // runtime mati/nempel owner mati — ambil alih dengan resume baru
            val out = resume(storedSessionId)
            client.call(
                "prompt.submit",
                buildJsonObject { put("session_id", out.runtimeId); put("text", text) },
                timeoutMs = 120_000,
            )
            out.runtimeId
        }
    }

    suspend fun interrupt(sessionId: String) {
        try { client.call("session.interrupt", buildJsonObject { put("session_id", sessionId) }) }
        catch (_: RpcException) {}
    }

    suspend fun createSession(title: String? = null): Pair<String, String> {
        // return (runtimeId, storedId) — session baru punya runtime beda dari stored
        val res = client.call("session.create", buildJsonObject {
            if (!title.isNullOrBlank()) put("title", title)
        })
        val runtimeId = res["session_id"]?.jsonPrimitive?.contentOrNull
            ?: throw RpcException(-1, "session.create: no session_id in result")
        val storedId = res["stored_session_id"]?.jsonPrimitive?.contentOrNull ?: runtimeId
        return runtimeId to storedId
    }
}

object Fmt {
    fun timeAgo(epochSec: Double?): String {
        if (epochSec == null || epochSec <= 0) return ""
        val diff = (System.currentTimeMillis() / 1000.0) - epochSec
        return when {
            diff < 60 -> "now"
            diff < 3600 -> "${(diff / 60).toInt()}m"
            diff < 86400 -> "${(diff / 3600).toInt()}h"
            diff < 86400 * 7 -> "${(diff / 86400).toInt()}d"
            else -> SimpleDateFormat("d MMM", Locale.getDefault()).format(Date((epochSec * 1000).toLong()))
        }
    }

    /** M3.2: jam chat "HH.mm" (gaya WhatsApp Indonesia) dari epoch detik. */
    fun clock(epochSec: Double?): String =
        if (epochSec == null || epochSec <= 0) ""
        else SimpleDateFormat("HH.mm", Locale("id", "ID")).format(Date((epochSec * 1000).toLong()))
}
