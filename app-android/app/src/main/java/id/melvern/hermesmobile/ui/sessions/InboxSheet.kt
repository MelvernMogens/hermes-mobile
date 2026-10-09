package id.melvern.hermesmobile.ui.sessions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import id.melvern.hermesmobile.HermesApp
import id.melvern.hermesmobile.core.repo.AgentWorkRepo
import id.melvern.hermesmobile.ui.components.QuietSheet
import id.melvern.hermesmobile.ui.components.StatusDot
import id.melvern.hermesmobile.ui.theme.Dim
import id.melvern.hermesmobile.ui.theme.Ink
import id.melvern.hermesmobile.ui.theme.Radius
import id.melvern.hermesmobile.ui.theme.Type
import id.melvern.hermesmobile.ui.theme.pressClickable
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Signal line on Chats: "2 requests waiting for you" — amber lamp, only when there are any. */
@Composable
fun InboxBanner(count: Int, onOpen: () -> Unit) {
    if (count <= 0) return
    id.melvern.hermesmobile.ui.components.SignalLine(
        id.melvern.hermesmobile.ui.components.Tally.WAIT,
        if (count == 1) "1 request waiting for you" else "$count requests waiting for you",
        action = "Review", onClick = onOpen,
    )
}

/**
 * Inbox semua approval/clarify yang menunggu, lintas chat. Approve/deny/jawab
 * langsung di sini; "Open chat" untuk konteks penuh.
 */
@Composable
fun InboxSheet(app: HermesApp, items: List<AgentWorkRepo.Pending>, onDismiss: () -> Unit, onOpenChat: (String) -> Unit, onChanged: () -> Unit) {
    val scope = rememberCoroutineScope()
    val done = remember { mutableStateOf(mapOf<String, String>()) }
    QuietSheet(onDismiss = onDismiss, title = "Waiting for you") {
        Column(Modifier.heightIn(max = 640.dp).verticalScroll(rememberScrollState())) {
            if (items.isEmpty()) Text("Nothing waiting. You're all caught up.", style = Type.Callout.copy(color = Ink.Text3),
                modifier = Modifier.padding(horizontal = Dim.ScreenH, vertical = 16.dp))
            items.forEach { p ->
                val status = done.value[p.requestId]
                fun answer(label: String, result: kotlinx.serialization.json.JsonObject) {
                    done.value = done.value + (p.requestId to "…")
                    scope.launch {
                        val ok = try { AgentWorkRepo(app.client ?: return@launch, app.profile.value).answer(p.requestId, result) } catch (_: Throwable) { false }
                        done.value = done.value + (p.requestId to if (ok) label else "Expired")
                        onChanged()
                    }
                }
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
                        .clip(Radius.Card).background(Ink.Surface1).padding(14.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(p.chatTitle, style = Type.Caption.copy(color = Ink.Text3), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Text("Open chat", style = Type.Caption.copy(color = Ink.Text2),
                            modifier = Modifier.clip(Radius.Chip).pressClickable { onOpenChat(p.storedId) }.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(if (p.kind == "approval") "Allow ${p.title.lowercase()}?" else p.title, style = Type.Title)
                    if (p.detail.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            p.detail.take(600),
                            style = if (p.kind == "approval") Type.MonoMeta.copy(color = Ink.Text2) else Type.Callout.copy(color = Ink.Text2),
                            modifier = if (p.kind == "approval") Modifier.fillMaxWidth().clip(Radius.Chip).background(Ink.Bg).padding(10.dp) else Modifier,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    when {
                        status != null -> Text(status, style = Type.Callout.copy(color = Ink.Text3))
                        p.kind == "approval" -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Pill("Deny", danger = true) { answer("Denied", buildJsonObject { put("choice", "deny") }) }
                            Pill("Allow once") { answer("Allowed", buildJsonObject { put("choice", "once") }) }
                            Pill("Always") { answer("Always allowed", buildJsonObject { put("choice", "always") }) }
                        }
                        else -> ClarifyQuick(p) { text ->
                            val qs = p.params?.get("questions") as? kotlinx.serialization.json.JsonArray
                            val qid = ((qs?.firstOrNull() as? kotlinx.serialization.json.JsonObject)?.get("qid") as? kotlinx.serialization.json.JsonPrimitive)?.content
                            if ((qs?.size ?: 0) > 1) onOpenChat(p.storedId)
                            else answer("Answered", buildJsonObject {
                                if (qid != null) put("answers", buildJsonObject { put(qid, text) }) else put("answer", text)
                            })
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun ClarifyQuick(p: AgentWorkRepo.Pending, onAnswer: (String) -> Unit) {
    val qs = p.params?.get("questions") as? kotlinx.serialization.json.JsonArray
    if ((qs?.size ?: 0) > 1) { Pill("Answer in chat") { onAnswer("") }; return }
    val first = qs?.firstOrNull() as? kotlinx.serialization.json.JsonObject
    val choices = ((p.params?.get("choices") ?: first?.get("choices")) as? kotlinx.serialization.json.JsonArray)
        ?.mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.content } ?: emptyList()
    var text by remember { mutableStateOf("") }
    Column {
        choices.forEach { c ->
            Text(c, style = Type.Callout, modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                .clip(Radius.Chip).background(Ink.Surface2).pressClickable { onAnswer(c) }.padding(horizontal = 12.dp, vertical = 10.dp))
        }
        Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(
                value = text, onValueChange = { text = it }, textStyle = Type.Callout, cursorBrush = SolidColor(Ink.Text),
                modifier = Modifier.weight(1f).clip(Radius.Chip).background(Ink.Surface2).padding(horizontal = 12.dp, vertical = 10.dp),
                decorationBox = { inner -> androidx.compose.foundation.layout.Box { if (text.isEmpty()) Text(if (choices.isEmpty()) "Your answer…" else "Other…", style = Type.Callout.copy(color = Ink.Text3)); inner() } },
            )
            Spacer(Modifier.width(8.dp))
            Pill("Send") { if (text.isNotBlank()) onAnswer(text.trim()) }
        }
    }
}

@Composable
private fun Pill(label: String, danger: Boolean = false, onClick: () -> Unit) {
    Text(
        label,
        style = Type.Callout.copy(color = if (danger) Ink.Danger else Ink.Text, fontWeight = FontWeight.Medium),
        modifier = Modifier.clip(Radius.Full).background(Ink.Surface2).pressClickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
    )
}
