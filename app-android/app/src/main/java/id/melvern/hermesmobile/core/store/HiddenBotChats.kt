package id.melvern.hermesmobile.core.store

import android.content.Context

/** v27: Bot Chat yang sengaja di-Hide user di HP — jangan dimunculkan paksa lagi. */
object HiddenBotChats {
    private fun sp(c: Context) = c.getSharedPreferences("hidden_bot_chats", Context.MODE_PRIVATE)
    fun has(c: Context, id: String) = sp(c).getBoolean(id, false)
    fun set(c: Context, id: String, hidden: Boolean) {
        if (hidden) sp(c).edit().putBoolean(id, true).apply() else sp(c).edit().remove(id).apply()
    }
}
