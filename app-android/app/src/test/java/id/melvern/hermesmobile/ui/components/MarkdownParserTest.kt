package id.melvern.hermesmobile.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit test parser markdown (bagian pure Kotlin — render Compose diverifikasi
 * via emulator). Kontrak: parse() mengubah teks mentah assistant jadi block.
 */
class MarkdownParserTest {

    // ---------- inline ----------

    @Test
    fun `teks polos jadi satu span Text`() {
        val blocks = MarkdownParser.parse("halo dunia")
        assertEquals(1, blocks.size)
        val p = blocks[0] as MdBlock.Paragraph
        assertEquals(listOf(MdSpan.Text("halo dunia")), p.spans)
    }

    @Test
    fun `bold italic dan inline code keparse`() {
        val p = MarkdownParser.parse("ini **tebal** dan *miring* dan `kode`")[0] as MdBlock.Paragraph
        val spans = p.spans
        assertTrue(spans.contains(MdSpan.Bold("tebal")))
        assertTrue(spans.contains(MdSpan.Italic("miring")))
        assertTrue(spans.contains(MdSpan.InlineCode("kode")))
        // teks di sekitarnya tetap utuh
        assertEquals(MdSpan.Text("ini "), spans[0])
        assertEquals(MdSpan.Text(" dan "), spans[2])
        assertEquals(MdSpan.Text(" dan "), spans[4])
    }

    @Test
    fun `link markdown jadi Link dengan url`() {
        val p = MarkdownParser.parse("coba [Google](https://google.com) ya")[0] as MdBlock.Paragraph
        assertTrue(p.spans.contains(MdSpan.Link("Google", "https://google.com")))
    }

    @Test
    fun `url polos di teks otomatis jadi link`() {
        val p = MarkdownParser.parse("lihat https://example.com/a?b=1 detail")[0] as MdBlock.Paragraph
        val link = p.spans.firstOrNull { it is MdSpan.Link } as? MdSpan.Link
        assertEquals("https://example.com/a?b=1", link?.url)
        assertEquals("https://example.com/a?b=1", link?.text)
    }

    @Test
    fun `bold di dalam kalimat campur tanpa makan teks sekitar`() {
        val p = MarkdownParser.parse("a **b** c *d* e")[0] as MdBlock.Paragraph
        assertEquals(
            listOf(
                MdSpan.Text("a "), MdSpan.Bold("b"), MdSpan.Text(" c "),
                MdSpan.Italic("d"), MdSpan.Text(" e"),
            ),
            p.spans,
        )
    }

    // ---------- block ----------

    @Test
    fun `heading level 1 dan 2 dibedain`() {
        val blocks = MarkdownParser.parse("# Judul\n\n## Sub")
        val h1 = blocks[0] as MdBlock.Heading
        val h2 = blocks[1] as MdBlock.Heading
        assertEquals(1, h1.level)
        assertEquals("Judul", (h1.spans[0] as MdSpan.Text).text)
        assertEquals(2, h2.level)
    }

    @Test
    fun `code block dengan header bahasa`() {
        val blocks = MarkdownParser.parse("teks\n```python\nprint(1)\nprint(2)\n```\nhabis")
        assertEquals(3, blocks.size)
        val code = blocks[1] as MdBlock.CodeBlock
        assertEquals("python", code.lang)
        assertEquals("print(1)\nprint(2)", code.code)
    }

    @Test
    fun `list butir dan bernomor dengan indent`() {
        val blocks = MarkdownParser.parse("- satu\n- dua **kuat**\n\n1. pertama\n2. kedua")
        val ul = blocks[0] as MdBlock.BulletList
        assertEquals(2, ul.items.size)
        assertEquals(listOf(MdSpan.Text("satu")), ul.items[0])
        assertTrue(ul.items[1].any { it is MdSpan.Bold })
        val ol = blocks[1] as MdBlock.NumberList
        assertEquals(2, ol.items.size)
        assertEquals(listOf(MdSpan.Text("pertama")), ol.items[0])
    }

    @Test
    fun `blockquote jadi Quote`() {
        val blocks = MarkdownParser.parse("awal\n\n> dikutip **ini**\n\nakhir")
        assertEquals(3, blocks.size)
        val q = blocks[1] as MdBlock.Quote
        assertTrue(q.spans.any { it is MdSpan.Bold && it.text == "ini" })
    }

    @Test
    fun `multi baris dalam satu paragraf tetap satu block`() {
        val blocks = MarkdownParser.parse("baris satu\nbaris dua")
        assertEquals(1, blocks.size)
        val p = blocks[0] as MdBlock.Paragraph
        assertTrue((p.spans[0] as MdSpan.Text).text.startsWith("baris satu"))
    }

    @Test
    fun `list item lanjut tanpa baris kosong tetap satu list`() {
        val blocks = MarkdownParser.parse("- a\n- b\n- c")
        assertEquals(1, blocks.size)
        assertEquals(3, (blocks[0] as MdBlock.BulletList).items.size)
    }

    @Test
    fun `list pakai bintang juga jalan`() {
        val blocks = MarkdownParser.parse("* a\n* b")
        assertEquals(1, blocks.size)
        assertEquals(2, (blocks[0] as MdBlock.BulletList).items.size)
    }
}
