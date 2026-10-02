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

class SessionRepo(
    private val client: GatewayClient,
    /** M4: profile aktif — dikirim sebagai params.profile (nullable, default = profile server). */
    private val profile: String? = null,
) {

    /** Lenient decode — server kirim field ekstra (mis. reasoning_content); strict default buang pesan senyap. */
    private val json = Json { ignoreUnknownKeys = true }

    /** Helper params.profile — null/"default" gak dikirim (server default = profile utama). */
    private fun JsonObjectBuilder.putProfile() {
        val p = profile?.trim()
        if (!p.isNullOrEmpty() && p != "default") put("profile", p)
    }

    suspend fun listSessions(limit: Int = 60, includeHidden: Boolean = false): List<SessionRow> {
        val res = client.call("session.list", buildJsonObject {
            put("limit", limit)
            if (includeHidden) put("include_hidden", true) // M5: toggle session tersembunyi (Bot Chat)
            putProfile()
        })
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
            buildJsonObject { put("session_id", sessionId); putProfile() },
            timeoutMs = 45_000,
        )
        val messages = res["messages"]?.jsonArray?.mapNotNull { el ->
            try { json.decodeFromJsonElement(TranscriptMessage.serializer(), el.jsonObject) } catch (_: Throwable) { null }
        } ?: emptyList()
        // M4: open_requests — server→client request yang belum dijawab (approval /
        // clarify), di-replay saat resume. Card di-restored dari sini.
        val openRequests = res["open_requests"]?.jsonArray?.mapNotNull { el ->
            val o = el.jsonObject
            val method = o["method"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            OpenRequest(
                id = o["id"]?.jsonPrimitive?.contentOrNull ?: "",
                method = method,
                params = o["params"]?.jsonObject,
            )
        } ?: emptyList()
        return ResumeOutcome(
            runtimeId = res["session_id"]?.jsonPrimitive?.contentOrNull ?: sessionId,
            messages = messages,
            running = res["running"]?.jsonPrimitive?.booleanOrNull ?: false,
            hydrated = !(res["hydrating"]?.jsonPrimitive?.booleanOrNull ?: false),
            openRequests = openRequests,
        )
    }

    /** Satu server→client request belum dijawab (OpenRequestEntry contract). */
    data class OpenRequest(val id: String, val method: String, val params: JsonObject?)

    data class ResumeOutcome(
        val runtimeId: String,
        val messages: List<TranscriptMessage>,
        val running: Boolean,
        val hydrated: Boolean,
        val openRequests: List<OpenRequest> = emptyList(),
    )

    suspend fun sendPrompt(sessionId: String, text: String) {
        client.call(
            "prompt.submit",
            buildJsonObject { put("session_id", sessionId); put("text", text) },
            timeoutMs = 120_000,
        )
    }

    /** Status balikan prompt.submit: "streaming" (turn langsung jalan) atau "queued" (server antri/steer). */
    enum class SubmitStatus { STREAMING, QUEUED }

    /**
     * Submit biasa yang balikin status — M5: kalau server balik "queued"
     * (busy path: diantrekan/steered), user bubble tampil "DIANTREKAN".
     * Return runtime id yang dipakai (result kadang bawa session_id baru).
     */
    suspend fun submitStatus(sessionId: String, text: String): SubmitStatus {
        val res = client.call(
            "prompt.submit",
            buildJsonObject { put("session_id", sessionId); put("text", text); putProfile() },
            timeoutMs = 120_000,
        )
        return if (res["status"]?.jsonPrimitive?.contentOrNull == "queued") SubmitStatus.QUEUED
        else SubmitStatus.STREAMING
    }

    /**
     * Submit dengan jaminan runtime hidup: kalau prompt.submit kena error session
     * (4001/4006/not-found/"live owner"/"already active"), re-resume sekali lalu
     * ulangi submit dengan runtime id baru. Return (runtime id, status submit —
     * M5: "queued" kalau server antri/steer, selain itu streaming).
     *
     * 4090 SESSION_NOT_OWNED (session masih di-hold surface lain — desktop app
     * buka session itu) TIDAK bisa diambil alih by design (#106217): dilempar
     * ke UI dengan reason utk pesan yang jelas.
     */
    suspend fun sendPromptResilient(storedSessionId: String, lastKnownRuntimeId: String, text: String): Pair<String, SubmitStatus> {
        fun retryable(e: RpcException): Boolean {
            val m = (e.message ?: "").lowercase()
            return listOf("live owner", "already", "active", "owner", "409", "4001", "4006", "not found")
                .any { it in m }
        }
        suspend fun submitOn(runtimeId: String): SubmitStatus {
            val res = client.call(
                "prompt.submit",
                buildJsonObject { put("session_id", runtimeId); put("text", text); putProfile() },
                timeoutMs = 120_000,
            )
            return if (res["status"]?.jsonPrimitive?.contentOrNull == "queued") SubmitStatus.QUEUED
            else SubmitStatus.STREAMING
        }
        return try {
            lastKnownRuntimeId to submitOn(lastKnownRuntimeId)
        } catch (e: RpcException) {
            if (e.code == 4090) throw SessionNotOwnedException(e.message ?: "session dipegang surface lain")
            if (!retryable(e)) throw e
            // runtime mati/nempel owner mati — ambil alih dengan resume baru
            val out = resume(storedSessionId)
            out.runtimeId to submitOn(out.runtimeId)
        }
    }

    suspend fun interrupt(sessionId: String) {
        try { client.call("session.interrupt", buildJsonObject { put("session_id", sessionId); putProfile() }) }
        catch (_: RpcException) {}
    }

    /**
     * M4: attach lazy tanpa history (session.resume lazy+omit_messages) — return
     * runtime id buat aksi yang butuh session live (session.title SET, session.branch),
     * tanpa build agent / transfer transcript. Fail → null (pemanggil pakai stored id).
     */
    suspend fun resumeAttachLazy(storedId: String): String? = try {
        val res = client.call(
            "session.resume",
            buildJsonObject {
                put("session_id", storedId); put("lazy", true); put("omit_messages", true); putProfile()
            },
            timeoutMs = 20_000,
        )
        res["session_id"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: storedId
    } catch (_: Throwable) { null }

    suspend fun createSession(
        title: String? = null,
        // M5: "GIG baru dengan model ini" — SessionCreateParams punya model+provider.
        model: String? = null,
        provider: String? = null,
        // M9 (item 2): reasoning effort utk chat baru (SessionCreateParams.reasoning_effort).
        reasoningEffort: String? = null,
    ): Pair<String, String> {
        // return (runtimeId, storedId) — session baru punya runtime beda dari stored
        val res = client.call("session.create", buildJsonObject {
            if (!title.isNullOrBlank()) put("title", title)
            if (!model.isNullOrBlank()) put("model", model)
            if (!provider.isNullOrBlank()) put("provider", provider)
            if (!reasoningEffort.isNullOrBlank()) put("reasoning_effort", reasoningEffort)
            putProfile()
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
