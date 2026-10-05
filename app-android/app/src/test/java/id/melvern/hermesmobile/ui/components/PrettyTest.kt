package id.melvern.hermesmobile.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class PrettyTest {
    @Test
    fun `model slugs become product names`() {
        assertEquals("Opus 5.5", Pretty.model("claude-opus-5-5"))
        assertEquals("GLM 5.3 Flash", Pretty.model("glm-5.3-flash"))
        assertEquals("GLM 5.3", Pretty.model("zai/glm-5.3"))
        assertEquals("Sonnet 5", Pretty.model("claude-sonnet-5"))
        assertEquals("", Pretty.model(null))
    }

    @Test
    fun `profile names read as names`() {
        assertEquals("Hermes", Pretty.profile("default"))
        assertEquals("PM", Pretty.profile("pm"))
        assertEquals("Ops", Pretty.profile("ops"))
        assertEquals("Coder", Pretty.profile("coder"))
    }

    @Test
    fun `mention tokens never leak into previews`() {
        val p = Pretty.preview("@url:`https://github.com/Hungbocluaqua/hermex-android-port-hermes` oke ini")
        assertEquals("github.com/…/hermex-android-port-hermes oke ini", p)
        val f = Pretty.preview("@file:`Library/Containers/at.EternalStorms.Yoink/shot.png` cek")
        assertEquals("shot.png cek", f)
    }

    @Test
    fun `markdown links and bare urls collapse to readable text`() {
        assertEquals("cek Google dulu", Pretty.preview("cek [Google](https://www.google.com/search?q=x) dulu"))
        assertEquals("buka github.com sekarang", Pretty.preview("buka https://github.com/a/b sekarang"))
        assertEquals("beres", Pretty.preview("beres -"))
    }
}
