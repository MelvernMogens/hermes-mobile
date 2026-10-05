package id.melvern.hermesmobile.core.repo

import id.melvern.hermesmobile.core.rpc.GatewayClient
import id.melvern.hermesmobile.core.rpc.RpcException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

/**
 * Slash commands, sama jalurnya dengan desktop:
 *  - saran: `complete.slash` (built-in + skill + bundle)
 *  - jalanin: `slash.exec` → output teks; 4018 (skill/bundle/pending-input) →
 *    `command.dispatch` → {type: send|skill, message} = prompt yang harus dikirim.
 */
class SlashRepo(private val c: GatewayClient) {
    data class Suggestion(val text: String, val display: String, val meta: String, val skill: Boolean)

    sealed interface Result {
        data class Output(val text: String) : Result
        /** Command ini sebenarnya prompt (skill / queue) → kirim [message] ke agent. */
        data class Send(val message: String, val notice: String?) : Result
    }

    suspend fun suggest(text: String): List<Suggestion> {
        if (!text.startsWith("/")) return emptyList()
        val r = c.call("complete.slash", buildJsonObject { put("text", text) })
        val items = r["items"] as? JsonArray ?: return emptyList()
        return items.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            fun s(k: String) = (o[k] as? JsonPrimitive)?.contentOrNull.orEmpty()
            val t = s("text").ifBlank { return@mapNotNull null }
            Suggestion(t, s("display").ifBlank { t }, s("meta"), s("kind") == "skill")
        }
    }

    suspend fun run(runtimeId: String, command: String): Result {
        val cmd = command.trim()
        return try {
            val r = c.call("slash.exec", buildJsonObject {
                put("session_id", runtimeId); put("command", cmd)
            }, timeoutMs = 60_000)
            Result.Output(cleanOutput((r["output"] as? JsonPrimitive)?.contentOrNull ?: "(no output)"))
        } catch (e: RpcException) {
            if (e.code != 4018) throw e
            val name = cmd.removePrefix("/").substringBefore(' ')
            val arg = cmd.substringAfter(' ', "").trim()
            val r = c.call("command.dispatch", buildJsonObject {
                put("session_id", runtimeId); put("name", name); put("arg", arg)
            }, timeoutMs = 60_000)
            parseDispatch(r)
        }
    }

    companion object {
        /** Pure: payload command.dispatch → Result. */
        fun parseDispatch(r: JsonObject): Result {
            fun s(k: String) = (r[k] as? JsonPrimitive)?.contentOrNull
            val msg = s("message")
            return when {
                msg != null && (s("type") == "send" || s("type") == "skill") -> Result.Send(msg, s("notice"))
                else -> Result.Output(cleanOutput(s("output") ?: msg ?: "Done."))
            }
        }

        /** Buang kode warna ANSI dari output CLI. */
        fun cleanOutput(t: String): String = t.replace(Regex("\u001B\\[[0-9;?]*[A-Za-z]"), "").trimEnd()

        /** Composer menampilkan saran hanya saat mengetik token pertama `/xxx` (belum ada spasi). */
        fun wantsSuggestions(input: String): Boolean = input.startsWith("/") && !input.contains(' ') && !input.contains('\n')
    }
}
