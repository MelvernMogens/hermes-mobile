package id.melvern.hermesmobile.core.store

import id.melvern.hermesmobile.core.store.AppPrefs.EnterKey
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppPrefsTest {
    @Test
    fun `phone keyboard enter is a new line by default`() {
        assertFalse(AppPrefs.enterShouldSend(hardwareKey = false, shift = false, soft = EnterKey.NEWLINE, hw = true))
    }

    @Test
    fun `phone keyboard enter sends only when chosen in settings`() {
        assertTrue(AppPrefs.enterShouldSend(hardwareKey = false, shift = false, soft = EnterKey.SEND, hw = true))
    }

    @Test
    fun `tablet keyboard enter sends and shift-enter is a new line`() {
        assertTrue(AppPrefs.enterShouldSend(hardwareKey = true, shift = false, soft = EnterKey.NEWLINE, hw = true))
        assertFalse(AppPrefs.enterShouldSend(hardwareKey = true, shift = true, soft = EnterKey.NEWLINE, hw = true))
        assertFalse(AppPrefs.enterShouldSend(hardwareKey = true, shift = false, soft = EnterKey.NEWLINE, hw = false))
    }
}
