package id.melvern.hermesmobile.core.notify

import id.melvern.hermesmobile.core.notify.NotifPolicy.Channel
import id.melvern.hermesmobile.core.notify.NotifPolicy.Decision
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M14: unit test pure logic notifikasi — dedup, background gate, session-terbuka gate,
 * mapping runtime→stored, preview 60 char, status error.
 */
class NotifPolicyTest {

    private val map = mapOf("rt-111" to "stored-abc")
    private fun ctx(
        now: Long = 100_000L,
        foreground: Boolean = false,
        open: String? = null,
        last: Map<String, Long> = emptyMap(),
    ) = NotifPolicy.Ctx(now, foreground, open, map, last)

    private fun completePayload(text: String? = "Halo, ini jawaban agent.", status: String? = null) = buildJsonObject {
        text?.let { put("text", it) }
        status?.let { put("status", it) }
        put("warning", "model rate-limited")
    }

    // --- message.complete: reply ---

    @Test fun `reply di background dari session lain jadi notif`() {
        val d = NotifPolicy.onMessageComplete(ctx(), "rt-111", completePayload(), "GIG baru")
        assertTrue(d is Decision.Notify)
        d as Decision.Notify
        assertEquals("stored-abc", d.storedId)
        assertEquals(Channel.AGENT, d.channel)
        assertEquals("Agent replied — GIG baru", d.title)
        assertEquals("Halo, ini jawaban agent.", d.preview)
        assertEquals("stored-abc#reply", d.tag)
    }

    @Test fun `foreground = skip`() {
        assertEquals(Decision.Skip, NotifPolicy.onMessageComplete(ctx(foreground = true), "rt-111", completePayload(), "T"))
    }

    @Test fun `session yang lagi dibuka di layar = skip`() {
        assertEquals(Decision.Skip, NotifPolicy.onMessageComplete(ctx(open = "stored-abc"), "rt-111", completePayload(), "T"))
    }

    @Test fun `dedup 30 detik dalam window = skip`() {
        val c = ctx(now = 100_000L, last = mapOf("stored-abc" to 90_000L))
        assertEquals(Decision.Skip, NotifPolicy.onMessageComplete(c, "rt-111", completePayload(), "T"))
    }

    @Test fun `dedup lewat 30 detik = notif lagi`() {
        val c = ctx(now = 100_000L, last = mapOf("stored-abc" to 69_999L))
        assertTrue(NotifPolicy.onMessageComplete(c, "rt-111", completePayload(), "T") is Decision.Notify)
    }

    @Test fun `runtime id gak dikenal tapi = stored id valid = tetap diproses`() {
        // beberapa event ring pakai stored id langsung (session lama belum pernah resume)
        val d = NotifPolicy.onMessageComplete(ctx(), "stored-abc", completePayload(), "T")
        assertTrue(d is Decision.Notify)
    }

    @Test fun `id asing sama sekali = skip`() {
        assertEquals(Decision.Skip, NotifPolicy.onMessageComplete(ctx(), "rt-xxx", completePayload(), "T"))
    }

    @Test fun `turn kosong (interrupt tanpa text) = skip`() {
        assertEquals(Decision.Skip, NotifPolicy.onMessageComplete(ctx(), "rt-111", completePayload(text = ""), "T"))
    }

    // --- message.complete: error ---

    @Test fun `status error = notif Agent error pakai warning`() {
        val d = NotifPolicy.onMessageComplete(ctx(), "rt-111", completePayload(status = "error"), "GIG")
        assertTrue(d is Decision.Notify)
        d as Decision.Notify
        assertEquals("Agent error — GIG", d.title)
        assertEquals("model rate-limited", d.preview)
        assertEquals("stored-abc#error", d.tag)
    }

    @Test fun `status error tanpa warning = fallback text generik`() {
        val p = buildJsonObject { put("status", "error"); put("text", "") }
        val d = NotifPolicy.onMessageComplete(ctx(), "rt-111", p, "GIG")
        assertTrue(d is Decision.Notify)
        assertEquals("Turn ended with an error.", (d as Decision.Notify).preview)
    }

    // --- approval / clarify ---

    @Test fun `server ask approval di background = notif high`() {
        val p = buildJsonObject { put("session_id", "rt-111"); put("command", "rm -rf /tmp/x") }
        val d = NotifPolicy.onServerAsk(ctx(), p, "Sinyal")
        assertTrue(d is Decision.Notify)
        d as Decision.Notify
        assertEquals("Agent needs your approval — Sinyal", d.title)
        assertEquals("rm -rf /tmp/x", d.preview)
    }

    @Test fun `server ask tanpa session_id = skip`() {
        val p = buildJsonObject { put("command", "ls") }
        assertEquals(Decision.Skip, NotifPolicy.onServerAsk(ctx(), p, "T"))
    }

