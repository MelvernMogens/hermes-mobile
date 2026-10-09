package id.melvern.hermesmobile

import id.melvern.hermesmobile.core.repo.EffortRepo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** v28: per-model effort keys (proxy /api/mobile-efforts → chips + lit key). */
class EffortRepoTest {

    // what the proxy answers for Claude Opus 5.5 (real Hermes builders)
    private val opus = """{"provider":"anthropic","model":"claude-opus-5-5","known":true,"dial":true,"can_off":true,
        "levels":[{"level":"minimal","wire":"low","native":false,"runs_as":"low"},
        {"level":"low","wire":"low","native":true,"runs_as":"low"},
        {"level":"medium","wire":"medium","native":true,"runs_as":"medium"},
        {"level":"high","wire":"high","native":true,"runs_as":"high"},
        {"level":"xhigh","wire":"xhigh","native":true,"runs_as":"xhigh"},
        {"level":"max","wire":"max","native":true,"runs_as":"max"},
        {"level":"ultra","wire":"max","native":false,"runs_as":"max"}]}"""

    // GLM 5.3: no xhigh, xhigh runs as max
    private val glm53 = """{"provider":"zai","model":"glm-5.3","known":true,"dial":true,"can_off":true,
        "levels":[{"level":"minimal","wire":"low","native":false,"runs_as":"low"},
        {"level":"low","wire":"low","native":true,"runs_as":"low"},
        {"level":"medium","wire":"medium","native":true,"runs_as":"medium"},
        {"level":"high","wire":"high","native":true,"runs_as":"high"},
        {"level":"xhigh","wire":"max","native":false,"runs_as":"max"},
        {"level":"max","wire":"max","native":true,"runs_as":"max"},
        {"level":"ultra","wire":"max","native":false,"runs_as":"max"}]}"""

    @Test fun opusShowsItsLevelsPlusUltra() {
        val m = EffortRepo.parse(opus)!!
        assertEquals(listOf("low", "medium", "high", "xhigh", "max", "ultra"), EffortRepo.chips(m).map { it.word })
        assertTrue(m.known && m.dial && m.canOff)
    }

    @Test fun ultraIsAlwaysOfferedAndRunsAsTheStrongest() {
        val m = EffortRepo.parse(glm53)!!
        val chips = EffortRepo.chips(m)
        assertEquals("ultra", chips.last().word)
        assertEquals("max", chips.last().runsAs)
        assertFalse("glm-5.3 has no xhigh", chips.any { it.word == "xhigh" })
    }

    @Test fun storedLevelTheModelLacksLightsTheKeyItRunsAs() {
        val m = EffortRepo.parse(glm53)!!
        assertEquals("max", EffortRepo.selectedChip(m, "xhigh"))
        assertEquals("ultra", EffortRepo.selectedChip(m, "ultra"))
        assertEquals("none", EffortRepo.selectedChip(m, "none"))
        assertEquals("low", EffortRepo.selectedChip(m, "minimal"))
        assertNull(EffortRepo.selectedChip(m, null))
    }

    @Test fun noDialMeansNoKeys() {
        val m = EffortRepo.parse("""{"known":true,"dial":false,"can_off":true,"levels":[]}""")!!
        assertTrue(EffortRepo.chips(m).isEmpty())
    }

    @Test fun fallbackOffersTheWholeLadder() {
        val chips = EffortRepo.chips(EffortRepo.Menu.fallback).map { it.word }
        assertEquals(EffortRepo.LADDER, chips)
    }

    @Test fun garbageBodyIsNull() {
        assertNull(EffortRepo.parse("not json"))
    }
}
