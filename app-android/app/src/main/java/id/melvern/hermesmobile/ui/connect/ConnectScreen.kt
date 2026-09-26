package id.melvern.hermesmobile.ui.connect

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.melvern.hermesmobile.HermesApp
import id.melvern.hermesmobile.core.auth.AuthException
import id.melvern.hermesmobile.core.rpc.ConnState
import id.melvern.hermesmobile.core.store.ConnectionSettings
import id.melvern.hermesmobile.core.store.SettingsStore
import id.melvern.hermesmobile.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ConnectScreen(app: HermesApp, onConnected: () -> Unit) {
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("melvern") }
    var pass by remember { mutableStateOf("") }
    var testing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Spacer(Modifier.height(72.dp))
        Text("Hermes", style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
        Text(
            "Kontrol penuh agen Hermes di Mac-mu, dari HP.\nSama persis dengan desktop — session, chat, tool, semuanya.",
            style = MaterialTheme.typography.bodyMedium, color = TextSecondary,
        )
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = url, onValueChange = { url = it.trim() },
            label = { Text("URL (https://…ts.net)") },
            modifier = Modifier.fillMaxWidth(), singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        )
        OutlinedTextField(
            value = user, onValueChange = { user = it.trim() },
            label = { Text("Username") },
            modifier = Modifier.fillMaxWidth(), singleLine = true,
        )
        OutlinedTextField(
            value = pass, onValueChange = { pass = it },
            label = { Text("Password") },
            modifier = Modifier.fillMaxWidth(), singleLine = true,
        )

        if (error != null) Text(error!!, color = Danger, style = MaterialTheme.typography.bodySmall)

        Button(
            onClick = {
                testing = true; error = null
                scope.launch {
                    try {
                        val settings = ConnectionSettings(baseUrl = url, username = user, password = pass)
                        if (!settings.configured) throw Exception("URL, username, dan password wajib diisi")
                        val client = app.buildClient(settings)
                        client.start()
                        var waited = 0
                        while (client.state.value != ConnState.OPEN && waited < 25000) { delay(250); waited += 250 }
                        when (client.state.value) {
                            ConnState.OPEN -> {
                                SettingsStore.save(app, settings)
                                app.settings.value = settings
                                onConnected()
                            }
                            else -> throw AuthException("WebSocket tidak terbuka — cek URL/password atau jalankan server/install.sh di Mac")
                        }
                    } catch (e: Throwable) {
                        error = e.message ?: "Gagal terhubung"
                    } finally { testing = false }
                }
            },
            enabled = !testing && url.isNotBlank() && user.isNotBlank() && pass.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            if (testing) CircularProgressIndicator(Modifier.size(20.dp), color = Bg, strokeWidth = 2.dp)
            else Text("Hubungkan")
        }
        Text(
            "Sekali setup di Mac: bash server/install.sh\n(serve :8788 + Tailscale + password).",
            style = MaterialTheme.typography.bodySmall, color = TextTertiary, lineHeight = 18.sp,
        )
        Spacer(Modifier.height(32.dp))
    }
}
