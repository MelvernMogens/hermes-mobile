package id.melvern.hermesmobile.ui.sessions

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import id.melvern.hermesmobile.core.store.ChatGroups
import id.melvern.hermesmobile.ui.components.Hairline
import id.melvern.hermesmobile.ui.components.QuietSheet
import id.melvern.hermesmobile.ui.theme.Dim
import id.melvern.hermesmobile.ui.theme.Ink
import id.melvern.hermesmobile.ui.theme.Radius
import id.melvern.hermesmobile.ui.theme.Type
import id.melvern.hermesmobile.ui.theme.pressClickable
import kotlinx.coroutines.launch

/**
 * Sheet "Move to group": daftar grup (dot warna), "No group", dan buat grup baru
 * (nama + pilih warna). Edit grup (rename/warna/hapus) lewat [GroupEditSheet].
 */
@Composable
fun GroupPickerSheet(sessionId: String, title: String, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var creating by remember { mutableStateOf(ChatGroups.groups.isEmpty()) }
    val current = ChatGroups.groupOf(sessionId)
    QuietSheet(onDismiss = onDismiss, title = if (creating) "New group" else "Move to group") {
        if (creating) {
            GroupForm(initialName = "", initialColor = ChatGroups.groups.size % ChatGroups.Palette.size, cta = "Create") { name, color ->
                scope.launch { ChatGroups.create(ctx, name, color, sessionId); onDismiss() }
            }
        } else {
            ChatGroups.groups.forEach { g ->
                GroupRow(g.name, ChatGroups.color(g.color), selected = current?.id == g.id) {
                    scope.launch { ChatGroups.assign(ctx, sessionId, g.id); onDismiss() }
                }
            }
            if (current != null) GroupRow("No group", null, selected = false) {
                scope.launch { ChatGroups.assign(ctx, sessionId, null); onDismiss() }
            }
            Hairline(Modifier.padding(vertical = 4.dp))
            Row(
                Modifier.fillMaxWidth().heightIn(min = Dim.SheetRow).pressClickable { creating = true }
                    .padding(horizontal = Dim.ScreenH),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Add, null, tint = Ink.Text2, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(14.dp))
                Text("New group", style = Type.Body)
            }
        }
    }
}

/** Rename / ganti warna / hapus grup (long-press header grup). */
@Composable
fun GroupEditSheet(group: ChatGroups.Group, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var confirmDelete by remember { mutableStateOf(false) }
    QuietSheet(onDismiss = onDismiss, title = "Edit group") {
        GroupForm(initialName = group.name, initialColor = group.color, cta = "Save") { name, color ->
            scope.launch { ChatGroups.update(ctx, group.id, name = name, color = color); onDismiss() }
        }
        Hairline(Modifier.padding(vertical = 4.dp))
        Row(
            Modifier.fillMaxWidth().heightIn(min = Dim.SheetRow)
                .pressClickable { if (confirmDelete) scope.launch { ChatGroups.delete(ctx, group.id); onDismiss() } else confirmDelete = true }
                .padding(horizontal = Dim.ScreenH),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (confirmDelete) "Tap again to delete — chats stay, only the group goes" else "Delete group",
                style = Type.Callout.copy(color = Ink.Danger),
            )
        }
    }
}

@Composable
private fun GroupForm(initialName: String, initialColor: Int, cta: String, onSubmit: (String, Int) -> Unit) {
    var name by remember { mutableStateOf(initialName) }
    var color by remember { mutableIntStateOf(initialColor) }
    val fr = remember { FocusRequester() }
    androidx.compose.runtime.LaunchedEffect(Unit) { if (initialName.isEmpty()) fr.requestFocus() }
    Column(Modifier.fillMaxWidth().padding(horizontal = Dim.ScreenH)) {
        BasicTextField(
            value = name,
            onValueChange = { name = it.take(32) },
            textStyle = Type.Body,
            cursorBrush = SolidColor(Ink.Text),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (name.isNotBlank()) onSubmit(name, color) }),
            modifier = Modifier.fillMaxWidth().focusRequester(fr)
                .clip(Radius.Chip).background(Ink.Surface2).padding(horizontal = 14.dp, vertical = 12.dp),
            decorationBox = { inner ->
                Box {
                    if (name.isEmpty()) Text("Group name", style = Type.Body.copy(color = Ink.Text3))
                    inner()
                }
            },
        )
        Spacer(Modifier.size(14.dp))
        // 9 swatch selalu muat satu baris (tanpa scroll / terpotong): ukuran ikut lebar.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            ChatGroups.Palette.indices.forEach { i ->
                val c = ChatGroups.color(i)
                val on = i == color
                Box(
                    Modifier.size(32.dp).clip(Radius.Full)
                        .then(if (on) Modifier.border(2.dp, Ink.Text, Radius.Full) else Modifier)
                        .padding(if (on) 4.dp else 0.dp)
                        .clip(Radius.Full).background(c)
                        .pressClickable { color = i },
                )
            }
        }
        Spacer(Modifier.size(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = { if (name.isNotBlank()) onSubmit(name, color) }, enabled = name.isNotBlank()) {
                Text(cta, style = Type.Callout.copy(color = if (name.isNotBlank()) Ink.Text else Ink.Text3))
            }
        }
    }
}

@Composable
private fun GroupRow(name: String, color: androidx.compose.ui.graphics.Color?, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = Dim.SheetRow).pressClickable(onClick = onClick).padding(horizontal = Dim.ScreenH),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(14.dp).clip(Radius.Full).background(color ?: Ink.Transparent)
                .then(if (color == null) Modifier.border(1.5.dp, Ink.Text3, Radius.Full) else Modifier),
        )
        Spacer(Modifier.width(16.dp))
        Text(name, style = Type.Body, modifier = Modifier.weight(1f))
        if (selected) Icon(Icons.Rounded.Check, "Current group", tint = Ink.Text, modifier = Modifier.size(20.dp))
    }
}
