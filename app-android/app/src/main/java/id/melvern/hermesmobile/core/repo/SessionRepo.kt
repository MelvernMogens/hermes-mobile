package id.melvern.hermesmobile.core.repo

import id.melvern.hermesmobile.core.model.SessionRow
import id.melvern.hermesmobile.core.model.TranscriptMessage
import id.melvern.hermesmobile.core.rpc.GatewayClient
import id.melvern.hermesmobile.core.rpc.RpcException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SessionRepo(private val client: GatewayClient) {

    suspend fun listSessions(limit: Int = 60): List<SessionRow> {
        val res = client.call("session.list", buildJsonObject { put("limit", limit) })
        val arr = res["sessions"]?.jsonArray ?: return emptyList()
        return arr.mapNotNull { el ->
            try { Json.decodeFromJsonElement(SessionRow.serializer(), el.jsonObject) } catch (_: Throwable) { null }
        }
    }

    suspend fun resume(sessionId: String): ResumeOutcome {
        // defer_history HARUS false — true mengembalikan messages kosong (server
        // menganggap history di-hydrate terpisah). 694KB/187 msg terverifikasi.
        val res = client.call(
            "session.resume",
            buildJsonObject { put("session_id", sessionId) },
            timeoutMs = 45_000,
        )
        val messages = res["messages"]?.jsonArray?.mapNotNull { el ->
            try { Json.decodeFromJsonElement(TranscriptMessage.serializer(), el.jsonObject) } catch (_: Throwable) { null }
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
     * (4001/4006 atau not-found), re-resume sekali lalu ulangi submit dengan
     * runtime id baru. Return runtime id yang dipakai (untuk sinkron filter event).
     */
    suspend fun sendPromptResilient(storedSessionId: String, lastKnownRuntimeId: String, text: String): String {
        return try {
            client.call(
                "prompt.submit",
                buildJsonObject { put("session_id", lastKnownRuntimeId); put("text", text) },
                timeoutMs = 120_000,
            )
            lastKnownRuntimeId
        } catch (e: RpcException) {
            // coba re-resume pakai stored id, lalu submit ke runtime baru
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

    suspend fun createSession(title: String? = null): String {
        val res = client.call("session.create", buildJsonObject {
            if (!title.isNullOrBlank()) put("title", title)
        })
        return res["session_id"]?.jsonPrimitive?.contentOrNull
            ?: throw RpcException(-1, "session.create: no session_id in result")
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
}
