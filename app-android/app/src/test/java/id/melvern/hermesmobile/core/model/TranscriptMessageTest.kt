package id.melvern.hermesmobile.core.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TranscriptMessageTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `tool rows with object args decode instead of being dropped`() {
        // shape session.resume actually returns for tool rows (args = dict)
        val raw = """{"role":"tool","name":"terminal","context":"ls -la","args":{"command":"ls -la","timeout":30}}"""
        val m = json.decodeFromString(TranscriptMessage.serializer(), raw)
        assertEquals("tool", m.role)
        val text = m.argsText
        assertNotNull(text)
        assertTrue(text!!.contains("\"command\""))
    }

    @Test
    fun `string args and missing args still work`() {
        val s = json.decodeFromString(TranscriptMessage.serializer(), """{"role":"tool","name":"x","args":"plain"}""")
        assertEquals("plain", s.argsText)
        val n = json.decodeFromString(TranscriptMessage.serializer(), """{"role":"assistant","text":"hi"}""")
        assertEquals(null, n.argsText)
    }
}
