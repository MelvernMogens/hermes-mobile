package id.melvern.hermesmobile.core.repo

import android.content.Context
import id.melvern.hermesmobile.core.model.ChatItem
import id.melvern.hermesmobile.core.model.UserMatch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap

/**
 * v26.3: pesan yang dikirim dari HP tapi belum dikonfirmasi server.
 *
 * Dulu kirim jalan di scope layar chat: keluar chat saat submit masih menunggu (koneksi
 * lambat / socket setengah mati) membatalkan kirim → pesan hilang diam-diam. Sekarang:
 * entri disimpan dulu (bertahan keluar chat & app di-kill), kirim jalan di scope aplikasi,
 * dan entri baru dibuang setelah server menerima. Gagal → bubble "Not sent · Tap to retry".
 */
object Outbox {
    @Serializable
    data class Out(
        val id: String, val storedId: String,
        /** teks bubble (tanpa quote) */ val display: String,
        /** teks yang dikirim ke server */ val text: String,
        val at: Long, val quote: String? = null,
        /** true = "Send after" (prompt.submit queued=true) */ val queued: Boolean = false,
    )

    private const val PREF = "outbox"
    private const val TTL_MS = 24 * 3600_000L
    private val json = Json { ignoreUnknownKeys = true }
    private val ser = ListSerializer(Out.serializer())
    private val inFlight: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val _version = MutableStateFlow(0)
    /** naik tiap ada perubahan (kirim selesai / gagal) — layar chat yang terbuka me-rekonsiliasi bubble. */
    val version: StateFlow<Int> = _version

    private fun sp(c: Context) = c.getSharedPreferences(PREF, Context.MODE_PRIVATE)
    private fun all(c: Context): List<Out> {
        val now = System.currentTimeMillis()
        return runCatching { json.decodeFromString(ser, sp(c).getString("items", "[]")!!) }
            .getOrDefault(emptyList()).filter { now - it.at < TTL_MS }
    }
    private fun save(c: Context, l: List<Out>) { sp(c).edit().putString("items", json.encodeToString(ser, l.takeLast(50))).apply(); _version.value++ }

    fun add(c: Context, o: Out) = save(c, all(c) + o)
    fun remove(c: Context, id: String) { val a = all(c); if (a.any { it.id == id }) save(c, a.filterNot { it.id == id }) }
    fun get(c: Context, id: String): Out? = all(c).firstOrNull { it.id == id }
    fun forChat(c: Context, storedId: String): List<Out> = all(c).filter { it.storedId == storedId }

    fun markSending(id: String) { inFlight += id; _version.value++ }
    fun markDone(id: String) { inFlight -= id; _version.value++ }
    fun isSending(id: String) = id in inFlight

    /** Buang entri yang sudah ada di transcript server (row user cocok, waktunya tidak lebih tua dari entri). */
    fun settle(c: Context, storedId: String, serverUsers: List<Pair<String, Double?>>) {
        val a = all(c)
        val keep = a.filter { o ->
            o.storedId != storedId || o.id in inFlight ||
                serverUsers.none { (t, at) -> UserMatch.same(o.display, t) && (at == null || at * 1000 >= o.at - 120_000) }
        }
        if (keep.size != a.size) save(c, keep)
    }

    /**
     * Pure: rekonsiliasi bubble dengan outbox — entri yang belum ada di [items] ditambah;
     * bubble ber-outboxId diset pending/failed sesuai status; yang sudah keluar outbox → normal.
     */
    fun reconcile(items: List<ChatItem>, pending: List<Out>, sending: (String) -> Boolean, clock: (Double) -> String): List<ChatItem> {
        val byId = pending.associateBy { it.id }
        var out = items.map { item ->
            val u = item as? ChatItem.User ?: return@map item
            val id = u.outboxId ?: return@map item
            val o = byId[id]
            when {
                o == null -> if (u.pending || u.failed) u.copy(pending = false, failed = false) else u
                sending(id) -> u.copy(pending = true, failed = false)
                else -> u.copy(pending = false, failed = true)
            }
        }
        val haveIds = out.mapNotNull { (it as? ChatItem.User)?.outboxId }.toSet()
        val confirmed = out.filterIsInstance<ChatItem.User>().filter { it.outboxId == null && !it.pending }.map { it.text }
        val add = pending.filter { o -> o.id !in haveIds && confirmed.none { UserMatch.same(o.display, it) } }.map { o ->
            val at = o.at / 1000.0
            ChatItem.User(o.display, time = clock(at), at = at, quote = o.quote,
                pending = sending(o.id), failed = !sending(o.id), outboxId = o.id)
        }
        if (add.isNotEmpty()) out = out + add
        return out
    }
}
