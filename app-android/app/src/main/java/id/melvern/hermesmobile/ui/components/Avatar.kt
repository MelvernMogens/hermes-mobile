package id.melvern.hermesmobile.ui.components

import android.graphics.BitmapFactory
import androidx.collection.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.melvern.hermesmobile.HermesApp
import id.melvern.hermesmobile.core.repo.MetaRepo
import id.melvern.hermesmobile.core.rpc.ConnState
import id.melvern.hermesmobile.ui.theme.Ink
import id.melvern.hermesmobile.ui.theme.Type
import id.melvern.hermesmobile.ui.theme.hairline
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

    /** profile → warna latar sprite (sudut kiri atas) untuk avatar pixel-art. */
    private val pixel = java.util.concurrent.ConcurrentHashMap<String, Int>()
    fun markPixel(name: String, bg: Int) { pixel[name] = bg }
    fun isPixel(name: String): Boolean = pixel.containsKey(name)
    fun pixelBg(name: String): Int? = pixel[name]

    /** When each avatar was fetched. An avatar changed on the Mac (or a bad one that got
     *  fixed there) must reach the phone without killing the app: entries older than
     *  [MAX_AGE_MS] are re-fetched on the next compose, and [expireAll] runs on app resume. */
    private val fetchedAt = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private const val MAX_AGE_MS = 10 * 60_000L

    fun lockFor(name: String): Mutex = locks.computeIfAbsent(name) { Mutex() }
    fun get(name: String): ImageBitmap? = cache.get(name)
    fun fresh(name: String): ImageBitmap? =
        cache.get(name)?.takeIf { System.currentTimeMillis() - (fetchedAt[name] ?: 0L) < MAX_AGE_MS }
    fun put(name: String, bmp: ImageBitmap) { cache.put(name, bmp); fetchedAt[name] = System.currentTimeMillis() }
    fun expireAll() { fetchedAt.clear() }
}

/**
 * M5c: avatar profil — lingkaran foto per-profile (profiles.get_asset).
 * Load async (RPC off main thread, decode di Dispatchers.Default dengan
 * downsampling), hasil di-cache process-wide. Placeholder kalau found=false /
 * gagal permanen: lingkaran Surface2 + huruf awal nama profil.
 */
