package id.melvern.hermesmobile.ui.connect

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import id.melvern.hermesmobile.HermesApp
import id.melvern.hermesmobile.R
import id.melvern.hermesmobile.core.auth.AuthException
import id.melvern.hermesmobile.core.rpc.ConnState
import id.melvern.hermesmobile.core.store.ConnectionSettings
import id.melvern.hermesmobile.core.store.SettingsStore
import id.melvern.hermesmobile.ui.theme.Dim
import id.melvern.hermesmobile.ui.theme.Ink
import id.melvern.hermesmobile.ui.theme.Radius
import id.melvern.hermesmobile.ui.theme.Type
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import id.melvern.hermesmobile.core.repo.GatewayDiscovery
import kotlinx.coroutines.launch

/**
 * M8 Connect: terpusat optik (sedikit di atas tengah), max width 420.
 * Satu-satunya tombol solid di app = "Connect" (putih, teks hitam).
 * Error jaringan dipetakan ke pesan manusiawi lewat [ConnectErrors].
 */
@Composable
fun ConnectScreen(app: HermesApp, onConnected: () -> Unit) {
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    var url by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("melvern") }
    var pass by remember { mutableStateOf("") }
    var showPass by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val canSubmit = !testing && url.isNotBlank() && user.isNotBlank() && pass.isNotBlank()

    fun connect() {
        if (!canSubmit) return
        focus.clearFocus()
        testing = true; error = null
        scope.launch {
            try {
                val settings = ConnectionSettings(baseUrl = ConnectErrors.normalizeUrl(url), username = user, password = pass)
                if (!settings.configured) throw IllegalStateException(ConnectErrors.MISSING_FIELDS)
                // Diagnosa cepat: host gak ke-resolve → gagal instan (bukan nunggu WS 25s).
                val host = runCatching { java.net.URI(settings.baseUrl).host }.getOrNull()
                    ?: throw java.net.UnknownHostException(settings.baseUrl)
                withContext(Dispatchers.IO) { java.net.InetAddress.getByName(host) }
                // buildClient menjalankan discovery (runBlocking) — jangan di main thread.
                val client = withContext(Dispatchers.IO) { app.buildClient(settings) }
                // Probe login dulu supaya 401/HTTP code kebaca (WS loop cuma bilang
                // "not open"). M8: juga di mode desktop — proxy inject token desktop
                // tanpa cek kredensial, jadi tanpa probe password salah tetap "connected".
                // Mode desktop: hanya 401 yang memblok (mobile-serve boleh mati).
                runCatching { app.auth?.loginTicket(settings.username, settings.password) }
                    .exceptionOrNull()?.let { e ->
                        val desktop = app.gatewayMode.value is GatewayDiscovery.Mode.Desktop
                        if (!desktop || ConnectErrors.message(e) == ConnectErrors.BAD_CREDENTIALS) {
                            client.stop(); throw e
                        }
                    }
                client.start()
                var waited = 0
                while (client.state.value != ConnState.OPEN && waited < 25000) { delay(250); waited += 250 }
                if (client.state.value == ConnState.OPEN) {
                    SettingsStore.save(app, settings)
                    app.settings.value = settings
                    onConnected()
                } else {
                    client.stop()
                    throw AuthException("WebSocket not open")
                }
            } catch (e: Throwable) {
                error = if (e is IllegalStateException && e.message == ConnectErrors.MISSING_FIELDS) e.message
                else ConnectErrors.message(e)
            } finally { testing = false }
        }
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Ink.Bg)
            .systemBarsPadding()
            .imePadding(),
    ) {
        val minH = maxHeight
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = minH)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // optik: ruang atas 0.8x ruang bawah → blok duduk sedikit di atas tengah
            Spacer(Modifier.weight(0.8f))
            Column(
                Modifier.widthIn(max = Dim.ConnectMaxW).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Image(
                    painterResource(R.drawable.ic_launcher_fg),
                    contentDescription = null,
                    modifier = Modifier.size(Dim.LogoConnect).clip(Radius.Full),
                )
                Spacer(Modifier.height(24.dp))
                Text("Connect to your Mac", style = Type.Display, textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Uses your Tailscale network. Same sessions as the desktop app.",
                    style = Type.Callout.copy(color = Ink.Text2),
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(32.dp))

                QuietField(
                    value = url, onValueChange = { url = it.trim(); error = null },
                    label = "Server address",
                    placeholder = "your-mac.tailnet.ts.net",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                    onImeAction = { focus.moveFocus(FocusDirection.Down) },
                )
                Spacer(Modifier.height(12.dp))
                QuietField(
                    value = user, onValueChange = { user = it.trim(); error = null },
                    label = "Username",
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    onImeAction = { focus.moveFocus(FocusDirection.Down) },
                )
                Spacer(Modifier.height(12.dp))
                QuietField(
                    value = pass, onValueChange = { pass = it; error = null },
                    label = "Password",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Go),
                    onImeAction = { connect() },
                    visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
                    trailing = {
                        IconButton(onClick = { showPass = !showPass }) {
                            Icon(
                                if (showPass) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                contentDescription = if (showPass) "Hide password" else "Show password",
                                tint = Ink.Text2,
                                modifier = Modifier.size(Dim.Icon),
                            )
                        }
                    },
                )
                Spacer(Modifier.height(24.dp))

                Button(
                    onClick = { connect() },
                    // saat connecting tetap putih (spinner hitam di atas putih, brief D);
                    // klik ganda dicegah guard `canSubmit` di connect().
                    enabled = canSubmit || testing,
                    modifier = Modifier.fillMaxWidth().height(Dim.ButtonH),
                    shape = Radius.Field,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Ink.Accent, contentColor = Ink.OnAccent,
                        disabledContainerColor = Ink.Surface2, disabledContentColor = Ink.Text3,
                    ),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                ) {
                    if (testing) {
                        CircularProgressIndicator(Modifier.size(18.dp), color = Ink.OnAccent, strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("Connecting…", style = Type.Button)
                    } else {
                        // warna teks ikut contentColor (disabled → text3)
                        Text("Connect", style = Type.Button.copy(color = androidx.compose.ui.graphics.Color.Unspecified))
                    }
                }
                error?.let {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        it,
                        style = Type.Callout.copy(color = Ink.Danger),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            Spacer(Modifier.weight(1f))
        }
    }
}

