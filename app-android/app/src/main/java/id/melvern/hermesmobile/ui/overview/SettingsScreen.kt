package id.melvern.hermesmobile.ui.overview

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import id.melvern.hermesmobile.HermesApp
import id.melvern.hermesmobile.core.store.AppPrefs
import id.melvern.hermesmobile.core.store.SettingsStore
import id.melvern.hermesmobile.ui.components.Hairline
import id.melvern.hermesmobile.ui.theme.Dim
import id.melvern.hermesmobile.ui.theme.Ink
import id.melvern.hermesmobile.ui.theme.Radius
import id.melvern.hermesmobile.ui.theme.Type
import id.melvern.hermesmobile.ui.theme.pressClickable
import kotlinx.coroutines.launch

/** Tab Settings: keyboard, tampilan, chat, koneksi, tentang. Semua lokal per HP. */
@Composable
fun SettingsScreen(app: HermesApp, onSignOut: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    fun persist() = scope.launch { AppPrefs.save(ctx) }
    var showHidden by remember { mutableStateOf<Boolean?>(null) }
    androidx.compose.runtime.LaunchedEffect(Unit) { showHidden = SettingsStore.loadShowHidden(ctx) }
    var confirmSignOut by remember { mutableStateOf(false) }
    val conn = app.connection

    Column(Modifier.fillMaxSize().background(Ink.Bg).statusBarsPadding()) {
        Text("Settings", style = Type.Display,
            modifier = Modifier.padding(horizontal = Dim.ScreenH).padding(top = 6.dp, bottom = 4.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            Column(
                Modifier.weight(1f, fill = false).widthIn(max = 640.dp).verticalScroll(rememberScrollState())
                    .padding(horizontal = Dim.GroupInset).padding(top = 8.dp, bottom = 48.dp),
            ) {
                Section("Keyboard") {
                    Segmented("Enter key on phone keyboard", AppPrefs.EnterKey.entries, AppPrefs.softEnter, { it.label }) {
                        AppPrefs.softEnter = it; persist()
                    }
                    Hint(if (AppPrefs.softEnter == AppPrefs.EnterKey.NEWLINE) "Enter adds a new line. Send with the arrow button."
                         else "Enter sends. You can't type multi-line messages on the phone keyboard.")
                    Divider()
                    Segmented("Enter key on tablet keyboard", listOf(false, true), AppPrefs.hardwareEnterSends,
                        { if (it) "Send" else "New line" }) { AppPrefs.hardwareEnterSends = it; persist() }
                    Hint(if (AppPrefs.hardwareEnterSends) "Enter sends. Shift + Enter adds a new line." else "Enter adds a new line.")
                }
                Section("Appearance") {
                    Segmented("Theme", AppPrefs.Theme.entries, AppPrefs.theme, { it.label }) { AppPrefs.theme = it; persist() }
                    Divider()
                    Segmented("Text size", AppPrefs.TextSize.entries, AppPrefs.textSize, { it.label }) { AppPrefs.textSize = it; persist() }
                }
                Section("Chats") {
                    Toggle("Autoplay videos", "Play videos in chat as soon as they load", AppPrefs.autoplayVideo) {
                        AppPrefs.autoplayVideo = it; persist()
                    }
                    Divider()
                    showHidden?.let { cur ->
                        Toggle("Show hidden chats", "Bot chats and chats you've hidden", cur) {
                            showHidden = it; scope.launch { SettingsStore.saveShowHidden(ctx, it) }
                        }
                    }
                    Divider()
                    Link("Notifications", "System notification settings") {
                        runCatching {
                            ctx.startActivity(
                                android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                    .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        }
                    }
                }
                Section("Connection") {
                    Value("Server", conn?.baseUrl?.removePrefix("https://")?.removePrefix("http://") ?: "—")
                    Divider()
                    Value("Signed in as", conn?.username ?: "—")
                    Divider()
                    if (!confirmSignOut) {
                        Link("Sign out", null, danger = true) { confirmSignOut = true }
                    } else {
                        Link("Tap again to sign out", "You'll need the server password to reconnect", danger = true) { onSignOut() }
                    }
                }
                Section("About") {
                    Value("Version", runCatching {
                        ctx.packageManager.getPackageInfo(ctx.packageName, 0).let { "${it.versionName} · ${it.lastUpdateTime.let { t -> java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale.US).format(java.util.Date(t)) }}" }
                    }.getOrDefault("—"))
                    Divider()
                    Link("Get latest version", "Opens the download on GitHub") {
                        runCatching {
                            ctx.startActivity(
                                android.content.Intent(android.content.Intent.ACTION_VIEW,
                                    android.net.Uri.parse("https://github.com/MelvernMogens/hermes-mobile/releases/latest"))
                                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(56.dp))
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    // v28: settings panel = a rack unit (bezel + screw mark + sentence-case label)
    Spacer(Modifier.height(12.dp))
    Column(
        Modifier.fillMaxWidth().clip(Radius.Rack).background(Ink.Surface1)
            .border(1.dp, Ink.Bezel, Radius.Rack),
    ) {
        Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = Type.RackLabel.copy(color = Ink.Text2))
        }
        content()
    }
}

@Composable
private fun Divider() = Hairline(Modifier.padding(horizontal = 16.dp))

@Composable
private fun Hint(text: String) {
    Text(text, style = Type.Caption.copy(color = Ink.Text3),
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp))
}

@Composable
private fun Toggle(title: String, sub: String?, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).pressClickable { onChange(!value) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = Type.Callout)
            sub?.let { Text(it, style = Type.Caption.copy(color = Ink.Text3)) }
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = value, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Ink.Bg, checkedTrackColor = Ink.Text, checkedBorderColor = Ink.Text,
                uncheckedThumbColor = Ink.Text2, uncheckedTrackColor = Ink.LampOff, uncheckedBorderColor = Ink.KeyBezel,
            ),
        )
    }
}

@Composable
private fun <T> Segmented(title: String, options: List<T>, selected: T, label: (T) -> String, onPick: (T) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(title, style = Type.Callout)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
            options.forEach { o ->
                id.melvern.hermesmobile.ui.components.KeyCap(label(o), selected = o == selected, modifier = Modifier.weight(1f)) { onPick(o) }
            }
        }
    }
}

@Composable
private fun Value(title: String, value: String) {
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = Type.Callout, modifier = Modifier.weight(1f))
        Text(value, style = Type.Callout.copy(color = Ink.Text3), maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 220.dp))
    }
}

@Composable
private fun Link(title: String, sub: String?, danger: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).pressClickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = Type.Callout.copy(color = if (danger) Ink.Danger else Ink.Text))
            sub?.let { Text(it, style = Type.Caption.copy(color = Ink.Text3)) }
        }
        if (!danger) androidx.compose.material3.Icon(
            Icons.AutoMirrored.Rounded.KeyboardArrowRight, null,
            tint = Ink.Text3, modifier = Modifier.padding(start = 8.dp),
        )
    }
}
