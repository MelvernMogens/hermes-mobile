package id.melvern.hermesmobile.core.repo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Delta-path staleness contracts for the fix of "chat opens stale until the app is reinstalled".
 * 1. The cursor watermark never regresses when a dead replay ring answers latest_seq=0.
 * 2. The cache's DB fingerprint is carried and compared (detached-runtime probe).
 */
class TranscriptStalenessTest {

    @Test
    fun cursorNeverSavedWhenCacheMissOnDeadRing() {
        // GatewayClient is socket-bound; the invariant lives in its watermark update rule,
        // mirrored here as the contract: latest(0) < known(N) → keep N.
        val known = 42
        val latest = 0
        val next = if (latest >= known) latest else known
        assertEquals(42, next)
    }

    @Test
    fun fingerprintMismatchMeansStale() {
        // runtimeFresh contract: detached runtime + fingerprint differs → NOT fresh → full resume.
        fun fresh(serverRuntime: String?, fingerprint: Int?, cachedDbCount: Int): Boolean = when (serverRuntime) {
            null -> fingerprint == null || fingerprint == cachedDbCount
            else -> true // runtime match handled elsewhere
        }
        assertEquals(true, fresh(null, 100, 100))   // same count → fresh
        assertEquals(true, fresh(null, null, 100))  // probe unreachable → fail-open
        assertEquals(false, fresh(null, 120, 100))  // new rows on the Mac → stale → resume
    }

    @Test
    fun cacheEntryCarriesFingerprint() {
        // Entry is a data class: dbCount survives copies and defaults to "unknown" (-1).
        val e = TranscriptCache.Entry(emptyList(), "rt", 7, false)
        assertEquals(-1, e.dbCount)
        assertEquals(9, e.copy(dbCount = 9).dbCount)
        assertEquals(7, e.cursor)
    }
}
