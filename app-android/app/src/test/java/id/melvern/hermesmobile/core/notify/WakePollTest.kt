package id.melvern.hermesmobile.core.notify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WakePollTest {
    @Test fun parse_events_and_task_flag() {
        val p = WakePoll.parse("""{"cursor":"default:5;run=coder","events":[
            {"kind":"message","profile":"default","session_id":"s","title":"t","at":1},
            {"kind":"task","profile":"coder","session_id":"t1","title":"x","at":2}]}""")!!
        assertEquals("default:5;run=coder", p.cursor)
        assertEquals(2, p.events)
        assertEquals(true, p.task)
        assertEquals(WakePoll.Parsed("c", 0, false), WakePoll.parse("""{"cursor":"c","events":[]}"""))
        assertNull(WakePoll.parse("""{"error":"unauthenticated"}"""))
        assertNull(WakePoll.parse("not json"))
    }

    @Test fun backoff_20_40_60_cap() {
        var b = 0L
        val seq = (1..5).map { b = WakePoll.nextBackoff(b); b }
        assertEquals(listOf(20_000L, 40_000L, 60_000L, 60_000L, 60_000L), seq)
    }

    @Test fun sleep_after_wake_never_tight_loops() {
        // events beruntun: minimal 5 dtk antar bangun
        assertEquals(5_000L, sleepAfterWake(WakePoll.Outcome.Events(1, false), 0))
        assertEquals(0L, sleepAfterWake(WakePoll.Outcome.Events(1, false), 9_000))
        // server pulang terlalu cepat tanpa event → genapi ke 20 dtk
        assertEquals(19_900L, sleepAfterWake(WakePoll.Outcome.Timeout, 100))
        assertEquals(0L, sleepAfterWake(WakePoll.Outcome.Timeout, 25_000))
        // error → delay lama
        assertEquals(20_000L, sleepAfterWake(WakePoll.Outcome.Error, 0))
    }
}
