package id.melvern.hermesmobile.ui.components

import android.graphics.BitmapFactory
import androidx.collection.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import id.melvern.hermesmobile.HermesApp
import id.melvern.hermesmobile.core.repo.MetaRepo
import id.melvern.hermesmobile.core.rpc.ConnState
import id.melvern.hermesmobile.ui.theme.F
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * M5c: cache avatar per-profile — app-level singleton, LruCache 12 entri
 * (jumlah profile terverifikasi 11; file lokal ~4KB, aman di memori).
 *
 * Known limitation (disengaja, di luar scope M5c): TIDAK ada invalidasi —
 * avatar yang diganti server-side tetap versi lama sampai proses mati.
 *
 * In-flight dedup: Mutex per profile — kalau header + 12 row sheet compose
 * bersamaan, RPC profiles.get_asset cuma jalan sekali per profile.
 */
object AvatarCache {
    private val cache = object : LruCache<String, ImageBitmap>(12) {
        override fun sizeOf(key: String, value: ImageBitmap) = 1
    }
    private val locks = java.util.concurrent.ConcurrentHashMap<String, Mutex>()

    fun lockFor(name: String): Mutex = locks.computeIfAbsent(name) { Mutex() }
    fun get(name: String): ImageBitmap? = cache.get(name)
    fun put(name: String, bmp: ImageBitmap) { cache.put(name, bmp) }
}

/**
 * M5c: avatar profil — lingkaran foto per-profile (profiles.get_asset).
 * Load async (RPC off main thread, decode di Dispatchers.Default dengan
 * downsampling), hasil di-cache process-wide. Placeholder kalau found=false /
 * gagal permanen: lingkaran Surface2 + huruf awal nama profil (Cream).
 */
@Composable
fun ProfileAvatar(app: HermesApp, profile: String, size: Dp) {
    var bmp by remember(profile) { mutableStateOf(AvatarCache.get(profile)) }
    var failed by remember(profile) { mutableStateOf(false) }

    LaunchedEffect(profile) {
        if (bmp != null || failed) return@LaunchedEffect
        try {
            // tunggu client tersedia (compose pertama sering mendahului load
            // DataStore) DAN koneksi OPEN — cap 15 detik seperti pola ChatScreen.
            // Baca app.client fresh per iterasi: buildClient bisa ganti client.
            val deadline = System.currentTimeMillis() + 15_000
            var client = app.client
            while ((client == null || client.state.value != ConnState.OPEN) &&
                System.currentTimeMillis() < deadline) {
                delay(200)
                client = app.client
            }
            if (client == null || client.state.value != ConnState.OPEN) return@LaunchedEffect

            // dedup: header + row sheet minta profile sama bersamaan → 1 RPC
            AvatarCache.lockFor(profile).withLock {
                AvatarCache.get(profile)?.let { bmp = it; return@withLock }
                // 3 attempt — flap reconnect pas startup jangan bikin placeholder permanen
                var lastErr: Throwable? = null
                var asset: MetaRepo.ProfileAsset? = null
                for (attempt in 1..3) {
                    try { asset = MetaRepo(client).profileAvatar(profile); lastErr = null; break }
                    catch (e: CancellationException) { throw e }
                    catch (e: Throwable) { lastErr = e; delay(attempt * 1000L) }
                }
                if (lastErr != null) throw lastErr
                val bytes = asset?.bytes
                    ?: run { failed = true; return@withLock } // found=false → inisial, bukan error
                // decode di background + downsampling: bounds dulu, inSampleSize
                // ke ~256px — avatar 1024² full RGBA x 12 bakal makan puluhan MB.
                val decoded = withContext(Dispatchers.Default) {
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                    var sample = 1
                    var w = bounds.outWidth; var h = bounds.outHeight
                    while (w / 2 >= 256 && h / 2 >= 256) { sample *= 2; w /= 2; h /= 2 }
                    BitmapFactory.decodeByteArray(
                        bytes, 0, bytes.size,
                        BitmapFactory.Options().apply { inSampleSize = sample },
                    )
                }
                if (decoded == null) { failed = true; return@withLock }
                val img = decoded.asImageBitmap()
                AvatarCache.put(profile, img)
                bmp = img
            }
        } catch (e: CancellationException) {
            throw e // effect dibatalkan (recompose/leave) — bukan kegagalan load
        } catch (_: Throwable) {
            failed = true
        }
    }

    Box(
        Modifier.size(size).clip(CircleShape).background(F.Surface2),
        contentAlignment = Alignment.Center,
    ) {
        val current = bmp
        if (current != null) {
            Image(
                bitmap = current,
                contentDescription = "avatar $profile",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            // M7 fix: loading & failed & svg-skip semua → inisial dim.
            // (Sebelumnya loading tampil kotak Surface2 polos — "ungu" abadi
            // kalau bytes null karena bug parse di MetaRepo.)
            Text(
                profile.trim().take(1).uppercase(),
                style = MaterialTheme.typography.labelLarge,
                color = if (failed) F.Cream else F.LavenderDim,
            )
        }
    }
}
