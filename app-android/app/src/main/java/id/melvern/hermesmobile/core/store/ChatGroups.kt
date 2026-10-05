package id.melvern.hermesmobile.core.store

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val Context.groupStore by androidx.datastore.preferences.preferencesDataStore(name = "chat_groups")

/**
 * Grup chat (lokal per HP): nama + warna + daftar stored session id.
 * Warna grup juga mewarnai avatar inisial semua chat di dalamnya.
 * Satu chat maksimal satu grup.
 */
object ChatGroups {
    @Serializable
    data class Group(val id: String, val name: String, val color: Int, val sessions: List<String> = emptyList(), val collapsed: Boolean = false)

    /** Palet: cukup jenuh supaya kebaca di hitam, tapi tidak neon. Index = warna yang disimpan. */
    val Palette: List<Pair<String, Color>> = listOf(
        "Blue" to Color(0xFF5B8DEF), "Purple" to Color(0xFF9B7CF2), "Pink" to Color(0xFFE56FA8),
        "Red" to Color(0xFFE5675F), "Orange" to Color(0xFFE8955A), "Yellow" to Color(0xFFD9BE52),
        "Green" to Color(0xFF55BE7E), "Teal" to Color(0xFF4CB6B0), "Gray" to Color(0xFF8E8E96),
    )

    fun color(index: Int): Color = Palette.getOrNull(index)?.second ?: Palette.last().second

    /** Avatar inisial: latar = warna grup gelap (22%), huruf = warna grup terang. */
    fun avatarColors(index: Int): Pair<Color, Color> {
        val c = color(index)
        val bg = Color(red = c.red * 0.30f, green = c.green * 0.30f, blue = c.blue * 0.30f, alpha = 1f)
        val ink = Color(red = 0.55f + c.red * 0.45f, green = 0.55f + c.green * 0.45f, blue = 0.55f + c.blue * 0.45f, alpha = 1f)
        return bg to ink
    }

    var groups by mutableStateOf<List<Group>>(emptyList()); private set

    private val KEY = stringPreferencesKey("groups_json")
    private val json = Json { ignoreUnknownKeys = true }
    @Volatile private var loaded = false

    suspend fun load(context: Context) {
        if (loaded) return
        groups = try {
            context.groupStore.data.first()[KEY]?.let { json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(Group.serializer()), it) } ?: emptyList()
        } catch (_: Throwable) { emptyList() }
        loaded = true
    }

    private suspend fun save(context: Context, next: List<Group>) {
        groups = next
        context.groupStore.edit { it[KEY] = json.encodeToString(kotlinx.serialization.builtins.ListSerializer(Group.serializer()), next) }
    }

    fun groupOf(sessionId: String): Group? = groups.firstOrNull { sessionId in it.sessions }

    suspend fun create(context: Context, name: String, color: Int, firstSession: String? = null): Group {
        val g = Group(
            id = "g" + System.currentTimeMillis().toString(36),
            name = name.trim().ifEmpty { "Group" },
            color = color,
            sessions = listOfNotNull(firstSession),
        )
        val stripped = if (firstSession == null) groups else groups.map { it.copy(sessions = it.sessions - firstSession) }
        save(context, stripped + g)
        return g
    }

    /** Pindah chat ke grup (null = keluarkan dari grup). */
    suspend fun assign(context: Context, sessionId: String, groupId: String?) {
        save(context, groups.map { g ->
            when {
                g.id == groupId -> if (sessionId in g.sessions) g else g.copy(sessions = g.sessions + sessionId)
                else -> g.copy(sessions = g.sessions - sessionId)
            }
        })
    }

    suspend fun update(context: Context, groupId: String, name: String? = null, color: Int? = null, collapsed: Boolean? = null) {
        save(context, groups.map { g ->
            if (g.id != groupId) g else g.copy(
                name = name?.trim()?.ifEmpty { g.name } ?: g.name,
                color = color ?: g.color,
                collapsed = collapsed ?: g.collapsed,
            )
        })
    }

    suspend fun delete(context: Context, groupId: String) = save(context, groups.filterNot { it.id == groupId })

    /** Pure: urutkan baris ke (grup → baris) mengikuti urutan [rows]; sisanya ungrouped. */
    fun <T> partition(rows: List<T>, id: (T) -> String, gs: List<Group> = groups): Pair<List<Pair<Group, List<T>>>, List<T>> {
        val byGroup = gs.map { g -> g to rows.filter { id(it) in g.sessions } }
        val grouped = gs.flatMap { it.sessions }.toSet()
        return byGroup to rows.filterNot { id(it) in grouped }
    }
}
