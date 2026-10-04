package id.melvern.hermesmobile.core.repo

import java.util.LinkedHashMap

/**
 * M17 (fix A): preview pesan TERAKHIR per stored session id — in-memory,
 * process-wide. server session.list memberi `preview` = pesan PERTAMA
 * (tip kompresi), jadi app menimpanya sendiri dari 2 sumber:
 *  1. event `message.complete` yang diterima app utk session apapun;
 *  2. snapshot TranscriptCache terakhir saat chat dipakai (items terakhir).
 *
 * Pure Kotlin (tanpa Android framework) supaya bisa di-unit-test.
 * Sumber mapping runtime id → stored id: SessionRuntimeIndex.
 */
object LastMsgStore {

    const val MAX_TEXT = 80
    private const val MAX_ENTRIES = 64

    data class Last(
        val text: String,
        /** Epoch millis — dipakai buang entry basi saat trim. */
        val at: Long = System.currentTimeMillis(),
    )

    private val map = LinkedHashMap<String, Last>(16, 0.75f, false)
    /** Uji/migrasi: keputusan trim (bukan UI). */
    @Volatile
    var dropOlderThanMs: Long = 7L * 24 * 3600 * 1000

    /** Simpan preview terakhir (text dipangkas 80 char, newline diratakan). */
    fun put(storedId: String, rawText: String) {
        val flat = rawText.replace('\n', ' ').trim()
        if (flat.isEmpty()) return
        map[storedId] = Last(flat.take(MAX_TEXT))
        trim(keep = storedId)
    }

    fun get(storedId: String): Last? = map[storedId]

    fun clear() = map.clear()

    fun size(): Int = map.size

    /** Pangkas jumlah entry ke <= 64: buang yang paling tua dulu.
     *  [keep]: entry yang baru ditulis tidak pernah ikut pass usia. */
    private fun trim(keep: String? = null) {
        if (map.size <= MAX_ENTRIES) return
        val cutoff = System.currentTimeMillis() - dropOlderThanMs
        // pass 1: buang entry lebih tua dari window (session mati lama).
        map.entries.removeIf { it.key != keep && it.value.at < cutoff }
        // pass 2: masih overload → buang paling tua sampai muat.
        if (map.size > MAX_ENTRIES) {
            map.entries.sortedBy { it.value.at }
                .take(map.size - MAX_ENTRIES)
                .forEach { map.remove(it.key, it.value) }
        }
    }

    /**
     * Ekstrak teks preview dari payload event `message.complete`
     * (payload["text"] — contract MessageCompletePayload). Pure fun supaya
     * dites: payload JSON → teks ("" kalau kosong).
     */
    fun textFromCompletePayload(payload: kotlinx.serialization.json.JsonObject?): String {
        val el = payload?.get("text") ?: return ""
        return (el as? kotlinx.serialization.json.JsonPrimitive)?.content ?: ""
    }
}