@Composable
fun ProfileAvatar(app: HermesApp, profile: String, size: Dp, modifier: Modifier = Modifier) {
    var bmp by remember(profile) { mutableStateOf(AvatarCache.get(profile)) }
    var failed by remember(profile) { mutableStateOf(false) }

    val resumed = androidx.lifecycle.compose.LocalLifecycleOwner.current
    var resumeTick by remember { mutableStateOf(0) }
    androidx.compose.runtime.DisposableEffect(resumed) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e ->
            if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) resumeTick++
        }
        resumed.lifecycle.addObserver(obs)
        onDispose { resumed.lifecycle.removeObserver(obs) }
    }
    LaunchedEffect(profile, resumeTick) {
        if (resumeTick > 1) AvatarCache.expireAll()   // back from background → re-check the Mac
        AvatarCache.fresh(profile)?.let { bmp = it; return@LaunchedEffect }
        // stale or missing: keep showing what we have while re-fetching
        failed = false
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
                AvatarCache.fresh(profile)?.let { bmp = it; return@withLock }
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
                    ?: run { if (bmp == null) failed = true; return@withLock } // found=false → inisial, bukan error
                // decode di background + downsampling: bounds dulu, inSampleSize
                // ke ~256px — avatar 1024² full RGBA x 12 bakal makan puluhan MB.
                val decoded = withContext(Dispatchers.Default) {
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                    var sample = 1
                    var w = bounds.outWidth; var h = bounds.outHeight
                    while (w / 2 >= 700 && h / 2 >= 700) { sample *= 2; w /= 2; h /= 2 }
                    BitmapFactory.decodeByteArray(
                        bytes, 0, bytes.size,
                        BitmapFactory.Options().apply { inSampleSize = sample },
                    )
                }
                if (decoded == null) { failed = true; return@withLock }
                // M8: semua avatar = lingkaran penuh. Avatar dengan margin transparan
                // (mis. squircle app-icon) di-trim ke bbox piksel opak dulu, supaya
                // clip lingkaran terisi penuh — bukan squircle kecil di dalam lingkaran.
                // Pixel art (blok seragam) → rebuild di resolusi grid aslinya, lalu
                // di-upscale nearest saat render: blok tajam & rata di ukuran apa pun.
                val (img, pixel) = withContext(Dispatchers.Default) {
                    val trimmed = trimTransparent(decoded)
                    val grid = pixelGridSize(trimmed)
                    if (grid != null) pixelReduce(trimmed, grid) to true else trimmed to false
                }
                if (pixel) AvatarCache.markPixel(profile, img.getPixel(0, 0))
                val ib = img.asImageBitmap()
                AvatarCache.put(profile, ib)
                bmp = ib
            }
        } catch (e: CancellationException) {
            throw e // effect dibatalkan (recompose/leave) — bukan kegagalan load
        } catch (_: Throwable) {
            if (bmp == null) failed = true
        }
    }

    // Bot = squircle (gaya app icon): pixel-art full-bleed gak kepotong lingkaran
    // (topi/props di tepi), dan beda bentuk dari avatar chat (lingkaran).
    val shape = BotShape
    val spriteBg = if (bmp != null) AvatarCache.pixelBg(profile) else null
    Box(
        modifier.size(size).clip(shape)
            .background(spriteBg?.let { androidx.compose.ui.graphics.Color(it) } ?: Ink.Surface3),
        contentAlignment = Alignment.Center,
    ) {
        val current = bmp
        if (current != null) {
            Image(
                bitmap = current,
                contentDescription = "avatar $profile",
                // pixel art: inset 9% di atas warna latarnya sendiri → topi/props gak kepotong sudut
                modifier = if (spriteBg != null) Modifier.fillMaxSize().padding(size * 0.09f) else Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                // pixel art: nearest-neighbour dari grid asli → tajam; foto biasa: halus
                filterQuality = if (AvatarCache.isPixel(profile)) FilterQuality.None else FilterQuality.Medium,
            )
        } else {
            Text(
                Pretty.profile(profile).take(1),
                style = Type.Title.copy(fontSize = (size.value * 0.42f).sp, lineHeight = (size.value * 0.42f).sp),
                color = if (failed) Ink.Text2 else Ink.Text3,
            )
        }
        // garis dalam 0.5dp — memisahkan avatar gelap dari kanvas hitam (efek "kaca")
        Box(Modifier.matchParentSize().border(hairline(), Ink.Text.copy(alpha = 0.08f), shape))
    }
}

/** Squircle avatar bot — 30% radius (mendekati superellipse app icon). */
val BotShape = RoundedCornerShape(percent = 30)

/**
 * Avatar chat: monogram huruf pertama judul di atas tint deterministik (hash
 * judul) — tiap chat punya identitas sendiri, tanpa aset. Lingkaran.
 */
@Composable
fun MonogramAvatar(key: String, label: String, size: Dp, modifier: Modifier = Modifier, groupColor: Int? = null) {
    // v28 Control Room: a channel badge — two-letter source ID in mono on a squared tile
    // (like a router source label). Group chats take the group colour (the one colour
    // exception); everything else stays neutral tonal grey.
    val (bg, ink) = remember(key, groupColor) {
        groupColor?.let { id.melvern.hermesmobile.core.store.ChatGroups.avatarColors(it) } ?: Ink.monoTint(key)
    }
    val code = remember(label) { channelCode(label) }
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.28f))
            .background(bg)
            .border(1.dp, ink.copy(alpha = 0.14f), RoundedCornerShape(size * 0.28f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            code,
            style = Type.Title.copy(
                fontSize = (size.value * (if (code.length > 1) 0.33f else 0.40f)).sp,
                lineHeight = (size.value * 0.40f).sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                letterSpacing = 0.2.sp,
            ),
            color = if (groupColor != null) ink else Ink.Text.copy(alpha = 0.82f),
        )
    }
}

