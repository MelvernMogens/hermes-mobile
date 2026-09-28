package id.melvern.hermesmobile.ui.chat

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Output tool live (ToolCompletePayload.result, mode non-verbose) harus jadi isi code block. */
class ToolResultTextTest {
    @Test fun objectWithOutput_returnsOutputField() {
        val r = Json.parseToJsonElement("""{"output":"hello\n","exit_code":0}""")
        assertEquals("hello", toolResultText(r))
    }

    @Test fun plainString_returnedTrimmed() {
        assertEquals("line1\nline2", toolResultText(JsonPrimitive("  line1\nline2 \n")))
    }

    @Test fun objectWithoutKnownField_fallsBackToJson() {
        val r = Json.parseToJsonElement("""{"exit_code":0}""")
        assertEquals("""{"exit_code":0}""", toolResultText(r))
    }

    @Test fun nullOrBlank_isNull() {
        assertNull(toolResultText(null))
        assertNull(toolResultText(JsonNull))
        assertNull(toolResultText(JsonPrimitive("   ")))
    }
}
