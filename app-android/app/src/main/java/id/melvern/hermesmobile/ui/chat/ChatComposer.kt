package id.melvern.hermesmobile.ui.chat

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import id.melvern.hermesmobile.HermesApp
import id.melvern.hermesmobile.core.model.Attachment
import id.melvern.hermesmobile.core.repo.MetaRepo
import id.melvern.hermesmobile.core.repo.SessionRepo
import id.melvern.hermesmobile.ui.components.Hairline
import id.melvern.hermesmobile.ui.components.OneLine
import id.melvern.hermesmobile.ui.components.Pretty
import id.melvern.hermesmobile.ui.components.QuietSheet
import id.melvern.hermesmobile.ui.components.SheetActionRow
import id.melvern.hermesmobile.ui.components.SkeletonSessionRow
import id.melvern.hermesmobile.ui.components.shimmerAlpha
import id.melvern.hermesmobile.ui.theme.Dim
import id.melvern.hermesmobile.ui.theme.Ink
import id.melvern.hermesmobile.ui.theme.Radius
import id.melvern.hermesmobile.ui.theme.Type
import id.melvern.hermesmobile.ui.theme.hairline
import id.melvern.hermesmobile.ui.theme.pressClickable
import kotlinx.coroutines.launch

/**
 * M8 composer — menempel di atas keyboard (imePadding di pemanggil).
 * Kiri: Add (attach sheet). Tengah: field surface1 radius 18, 1–6 baris.
 * Kanan: tombol bulat 36 — kosong: outline redup; ada teks: putih + ArrowUpward
 * hitam; agent jalan & field kosong: Stop putih (interrupt).
 * M5 tetap: kirim saat running = antri/steer (server), draft tidak hilang.
 */
@Composable
fun Composer(
    value: String, onValueChange: (String) -> Unit,
    running: Boolean, connected: Boolean,
    readOnly: Boolean,
    attachment: Attachment?,
    attachThumb: ImageBitmap?,
    attaching: Boolean,
    attachError: String?,
    quote: String? = null,
    onCancelQuote: () -> Unit = {},
    onAttach: () -> Unit,
    onRemoveAttachment: () -> Unit,
    onSend: () -> Unit, onStop: () -> Unit,
    /** M15: expanded → composer max 640 center (Discord style). */
    wide: Boolean = false,
) {
    val canSend = connected && !readOnly && (value.isNotBlank() || attachment?.isImage == true)
    val showStop = running && value.isBlank() && attachment == null && !readOnly
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
    Column(Modifier.then(if (wide) Modifier.widthIn(max = Dim.ChatMaxW) else Modifier).fillMaxWidth().background(Ink.Bg)) {
        attachError?.let {
            Text(it, style = Type.Meta.copy(color = Ink.Danger), modifier = Modifier.padding(start = Dim.ScreenH, end = Dim.ScreenH, top = 8.dp))
        }
        // M11: chip reply — kutipan pesan yang dibalas, X untuk batal.
        quote?.let { q ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = Dim.ScreenH, end = Dim.ScreenH, top = 8.dp)
                    .clip(Radius.Chip)
                    .background(Ink.Surface1)
                    .padding(start = 10.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.width(3.dp).height(32.dp).clip(Radius.Full).background(Ink.Text2))
                Spacer(Modifier.width(8.dp))
                Text(
                    q, style = Type.Meta.copy(color = Ink.Text2),
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    Modifier
                        .size(32.dp)
                        .clip(Radius.Full)
                        .pressClickable(onClick = onCancelQuote),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Close, "Cancel reply", tint = Ink.Text3, modifier = Modifier.size(Dim.IconSmall))
                }
            }
        }
        if (attachment != null || attaching) {
            AttachmentChip(attachment, attachThumb, attaching, onRemoveAttachment)
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 8.dp)
                .clip(Radius.Bubble)
                .background(Ink.Surface1)
                .padding(start = 4.dp, end = 5.dp, top = 5.dp, bottom = 5.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(Radius.Full)
                    .pressClickable(enabled = !attaching && !readOnly, onClick = onAttach)
                    .semantics { contentDescription = "Attach photo or file" },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Add, null, tint = if (readOnly) Ink.Text4 else Ink.Text3, modifier = Modifier.size(22.dp))
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = !readOnly,
                textStyle = Type.Body.copy(color = if (readOnly) Ink.Text3 else Ink.Text),
                cursorBrush = SolidColor(Ink.Text),
                maxLines = 6,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    // Enter biasa = kirim, Shift+Enter = baris baru (perilaku WhatsApp).
                    imeAction = ImeAction.Send,
                    autoCorrect = true,
                ),
                keyboardActions = KeyboardActions(onSend = { if (canSend) onSend() }),
                modifier = Modifier
                    .weight(1f)
                    .onKeyEvent { e ->
                        if (e.key == Key.Enter && e.type == KeyEventType.KeyUp) {
                            val shifted = e.isShiftPressed
                            if (!shifted && canSend) { onSend(); true } else false
                        } else false
                    },
                decorationBox = { inner ->
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 36.dp)
                            .padding(start = 6.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (value.isEmpty()) Text(
                            when {
                                readOnly -> "View only"
                                running -> "Queue a follow-up…"
                                else -> "Message"
                            },
                            style = Type.Body.copy(color = Ink.Text4),
                            maxLines = 1,
                        )
                        inner()
                    }
                },
            )
            when {
                showStop -> StopAction(enabled = connected, onClick = onStop)
                else -> RoundAction(Icons.Rounded.ArrowUpward, "Send", filled = canSend, enabled = canSend, onClick = { if (canSend) onSend() })
            }
        }
    }
    }
}