/**
 * OutlinedTextField gaya M8: label mengambang, radius 12, border idle
 * hairline-terang 1dp, fokus putih 1.5dp (Container M3 dengan ketebalan custom).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuietField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    onImeAction: () -> Unit = {},
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailing: (@Composable () -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val colors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = Ink.Text,
        unfocusedTextColor = Ink.Text,
        focusedContainerColor = Ink.Transparent,
        unfocusedContainerColor = Ink.Transparent,
        focusedBorderColor = Ink.Text,
        unfocusedBorderColor = Ink.HairlineStrong,
        focusedLabelColor = Ink.Text,
        unfocusedLabelColor = Ink.Text2,
        focusedPlaceholderColor = Ink.Text3,
        unfocusedPlaceholderColor = Ink.Text3,
        cursorColor = Ink.Text,
        focusedTrailingIconColor = Ink.Text2,
        unfocusedTrailingIconColor = Ink.Text2,
    )
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = Type.Body,
        cursorBrush = SolidColor(Ink.Text),
        keyboardOptions = keyboardOptions,
        keyboardActions = KeyboardActions(onAny = { onImeAction() }),
        visualTransformation = visualTransformation,
        interactionSource = interaction,
        modifier = Modifier.fillMaxWidth().heightIn(min = Dim.FieldH),
    ) { inner ->
        OutlinedTextFieldDefaults.DecorationBox(
            value = value,
            innerTextField = inner,
            enabled = true,
            singleLine = true,
            visualTransformation = visualTransformation,
            interactionSource = interaction,
            label = { Text(label) },
            placeholder = placeholder?.let { { Text(it, style = Type.Body) } },
            trailingIcon = trailing,
            colors = colors,
            container = {
                OutlinedTextFieldDefaults.Container(
                    enabled = true,
                    isError = false,
                    interactionSource = interaction,
                    colors = colors,
                    shape = Radius.Field,
                    focusedBorderThickness = 1.5.dp,
                    unfocusedBorderThickness = 1.dp,
                )
            },
        )
    }
}
