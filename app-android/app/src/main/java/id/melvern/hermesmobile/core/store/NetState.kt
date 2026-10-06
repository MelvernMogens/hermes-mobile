package id.melvern.hermesmobile.core.store

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/** v27: apakah HP sendiri punya internet (beda dari "Mac tidak terjangkau"). */
object NetState {
    fun hasInternet(c: Context): Boolean {
        val cm = c.getSystemService(ConnectivityManager::class.java) ?: return true
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    @Composable
    fun rememberHasInternet(): Boolean {
        val ctx = LocalContext.current
        var ok by remember { mutableStateOf(hasInternet(ctx)) }
        DisposableEffect(ctx) {
            val cm = ctx.getSystemService(ConnectivityManager::class.java)
            val cb = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) { ok = hasInternet(ctx) }
                override fun onLost(network: Network) { ok = hasInternet(ctx) }
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) { ok = hasInternet(ctx) }
            }
            runCatching { cm?.registerDefaultNetworkCallback(cb) }
            onDispose { runCatching { cm?.unregisterNetworkCallback(cb) } }
        }
        return ok
    }
}
