package id.melvern.hermesmobile.core.store

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Enter behavior on a HARDWARE keyboard (tablet): Enter sends, Shift+Enter makes a newline.
 * Some tablet keyboards deliver the Enter event WITHOUT the Shift meta bit — the app then
 * tracks Shift from Shift's own key events (v28.5). These tests pin both sources.
 */
class EnterKeyShiftTest {

    /** eventCarriesShift = the Enter event's own modifier bit; shiftTracked = seen on Shift's own KeyDown. */
    private fun shouldSend(eventCarriesShift: Boolean, shiftTracked: Boolean): Boolean =
        AppPrefs.enterShouldSend(hardwareKey = true, shift = eventCarriesShift || shiftTracked)

    @Test fun plainEnterSends() = assertTrue(shouldSend(false, false))

    @Test fun shiftBitOnEnterMakesNewline() = assertFalse(shouldSend(true, false))

    /** The v28.5 fix: keyboard drops the Shift bit on Enter, but Shift's own KeyDown was seen. */
    @Test fun trackedShiftWithoutBitMakesNewline() = assertFalse(shouldSend(false, true))

    @Test fun softwareKeyboardIgnoresShiftEntirely() {
        assertFalse(AppPrefs.enterShouldSend(hardwareKey = false, shift = false, soft = AppPrefs.EnterKey.NEWLINE))
        assertTrue(AppPrefs.enterShouldSend(hardwareKey = false, shift = false, soft = AppPrefs.EnterKey.SEND))
    }
}
