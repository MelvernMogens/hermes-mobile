package id.melvern.hermesmobile.core.store

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

private val Context.prefsStore by androidx.datastore.preferences.preferencesDataStore(name = "app_prefs")

/**
 * Preferensi app (tab Settings). Compose state → UI yang membaca langsung ikut
 * berubah. Dimuat SEKALI sinkron sebelum setContent (tema harus benar di frame pertama).
 */
object AppPrefs {
    enum class EnterKey(val label: String) { NEWLINE("New line"), SEND("Send") }
    enum class Theme(val label: String) { BLACK("Black"), GRAPHITE("Graphite") }
    enum class TextSize(val label: String, val scale: Float) { SMALL("Small", 0.92f), DEFAULT("Default", 1f), LARGE("Large", 1.12f) }

    /** Enter di keyboard layar: default baris baru (HP gak punya Shift+Enter). */
    var softEnter by mutableStateOf(EnterKey.NEWLINE)
    /** Keyboard fisik (tablet): Enter kirim, Shift+Enter baris baru. */
    var hardwareEnterSends by mutableStateOf(true)
    var theme by mutableStateOf(Theme.BLACK)
    var textSize by mutableStateOf(TextSize.DEFAULT)
    var autoplayVideo by mutableStateOf(false)

    private val K_SOFT = stringPreferencesKey("soft_enter")
    private val K_HW = booleanPreferencesKey("hw_enter_sends")
    private val K_THEME = stringPreferencesKey("theme")
    private val K_TEXT = stringPreferencesKey("text_size")
    private val K_AUTOPLAY = booleanPreferencesKey("autoplay_video")

    @Volatile private var loaded = false

    fun loadBlocking(context: Context) {
        if (loaded) return
        runBlocking {
            val p = context.prefsStore.data.first()
            softEnter = p[K_SOFT]?.let { v -> EnterKey.entries.firstOrNull { it.name == v } } ?: EnterKey.NEWLINE
            hardwareEnterSends = p[K_HW] ?: true
            theme = p[K_THEME]?.let { v -> Theme.entries.firstOrNull { it.name == v } } ?: Theme.BLACK
            textSize = p[K_TEXT]?.let { v -> TextSize.entries.firstOrNull { it.name == v } } ?: TextSize.DEFAULT
            autoplayVideo = p[K_AUTOPLAY] ?: false
        }
        loaded = true
    }

    suspend fun save(context: Context) {
        context.prefsStore.edit {
            it[K_SOFT] = softEnter.name
            it[K_HW] = hardwareEnterSends
            it[K_THEME] = theme.name
            it[K_TEXT] = textSize.name
            it[K_AUTOPLAY] = autoplayVideo
        }
    }

    /**
     * Keputusan tombol Enter — pure, di-unit-test.
     * @return true = kirim; false = biarkan jadi baris baru.
     */
    fun enterShouldSend(hardwareKey: Boolean, shift: Boolean, soft: EnterKey = softEnter, hw: Boolean = hardwareEnterSends): Boolean =
        if (hardwareKey) hw && !shift else soft == EnterKey.SEND
}