    @Test fun `server ask saat chat itu terbuka = skip`() {
        val p = buildJsonObject { put("session_id", "rt-111") }
        assertEquals(Decision.Skip, NotifPolicy.onServerAsk(ctx(open = "stored-abc"), p, "T"))
    }

    // --- preview ---

    @Test fun `preview dipotong 60 char + elipsis`() {
        val long = "x".repeat(80)
        assertEquals(61, NotifPolicy.previewOf(long)!!.length)
        assertTrue(NotifPolicy.previewOf(long)!!.endsWith("…"))
    }

    // --- polling diff (M14 core) ---

    private fun row(sid: String = "650ad4c2", key: String = "stored-abc", status: String = "idle",
                    msgs: Int = 10, preview: String = "jawaban baru", title: String = "GIG") =
        NotifPolicy.LiveRow(sid, key, status, msgs, preview, title)

    @Test fun `working ke idle + count naik = notif reply`() {
        val prev = mapOf("650ad4c2" to row(status = "working", msgs = 28))
        val now = mapOf("650ad4c2" to row(status = "idle", msgs = 30))
        val out = NotifPolicy.onPollDiff(ctx(), prev, now)
        assertEquals(1, out.size)
        val d = out[0] as Decision.Notify
        assertEquals("Agent replied — GIG", d.title)
        assertEquals("jawaban baru", d.preview)
        assertEquals("stored-abc", d.storedId)
    }

    @Test fun `masih working walau count naik = belum notif`() {
        val prev = mapOf("s" to row(status = "working", msgs = 5))
        val now = mapOf("s" to row(status = "working", msgs = 6))
        assertTrue(NotifPolicy.onPollDiff(ctx(), prev, now).isEmpty())
    }

    @Test fun `idle tanpa perubahan count = tidak ada notif`() {
        val prev = mapOf("s" to row(status = "idle", msgs = 5))
        val now = mapOf("s" to row(status = "idle", msgs = 5))
        assertTrue(NotifPolicy.onPollDiff(ctx(), prev, now).isEmpty())
    }

    @Test fun `baseline baru (session belum terlihat sebelumnya) = tidak ada notif`() {
        val now = mapOf("s" to row(status = "idle", msgs = 99))
        assertTrue(NotifPolicy.onPollDiff(ctx(), emptyMap(), now).isEmpty())
    }

    @Test fun `status waiting muncul = notif approval`() {
        val prev = mapOf("s" to row(status = "working", msgs = 5))
        val now = mapOf("s" to row(status = "waiting", msgs = 5))
        val out = NotifPolicy.onPollDiff(ctx(), prev, now)
        assertEquals(1, out.size)
        assertEquals("Agent needs your approval — GIG", (out[0] as Decision.Notify).title)
    }

    @Test fun `waiting persist = tidak ada notif kedua`() {
        val prev = mapOf("s" to row(status = "waiting"))
        val now = mapOf("s" to row(status = "waiting"))
        assertTrue(NotifPolicy.onPollDiff(ctx(), prev, now).isEmpty())
    }

    @Test fun `waiting dengan baseline kosong = tidak ada notif palsu (review H2)`() {
        // WS reconnect reset baseline — session yang SUDAH waiting sejak lama gak boleh
        // memicu notif approval palsu.
        val now = mapOf("s" to row(status = "waiting"))
        assertTrue(NotifPolicy.onPollDiff(ctx(), emptyMap(), now).isEmpty())
    }

    @Test fun `poll diff di foreground = kosong`() {
        val prev = mapOf("s" to row(status = "working", msgs = 5))
        val now = mapOf("s" to row(status = "idle", msgs = 7))
        assertTrue(NotifPolicy.onPollDiff(ctx(foreground = true), prev, now).isEmpty())
    }

    @Test fun `poll diff tetap notif utk chat terakhir dibuka saat app di background`() {
        // v22: poller cuma jalan di background — chat "terbuka" sebenarnya tidak dilihat user.
        val prev = mapOf("s" to row(status = "working", msgs = 5))
        val now = mapOf("s" to row(status = "idle", msgs = 7))
        assertEquals(1, NotifPolicy.onPollDiff(ctx(open = "stored-abc"), prev, now).size)
    }

    @Test fun `dedup juga berlaku di poll diff`() {
        val c = ctx(now = 100_000L, last = mapOf("stored-abc" to 95_000L))
        val prev = mapOf("s" to row(status = "working", msgs = 5))
        val now = mapOf("s" to row(status = "idle", msgs = 7))
        assertTrue(NotifPolicy.onPollDiff(c, prev, now).isEmpty())
    }

    @Test fun `session_key kosong pakai runtime id sebagai stored`() {
        val prev = mapOf("rt9" to row(sid = "rt9", key = "", status = "working", msgs = 1))
        val now = mapOf("rt9" to row(sid = "rt9", key = "", status = "idle", msgs = 2))
        val out = NotifPolicy.onPollDiff(ctx(), prev, now)
        assertEquals("rt9", (out[0] as Decision.Notify).storedId)
    }
}
