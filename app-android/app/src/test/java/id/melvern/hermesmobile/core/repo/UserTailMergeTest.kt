package id.melvern.hermesmobile.core.repo

import id.melvern.hermesmobile.core.model.ChatItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class UserTailMergeTest {
    private val clock: (Double) -> String = { "t" }

    @Test
    fun `desktop prompt is inserted before the streaming reply`() {
        val items = listOf(
            ChatItem.User("hi from phone", rowId = 1, at = 10.0),
            ChatItem.Assistant("ok", done = true, at = 11.0),
            ChatItem.Assistant("streaming…", done = false, at = 21.0),
        )
        val out = InsightsRepo.mergeUserTail(items, listOf(InsightsRepo.UserMsg(2, "typed on laptop", 20.0)), clock)
        assertEquals(listOf("hi from phone", "ok", "typed on laptop", "streaming…"),
            out.map { (it as? ChatItem.User)?.text ?: (it as ChatItem.Assistant).text })
    }

    @Test
    fun `prompt sent from this phone is not duplicated`() {
        // optimistic bubble has no rowId yet; server copy carries the quote prefix
        val items = listOf(ChatItem.User("balas ok", at = 30.0))
        val tail = listOf(InsightsRepo.UserMsg(9, "> quoted text\n\nbalas ok", 30.5))
        assertSame(items, InsightsRepo.mergeUserTail(items, tail, clock))
    }

    @Test
    fun `already-known row ids are skipped`() {
        val items = listOf(ChatItem.User("x", rowId = 5, at = 1.0))
        assertSame(items, InsightsRepo.mergeUserTail(items, listOf(InsightsRepo.UserMsg(5, "x", 1.0)), clock))
    }
}
