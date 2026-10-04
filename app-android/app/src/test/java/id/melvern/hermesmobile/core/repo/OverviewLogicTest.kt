package id.melvern.hermesmobile.core.repo

import id.melvern.hermesmobile.core.notify.NotifPolicy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M18: pure logic fleet dashboard — mapping active_list → Running/Idle/—,
 * sorting, badge count, usage.bars parse + fail-open.
 */
class OverviewLogicTest {

    private fun row(
        name: String,
        isDefault: Boolean = false,
        model: String? = "glm-5.3",
        botChat: String? = null,
        resolved: String? = null,
        last: String? = null,
        worker: String? = null,
    ) = MetaRepo.ProfileRow(
        name = name, path = "/x/$name", isDefault = isDefault, model = model, provider = "zai",
        displayName = "", description = "",
        lastSession = last?.let { MetaRepo.ProfileSessionPreview(id = it) },
        workerSession = worker?.let { MetaRepo.ProfileWorkerSession(id = it) },
        canonicalSession = botChat?.let {
            MetaRepo.ProfileCanonicalSession(id = it, resolvedId = resolved ?: it)
        },
    )

    private fun live(key: String, status: String, sid: String = "rt-$key") =
        NotifPolicy.LiveRow(sid = sid, sessionKey = key, status = status, messageCount = 0, preview = "", title = "")

    // ── Status mapping ──────────────────────────────────────────────────

    @Test
    fun `bot chat active non-idle maps running`() {
        val cards = FleetLogic.buildCards(listOf(row("coder", botChat = "bc-1")), listOf(live("bc-1", "working")))
        assertEquals(BotStatus.RUNNING, cards.single().status)
    }

    @Test
    fun `bot chat live but idle maps idle`() {
        val cards = FleetLogic.buildCards(listOf(row("coder", botChat = "bc-1")), listOf(live("bc-1", "idle")))
        assertEquals(BotStatus.IDLE, cards.single().status)
    }

    @Test
    fun `no session maps offline dash`() {
        val cards = FleetLogic.buildCards(listOf(row("coder", botChat = "bc-1")), emptyList())
        assertEquals(BotStatus.OFFLINE, cards.single().status)
    }

    @Test
    fun `human session or worker counts toward status`() {
        // default punya session manusia live (bukan Bot Chat) → Running
        val a = FleetLogic.buildCards(
            listOf(row("default", isDefault = true, botChat = "bc-d", last = "s-human")),
            listOf(live("s-human", "streaming")),
        )
        assertEquals(BotStatus.RUNNING, a.single().status)
        // coder cuma worker idle → Idle
        val b = FleetLogic.buildCards(
            listOf(row("coder", worker = "w-1")),
            listOf(live("w-1", "idle")),
        )
        assertEquals(BotStatus.IDLE, b.single().status)
    }

    @Test
    fun `session live via resolved_id also maps`() {
        // canonical id lama, runtime pegang resolved_id (compression tip)
        val cards = FleetLogic.buildCards(
            listOf(row("default", isDefault = true, botChat = "bc-old", resolved = "bc-tip")),
            listOf(live("bc-tip", "working")),
        )
        assertEquals(BotStatus.RUNNING, cards.single().status)
    }

    @Test
    fun `any non-idle wins over idle siblings`() {
        val cards = FleetLogic.buildCards(
            listOf(row("default", isDefault = true, botChat = "bc-1", last = "s-2")),
            listOf(live("bc-1", "idle"), live("s-2", "waiting")),
        )
        assertEquals(BotStatus.RUNNING, cards.single().status)
    }

    @Test
    fun `runtime-id fallback when session_key blank`() {
        val cards = FleetLogic.buildCards(
            listOf(row("coder", botChat = "bc-1")),
            listOf(live("", "working", sid = "bc-1")),
        )
        assertEquals(BotStatus.RUNNING, cards.single().status)
    }

