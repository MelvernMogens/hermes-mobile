package id.melvern.hermesmobile.core.repo

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M17 (fix A): LastMsgStore — preview pesan terakhir per stored id.
 * Pure JVM (object tanpa Android framework).
 */
class LastMsgStoreTest {

    @org.junit.Before
    fun resetStore() {
        LastMsgStore.clear()
        LastMsgStore.dropOlderThanMs = 7L * 24 * 3600 * 1000
    }


    @After
    fun reset() {
        LastMsgStore.clear()
        LastMsgStore.dropOlderThanMs = 7L * 24 * 3600 * 1000
    }

    @Test
    fun `put menyimpan teks dipangkas 80 char dan newline diratakan`() {
        val long = "a".repeat(200)
        LastMsgStore.put("s1", long)
        assertEquals(80, LastMsgStore.get("s1")!!.text.length)
        LastMsgStore.put("s2", "baris1\nbaris2")
        assertEquals("baris1 baris2", LastMsgStore.get("s2")!!.text)
    }

    @Test
    fun `put menolak teks kosong`() {
        LastMsgStore.put("s1", "ada")
        LastMsgStore.put("s1", "   \n ")
        assertEquals("ada", LastMsgStore.get("s1")!!.text)
        LastMsgStore.put("s3", "")
        assertNull(LastMsgStore.get("s3"))
    }

    @Test
    fun `update dari payload message complete`() {
        // payload event message.complete: {"type":"message.complete","text":"..."}
        val payload = buildJsonObject {
            put("type", "message.complete")
            put("text", "jawaban agent terbaru")
        }
        val sid = "20261002_175753_2fc48e"
        LastMsgStore.put(sid, LastMsgStore.textFromCompletePayload(payload))
        assertEquals("jawaban agent terbaru", LastMsgStore.get(sid)!!.text)
    }

    @Test
    fun `payload tanpa text menghasilkan string kosong`() {
        val payload = buildJsonObject { put("type", "message.complete") }
        assertEquals("", LastMsgStore.textFromCompletePayload(payload))
        assertEquals("", LastMsgStore.textFromCompletePayload(null))
        // non-string tetap dibaca sebagai content primitive
        assertEquals("42", LastMsgStore.textFromCompletePayload(buildJsonObject { put("text", JsonPrimitive(42)) }))
    }

    @Test
    fun `max 64 entry - yang paling tua dibuang`() {
        repeat(70) { i -> LastMsgStore.put("s$i", "pesan $i") }
        assertEquals(64, LastMsgStore.size())
        assertNull(LastMsgStore.get("s0")) // paling tua terbuang
        assertTrue(LastMsgStore.get("s69") != null)
    }

    @Test
    fun `trim bisa buang entry lebih tua dari window`() {
        // age-pass hanya jalan saat overload (>64) — isi dummy sampai penuh dulu.
        // dropOlderThanMs -1: cutoff = now+1 → semua entry (kecuali yang baru ditulis,
        // dilindungi param keep) dianggap basi.
        repeat(64) { i -> LastMsgStore.put("old$i", "pesan $i") }
        LastMsgStore.dropOlderThanMs = -1
        LastMsgStore.put("segar", "baru") // trim jalan di put ini → semua old* terbuang
        assertNull(LastMsgStore.get("old0"))
        assertNull(LastMsgStore.get("old63"))
        assertTrue(LastMsgStore.get("segar") != null)
    }
}
