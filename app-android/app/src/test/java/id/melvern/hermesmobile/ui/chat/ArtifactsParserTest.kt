package id.melvern.hermesmobile.ui.chat

import id.melvern.hermesmobile.core.model.ChatItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M13: unit test parser artifacts (pure fn) — link + image + video + file
 * dari sampel teks, plus distinct/sort/grup hari.
 */
class ArtifactsParserTest {

    private fun user(text: String, at: Double? = 1000.0) = ChatItem.User(text, at = at)
    private fun bot(text: String, at: Double? = 1001.0) = ChatItem.Assistant(text, done = true, at = at)

    @Test
    fun `link http dan https dari teks assistant`() {
        val a = ArtifactsParser.parse(listOf(bot("Lihat https://example.com/page dan http://plain.org, itu saja.")))
        assertEquals(2, a.size)
        assertTrue(a.all { it.type == Artifact.Type.Link })
        assertTrue(a.any { it.value == "https://example.com/page" && it.title == "example.com" })
        // koma di ujung kalimat tidak ikut URL
        assertTrue(a.any { it.value == "http://plain.org" && it.title == "plain.org" })
    }

    @Test
    fun `link markdown pakai domain sebagai title`() {
        val a = ArtifactsParser.parse(listOf(bot("[dokumentasi](https://docs.situs.id/kotlin?q=1) ada.")))
        assertEquals(1, a.size)
        assertEquals("docs.situs.id", a.first().title)
        assertEquals("https://docs.situs.id/kotlin?q=1", a.first().value)
    }

    @Test
    fun `link host ip pakai jalur sebagai title`() {
        val a = ArtifactsParser.parse(listOf(bot("proxy di http://10.0.2.2:8790/api/health oke.")))
        assertEquals(1, a.size)
        assertEquals("api/health", a.first().title)
        // port polos tanpa jalur → fallback host
        val b = ArtifactsParser.parse(listOf(bot("buka http://127.0.0.1:8790 ya")))
        assertEquals("127.0.0.1:8790", b.first().title)
    }

    @Test
    fun `foto dari path unix di teks`() {
        val a = ArtifactsParser.parse(listOf(
            bot("Hasil screenshot:\n/Users/x/.hermes/images/upload_2026_0928_101010_1.png\noke?",
                at = 1002.0),
        ))
        assertEquals(1, a.size)
        assertEquals(Artifact.Type.Image, a.first().type)
        assertEquals("/Users/x/.hermes/images/upload_2026_0928_101010_1.png", a.first().value)
        assertEquals("upload_2026_0928_101010_1.png", a.first().title)
    }

    @Test
    fun `video dan file non-media dari teks`() {
        val a = ArtifactsParser.parse(listOf(bot(
            "Video: /Users/x/out/demo.mp4\nFile: /Users/x/Docs/laporan.pdf dan /Users/x/app-release.apk",
        )))
        assertEquals(3, a.size)
        assertTrue(a.any { it.type == Artifact.Type.Video && it.value.endsWith("demo.mp4") })
        assertTrue(a.any { it.type == Artifact.Type.File && it.value.endsWith("laporan.pdf") })
        assertTrue(a.any { it.type == Artifact.Type.File && it.value.endsWith("app-release.apk") })
        // video TIDAK ikut dihitung sebagai File (mp4 bukan ekstensi file list)
        assertTrue(a.none { it.type == Artifact.Type.File && it.value.endsWith(".mp4") })
    }

    @Test
    fun `chip attach file di pesan user`() {
        val a = ArtifactsParser.parse(listOf(user("@file:data-notes.txt\ntolong rangkum", at = 999.0)))
        assertEquals(1, a.size)
        assertEquals(Artifact.Type.File, a.first().type)
        assertEquals("data-notes.txt", a.first().value)
        assertTrue(a.first().fromUser)
    }

    @Test
    fun `chip attach image di pesan user`() {
        val a = ArtifactsParser.parse(listOf(user("@image:foto-lokasi.png", at = 998.0)))
        assertEquals(1, a.size)
        assertEquals(Artifact.Type.Image, a.first().type)
        assertEquals("foto-lokasi.png", a.first().value)
    }

    @Test
    fun `campuran semua tipe dari transcript multi pesan`() {
        val items = listOf(
            user("cek https://news.site/artikel dan attach @file:notulensi.md", at = 900.0),
            ChatItem.Tool("web_search", "done"),
            bot(
                "Ringkasan:\n- sumber: https://news.site/artikel\n- grafik: /tmp/chart_2026.png\n" +
                    "- video demo: /Users/x/clip/demo-screen.mov\n- lampiran: /Users/x/Docs/quote.pdf",
                at = 950.0,
            ),
        )
        val a = ArtifactsParser.parse(items)
        // link artikel muncul 2x (user + bot) → distinct
        assertEquals(1, a.count { it.value == "https://news.site/artikel" })
        assertEquals(setOf(Artifact.Type.Link, Artifact.Type.Image, Artifact.Type.Video, Artifact.Type.File), a.map { it.type }.toSet())
        // terbaru dulu: pesan bot (950) sebelum pesan user (900)
        assertTrue(a.indexOfFirst { it.at == 950.0 } < a.indexOfFirst { it.at == 900.0 })
    }

