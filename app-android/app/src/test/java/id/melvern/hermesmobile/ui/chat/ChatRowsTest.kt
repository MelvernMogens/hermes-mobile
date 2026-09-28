package id.melvern.hermesmobile.ui.chat

import id.melvern.hermesmobile.core.model.ChatItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ChatRowsTest {
    private val d1 = LocalDate.of(2026, 9, 27)
    private val d2 = LocalDate.of(2026, 9, 28)
    private fun dayOf(at: Double?): LocalDate? = when (at) { 1.0 -> d1; 2.0 -> d2; else -> null }
    private fun label(d: LocalDate) = d.toString()

    @Test fun consecutiveTools_groupIntoOneRow() {
        val rows = buildRows(
            listOf(
                ChatItem.User("hi", at = 1.0),
                ChatItem.Tool("terminal", "done"),
                ChatItem.Tool("read_file", "done"),
                ChatItem.Assistant("ok", done = true, at = 1.0),
            ),
            ::dayOf, ::label,
        )
        val tools = rows.filterIsInstance<ChatRow.Tools>()
        assertEquals(1, tools.size)
        assertEquals(2, tools.first().tools.size)
    }

    @Test fun daySeparator_insertedOnlyWhenDateChanges() {
        val rows = buildRows(
            listOf(
                ChatItem.User("a", at = 1.0),
                ChatItem.Assistant("b", done = true, at = 1.0),
                ChatItem.User("c", at = 2.0),
            ),
            ::dayOf, ::label,
        )
        assertEquals(listOf(d1.toString(), d2.toString()), rows.filterIsInstance<ChatRow.Day>().map { it.label })
    }

    @Test fun thoughtOnlyAssistants_collapseIntoOneRow_realAnswerStaysItem() {
        val rows = buildRows(
            listOf(
                ChatItem.Assistant("…", done = true, reasoning = "r1"),
                ChatItem.Assistant("…", done = true, reasoning = "r2"),
                ChatItem.Assistant("final", done = true, reasoning = "r3"),
            ),
            ::dayOf, ::label,
        )
        val thoughts = rows.filterIsInstance<ChatRow.Thoughts>()
        assertEquals(1, thoughts.size)
        assertEquals(listOf("r1", "r2"), thoughts.first().texts)
        assertTrue(rows.last() is ChatRow.Item)
    }

    @Test fun keys_areUnique() {
        val rows = buildRows(
            listOf(
                ChatItem.User("a", at = 1.0), ChatItem.Tool("t", "done"),
                ChatItem.Assistant("…", done = true, reasoning = "x"), ChatItem.User("b", at = 2.0),
            ),
            ::dayOf, ::label,
        )
        assertEquals(rows.size, rows.map { it.key }.toSet().size)
    }
}
