package id.melvern.hermesmobile.core.repo

import id.melvern.hermesmobile.core.model.ChatItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TailRepoTest {
    /** Fixture = keluaran nyata server/mobile_tail.py (bentuk session.resume messages). */
    private val fixture = """{"messages":[
        {"role":"user","text":"build it","timestamp":10.0,"row_id":101},
        {"role":"tool","name":"terminal","context":"ls -la","args":{"command":"ls -la"}},
        {"role":"assistant","text":"done","timestamp":14.0,"row_id":105,"reasoning":"thought"},
        {"role":"user","text":"[IMPORTANT: Background process…]","timestamp":15.0,"row_id":106,
         "display_kind":"process_complete","display_metadata":{"display_text":"Background Process Finished"}}
    ],"has_more":true,"total_active":3148}"""

    @Test fun maps_same_fields_as_resume() {
        val t = TailRepo.parse(fixture)!!
        assertTrue(t.hasMore)
        assertEquals(4, t.messages.size)
        val (u, tool, a) = t.messages
        assertEquals("user", u.role); assertEquals("build it", u.text); assertEquals(101, u.rowId); assertEquals(10.0, u.timestamp!!, 0.0)
        assertEquals("tool", tool.role); assertEquals("terminal", tool.name); assertEquals("ls -la", tool.context)
        assertTrue(tool.argsText!!.contains("\"command\""))
        assertEquals("thought", a.reasoning); assertEquals(105, a.rowId)
        assertEquals(106, t.messages[3].rowId) // field ekstra (display_*) diabaikan, baris tetap ada
    }

    @Test fun partial_notice_only_when_more() {
        val items = listOf<ChatItem>(ChatItem.NoticeLine("x"))
        assertEquals(ChatItem.NoticeLine(TailRepo.PARTIAL_NOTICE), TailRepo.withPartialNotice(items, true).first())
        assertEquals(items, TailRepo.withPartialNotice(items, false))
    }

    @Test fun bad_body() {
        assertNull(TailRepo.parse("""{"detail":"unknown profile"}"""))
        assertEquals(0, TailRepo.parse("""{"messages":[],"has_more":false}""")!!.messages.size)
    }
}