    @Test
    fun `distinct per value keeps kemunculan terbaru`() {
        val a = ArtifactsParser.parse(listOf(
            bot("lihat https://sama.id/a", at = 100.0),
            bot("lagi https://sama.id/a", at = 200.0),
        ))
        assertEquals(1, a.size)
        assertEquals(200.0, a.first().at!!, 0.0)
    }

    @Test
    fun `streaming assistant belum done di-skip, notice dan tool di-skip`() {
        val a = ArtifactsParser.parse(listOf(
            ChatItem.Assistant("draft https://draft.io/x", done = false, at = 100.0),
            ChatItem.NoticeLine("notice https://notice.io"),
            ChatItem.Tool("web_search", "done", detail = "https://detail.io"),
        ))
        assertTrue(a.isEmpty())
    }

    @Test
    fun `rows menyisipkan pemisah hari saat tanggal berganti`() {
        val today = System.currentTimeMillis() / 1000.0
        val yesterday = today - 86_400.0
        val a = ArtifactsParser.parse(listOf(
            bot("https://lama.io", at = yesterday),
            bot("https://baru.io", at = today),
        ))
        val rows = ArtifactsParser.rows(a) { "DAY:${it.dayOfWeek}" }
        assertEquals(4, rows.size)
        assertTrue(rows[0] is ArtifactRow.Day)
        assertTrue(rows[1] is ArtifactRow.Item && (rows[1] as ArtifactRow.Item).artifact.value == "https://baru.io")
        assertTrue(rows[2] is ArtifactRow.Day)
        assertTrue(rows[3] is ArtifactRow.Item && (rows[3] as ArtifactRow.Item).artifact.value == "https://lama.io")
    }

    @Test
    fun `transcript kosong menghasilkan list kosong`() {
        assertTrue(ArtifactsParser.parse(emptyList()).isEmpty())
    }

    // ── Review M13 (fix HIGH#1 + MED#5) — regresi phantom path-in-URL & chip ──

    @Test
    fun `url berakhiran ekstensi file tidak jadi phantom File`() {
        val a = ArtifactsParser.parse(listOf(bot("unduh di https://example.com/report.pdf ya.")))
        assertEquals(1, a.size)
        assertEquals(Artifact.Type.Link, a.first().type)
        assertEquals("https://example.com/report.pdf", a.first().value)
        assertTrue(a.none { it.type == Artifact.Type.File })
    }

    @Test
    fun `url berakhiran ekstensi gambar tidak jadi phantom Image`() {
        val a = ArtifactsParser.parse(listOf(bot("logo: https://cdn.site.net/brand/photo.png done")))
        assertEquals(1, a.size)
        assertEquals(Artifact.Type.Link, a.first().type)
        assertTrue(a.none { it.type == Artifact.Type.Image })
    }

    @Test
    fun `path lokal tetap kedeteksi di samping url file`() {
        val a = ArtifactsParser.parse(listOf(bot(
            "server: https://api.example.com/data.json\nlokal: /tmp/extract/data.json",
        )))
        assertEquals(2, a.size)
        assertTrue(a.any { it.type == Artifact.Type.Link && it.value.endsWith("data.json") })
        assertTrue(a.any { it.type == Artifact.Type.File && it.value == "/tmp/extract/data.json" })
    }

    @Test
    fun `chip file trailing punctuation dibersihkan`() {
        val a = ArtifactsParser.parse(listOf(user("@file:notes.md, tolong rangkum", at = 990.0)))
        assertEquals(1, a.size)
        assertEquals("notes.md", a.first().value)
    }

    @Test
    fun `chip di dalam url atau bukan awal kata diabaikan`() {
        // @image: di dalam URL → bukan chip
        val a = ArtifactsParser.parse(listOf(user("lihat https://site.com/@image:pic.png yuk", at = 990.0)))
        assertEquals(1, a.size)
        assertEquals(Artifact.Type.Link, a.first().type)
        // @file: nempel di tengah kata → bukan chip
        val b = ArtifactsParser.parse(listOf(user("format emailx@file:doc.txt dipakai", at = 991.0)))
        assertTrue(b.isEmpty())
    }

    @Test
    fun `url di dalam inline code backtick dibersihkan`() {
        // pola asli dari transcript nyata: "`http://127.0.0.1:8787/health`."
        val a = ArtifactsParser.parse(listOf(bot("cek `http://127.0.0.1:8787/health`. oke?")))
        assertEquals(1, a.size)
        assertEquals("http://127.0.0.1:8787/health", a.first().value)
        // IP host + path → judul = path (by design, host polos kurang informatif)
        assertEquals("health", a.first().title)
    }

    @Test
    fun `bare https proto di backtick bukan link`() {
        // "`https://`" di prosa → bukan URL nyata, jangan jadi artifact
        val a = ArtifactsParser.parse(listOf(bot("protokol `https://` dipakai endpoint itu.")))
        assertTrue(a.isEmpty())
    }
}
