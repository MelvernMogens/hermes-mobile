package id.melvern.hermesmobile.core.notify

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * M14: pure logic notifikasi background — gak ada dependensi Android, unit-testable.
 *
 * Input: (clock, foreground, openStoredId, runtimeToStored map, event terpilih).
 * Output: apakah event ini harus jadi notifikasi + versi final judul/preview.
 */
object NotifPolicy {

    /** Dedup: max 1 notif per session per 30s (HashMap ts — dibersihkan saat cek). */
    const val DEDUP_WINDOW_MS = 30_000L

    /** Sesi yang gak dibuka di layar → boleh notif. null = gak ada chat terbuka. */
    data class Ctx(
        val now: Long,
        val foreground: Boolean,
        /** stored id chat yang SEDANG dibuka di layar (null = di luar chat / list). */
        val openStoredId: String?,
        /** runtime id → stored id (event ring pakai runtime id yang berganti tiap resume). */
        val runtimeToStored: Map<String, String>,
        /** ts notif terakhir per stored id (dedup). */
        val lastNotifAt: Map<String, Long>,
    )

    sealed interface Decision {
        /** Skip — gak layak notif (foreground / session terbuka / dedup / bukan event notif). */
        data object Skip : Decision
        data class Notify(
            val storedId: String,
            val channel: Channel,
            val title: String,
            val preview: String,
            /** id stabil utk dedup lanjutan: storedId + jenis. */
            val tag: String,
        ) : Decision
    }

    enum class Channel { AGENT, CONNECTION }

    /**
     * M14 (polling): diff dua snapshot active_list → keputusan notif.
     * Non-viewer WS TIDAK menerima event ring (server hanya mengirim ke transport
     * session itu — terverifikasi E2E), jadi deteksi lewat poll active_list:
     * - working→idle + message_count naik → "Agent replied" (preview dari row baru).
     * - status "waiting" (server-request pending: approval/clarify) → notif high.
     */
    data class LiveRow(
        val sid: String,
        val sessionKey: String,
        val status: String,
        val messageCount: Int,
        val preview: String,
        val title: String,
    )

    fun onPollDiff(ctx: Ctx, prev: Map<String, LiveRow>, now: Map<String, LiveRow>): List<Decision> {
        if (ctx.foreground) return emptyList()
        val out = ArrayList<Decision>()
        for ((sid, row) in now) {
            val storedId = row.sessionKey.ifEmpty { sid }
            // (dulu: skip chat yang terakhir dibuka — salah: app di background =
            // user TIDAK lihat chat itu, justru paling butuh notif.)
            val before = prev[sid]
            // count naik SAAT masih working = stream berjalan — bukan turn tamat.
            // Tanda selesai: keluar dari working, ATAU count naik saat status != working.
            val finished = before != null && before.status == "working" && row.status != "working"
            val countGrew = before != null && row.status != "working" && row.messageCount > before.messageCount
            val nowWaiting = before != null && row.status == "waiting" && before.status != "waiting"
            when {
                // waiting menang atas reply — turn berakhir dengan pertanyaan menunggu jawaban.
                nowWaiting -> if (!inDedupWindow(ctx, storedId)) {
                    out.add(Decision.Notify(
                        storedId, Channel.AGENT,
                        "Agent needs your approval — ${row.title.ifEmpty { storedId.take(12) }}",
                        "Approval is waiting for your response.", "$storedId#approval",
                    ))
                }
                finished || countGrew -> if (!inDedupWindow(ctx, storedId)) {
                    val preview = previewOf(row.preview) ?: "New activity in this chat."
                    out.add(Decision.Notify(
                        storedId, Channel.AGENT,
                        "Agent replied — ${row.title.ifEmpty { storedId.take(12) }}",
                        preview, "$storedId#reply",
                    ))
                }
            }
        }
        return out
    }

    /** Substring preview sesuai spec M14. */
    fun previewOf(text: String?): String? {
        val t = text?.trim().orEmpty()
        if (t.isEmpty()) return null
        return if (t.length <= 60) t else t.take(60) + "…"
    }

    fun jsonStr(o: JsonObject?, key: String): String? =
        o?.get(key)?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotEmpty() }

    /**
     * message.complete: notif "Agent replied" / "Agent error" (payload.status).
     * status enum TurnStatus: complete | error | interrupted.
     */
    fun onMessageComplete(ctx: Ctx, sessionId: String, payload: JsonObject?, title: String): Decision {
        if (ctx.foreground) return Decision.Skip
        val storedId = resolveStored(ctx, sessionId) ?: return Decision.Skip
        if (storedId == ctx.openStoredId) return Decision.Skip
        if (inDedupWindow(ctx, storedId)) return Decision.Skip
        return when (jsonStr(payload, "status")) {
            "error" -> Decision.Notify(
                storedId, Channel.AGENT, "Agent error — $title",
                jsonStr(payload, "warning")?.takeIf { it.isNotBlank() }
                    ?: jsonStr(payload, "text")?.takeIf { it.isNotBlank() }?.let { "Turn ended with an error." }
                    ?: "Turn ended with an error.",
                tag = "$storedId#error",
            )
            else -> {
                val preview = previewOf(jsonStr(payload, "text"))
                    ?: return Decision.Skip // turn kosong (mis. interrupt) — bukan kabar
                Decision.Notify(storedId, Channel.AGENT, "Agent replied — $title", preview, "$storedId#reply")
            }
        }
    }

    /** approval / clarify server-request (frame bawa session_id runtime di params). */
    fun onServerAsk(ctx: Ctx, params: JsonObject?, title: String): Decision {
        if (ctx.foreground) return Decision.Skip
        val sid = jsonStr(params, "session_id") ?: return Decision.Skip
        val storedId = resolveStored(ctx, sid) ?: return Decision.Skip
        if (storedId == ctx.openStoredId) return Decision.Skip
        if (inDedupWindow(ctx, storedId)) return Decision.Skip
        return Decision.Notify(
            storedId, Channel.AGENT, "Agent needs your approval — $title",
            jsonStr(params, "question") ?: jsonStr(params, "command") ?: "Approval is waiting for your response.",
            tag = "$storedId#approval",
        )
    }

    private fun resolveStored(ctx: Ctx, sessionId: String): String? =
        ctx.runtimeToStored[sessionId] ?: sessionId.takeIf { it in ctx.runtimeToStored.values }

    private fun inDedupWindow(ctx: Ctx, storedId: String): Boolean =
        (ctx.now - (ctx.lastNotifAt[storedId] ?: 0L)) < DEDUP_WINDOW_MS
}
