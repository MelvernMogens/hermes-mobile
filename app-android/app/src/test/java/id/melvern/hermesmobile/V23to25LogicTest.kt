package id.melvern.hermesmobile

import id.melvern.hermesmobile.core.model.ChatItem
import id.melvern.hermesmobile.core.repo.AgentWorkRepo
import id.melvern.hermesmobile.core.repo.MacRepo
import id.melvern.hermesmobile.core.repo.ScheduleRepo
import id.melvern.hermesmobile.core.repo.SessionRepo
import id.melvern.hermesmobile.core.repo.UnreadStore
import id.melvern.hermesmobile.core.share.ChatExport
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class V23to25LogicTest {
    private fun obj(s: String) = Json.parseToJsonElement(s).jsonObject

    // ── v23 ──
    @Test fun todosParseFromSnapshot() {
        val t = AgentWorkRepo.parseTodos(obj("""{"todos":[{"id":"1","content":"Read files","status":"completed"},
            {"id":"2","content":"Write fix","status":"in_progress"},{"id":"3","content":"  ","status":"pending"}],"revision":3}"""))
        assertEquals(2, t.size)
        assertTrue(t[0].done); assertTrue(t[1].active)
    }

    @Test fun subagentsParse() {
        val s = AgentWorkRepo.parseSubagents(obj("""{"subagents":[{"subagent_id":"sa-1","goal":"Audit","status":"running","tool_count":4,
            "last_tool":"terminal","model":"glm-5.3","depth":1,"accepting_steer":true}],"delegations":[]}"""))
        assertEquals("sa-1", s.single().id)
        assertTrue(s.single().running)
        assertEquals(4, s.single().toolCount)
    }

    @Test fun inboxApprovalAndClarify() {
        val ap = AgentWorkRepo.toPending("st", "Chat", SessionRepo.OpenRequest("r1", "approval",
            obj("""{"tool_name":"terminal","command":"rm -rf build"}""")))!!
        assertEquals("approval", ap.kind); assertEquals("rm -rf build", ap.detail)
        val cl = AgentWorkRepo.toPending("st", "Chat", SessionRepo.OpenRequest("r2", "clarify",
            obj("""{"questions":[{"qid":"q1","question":"Which theme?","choices":["A","B"]}]}""")))!!
        assertEquals("Which theme?", cl.detail)
        assertNull(AgentWorkRepo.toPending("st", "Chat", SessionRepo.OpenRequest("r3", "other", null)))
    }

    @Test fun unreadOnlyForNewAssistantAfterSeen() {
        assertFalse(UnreadStore.isUnreadPure(null, 100.0, "assistant"))   // never opened on this phone
        assertTrue(UnreadStore.isUnreadPure(50.0, 100.0, "assistant"))
        assertFalse(UnreadStore.isUnreadPure(150.0, 100.0, "assistant"))
        assertFalse(UnreadStore.isUnreadPure(50.0, 100.0, "user"))       // own message isn't unread
    }

    @Test fun steerWrapperUnwrapped() {
        val (t, steered) = id.melvern.hermesmobile.core.model.SteerText.unwrap(
            "[OUT-OF-BAND USER MESSAGE — a direct message from the user, delivered once]\nchange of plan: BANANA\n[/OUT-OF-BAND USER MESSAGE]")
        assertEquals("change of plan: BANANA", t); assertTrue(steered)
        assertEquals("hi" to false, id.melvern.hermesmobile.core.model.SteerText.unwrap("hi"))
    }

    @Test fun rowIdsBackfilledForLocalBubbles() {
        val items = listOf(ChatItem.User("old", rowId = 5), ChatItem.User("first"), ChatItem.Assistant("a", true), ChatItem.User("hello again"))
        val tail = listOf(
            id.melvern.hermesmobile.core.repo.InsightsRepo.UserMsg(7, "first", 1.0),
            id.melvern.hermesmobile.core.repo.InsightsRepo.UserMsg(9, "hello again", 2.0),
        )
        val out = id.melvern.hermesmobile.core.repo.InsightsRepo.backfillRowIds(items, tail).filterIsInstance<ChatItem.User>()
        assertEquals(listOf(5, 7, 9), out.map { it.rowId })
    }

    // ── v24 ──
    @Test fun macStatusParse() {
        val s = MacRepo.parseStatus("""{"battery":{"percent":80,"state":"charging","ac":true},"load":[2.0,1,1],"cpu_count":8,
            "uptime_days":3,"disk":{"free_bytes":1000,"total_bytes":2000},"keep_awake":true,
            "services":[{"label":"x","name":"Mobile server","running":false}],"bot_tabs":5,"host":"Mac"}""")!!
        assertEquals(80, s.batteryPct); assertTrue(s.keepAwake); assertFalse(s.services.single().running)
    }

    @Test fun diffSplitPerFile() {
        val patch = "diff --git a/x.kt b/x.kt\nindex 1..2\n--- a/x.kt\n+++ b/x.kt\n@@ -1 +1,2 @@\n one\n+two\n" +
            "diff --git a/y.md b/y.md\nnew file\n--- /dev/null\n+++ b/y.md\n@@ -0,0 +1 @@\n+hi\n"
        val m = MacRepo.splitPatch(patch).toMap()
        assertEquals(listOf("@@ -1 +1,2 @@", " one", "+two"), m["x.kt"]!!.filter { it.isNotEmpty() })
        assertEquals(listOf("@@ -0,0 +1 @@", "+hi"), m["y.md"]!!.filter { it.isNotEmpty() })
    }

    @Test fun touchedPathsFromToolOutput() {
        val p = MacRepo.touchedPaths(listOf("""{"path": "/Users/m/Code/app/src/A.kt", "x": 1}""", "edited /Users/m/Code/app/README.md."))
        assertEquals(listOf("/Users/m/Code/app/src/A.kt", "/Users/m/Code/app/README.md"), p)
    }

    @Test fun exportSkipsToolsAndAttachmentNoise() {
        val md = ChatExport.markdown("T", listOf(
            ChatItem.User("hello\nMEDIA:/x.png"), ChatItem.Tool("terminal", "done"),
            ChatItem.Assistant("hi there", done = true), ChatItem.Assistant("partial", done = false),
        ))
        assertEquals("# T\n\n**You:** hello\n\n**Hermes:** hi there\n", md)
    }

    // ── v25 ──
    @Test fun cronJobsParse() {
        val j = ScheduleRepo.parseJobs("""[{"id":"j1","name":"Brief","prompt":"news","schedule":{"kind":"cron","expr":"0 8 * * *","display":"0 8 * * *"},
            "enabled":true,"state":"scheduled","next_run_at":"2026-10-07T08:00:00+07:00"}]""")!!
        assertEquals("j1", j.single().id); assertEquals("0 8 * * *", j.single().schedule); assertFalse(j.single().paused)
        assertEquals("Every morning 8:00", ScheduleRepo.humanSchedule("0 8 * * *"))
    }

    @Test fun snippetEscapesCleaned() {
        val raw = "[{\"function\": {\"arguments\": \"{\\\\\"command\\\\\":\\\\\"tailscale switch | grep '\\\\\\\\*'\\\\nnext\"}}]"
        val c = ScheduleRepo.cleanSnippet(raw)
        assertFalse(c, c.contains("\\\\"))
        assertTrue(c, c.startsWith("tailscale switch"))
    }

    @Test fun searchHitsParseAndHighlightMarkersKept() {
        val h = ScheduleRepo.parseHits("""{"results":[{"session_id":"s2","lineage_root":"s1","title":"Tailscale fix",
            "snippet":"switch >>>tailscale<<< account","role":"assistant","last_active":1.0}]}""")!!
        assertEquals("s1", h.single().sessionId)
        assertTrue(h.single().snippet.contains(">>>tailscale<<<"))
    }

    // ── v26 ──
    @Test fun sendAfterBubbleRestoredWhenNotInTranscript() {
        val now = System.currentTimeMillis()
        val q = id.melvern.hermesmobile.core.repo.PromptStore.Queued("s1", "after this, say HI", now)
        val items = listOf<id.melvern.hermesmobile.core.model.ChatItem>(
            id.melvern.hermesmobile.core.model.ChatItem.User("first", rowId = 1, at = now / 1000.0 - 60))
        val out = id.melvern.hermesmobile.core.repo.PromptStore.withServerQueued(items, listOf(q)) { "" }
        assertEquals(2, out.size)
        val u = out.last() as id.melvern.hermesmobile.core.model.ChatItem.User
        assertEquals("after this, say HI", u.text); assertTrue(u.queued)
    }

    @Test fun sendAfterBubbleNotDuplicatedWhenAlreadyShown() {
        val q = id.melvern.hermesmobile.core.repo.PromptStore.Queued("s1", "say HI", System.currentTimeMillis())
        val items = listOf<id.melvern.hermesmobile.core.model.ChatItem>(
            id.melvern.hermesmobile.core.model.ChatItem.User("say HI", queued = true))
        assertEquals(1, id.melvern.hermesmobile.core.repo.PromptStore.withServerQueued(items, listOf(q)) { "" }.size)
    }

    @Test fun botChatAlwaysVisible() {
        assertTrue(id.melvern.hermesmobile.ui.sessions.isBotChat(id.melvern.hermesmobile.core.model.SessionRow(id = "x", title = "Bot Chat")))
        assertFalse(id.melvern.hermesmobile.ui.sessions.isBotChat(id.melvern.hermesmobile.core.model.SessionRow(id = "y", title = "Lid Open")))
    }

    @Test fun liveOutputGoesAboveQueuedBubble() {
        val items = listOf<ChatItem>(ChatItem.User("run sleep", rowId = 5), ChatItem.User("after that QAFTER2", queued = true))
        val out = id.melvern.hermesmobile.ui.chat.liveAppend(items, ChatItem.Assistant("DONE2", done = true))
        assertEquals(listOf("run sleep", "DONE2", "after that QAFTER2"), out.map { (it as? ChatItem.User)?.text ?: (it as ChatItem.Assistant).text })
    }

    @Test fun cliWorkerMarksBotRunning() {
        val w = id.melvern.hermesmobile.core.repo.FleetLogic.parseWork(
            """{"bots":[{"profile":"coder","running_secs":3727,"task":"B5: tebakan figur","session_id":"s1"}]}""")!!
        assertEquals(1, w.size)
        assertEquals("1 h 02 min", id.melvern.hermesmobile.core.repo.FleetLogic.duration(3727))
        assertEquals("4 min", id.melvern.hermesmobile.core.repo.FleetLogic.duration(250))
    }

    @Test fun fileMessageNotDuplicatedAgainstServerRow() {
        val local = ChatItem.User("ini derrick kirim ini\nMEDIA:/Users/m/.hermes/attachments/a.md", at = 100.0)
        val server = id.melvern.hermesmobile.core.repo.InsightsRepo.UserMsg(65831,
            "ini derrick kirim ini\n@file:.hermes/attachments/a.md\nMEDIA:/Users/m/.hermes/attachments/a.md\n\n--- Attached Context ---\n\nisi file", 101.0)
        val merged = id.melvern.hermesmobile.core.repo.InsightsRepo.mergeUserTail(listOf(local), listOf(server)) { "" }
        assertEquals(1, merged.size)
        val back = id.melvern.hermesmobile.core.repo.InsightsRepo.backfillRowIds(listOf(local), listOf(server))
        assertEquals(65831, (back[0] as ChatItem.User).rowId)
    }

    @Test fun outboxBubbleSurvivesReopenAndShowsFailed() {
        val o = id.melvern.hermesmobile.core.repo.Outbox.Out("o1", "s1", "after that QX", "after that QX", 1_000_000L, queued = true)
        val base = listOf<ChatItem>(ChatItem.User("hi", rowId = 1))
        val out = id.melvern.hermesmobile.core.repo.Outbox.reconcile(base, listOf(o), { false }) { "" }
        val u = out.last() as ChatItem.User
        assertEquals("after that QX", u.text); assertTrue(u.failed); assertEquals("o1", u.outboxId)
        // sedang dikirim → pending, bukan failed
        val sending = id.melvern.hermesmobile.core.repo.Outbox.reconcile(base, listOf(o), { true }) { "" }.last() as ChatItem.User
        assertTrue(sending.pending); assertFalse(sending.failed)
        // terkirim (keluar outbox) → bubble normal
        val done = id.melvern.hermesmobile.core.repo.Outbox.reconcile(out, emptyList(), { false }) { "" }.last() as ChatItem.User
        assertFalse(done.failed); assertFalse(done.pending)
    }
}
