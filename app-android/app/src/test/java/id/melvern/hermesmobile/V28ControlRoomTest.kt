package id.melvern.hermesmobile

import id.melvern.hermesmobile.ui.components.Timecode
import id.melvern.hermesmobile.ui.components.channelCode
import id.melvern.hermesmobile.ui.chat.toolShort
import id.melvern.hermesmobile.ui.chat.toolRunningLabel
import id.melvern.hermesmobile.ui.overview.binName
import org.junit.Assert.assertEquals
import org.junit.Test

/** v28 Control Room — pure helpers behind the redesign (timecode, channel badge, tool words). */
class V28ControlRoomTest {
    @Test fun timecodeIsAlwaysElapsedHms() {
        assertEquals("0:00:00", Timecode.of(0))
        assertEquals("0:12:34", Timecode.of(754))
        assertEquals("1:06:40", Timecode.of(4000))
        assertEquals("74:43:02", Timecode.of(74 * 3600 + 43 * 60 + 2))
        assertEquals("0:00:00", Timecode.of(-5)) // clock skew never shows a negative timecode
    }

    @Test fun shortDurations() {
        assertEquals("45s", Timecode.short(45))
        assertEquals("12m", Timecode.short(12 * 60 + 5))
        assertEquals("1h 05m", Timecode.short(3900))
        assertEquals("3d", Timecode.short(3 * 86_400 + 10))
    }

    @Test fun channelCodeFirstAndLastWord() {
        assertEquals("BC", channelCode("Bot Chat"))
        assertEquals("BL", channelCode("Blokees"))
        assertEquals("UR", channelCode("UTS Web Review"))
        assertEquals("SJ", channelCode("Sinar Bulian Jaya"))
        assertEquals("SB", channelCode("Saham Bot"))
        assertEquals("LO", channelCode("Lid Open"))
        assertEquals("·", channelCode("—"))
    }

    @Test fun toolWordsAreVerbs() {
        assertEquals("Running", toolShort("terminal"))
        assertEquals("Coding", toolShort("execute_code"))
        assertEquals("Looking", toolShort("vision_analyze"))
        assertEquals("Editing", toolShort("patch"))
        assertEquals("Browsing", toolShort("browser_exec"))
        assertEquals("Delegating", toolShort("delegate_task"))
        assertEquals("Working", toolShort("mystery_tool"))
        assertEquals("Running terminal", toolRunningLabel("terminal"))
        assertEquals("Browsing the web", toolRunningLabel("browser_exec"))
    }

    @Test fun fileNamesBreakOnlyAtTheirOwnSeparators() {
        val zw = '\u200B'
        assertEquals("Sesi-${zw}2-${zw}Tangan-${zw}dan-${zw}Mata.${zw}pptx", binName("Sesi-2-Tangan-dan-Mata.pptx"))
        assertEquals("LAPORAN.${zw}md", binName("LAPORAN.md"))
        assertEquals("a-", binName("a-"))                           // no trailing break
        assertEquals("Sesi-2-Tangan-dan-Mata.pptx", binName("Sesi-2-Tangan-dan-Mata.pptx").replace(zw.toString(), ""))
    }
}
