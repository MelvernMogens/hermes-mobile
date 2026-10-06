package id.melvern.hermesmobile

import id.melvern.hermesmobile.core.repo.SlashRepo
import id.melvern.hermesmobile.core.share.ShareInbox
import id.melvern.hermesmobile.core.store.ChatGroups
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class V22LogicTest {
    // ── Groups ──
    private val gA = ChatGroups.Group("a", "Android", 0, listOf("s2", "s9"))
    private val gB = ChatGroups.Group("b", "Content", 6, listOf("s3"))

    @Test fun partitionKeepsRowOrderInsideGroups() {
        val rows = listOf("s1", "s9", "s3", "s2", "s4")
        val (groups, rest) = ChatGroups.partition(rows, { it }, listOf(gA, gB))
        assertEquals(listOf("s9", "s2"), groups[0].second)
        assertEquals(listOf("s3"), groups[1].second)
        assertEquals(listOf("s1", "s4"), rest)
    }

    @Test fun emptyGroupStillListed() {
        val (groups, rest) = ChatGroups.partition(listOf("s1"), { it }, listOf(gA))
        assertEquals(1, groups.size)
        assertTrue(groups[0].second.isEmpty())
        assertEquals(listOf("s1"), rest)
    }

    @Test fun avatarColorsAreDarkBgLightInk() {
        val (bg, ink) = ChatGroups.avatarColors(0)
        assertTrue(bg.red + bg.green + bg.blue < ink.red + ink.green + ink.blue)
        // index liar → fallback gray, tidak crash
        ChatGroups.avatarColors(99)
    }

    // ── Share ──
    @Test fun shareSubjectNotDuplicated() {
        assertEquals("https://x.com/a", ShareInbox.joinSubject(null, "https://x.com/a"))
        assertEquals("Cool video\nhttps://t.co/1", ShareInbox.joinSubject("Cool video", "https://t.co/1"))
        assertEquals("Cool video https://t.co/1", ShareInbox.joinSubject("Cool video", "Cool video https://t.co/1"))
        assertEquals("Title only", ShareInbox.joinSubject("Title only", null))
    }

    // ── Slash ──
    @Test fun suggestionsOnlyForFirstToken() {
        assertTrue(SlashRepo.wantsSuggestions("/mo"))
        assertTrue(SlashRepo.wantsSuggestions("/"))
        assertFalse(SlashRepo.wantsSuggestions("/model gpt"))
        assertFalse(SlashRepo.wantsSuggestions("hello /x"))
    }

    @Test fun dispatchSendBecomesPrompt() {
        val r = SlashRepo.parseDispatch(buildJsonObject { put("type", "skill"); put("message", "run the skill"); put("name", "x") })
        assertTrue(r is SlashRepo.Result.Send)
        assertEquals("run the skill", (r as SlashRepo.Result.Send).message)
    }

    @Test fun dispatchOutputAndAnsiStripped() {
        val r = SlashRepo.parseDispatch(buildJsonObject { put("output", "\u001B[1mModel:\u001B[0m opus  \n") })
        assertEquals(SlashRepo.Result.Output("Model: opus"), r)
    }

    @Test fun slashExecDirectDispatchPayloadIsPrompt() {
        // server rutekan /queue dkk langsung lewat slash.exec → {type:send, message} (tanpa 4018)
        val r = SlashRepo.parseDispatch(buildJsonObject { put("type", "send"); put("message", "do X later") })
        assertEquals(SlashRepo.Result.Send("do X later", null), r)
    }
}
