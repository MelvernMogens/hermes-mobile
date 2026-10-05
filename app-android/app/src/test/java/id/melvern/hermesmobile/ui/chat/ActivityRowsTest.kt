package id.melvern.hermesmobile.ui.chat

import id.melvern.hermesmobile.core.model.ChatItem
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivityRowsTest {
    private fun rows(items: List<ChatItem>) = buildRows(items, { null }, { it.toString() })

    @Test
    fun `tool and reasoning runs between prose collapse into one activity row`() {
        val r = rows(listOf(
            ChatItem.User("go"),
            ChatItem.Assistant("…", done = true, reasoning = "plan"),
            ChatItem.Tool("terminal", "done"),
            ChatItem.Assistant("…", done = true, reasoning = "check"),
            ChatItem.Tool("read_file", "done"),
            ChatItem.Tool("patch", "done"),
            ChatItem.Assistant("done!", done = true),
        ))
        val act = r.filterIsInstance<ChatRow.Activity>()
        assertEquals(1, act.size)
        assertEquals(3, act.first().tools.size)
        assertEquals(listOf("plan", "check"), act.first().thoughts)
        // prose on both sides stays its own row
        assertTrue(r.first() is ChatRow.Item && r.last() is ChatRow.Item)
    }

    @Test
    fun `a lone tool group is not wrapped`() {
        val r = rows(listOf(ChatItem.User("a"), ChatItem.Tool("t", "done"), ChatItem.Assistant("b", done = true)))
        assertEquals(0, r.filterIsInstance<ChatRow.Activity>().size)
        assertEquals(1, r.filterIsInstance<ChatRow.Tools>().size)
    }

    @Test
    fun `tool failure is read from the result payload`() {
        fun j(s: String) = Json.parseToJsonElement(s)
        assertTrue(toolFailed(j("""{"error":"boom"}""")))
        assertTrue(toolFailed(j("""{"exit_code":1,"output":"x"}""")))
        assertTrue(toolFailed(j("""{"success":false}""")))
        assertFalse(toolFailed(j("""{"exit_code":0,"output":"ok","error":null}""")))
        assertFalse(toolFailed(j("\"plain text\"")))
        assertFalse(toolFailed(null))
    }
}
