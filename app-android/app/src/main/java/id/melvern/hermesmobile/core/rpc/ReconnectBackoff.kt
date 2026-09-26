package id.melvern.hermesmobile.core.rpc

import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

/**
 * Full-jitter exponential backoff — port dari apps/shared/src/reconnect-backoff.ts.
 * Jitter penuh supaya armada client gak retry barengan (reconnect storm).
 */
object ReconnectBackoff {
    private const val BASE_DELAY_MS = 300L
    private const val CAP_MS = 15_000L
    private const val MAX_EXPONENT = 32

    /** Delay untuk percobaan ke-`attempt` (0-indexed). */
    fun delayMs(attempt: Int): Long {
        val exponent = min(attempt.coerceAtLeast(0), MAX_EXPONENT)
        val ceiling = min(CAP_MS, BASE_DELAY_MS * 2.0.pow(exponent).toLong())
        return (Random.nextLong(ceiling)).coerceAtLeast(0)
    }

    /** Ceiling deterministik — buat nampilin "reconnect dalam Ns". */
    fun ceilingMs(attempt: Int): Long {
        val exponent = min(attempt.coerceAtLeast(0), MAX_EXPONENT)
        return min(CAP_MS, BASE_DELAY_MS * 2.0.pow(exponent).toLong())
    }
}
