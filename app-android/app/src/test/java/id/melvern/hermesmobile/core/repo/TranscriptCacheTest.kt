package id.melvern.hermesmobile.core.repo

import android.util.Log
import id.melvern.hermesmobile.core.model.ChatItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
/** M9 (item 6): TranscriptCache LRU + hit/miss counter (Log no-op via returnDefaultValues). */
class TranscriptCacheTest {

    @Before
    fun reset() = TranscriptCache.clear()

    @Test
    fun `miss lalu hit`() {
        assertNull(TranscriptCache.get("s1"))
        TranscriptCache.snapshot("s1", listOf(ChatItem.NoticeLine("x")), "r1", 5, false)
        val e = TranscriptCache.get("s1")
        assertNotNull(e)
        assertEquals(5, e!!.cursor)
        assertEquals(1, TranscriptCache.hits.value)
        assertEquals(1, TranscriptCache.misses.value)
    }

    @Test
    fun `LRU evict di 9 entry`() {
        repeat(8) { i -> TranscriptCache.snapshot("s$i", emptyList(), "r$i", i, false) }
        assertEquals(8, TranscriptCache.size())
        TranscriptCache.get("s0") // refresh s0 biar bukan yang tertua
        TranscriptCache.snapshot("s8", emptyList(), "r8", 8, false)
        assertEquals(8, TranscriptCache.size())
        assertNull(TranscriptCache.get("s1")) // tertua terevict
        assertNotNull(TranscriptCache.get("s0"))
    }

    @Test
    fun `setCursor tanpa entry aman`() {
        TranscriptCache.setCursor("nobody", 3)
        assertEquals(0, TranscriptCache.size())
    }
}