/**
 * "Bot Chat" → "BC", "Blokees" → "BL", "UTS Web Review" → "UR" (first + last word).
 * Letters/digits only; falls back to "·".
 */
internal fun channelCode(label: String): String {
    val words = label.trim().split(Regex("[^\\p{L}\\p{N}]+")).filter { w -> w.any { it.isLetter() } }
    return when {
        words.size >= 2 -> "${words.first().first()}${words.last().first()}".uppercase()
        words.size == 1 -> words[0].filter { it.isLetterOrDigit() }.take(2).uppercase()
        else -> label.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "·"
    }
}

/** Crop bitmap ke bounding box piksel dengan alpha > 24; utuh kalau tidak ada margin. */
internal fun trimTransparent(src: android.graphics.Bitmap): android.graphics.Bitmap {
    if (!src.hasAlpha()) return src
    val w = src.width; val h = src.height
    val px = IntArray(w * h)
    src.getPixels(px, 0, w, 0, 0, w, h)
    var minX = w; var minY = h; var maxX = -1; var maxY = -1
    for (y in 0 until h) for (x in 0 until w) {
        if ((px[y * w + x] ushr 24) > 24) {
            if (x < minX) minX = x; if (x > maxX) maxX = x
            if (y < minY) minY = y; if (y > maxY) maxY = y
        }
    }
    if (maxX < 0) return src
    val bw = maxX - minX + 1; val bh = maxY - minY + 1
    if (bw >= w - 2 && bh >= h - 2) return src
    // kotak: sisi = max(bw,bh), dipusatkan — jaga rasio avatar
    val side = maxOf(bw, bh)
    val cx = minX + bw / 2; val cy = minY + bh / 2
    val left = (cx - side / 2).coerceIn(0, (w - side).coerceAtLeast(0))
    val top = (cy - side / 2).coerceIn(0, (h - side).coerceAtLeast(0))
    return android.graphics.Bitmap.createBitmap(src, left, top, minOf(side, w - left), minOf(side, h - top))
}


/**
 * Ukuran blok pixel-art (px) kalau bitmap tersusun dari blok persegi seragam;
 * null untuk gambar biasa. GCD panjang run warna identik di 5 baris + 5 kolom.
 */
internal fun pixelGridSize(src: android.graphics.Bitmap): Int? {
    val w = src.width; val h = src.height
    if (w < 64 || h < 64) return null
    fun gcd(a: Int, b: Int): Int { var x = a; var y = b; while (y != 0) { val t = x % y; x = y; y = t }; return x }
    var g = 0
    val rows = IntArray(w)
    for (k in 1..5) {
        val y = h * k / 6
        src.getPixels(rows, 0, w, 0, y, w, 1)
        var run = 1
        for (x in 1 until w) {
            if (rows[x] == rows[x - 1]) run++ else { g = gcd(g, run); run = 1 }
        }
        g = gcd(g, run)
    }
    val cols = IntArray(h)
    for (k in 1..5) {
        val x = w * k / 6
        src.getPixels(cols, 0, 1, x, 0, 1, h)
        var run = 1
        for (y in 1 until h) {
            if (cols[y] == cols[y - 1]) run++ else { g = gcd(g, run); run = 1 }
        }
        g = gcd(g, run)
    }
    return if (g >= 4 && w % g == 0 && h % g == 0) g else null
}

/** Ambil piksel tengah tiap blok → bitmap grid asli (mis. 672/16 = 42×42). */
internal fun pixelReduce(src: android.graphics.Bitmap, block: Int): android.graphics.Bitmap {
    val gw = src.width / block; val gh = src.height / block
    val out = IntArray(gw * gh)
    for (gy in 0 until gh) for (gx in 0 until gw) {
        out[gy * gw + gx] = src.getPixel(gx * block + block / 2, gy * block + block / 2)
    }
    return android.graphics.Bitmap.createBitmap(out, gw, gh, android.graphics.Bitmap.Config.ARGB_8888)
}
