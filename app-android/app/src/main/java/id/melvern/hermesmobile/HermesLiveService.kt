package id.melvern.hermesmobile

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import id.melvern.hermesmobile.core.notify.AppNotifier

/**
 * M14: foreground service "HermesLiveService" — SATU-SATUNYA tugasnya menjaga proses
 * (dan CPU via partial wake lock) supaya koneksi WS di HermesApp gak dibekukan Doze
 * saat app background / layar mati. Koneksi WS TETAP di HermesApp — service ini
 * tidak pernah bikin koneksi kedua (larangan spec M14).
 *
 * - NOTIF_ONGOING: channel "connection", low priority, silent, tap buka app
 *   (syarat startForeground di API 29+ dengan foregroundServiceType=dataSync).
 * - Wake lock PARTIAL, timeout 4 jam, re-acquire per interval — bukan selamanya.
 * - Android 14+ (targetSdk 36): dataSync FGS kena limit ~6 jam / 24 jam →
 *   override onTimeout: mati rapi (koneksi WS tetap hidup tanpa wake lock; reconnect
 *   logic sudah ada). START_NOT_STICKY agar gak di-restart ke limit lagi.
 */
class HermesLiveService : Service() {

    companion object {
        /** Timeout wake lock per acquire — lalu re-acquire (hemat + lolos audit battery). */
        const val WAKE_TIMEOUT_MS = 4L * 60 * 60 * 1000 // 4 jam
        private const val NOTIF_ONGOING_ID = 1
        private const val ACTION_STOP = "id.melvern.hermesmobile.live.STOP"

        fun start(context: Context) {
            context.startForegroundService(Intent(context, HermesLiveService::class.java))
        }

        /**
         * M14 fix (review M4): JANGAN startService dari background (IllegalState 12+) —
         * stopService bebas restriksi background.
         */
        fun stop(context: Context) {
            context.stopService(Intent(context, HermesLiveService::class.java))
        }
    }

    @Volatile private var wakeLock: PowerManager.WakeLock? = null
    @Volatile private var stopped = false
    private val reaper = Thread {
        // Re-acquire loop: acquire 4 jam, lepas, acquire lagi — jaga CPU tanpa lock abadi.
        while (!stopped) {
            try { Thread.sleep(WAKE_TIMEOUT_MS - 60_000) } catch (_: InterruptedException) { return@Thread }
            if (stopped) return@Thread
            val wl = wakeLock ?: return@Thread
            runCatching {
                synchronized(this) {
                    if (stopped) return@Thread
                    if (wl.isHeld) wl.release()
                    wl.acquire(WAKE_TIMEOUT_MS)
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        startForeground(
            NOTIF_ONGOING_ID,
            (application as HermesApp).notifier.ongoingConnection(),
            if (android.os.Build.VERSION.SDK_INT >= 29)
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0,
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        acquireWake()
        // Sticky: kalau proses dibunuh saat masih harus konek, restart kosong —
        // HermesApp.onCreate me-rebuild koneksi dari DataStore.
        return START_STICKY
    }

    /**
     * M14 fix (review M2): Android 14+ dataSync limit ~6 jam — mati rapi, jangan
     * sampai ForegroundServiceDidNotStopInTimeException (crash di 15+).
     * API 34: onTimeout(int); API 35+: onTimeout(int, int).
     */
    override fun onTimeout(startId: Int) {
        stopClean("dataSync timeout (Android 14+ 6h limit)")
        stopSelf()
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        stopClean("dataSync timeout (fgsType=$fgsType)")
        stopSelf()
    }

    private fun acquireWake() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "hermes:live").apply {
            setReferenceCounted(false)
            acquire(WAKE_TIMEOUT_MS)
        }
        if (!reaper.isAlive) reaper.start()
    }

    private fun stopClean(reason: String) {
        stopped = true
        reaper.interrupt()
        synchronized(this) {
            runCatching { wakeLock?.takeIf { it.isHeld }?.release() }
            wakeLock = null
        }
        android.util.Log.i("HermesLive", "service stop: $reason")
    }

    override fun onDestroy() {
        stopClean("destroy")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
