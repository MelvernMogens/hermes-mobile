package id.melvern.hermesmobile.ui.components

import id.melvern.hermesmobile.ui.components.MdBlock
import id.melvern.hermesmobile.ui.components.MdSpan
import id.melvern.hermesmobile.ui.components.MarkdownParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** M9: parser tabel markdown + video path + rapikan teks bot. */
class MarkdownM9Test {

    @Test
    fun `tabel header dan body`() {
        val blocks = MarkdownParser.parse(
            "| Kolom A | Kolom B |\n|---|---|\n| satu | dua |\n| tiga | **empat** |"
        )
        assertEquals(1, blocks.size)
        val t = blocks[0] as MdBlock.Table
        assertEquals(2, t.header.size)
        assertEquals(2, t.rows.size)
        assertEquals(2, t.rows[0].size)
        assertTrue(t.rows[0][0].any { it is MdSpan.Text && it.text == "satu" })
        assertTrue(t.rows[1][1].any { it is MdSpan.Bold })
    }

    @Test
    fun `tabel dengan alignment separator`() {
        val blocks = MarkdownParser.parse("| a | b |\n|:---|---:|\n| 1 | 2 |")
        val t = blocks[0] as MdBlock.Table
        assertEquals(2, t.rows[0].size)
    }

    @Test
    fun `tabel escaped pipe di dalam sel`() {
        val blocks = MarkdownParser.parse("| a \\| b | c |\n|---|---|\n| x | y |")
        val t = blocks[0] as MdBlock.Table
        assertEquals(2, t.header.size)
        assertTrue(t.header[0].any { it is MdSpan.Text && it.text.contains("a | b") })
    }

    @Test
    fun `tabel di antara paragraf`() {
        val blocks = MarkdownParser.parse("awal\n\n| h |\n|---|\n| v |\n\nakhir")
        assertEquals(3, blocks.size)
        assertTrue(blocks[1] is MdBlock.Table)
    }

    @Test
    fun `baris pipe tanpa separator bukan tabel`() {
        val blocks = MarkdownParser.parse("lihat | pipa | saja")
        assertEquals(1, blocks.size)
        assertTrue(blocks[0] is MdBlock.Paragraph)
    }

    @Test
    fun `baris path video jadi VideoRef`() {
        val blocks = MarkdownParser.parse("/Users/melvern/Desktop/clip.mp4")
        assertEquals(1, blocks.size)
        assertEquals("/Users/melvern/Desktop/clip.mp4", (blocks[0] as MdBlock.VideoRef).path)
    }

    @Test
    fun `video dalam MEDIA dan backtick`() {
        assertEquals(
            "/tmp/a.mov",
            (MarkdownParser.parse("MEDIA: /tmp/a.mov")[0] as MdBlock.VideoRef).path,
        )
        assertEquals(
            "/tmp/b.webm",
            (MarkdownParser.parse("`/tmp/b.webm`")[0] as MdBlock.VideoRef).path,
        )
    }

    @Test
    fun `videoPathsIn distinct`() {
        val paths = MarkdownParser.videoPathsIn("lihat /a/x.mp4 dan /a/x.mp4 lalu /b/y.mkv")
        assertEquals(listOf("/a/x.mp4", "/b/y.mkv"), paths)
    }

    @Test
    fun `bullet nested level dari indent`() {
        val blocks = MarkdownParser.parse("- atas\n  - bawah\n    - lebih bawah")
        // tiap level jadi BulletList sendiri (indentasi dipertahankan via level)
        val levels = blocks.filterIsInstance<MdBlock.BulletList>().map { it.items.first().second }
        assertEquals(listOf(0, 1, 2), levels)
    }

    @Test
    fun `spasi ganda di paragraf dikolaps`() {
        val blocks = MarkdownParser.parse("kata  kata   dan   lagi")
        val spans = (blocks[0] as MdBlock.Paragraph).spans
        assertTrue(spans.joinToString("") { it.text }.contains("kata kata dan lagi"))
    }

    @Test
    fun `bullet beda level jadi list terpisah`() {
        val blocks = MarkdownParser.parse("- luar\n  - dalam")
        assertEquals(2, blocks.size)
        assertTrue(blocks[0] is MdBlock.BulletList)
        assertTrue(blocks[1] is MdBlock.BulletList)
    }
}