@Composable
private fun RoundAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    filled: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val bg by androidx.compose.animation.animateColorAsState(if (filled) Ink.Accent else Ink.Surface3, label = "sendBg")
    val fg by androidx.compose.animation.animateColorAsState(if (filled) Ink.OnAccent else Ink.Text4, label = "sendFg")
    Box(
        Modifier
            .size(36.dp)
            .clip(Radius.Full)
            .background(bg)
            .pressClickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = fg, modifier = Modifier.size(19.dp))
    }
}

/** Stop: kontrol interrupt — tonal netral + kotak putih (bukan CTA, bukan hijau). */
@Composable
private fun StopAction(enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(36.dp)
            .clip(Radius.Full)
            .background(Ink.Raised)
            .pressClickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = "Stop" },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(11.dp).clip(RoundedCornerShape(2.5.dp)).background(Ink.Text))
    }
}

/** Chip attachment di atas field: thumbnail 40 radius 8 / ikon file, nama 1 baris, X. */
@Composable
private fun AttachmentChip(att: Attachment?, thumb: ImageBitmap?, attaching: Boolean, onRemove: () -> Unit) {
    Row(
        Modifier
            .padding(start = Dim.ScreenH, end = Dim.ScreenH, top = 8.dp)
            .widthIn(max = 320.dp)
            .clip(Radius.Chip)
            .background(Ink.Surface1)
            .padding(start = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(Dim.Thumb).clip(Radius.Thumb).background(Ink.Surface2),
            contentAlignment = Alignment.Center,
        ) {
            when {
                attaching -> CircularProgressIndicator(Modifier.size(16.dp), color = Ink.Text2, strokeWidth = 2.dp)
                thumb != null -> Image(thumb, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                else -> Icon(
                    if (att?.isImage == true) Icons.Rounded.Image else Icons.Rounded.Description,
                    null, tint = Ink.Text2, modifier = Modifier.size(Dim.Icon),
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(
            if (attaching) "Attaching…" else att?.name.orEmpty(),
            style = Type.Callout,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Box(
            Modifier
                .size(Dim.Touch - 8.dp)
                .clip(Radius.Full)
                .pressClickable(enabled = !attaching, onClick = onRemove)
                .semantics { contentDescription = "Remove attachment" },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Close, null, tint = Ink.Text2, modifier = Modifier.size(Dim.Icon))
        }
    }
}

/** Sheet attach: Photo / File. */
@Composable
fun AttachSheet(onDismiss: () -> Unit, onPhoto: () -> Unit, onFile: () -> Unit) {
    QuietSheet(onDismiss = onDismiss) {
        SheetActionRow("Photo", Icons.Outlined.Image) { onDismiss(); onPhoto() }
        SheetActionRow("File", Icons.Outlined.Description) { onDismiss(); onFile() }
    }
}

/**
 * Model sheet: row model + provider (meta), Check on the active model.
 * Live chat → tap switches THIS chat's model in place (config.set … --session,
 * same as desktop). Only a chat with no session yet falls back to creating one.
 */
// M9 (item 2): chip effort — label → kata server (desktop pakai kata sama).
private val EFFORT_CHIPS = listOf("Low" to "low", "Medium" to "medium", "High" to "high", "Max" to "max")
/** remembered effort buat chat baru (session_id blank) — hidup selama app. */
@Volatile
private var pendingEffort: String? = null

@Composable
fun ModelSheet(
    app: HermesApp,
    sessionId: String,
    onDismiss: () -> Unit,
    onSwitched: (model: String, deferred: Boolean) -> Unit,
    onNewChat: (String, String) -> Unit,
) {
    var options by remember { mutableStateOf<MetaRepo.ModelOptions?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf<String?>(null) }
    var actionError by remember { mutableStateOf<String?>(null) }
    // expensive-model guard from the gateway: (model, provider, message)
    var confirm by remember { mutableStateOf<Triple<String, String, String>?>(null) }
    // M9 (item 2): effort aktif — null = belum ketahuan (chip kosong semua).
    var effort by remember { mutableStateOf<String?>(null) }
    var effortBusy by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        val c = app.client ?: return@LaunchedEffect
        try { options = MetaRepo(c).modelOptions(sessionId = sessionId.ifBlank { null }, profile = app.profile.value) }
        catch (e: Throwable) { error = e.message }
        // chat kosong: pakai pilihan yang diingat; session live: tanya server.
        effort = if (sessionId.isBlank()) pendingEffort
        else try { MetaRepo(c).reasoningEffort(sessionId) } catch (_: Throwable) { null }
    }

    /** Terapkan effort: live → config.set session-scoped; chat baru → remembered. */
    fun pickEffort(word: String) {
        if (effortBusy != null) return
        effortBusy = word
        app.appScope.launch {
            try {
                if (sessionId.isBlank()) {
                    pendingEffort = word
                    effort = word
                } else {
                    val c = app.client ?: return@launch
                    MetaRepo(c).setReasoningEffort(sessionId, word)
                    effort = word
                }
            } catch (e: Throwable) {
                actionError = "Couldn't set effort: ${e.message}"
            } finally { effortBusy = null }
        }
    }

    fun pick(model: String, provider: String, confirmed: Boolean = false) {
        busy = "$provider/$model"; actionError = null
        app.appScope.launch {
            val c = app.client ?: run { busy = null; return@launch }
            try {
                if (sessionId.isBlank()) {
                    val (newRuntime, _) = SessionRepo(c, app.profile.value).createSession(
                        model = model, provider = provider, reasoningEffort = pendingEffort,
                    )
                    onDismiss(); onNewChat(newRuntime, "")
                    return@launch
                }
                val r = MetaRepo(c).switchModel(sessionId, model, provider, confirm = confirmed)
                if (r.confirmRequired) {
                    busy = null
                    confirm = Triple(model, provider, r.confirmMessage.ifBlank { "$model is an expensive model. Switch anyway?" })
                    return@launch
                }
                onDismiss(); onSwitched(r.value.ifBlank { model }, r.deferred)
            } catch (e: Throwable) {
                busy = null
                actionError = "Couldn't switch to $model: ${e.message}"
            }
        }
    }

    QuietSheet(onDismiss = onDismiss, title = "Model") {
        actionError?.let {
            Text(it, style = Type.Meta.copy(color = Ink.Danger), modifier = Modifier.padding(horizontal = Dim.ScreenH).padding(bottom = 8.dp))
        }
        confirm?.let { (m, prov, msg) ->
            Column(Modifier.fillMaxWidth().padding(horizontal = Dim.ScreenH, vertical = 8.dp)) {
                Text(msg, style = Type.Callout.copy(color = Ink.Text))
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                    Text(
                        "Cancel", style = Type.Callout.copy(color = Ink.Text2),
                        modifier = Modifier.heightIn(min = 44.dp).pressClickable { confirm = null }.padding(horizontal = 12.dp, vertical = 12.dp),
                    )
                    Text(
                        "Switch", style = Type.Callout.copy(color = Ink.Text, fontWeight = FontWeight.SemiBold),
                        modifier = Modifier.heightIn(min = 44.dp).pressClickable { confirm = null; pick(m, prov, confirmed = true) }.padding(horizontal = 12.dp, vertical = 12.dp),
                    )
                }
            }
            Hairline()
        }
        when {
            options == null && error == null -> {
                val a = shimmerAlpha()
                repeat(3) { SkeletonSessionRow(a) }
            }
            error != null -> Text(
                "Couldn't load models: $error",
                style = Type.Callout.copy(color = Ink.Danger),
                modifier = Modifier.padding(horizontal = Dim.ScreenH, vertical = 12.dp),
            )
            else -> {
                val opt = options!!
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 460.dp)) {
                    opt.providers.forEach { p ->
                        val all = (p.featuredModels.orEmpty() + p.models.filter { it !in (p.featuredModels ?: emptyList()) }).distinct()
                        val shown = all.take(12)
                        val providerName = p.name.ifBlank { p.slug }
                        shown.forEach { m ->
                            item(key = "${p.slug}/$m") {
                                val key = "${p.slug}/$m"
                                val active = m == opt.model && (p.slug == opt.provider || p.isCurrent == true)
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 56.dp)
                                        .pressClickable(enabled = busy == null && !active) { pick(m, p.slug) }
                                        .padding(horizontal = Dim.ScreenH, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        OneLine(Pretty.model(m).ifBlank { m }, Type.RowTitle.copy(color = if (active) Ink.Text else Ink.Text))
                                        OneLine("$providerName · $m", Type.MonoMeta)
                                    }
                                    when {
                                        busy == key -> CircularProgressIndicator(Modifier.size(18.dp), color = Ink.Text2, strokeWidth = 2.dp)
                                        active -> Icon(Icons.Rounded.Check, "Active model", tint = Ink.Text, modifier = Modifier.size(Dim.Icon))
                                    }
                                }
                            }
                        }
                        if (all.size > shown.size) item(key = "${p.slug}/more") {
                            Text(
                                "${all.size - shown.size} more $providerName models on desktop",
                                style = Type.Meta.copy(color = Ink.Text3),
                                modifier = Modifier.padding(horizontal = Dim.ScreenH, vertical = 6.dp),
                            )
                        }
                    }
                }
            }
        }
        Hairline(Modifier.padding(top = 4.dp))
        // M9 (item 2): Effort — 4 chip segmented (aktif = bg putih teks hitam).
        Column(Modifier.fillMaxWidth().padding(horizontal = Dim.ScreenH)) {
            Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Effort", style = Type.Meta.copy(color = Ink.Text3))
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp)
                    .clip(Radius.Chip)
                    .background(Ink.Surface1)
                    .border(hairline(), Ink.Hairline, Radius.Chip)
                    .padding(3.dp),
            ) {
                EFFORT_CHIPS.forEach { (label, word) ->
                    val active = effort == word
                    Box(
                        Modifier
                            .weight(1f)
                            .heightIn(min = 36.dp)
                            .clip(Radius.Inline)
                            .then(if (active) Modifier.background(Ink.Accent) else Modifier)
                            .pressClickable(enabled = effortBusy == null) { pickEffort(word) }
                            .padding(vertical = 7.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (effortBusy == word) "…" else label,
                            style = Type.Callout.copy(
                                color = if (active) Ink.OnAccent else Ink.Text2,
                                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                            ),
                            maxLines = 1,
                        )
                    }
                }
            }
        }
        Text(
            if (sessionId.isBlank()) "Model for this new chat" else "Applies to this chat only",
            style = Type.Callout.copy(color = Ink.Text3),
            modifier = Modifier.padding(horizontal = Dim.ScreenH, vertical = 12.dp),
        )
    }
}
