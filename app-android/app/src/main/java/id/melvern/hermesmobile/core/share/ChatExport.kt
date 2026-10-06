package id.melvern.hermesmobile.core.share

import android.content.Context
import android.content.Intent
import id.melvern.hermesmobile.core.model.ChatItem

/** v24 share-out: kirim balasan / seluruh chat ke app lain (WhatsApp, Notes, email). */
object ChatExport {
    /** Pure: transcript → markdown ringkas (tanpa tool noise, tanpa blok lampiran internal). */
    fun markdown(title: String, items: List<ChatItem>): String = buildString {
        append("# ").append(title.trim()).append("\n\n")
        for (it in items) when (it) {
            is ChatItem.User -> append("**You:** ").append(clean(it.text)).append("\n\n")
            is ChatItem.Assistant -> if (it.done && it.text.isNotBlank()) append("**Hermes:** ").append(clean(it.text)).append("\n\n")
            else -> {}
        }
    }.trimEnd() + "\n"

    private fun clean(t: String): String = t
        .replace(Regex("""(?s)--- Attached Context ---.*$"""), "")
        .lines().filterNot { it.trimStart().startsWith("MEDIA:") || it.trimStart().startsWith("@file:") }
        .joinToString("\n").trim()

    fun share(context: Context, title: String, text: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, text.take(90_000))
        }
        context.startActivity(Intent.createChooser(send, "Share").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
