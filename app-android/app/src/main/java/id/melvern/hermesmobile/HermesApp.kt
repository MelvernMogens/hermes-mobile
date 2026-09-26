package id.melvern.hermesmobile

import android.app.Application
import id.melvern.hermesmobile.core.auth.DashboardAuth
import id.melvern.hermesmobile.core.rpc.GatewayClient
import id.melvern.hermesmobile.core.store.ConnectionSettings
import id.melvern.hermesmobile.core.store.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/** Holder process-wide: satu koneksi gateway, dipakai semua screen. */
class HermesApp : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** null = belum selesai load dari DataStore; setelah itu selalu ada nilai. */
    val settings = MutableStateFlow<ConnectionSettings?>(null)

    @Volatile var client: GatewayClient? = null
        private set
    @Volatile var auth: DashboardAuth? = null
        private set

    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            val loaded = SettingsStore.load(this@HermesApp)
            settings.value = loaded
            if (loaded.configured) {
                val c = buildClient(loaded)
                c.start()
            }
        }
    }

    fun buildClient(settings: ConnectionSettings): GatewayClient {
        client?.stop()
        val base = settings.baseUrl.trim().trimEnd('/')
        val auth = DashboardAuth(base)
        this.auth = auth
        val wsBase = if (base.startsWith("https://")) "wss://${base.removePrefix("https://")}/api/ws"
                     else "ws://${base.removePrefix("http://")}/api/ws"
        val c = GatewayClient(
            wsBase,
            ticketSupplier = { auth.loginTicket(settings.username, settings.password) },
            scope = appScope,
        )
        client = c
        return c
    }
}
