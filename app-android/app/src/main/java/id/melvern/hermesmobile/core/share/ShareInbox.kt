package id.melvern.hermesmobile.core.share

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * "Share to Hermes": isi yang dibagikan app lain (teks/link, foto, video, file).
 * Ditahan di sini sampai user memilih chat tujuan; ChatScreen yang terbuka lalu
 * mengambilnya (teks → draft composer, file → upload seperti tombol +).
 */
object ShareInbox {
    data class Shared(val text: String?, val uris: List<Uri>, val subject: String? = null) {
        val summary: String
            get() = when {
                uris.size > 1 -> "${uris.size} files"
                uris.size == 1 -> "1 attachment" + (text?.let { " + text" } ?: "")
                else -> (text ?: "").take(120)
            }
    }

    private val _pending = MutableStateFlow<Shared?>(null)
    val pending: StateFlow<Shared?> = _pending

    /** Chat tujuan sudah dipilih → ChatScreen untuk stored id ini yang mengambil. */
    private val _target = MutableStateFlow<String?>(null)
    val target: StateFlow<String?> = _target

    fun offer(intent: Intent?, context: Context): Boolean {
        val shared = parse(intent) ?: return false
        // izin baca URI share hanya hidup selama task — salin ke cache supaya aman diupload nanti
        val copied = shared.uris.mapNotNull { copyToCache(context, it) }
        _pending.value = shared.copy(uris = copied)
        _target.value = null
        return true
    }

    fun route(storedId: String) { _target.value = storedId }

    /** Diambil SEKALI oleh chat tujuan. */
    fun take(storedId: String): Shared? {
        if (_target.value != storedId) return null
        val s = _pending.value
        _pending.value = null; _target.value = null
        return s
    }

    fun dismiss() { _pending.value = null; _target.value = null }

    /** Pure-ish: intent → Shared (null = bukan share). */
    fun parse(intent: Intent?): Shared? {
        intent ?: return null
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim()?.takeIf { it.isNotEmpty() }
        val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT)?.trim()?.takeIf { it.isNotEmpty() }
        return when (intent.action) {
            Intent.ACTION_SEND -> {
                val uri = streamExtra(intent)
                if (text == null && uri == null) null else Shared(joinSubject(subject, text), listOfNotNull(uri), subject)
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                val uris = streamListExtra(intent)
                if (uris.isEmpty() && text == null) null else Shared(joinSubject(subject, text), uris, subject)
            }
            else -> null
        }
    }

    /** Subjek (judul artikel/video) + link, tanpa dobel kalau teks sudah memuat subjek. */
    fun joinSubject(subject: String?, text: String?): String? = when {
        text == null -> subject
        subject == null || text.contains(subject) -> text
        else -> "$subject\n$text"
    }

    @Suppress("DEPRECATION")
    private fun streamExtra(i: Intent): Uri? =
        if (Build.VERSION.SDK_INT >= 33) i.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        else i.getParcelableExtra(Intent.EXTRA_STREAM)

    @Suppress("DEPRECATION")
    private fun streamListExtra(i: Intent): List<Uri> =
        (if (Build.VERSION.SDK_INT >= 33) i.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
        else i.getParcelableArrayListExtra(Intent.EXTRA_STREAM)) ?: emptyList()

    private fun copyToCache(context: Context, uri: Uri): Uri? = runCatching { copyToCacheOrThrow(context, uri) }.getOrNull()

    private fun copyToCacheOrThrow(context: Context, uri: Uri): Uri? {
        val cr = context.contentResolver
        var name = "shared"
        cr.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0)?.let { name = it }
        }
        if (!name.contains('.')) {
            val ext = android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(cr.getType(uri))
            if (ext != null) name = "$name.$ext"
        }
        // subfolder unik per share → nama file asli tetap utuh (yang tampil di chat & di Mac)
        val dir = java.io.File(context.cacheDir, "share/${System.currentTimeMillis()}").apply { mkdirs() }
        val f = java.io.File(dir, name.replace(Regex("[/\\\\:*?\"<>|]"), "_"))
        cr.openInputStream(uri)?.use { input -> f.outputStream().use { input.copyTo(it) } } ?: return null
        return androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.files", f)
    }
}
