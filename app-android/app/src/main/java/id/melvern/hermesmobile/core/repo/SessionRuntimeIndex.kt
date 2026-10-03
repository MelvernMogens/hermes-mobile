package id.melvern.hermesmobile.core.repo

import android.util.Log
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * M17 (fix A): map RUNTIME session id (server id; berganti tiap resume di
 * surface lain) → STORED session id (stabil, = key session.list & key
 * LastMsgStore). Event `message.complete` membawa runtime id — butuh map ini
 * supaya preview bisa disimpan ke LastMsgStore per stored id.
 *
 * Diisi dari dua sumber (kapanpun keduanya diketahui):
 *  - session.list: sessions[].session_key (stored) vs .id (runtime);
 *  - session.active_list: id (runtime) vs session_key (stored).
 * Live dan sesudahnya idak berubah sampai resume lain — entri lama ditimpa,
 * bukan dibuang (resume di surface lain hanya memindahkan key).
 */
object SessionRuntimeIndex {

    private const val TAG = "SessionRuntimeIndex"
    /** Runtime id berumur pendek — jangan biarkan map bocor tanpa batas. */
    private const val MAX_ENTRIES = 64

    private val map = HashMap<String, String>()

    @Synchronized
    fun put(runtimeId: String, storedId: String) {
        if (runtimeId.isBlank() || storedId.isBlank() || runtimeId == storedId) return
        val old = map.put(runtimeId, storedId)
        if (old != null && old != storedId) {
            Log.i(TAG, "re-map $runtimeId: $old -> $storedId (resume surface lain)")
        }
        if (map.size > MAX_ENTRIES) {
            map.keys.take(map.size - MAX_ENTRIES).forEach { map.remove(it) }
        }
    }

    @Synchronized
    fun storedFor(runtimeId: String): String? = map[runtimeId]

    /** Simpan semua pasangan dari array `sessions` (session.list / active_list). */
    @Synchronized
    fun ingestSessionsArray(sessions: List<JsonObject>) {
        sessions.forEach { o ->
            val runtime = o["id"]?.jsonPrimitive?.content ?: return@forEach
            val stored = o["session_key"]?.jsonPrimitive?.content ?: return@forEach
            put(runtime, stored)
        }
    }

    @Synchronized
    fun clear() = map.clear()

    @Synchronized
    fun size(): Int = map.size
}
