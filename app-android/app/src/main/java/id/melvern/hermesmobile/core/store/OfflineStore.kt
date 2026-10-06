package id.melvern.hermesmobile.core.store

import android.content.Context
import id.melvern.hermesmobile.core.model.SessionRow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * v27: snapshot daftar chat terakhir per profile — list tetap tampil (dan chat bisa dibuka)
 * saat Mac tidak terjangkau. Ditimpa tiap refresh online sukses.
 */
object OfflineStore {
    private const val PREF = "offline_sessions"
    private val json = Json { ignoreUnknownKeys = true }
    private val ser = ListSerializer(SessionRow.serializer())
    private fun sp(c: Context) = c.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    fun save(c: Context, profile: String, rows: List<SessionRow>) {
        runCatching { sp(c).edit().putString("p:$profile", json.encodeToString(ser, rows.take(200)))
            .putLong("at:$profile", System.currentTimeMillis()).apply() }
    }
    fun load(c: Context, profile: String): List<SessionRow> =
        runCatching { json.decodeFromString(ser, sp(c).getString("p:$profile", "[]")!!) }.getOrDefault(emptyList())
    /** epoch ms snapshot terakhir (0 = belum pernah). */
    fun savedAt(c: Context, profile: String): Long = sp(c).getLong("at:$profile", 0L)

    // ── transcript ringan per chat (user/assistant teks saja, 80 pesan terakhir) ──
    @kotlinx.serialization.Serializable
    data class Msg(val u: Boolean, val t: String, val at: Double? = null)

    private val msgSer = ListSerializer(Msg.serializer())
    private fun dir(c: Context) = java.io.File(c.cacheDir, "offline_chats").apply { mkdirs() }
    private fun file(c: Context, storedId: String) = java.io.File(dir(c), storedId.replace(Regex("[^A-Za-z0-9_.-]"), "_") + ".json")

    fun saveChat(c: Context, storedId: String, items: List<id.melvern.hermesmobile.core.model.ChatItem>) {
        if (storedId.isBlank()) return
        val msgs = items.mapNotNull {
            when (it) {
                is id.melvern.hermesmobile.core.model.ChatItem.User -> if (it.outboxId != null) null else Msg(true, it.text, it.at)
                is id.melvern.hermesmobile.core.model.ChatItem.Assistant -> if (!it.done || it.text.isBlank() || it.text == "…") null else Msg(false, it.text, it.at)
                else -> null
            }
        }.takeLast(80)
        if (msgs.isEmpty()) return
        runCatching {
            file(c, storedId).writeText(json.encodeToString(msgSer, msgs))
            dir(c).listFiles()?.sortedByDescending { f -> f.lastModified() }?.drop(30)?.forEach { f -> f.delete() }
        }
    }

    fun loadChat(c: Context, storedId: String, clock: (Double) -> String): List<id.melvern.hermesmobile.core.model.ChatItem> =
        runCatching {
            val f = file(c, storedId); if (!f.exists()) return emptyList()
            json.decodeFromString(msgSer, f.readText()).map { m ->
                if (m.u) id.melvern.hermesmobile.core.model.ChatItem.User(m.t, time = m.at?.let(clock) ?: "", at = m.at)
                else id.melvern.hermesmobile.core.model.ChatItem.Assistant(m.t, done = true, time = m.at?.let(clock) ?: "", at = m.at)
            }
        }.getOrDefault(emptyList())
}
