package id.melvern.hermesmobile.ui.sessions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.CallSplit
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import id.melvern.hermesmobile.HermesApp
import id.melvern.hermesmobile.core.model.SessionRow
import id.melvern.hermesmobile.core.repo.MetaRepo
import id.melvern.hermesmobile.core.repo.SessionRepo
import id.melvern.hermesmobile.ui.components.Hairline
import id.melvern.hermesmobile.ui.components.QuietSheet
import id.melvern.hermesmobile.ui.components.SheetActionRow
import id.melvern.hermesmobile.ui.theme.Dim
import id.melvern.hermesmobile.ui.theme.Ink
import id.melvern.hermesmobile.ui.theme.Radius
import id.melvern.hermesmobile.ui.theme.Type
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * M4→M8: aksi session — rename (session.title), branch (session.branch),
 * hide (session.set_hidden), copy id, delete (session.delete + konfirmasi).
 * Dipakai dua tempat: long-press row di Chats dan menu MoreVert di Chat.
 */
@Composable
fun SessionActionSheet(
    app: HermesApp,
    row: SessionRow,
    onDone: () -> Unit,
    onDismiss: () -> Unit,
    onOpenBranch: (String, String) -> Unit,
    hidden: Boolean = false,
    onDeleted: () -> Unit = onDone,
) {
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    var renaming by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf(row.displayTitle) }
    var confirmingDelete by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    val profile = app.profile.value

    fun run(after: () -> Unit = onDone, action: suspend () -> Unit) {
        busy = true; notice = null
        scope.launch {
            try { action(); after() }
            catch (e: Throwable) { notice = e.message ?: "Something went wrong"; busy = false }
        }
    }

    QuietSheet(onDismiss = onDismiss, title = row.displayTitle) {
        notice?.let {
            Text(it, style = Type.Meta.copy(color = Ink.Danger), modifier = Modifier.padding(horizontal = Dim.ScreenH).padding(bottom = 8.dp))
        }
        when {
            renaming -> {
                val fr = remember { FocusRequester() }
                LaunchedEffect(Unit) { fr.requestFocus() }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = Dim.ScreenH, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BasicTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        textStyle = Type.Body,
                        cursorBrush = SolidColor(Ink.Text),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { if (newName.isNotBlank() && !busy) run { rename(app, row, newName.trim(), profile) } }),
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(fr)
                            .background(Ink.Surface2, Radius.Chip)
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                    )
                    TextButton(
                        onClick = { if (newName.isNotBlank() && !busy) run { rename(app, row, newName.trim(), profile) } },
                        enabled = newName.isNotBlank() && !busy,
                    ) { Text("Save", style = Type.Callout.copy(color = if (newName.isNotBlank() && !busy) Ink.Text else Ink.Text3)) }
                }
            }
            confirmingDelete -> {
                Text(
                    "Delete this chat and its transcript? This can't be undone.",
                    style = Type.Callout.copy(color = Ink.Text2),
                    modifier = Modifier.padding(horizontal = Dim.ScreenH, vertical = 8.dp),
                )
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = { confirmingDelete = false }) { Text("Cancel", style = Type.Callout.copy(color = Ink.Text2)) }
                    TextButton(
                        enabled = !busy,
                        onClick = {
                            run(after = onDeleted) {
                                val c = app.client!!
                                try { MetaRepo(c).deleteSession(row.id, profile) }
                                catch (e: id.melvern.hermesmobile.core.rpc.RpcException) {
                                    if (e.code == 4023) {
                                        // session live di backend — close pakai RUNTIME id
                                        // (session.close cuma resolve runtime id, bukan stored)
                                        val runtime = SessionRepo(c, profile).resumeAttachLazy(row.id)
                                        c.call("session.close", buildJsonObject {
                                            put("session_id", runtime ?: row.id)
                                            if (profile.isNotBlank() && profile != "default") put("profile", profile)
                                        })
                                        MetaRepo(c).deleteSession(row.id, profile)
                                    } else throw e
                                }
                            }
                        },
                    ) { Text("Delete", style = Type.Callout.copy(color = Ink.Danger)) }
                }
            }
            else -> {
                SheetActionRow("Rename", Icons.Rounded.DriveFileRenameOutline, enabled = !busy) { renaming = true }
                SheetActionRow("New branch", Icons.AutoMirrored.Rounded.CallSplit, enabled = !busy) {
                    run(after = {}) {
                        // branch butuh session live — attach lazy dulu (session.resume lazy)
                        val c = app.client!!
                        val live = try { SessionRepo(c, profile).resumeAttachLazy(row.id) } catch (_: Throwable) { null }
                        val out = MetaRepo(c).branchSession(live ?: row.id, profile)
                        onOpenBranch(out.runtimeId, out.storedId)
                    }
                }
                if (!hidden) SheetActionRow("Hide", Icons.Rounded.VisibilityOff, enabled = !busy) {
                    run { MetaRepo(app.client!!).hideSession(row.id, profile) }
                }
                SheetActionRow("Copy session ID", Icons.Rounded.ContentCopy, enabled = !busy) {
                    clipboard.setText(AnnotatedString(row.id))
                    onDismiss()
                }
                Hairline(Modifier.padding(vertical = 4.dp))
                SheetActionRow("Delete", Icons.Rounded.DeleteOutline, danger = true, enabled = !busy) { confirmingDelete = true }
            }
        }
    }
}

/**
 * Rename: session.title SET butuh runtime live — attach lazy via session.resume
 * (gak build agent, gak transfer history) lalu set title di runtime itu.
 */
private suspend fun rename(app: HermesApp, row: SessionRow, newTitle: String, profile: String) {
    val c = app.client!!
    val live = try { SessionRepo(c, profile).resumeAttachLazy(row.id) } catch (_: Throwable) { null }
    MetaRepo(c).renameSession(live ?: row.id, newTitle, profile)
}
