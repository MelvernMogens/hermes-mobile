package id.melvern.hermesmobile.ui.layout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M15: pane selection state machine — mapping Compact vs Expanded + parser
 * argumen chat route yang dipakai dua-duanya (nav compact, ChatPane expanded).
 */
class AdaptiveTest {

    // ── Window size → mode ──────────────────────────────────────────────

    @Test
    fun `only expanded width enables two-pane`() {
        assertTrue(WinSize.Expanded.isExpanded)
        assertFalse(WinSize.Medium.isExpanded)   // tablet portrait ~600-840dp: single pane
        assertFalse(WinSize.Compact.isExpanded)  // phone: perilaku lama
    }

    // ── ChatRouteArg parser (dipakai MainActivity nav + ChatPane) ───────

    @Test
    fun `bare storedId`() {
        val p = ChatRouteArg.parse("abc123")
        assertEquals("abc123", p.storedId)
        assertNull(p.runtimeId)
        assertNull(p.title)
    }

    @Test
    fun `storedId pipe runtimeId (chat baru)`() {
        val p = ChatRouteArg.parse("stored-1|rt-9")
        assertEquals("stored-1", p.storedId)
        assertEquals("rt-9", p.runtimeId)
        assertNull(p.title)
    }

    @Test
    fun `storedId pipe encoded title (dari list)`() {
        val p = ChatRouteArg.parse("stored-2|t=Fix%20M15%20%E2%86%92%20layout")
        assertEquals("stored-2", p.storedId)
        assertNull(p.runtimeId)
        assertEquals("Fix M15 → layout", p.title)
    }

    @Test
    fun `selection arg roundtrip expanded pane`() {
        // Simulasi state machine: expanded tap row → paneSelection; rotate (reparse)
        // harus balik identik.
        val arg = "stored-3|t=Hello%20world"
        val first = ChatRouteArg.parse(arg)
        val second = ChatRouteArg.parse(arg)
        assertEquals(first, second)
        assertEquals("Hello world", first.title)
    }

    // ── Mode mapping (selection behavior) ────────────────────────────────

    @Test
    fun `compact selection goes to navigation not pane`() {
        fun openChatTarget(win: WinSize, arg: String): String? =
            if (win.isExpanded) null else arg // null = pane; non-null = nav
        assertEquals("s|t=x", openChatTarget(WinSize.Compact, "s|t=x"))
        assertEquals("s|t=x", openChatTarget(WinSize.Medium, "s|t=x"))
        assertNull(openChatTarget(WinSize.Expanded, "s|t=x"))
    }
}
