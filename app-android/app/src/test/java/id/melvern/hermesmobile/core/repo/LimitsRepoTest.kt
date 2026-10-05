package id.melvern.hermesmobile.core.repo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class LimitsRepoTest {
    private val body = """{"plans":[
        {"provider":"anthropic","label":"Claude","plan":null,"windows":[
            {"label":"Current session","used_percent":16.0,"resets_at":"2026-10-05T22:19:59.639985+00:00"},
            {"label":"Current week","used_percent":250.0,"resets_at":null}],"note":null},
        {"provider":"x","label":"Empty","windows":[]}],
        "ram":{"used_bytes":9542795264,"total_bytes":17179869184,"used_percent":55.5,"swap_used_bytes":920649728}}"""

    @Test
    fun `parses plans, clamps percent, drops plans with no windows`() {
        val l = LimitsRepo.parse(body)!!
        assertEquals(listOf("Claude"), l.plans.map { it.label })
        assertEquals(16.0, l.plans[0].windows[0].usedPercent, 0.0)
        assertEquals(100.0, l.plans[0].windows[1].usedPercent, 0.0)
        assertNotNull(l.plans[0].windows[0].resetsAtEpochMs)
        assertNull(l.plans[0].windows[1].resetsAtEpochMs)
        assertEquals(55.5, l.ram!!.usedPercent, 0.0)
    }

    @Test
    fun `reset countdown is short and human`() {
        val now = 1_000_000_000L
        assertEquals("resets in 45m", LimitsRepo.resetIn(now + 45 * 60_000, now))
        assertEquals("resets in 3h 20m", LimitsRepo.resetIn(now + 200 * 60_000, now))
        assertEquals("resets in 4h", LimitsRepo.resetIn(now + 240 * 60_000, now))
        assertEquals("resets in 6d 2h", LimitsRepo.resetIn(now + (6 * 24 + 2) * 3_600_000L, now))
        assertEquals("resets now", LimitsRepo.resetIn(now - 1, now))
    }

    @Test
    fun `garbage body is null, not a crash`() {
        assertNull(LimitsRepo.parse("not json"))
    }
}
