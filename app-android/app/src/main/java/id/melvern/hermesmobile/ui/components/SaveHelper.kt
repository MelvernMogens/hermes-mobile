package id.melvern.hermesmobile.ui.components

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

/**
 * M10: simpan file hasil unduhan ke penyimpanan bersama via MediaStore
 * (tanpa permission di API 29+; app < 29 tulis ke public Downloads lewat File).
 * Return path tampilan, atau null kalau gagal.
 */
object SaveHelper {

    fun save(context: Context, bytes: ByteArray, mime: String, displayName: String): String? = try {
        val safe = displayName.substringAfterLast('/').takeIf { it.isNotBlank() } ?: "hermes-file"
        if (Build.VERSION.SDK_INT >= 29) {
            val isImage = mime.startsWith("image/")
            val isVideo = mime.startsWith("video/")
            val collection = when {
                isImage -> MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                isVideo -> MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                else -> MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            }
            val subdir = when {
                isImage -> "Pictures/Hermes"
                isVideo -> "Movies/Hermes"
                else -> "Download/Hermes"
            }
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, safe)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, subdir)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(collection, values) ?: return null
            resolver.openOutputStream(uri)?.use { it.write(bytes) } ?: run {
                resolver.delete(uri, null, null); return null
            }
            "$subdir/$safe"
        } else {
            @Suppress("DEPRECATION")
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val f = File(dir, "Hermes").apply { mkdirs() }
            val out = File(f, safe)
            out.writeBytes(bytes)
            "Download/Hermes/$safe"
        }
    } catch (_: Throwable) {
        null
    }
}
