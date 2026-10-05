package id.melvern.hermesmobile

import android.app.Activity
import android.app.Application
import android.os.Bundle
import id.melvern.hermesmobile.core.auth.DashboardAuth
import id.melvern.hermesmobile.core.notify.AppNotifier
import id.melvern.hermesmobile.core.notify.startNotifPoller
import id.melvern.hermesmobile.core.repo.GatewayDiscovery
import id.melvern.hermesmobile.core.repo.BotFleet
import id.melvern.hermesmobile.core.rpc.GatewayClient
import id.melvern.hermesmobile.core.store.ConnectionSettings
import id.melvern.hermesmobile.core.store.SettingsStore
import id.melvern.hermesmobile.ui.layout.WinSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/** Holder process-wide: satu koneksi gateway, dipakai semua screen. */
class HermesApp : Application(), Application.ActivityLifecycleCallbacks {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** null = belum selesai load dari DataStore; setelah itu selalu ada nilai. */
    val settings = MutableStateFlow<ConnectionSettings?>(null)

    /** M4: profile aktif — semua RPC session kirim params.profile=<ini>. */
    val profile = MutableStateFlow("default")

    /** M15: window size class aktif (diisi MainActivity dari calculateWindowSizeClass). */
    val windowSize = MutableStateFlow(WinSize.Compact)

    @Volatile var client: GatewayClient? = null
        private set
    @Volatile var auth: DashboardAuth? = null
        private set

    /** M5: settings koneksi aktif — dipakai MediaRepo (base URL + kredensial /api/media). */
    @Volatile var connection: ConnectionSettings? = null
        set(value) {
            field = value
            id.melvern.hermesmobile.ui.components.MediaFetchSave.connection = value
        }

    /** M6: mode discovery aktif — desktop (multi-surface) atau mobile (fallback 8788). */
    val gatewayMode = MutableStateFlow<GatewayDiscovery.Mode>(GatewayDiscovery.Mode.Mobile)

    // ---------- M14: notifikasi background ----------

    /** M14: >0 = minimal satu activity STARTED (app di foreground). */
    @Volatile var startedActivities = 0
        private set
    val isForeground: Boolean get() = startedActivities > 0

    /** M14: stored id chat yang sedang dibuka di layar (null = gak di chat). */
    @Volatile var openChatStoredId: String? = null

    /**
     * M14: permintaan buka chat dari tap notif. distinct: null-lagi supaya event
     * yang sama gak dinavigasi dua kali (Conflated = nilai terakhir menang).
     */
    private val _openChatRequests = kotlinx.coroutines.flow.MutableSharedFlow<String>(
        extraBufferCapacity = 4,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST,
    )
    val openChatRequests: kotlinx.coroutines.flow.SharedFlow<String> = _openChatRequests

    /** Share masuk saat app sudah jalan → kembali ke tab Chats untuk memilih tujuan. */
    private val _homeRequests = kotlinx.coroutines.flow.MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val homeRequests: kotlinx.coroutines.flow.SharedFlow<Unit> = _homeRequests
    fun offerHome() { _homeRequests.tryEmit(Unit) }

    fun offerOpenChat(storedId: String?) {
        // onCreate: extra dibaca langsung di AppNav; onNewIntent: lewat sini.
        if (!storedId.isNullOrBlank()) _openChatRequests.tryEmit(storedId)
    }

    /** M14: pengecer notifikasi (channel + build + post) — di-init onCreate. */
    lateinit var notifier: AppNotifier
        private set

    override fun onCreate() {
        super.onCreate()
        id.melvern.hermesmobile.ui.components.RelTime.use24h = android.text.format.DateFormat.is24HourFormat(this)
        notifier = AppNotifier(this)
        registerActivityLifecycleCallbacks(this)
        appScope.launch {
            val loaded = SettingsStore.load(this@HermesApp)
            settings.value = loaded
            profile.value = loaded.profile.ifBlank { "default" }
            if (loaded.configured) {
                connection = loaded
                val c = buildClient(loaded)
                c.start()
            }
        }
    }

    // ---------- ActivityLifecycleCallbacks: started-count sederhana ----------

    override fun onActivityStarted(activity: Activity) {
        startedActivities += 1
        // M14: kembali ke foreground → heartbeat normal 15s.
        if (startedActivities == 1) client?.heartbeatIntervalMs = { GatewayClient.HEARTBEAT_INTERVAL_MS }
    }

    override fun onActivityStopped(activity: Activity) {
        if (startedActivities > 0) startedActivities -= 1
        // M14: semua activity berhenti → background → heartbeat hemat 25s.
        if (startedActivities == 0) client?.heartbeatIntervalMs = { GatewayClient.HEARTBEAT_BACKGROUND_MS }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityResumed(activity: Activity) {}
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}

    /**
     * M4: ganti profile aktif — persist ke DataStore. Koneksi WS gak perlu
     * di-rebuild (profile adalah param per-RPC, bukan per-socket — sesuai
     * schema SessionListParams/PromptSubmitParams: field `profile` nullable).
     */
    fun setProfile(name: String) {
        profile.value = name
        appScope.launch {
            val cur = settings.value ?: return@launch
            SettingsStore.save(this@HermesApp, cur.copy(profile = name))
        }
    }

    fun buildClient(settings: ConnectionSettings): GatewayClient {
        client?.stop()
        // M14 fix (review H1): poller lama wajib mati sebelum spin yang baru —
        // retry connect tanpa ini bikin N poller nge-poll client sama (RPC + notif dobel).
        notifPollerJob?.cancel()
        connection = settings
        val base = settings.baseUrl.trim().trimEnd('/')
        val auth = DashboardAuth(base)
        this.auth = auth
        val wsBase = if (base.startsWith("https://")) "wss://${base.removePrefix("https://")}/api/ws"
                     else "ws://${base.removePrefix("http://")}/api/ws"
        // M6: discovery dulu — desktop mode = gateway yang sama dengan desktop app
        // (multi-surface, tanpa 4090). Mobile mode = tiket seperti M1.
        val mode = kotlinx.coroutines.runBlocking { GatewayDiscovery.resolve(base) }
        gatewayMode.value = mode
        val c = GatewayClient(
            wsBase,
            ticketSupplier = { auth.loginTicket(settings.username, settings.password) },
            scope = appScope,
            desktopMode = mode is GatewayDiscovery.Mode.Desktop,
        )
        client = c
        // M14: poller notifikasi (active_list diff) + foreground service + heartbeat hemat.
        notifPollerJob = c.startNotifPoller(this, appScope)
        // M18: badge fleet di tab bar hidup sejak koneksi pertama — bukan cuma
        // setelah tab Overview dibuka. profileRows di-cache proses-wide; refetch
        // ringan sekali per rebuild client (reconnect), bukan per poll tick.
        BotFleet.appScope = appScope
        BotFleet.ensureProfiles(c)
        HermesLiveService.start(this)
        return c
    }

    /** M14: job poller aktif — di-cancel saat buildClient baru / disconnect (review H1). */
    @Volatile private var notifPollerJob: kotlinx.coroutines.Job? = null

    /** M14: putus total (logout) — service + koneksi mati rapi, shade dibersihkan. */
    fun disconnect() {
        notifPollerJob?.cancel()
        HermesLiveService.stop(this)
        notifier.cancelSessionNotifications()
        client?.stop()
        client = null
        // M16: connection global MediaFetchSave ikut dibersihkan saat logout.
        id.melvern.hermesmobile.ui.components.MediaFetchSave.connection = null
    }
}