    @Test
    fun `session of other profile does not leak`() {
        val cards = FleetLogic.buildCards(
            listOf(row("analyst", botChat = "bc-a"), row("coder", botChat = "bc-c")),
            listOf(live("bc-a", "working")),
        )
        assertEquals(BotStatus.RUNNING, cards.first { it.name == "analyst" }.status)
        assertEquals(BotStatus.OFFLINE, cards.first { it.name == "coder" }.status)
    }

    // ── Sorting & badge ─────────────────────────────────────────────────

    @Test
    fun `default first then alphabetical`() {
        val cards = listOf(
            row("zeta"), row("alpha"), row("default", isDefault = true), row("Beta"),
        ).map { it.copy() }
        val sorted = FleetLogic.sort(FleetLogic.buildCards(cards, emptyList()))
        assertEquals(listOf("default", "alpha", "Beta", "zeta"), sorted.map { it.name })
    }

    @Test
    fun `running count drives tab badge`() {
        val cards = FleetLogic.buildCards(
            listOf(row("a", botChat = "1"), row("b", botChat = "2"), row("c", botChat = "3")),
            listOf(live("1", "working"), live("2", "idle"), live("3", "streaming")),
        )
        assertEquals(2, FleetLogic.runningCount(cards))
    }

    @Test
    fun `bot chat stored id carried for tap`() {
        val cards = FleetLogic.buildCards(listOf(row("coder", botChat = "bc-9")), emptyList())
        assertEquals("bc-9", cards.single().botChatStoredId)
        assertNull(FleetLogic.buildCards(listOf(row("x")), emptyList()).single().botChatStoredId)
    }

    // ── usage.bars parse + fail-open ────────────────────────────────────

    private fun parse(s: String): UsageUi = FleetLogic.parseUsage(Json.parseToJsonElement(s).let { it as JsonObject })

    @Test
    fun `usage unavailable fail-open`() {
        // bentuk nyata dari server 4 Okt 2026: {ok:true, available:false}
        val u = parse("""{"ok": true, "available": false}""")
        assertFalse(u.available)
        assertNull(u.plan)
        assertNull(u.topup)
    }

    @Test
    fun `usage error result treated unavailable`() {
        // Pemanggil catch RPC error → UNAVAILABLE; parse sendiri gak boleh crash
        // pada bentuk aneh (available hilang).
        val u = parse("""{"ok": false}""")
        assertFalse(u.available)
    }

    @Test
    fun `usage full parse plan and topup`() {
        val u = parse(
            """
            {"ok": true, "available": true, "plan_name": "Pro", "renews_display": "in 12 days",
             "plan_bar": {"kind": "plan", "remaining_display": "$5.00", "total_display": "$20.00",
                          "spent_display": "$15.00", "pct_used": 75, "fill_fraction": 0.75},
             "topup_bar": {"kind": "topup", "remaining_display": "$2.00", "total_display": "$10.00",
                           "spent_display": "$8.00", "pct_used": null, "fill_fraction": 0.8},
             "has_topup": true}
            """.trimIndent(),
        )
        assertTrue(u.available)
        assertEquals("Pro", u.planName)
        assertEquals("in 12 days", u.renews)
        assertEquals("$15.00", u.plan?.spent)
        assertEquals(75, u.plan?.pctUsed)
        assertEquals(0.75f, u.plan?.fill)
        assertEquals("$8.00", u.topup?.spent)
        assertNull(u.topup?.pctUsed)
    }

    @Test
    fun `usage fill fraction clamped`() {
        val u = parse(
            """{"available": true, "plan_bar": {"kind":"plan","remaining_display":"","total_display":"$10",
               "spent_display":"$50","pct_used":500,"fill_fraction": 5.0}}""",
        )
        assertEquals(1f, u.plan?.fill)
    }

    @Test
    fun `usage available without bars still not crash`() {
        val u = parse("""{"available": true}""")
        assertTrue(u.available)
        assertNull(u.plan)
        assertNull(u.topup)
    }
}
