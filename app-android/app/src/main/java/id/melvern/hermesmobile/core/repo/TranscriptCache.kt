package id.melvern.hermesmobile.core.repo

import android.util.Log
import id.melvern.hermesmobile.core.model.ChatItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * M9 (item 6): cache transcript level-app — selamat dari back / rotate /
 * re-composition (composition-scoped `remember` mati tiap kali ChatScreen
 * tinggalkan composition).
 *
 * Key = stored session id. Value = (items, runtimeId, events-since cursor).
 * LRU 8; cache harus di-update oleh pemilik ChatScreen di titik-titik
 * mutasi items (delta, complete, send) — via [snapshot].
 */
object TranscriptCache {
    private const val TAG = "TranscriptCache"
    private const val MAX_ENTRIES = 8
    const val CURSOR_UNKNOWN = -1

    data class Entry(
        val items: List<ChatItem>,
        val runtimeId: String,
        /** Watermark seq events terakhir yang sudah masuk `items` (-1 = belum ada). */
        val cursor: Int,
        val running: Boolean,
        /** Replay-epoch gateway saat snapshot diambil — berubah tiap restart gateway;
         *  epoch beda = ring replay kosong → delta count=0 TIDAK bisa dipercaya → full resume. */
        val epoch: Int = 0,
        val at: Long = System.currentTimeMillis(),
    )

    private val _hits = MutableStateFlow(0)
    private val _misses = MutableStateFlow(0)
    /** Observability bukti verify item 6 (bisa dibaca test + logcat). */
    val hits: StateFlow<Int> = _hits
    val misses: StateFlow<Int> = _misses

    private val map = LinkedHashMap<String, Entry>(8, 0.75f, true)

    @Synchronized
    fun get(storedId: String): Entry? {
        val e = map[storedId]
        if (e != null) { _hits.value = _hits.value + 1; Log.i(TAG, "cache hit: $storedId (${e.items.size} items)") }
        else { _misses.value = _misses.value + 1; Log.i(TAG, "cache miss: $storedId") }
        return e
    }

    @Synchronized
    fun snapshot(storedId: String, items: List<ChatItem>, runtimeId: String, cursor: Int, running: Boolean) {
        map[storedId] = Entry(items.toList(), runtimeId, cursor, running)
        trim()
    }

    @Synchronized
    fun setCursor(storedId: String, cursor: Int) {
        map[storedId]?.let { map[storedId] = it.copy(cursor = cursor, at = System.currentTimeMillis()) }
    }

    @Synchronized
    fun drop(storedId: String) {
        map.remove(storedId)
    }

    @Synchronized
    fun clear() {
        map.clear(); _hits.value = 0; _misses.value = 0
    }

    @Synchronized
    fun size(): Int = map.size

    /** Buang entry paling lama sampai <= MAX_ENTRIES. */
    private fun trim() {
        while (map.size > MAX_ENTRIES) {
            val eldest = map.keys.firstOrNull() ?: break
            Log.i(TAG, "evict: $eldest")
            map.remove(eldest)
        }
    }
    @Synchronized
    fun setEpoch(storedId: String, epoch: Int) {
        map[storedId]?.let { map[storedId] = it.copy(epoch = epoch) }
    }

}
