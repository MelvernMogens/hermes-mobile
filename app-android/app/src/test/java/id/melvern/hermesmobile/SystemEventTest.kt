package id.melvern.hermesmobile

import id.melvern.hermesmobile.core.model.ChatItem
import id.melvern.hermesmobile.core.model.SystemEvent
import id.melvern.hermesmobile.core.repo.InsightsRepo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemEventTest {
    private val asyncText = "[ASYNC DELEGATION BATCH COMPLETE — deleg_d747dcc5]\nA background fan-out unit you dispatched earlier — 1 subagent(s) — has finished\n\n--- ✓ TASK 1/1: Bikin slide papan tulis (status=completed, api_calls=24, 547.3s) ---"

    @Test fun typedRowsAreEvents() {
        assertEquals("async_delegation_complete", SystemEvent.kindOf("async_delegation_complete", asyncText))
        assertEquals("process_complete", SystemEvent.kindOf("process_complete", "[IMPORTANT: Background process proc_x completed"))
        assertEquals("model_switch", SystemEvent.kindOf("model_switch", "[Note: model was just switched"))
    }

    @Test fun untypedLegacyRowsAreSniffed() {
        assertEquals("async_delegation_complete", SystemEvent.kindOf(null, asyncText))
        assertEquals("process_complete", SystemEvent.kindOf(null, "[IMPORTANT: Background process proc_2c40 completed normally (exit code 0)."))
        assertEquals("auto_continue", SystemEvent.kindOf(null, "[System note: Your previous turn was interrupted mid-run …"))
    }

    @Test fun realUserTextIsNotAnEvent() {
        assertNull(SystemEvent.kindOf(null, "ginian kadang muncul ini apa?"))
        assertNull(SystemEvent.kindOf(null, "Here is the [ASYNC DELEGATION BATCH COMPLETE] text I copied"))
        // steer / skill invocation kinds stay user bubbles
        assertNull(SystemEvent.kindOf("steer", "[OUT-OF-BAND USER MESSAGE] hi [/OUT-OF-BAND USER MESSAGE]"))
        assertNull(SystemEvent.kindOf("skill_invocation", "/check"))
    }

    @Test fun labels() {
        assertEquals("Subagent Task Completed: Bikin slide", SystemEvent.label("async_delegation_complete", "Subagent Task Completed:  Bikin\nslide", asyncText))
        assertEquals("Subagent finished: Bikin slide papan tulis", SystemEvent.label("async_delegation_complete", null, asyncText))
        assertEquals("Background process finished", SystemEvent.label("process_complete", null, "[IMPORTANT: Background process …"))
    }

    @Test fun liveTailMergesAsEventNotBubble() {
        val items = listOf<ChatItem>(ChatItem.User("hi", rowId = 1, at = 1.0))
        val tail = listOf(InsightsRepo.UserMsg(7, asyncText, 2.0, "async_delegation_complete", "Subagent Task Completed: Bikin slide"))
        val out = InsightsRepo.mergeUserTail(items, tail) { "" }
        val ev = out.last()
        assertTrue(ev is ChatItem.Event)
        assertEquals("Subagent Task Completed: Bikin slide", (ev as ChatItem.Event).label)
        assertEquals(1, out.count { it is ChatItem.User })
        // second pull with the same row is deduped
        assertEquals(out, InsightsRepo.mergeUserTail(out, tail) { "" })
    }
}
