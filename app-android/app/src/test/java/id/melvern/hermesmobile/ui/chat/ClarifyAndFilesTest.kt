package id.melvern.hermesmobile.ui.chat

import id.melvern.hermesmobile.ui.components.MarkdownParser
import id.melvern.hermesmobile.ui.components.MdBlock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClarifyAndFilesTest {
    private fun obj(s: String) = Json.parseToJsonElement(s).jsonObject

    @Test
    fun `single clarify keeps its choices and answers with {answer}`() {
        val cq = AskClarify.parse("r1", obj("""{"question":"Which?","choices":["A","B"]}"""))
        assertEquals(1, cq.questions.size)
        assertEquals(listOf("A", "B"), cq.questions[0].choices)
        assertNull(cq.questions[0].qid)
        assertEquals("""{"answer":"B"}""", cq.result(listOf("B")).toString())
    }

    @Test
    fun `batch clarify answers every qid at once`() {
        val cq = AskClarify.parse("r2", obj("""{"questions":[
            {"qid":"q1","question":"Color?","choices":["Red","Blue"]},
            {"qid":"q2","question":"Name?","multi_select":true,"choices":["x","y"]}]}"""))
        assertEquals(2, cq.questions.size)
        assertTrue(cq.questions[1].multi)
        assertEquals("""{"answers":{"q1":"Blue","q2":"x, y"}}""", cq.result(listOf("Blue", "x, y")).toString())
    }

    @Test
    fun `document path lines become file cards, text files are readable`() {
        val blocks = MarkdownParser.parse("Here it is:\nMEDIA:/Users/me/out/notes.md\n`/Users/me/out/build.zip`")
        val files = blocks.filterIsInstance<MdBlock.FileRef>().map { it.path }
        assertEquals(listOf("/Users/me/out/notes.md", "/Users/me/out/build.zip"), files)
        assertTrue(MarkdownParser.isTextFile("/x/notes.md"))
        assertFalse(MarkdownParser.isTextFile("/x/build.zip"))
        assertTrue(MarkdownParser.containsMediaLine("hi\nMEDIA:/Users/me/a.pdf"))
    }

    @Test
    fun `path mentioned inside a sentence stays text`() {
        val blocks = MarkdownParser.parse("I saved it to /Users/me/out/notes.md for you.")
        assertTrue(blocks.none { it is MdBlock.FileRef })
    }
}
