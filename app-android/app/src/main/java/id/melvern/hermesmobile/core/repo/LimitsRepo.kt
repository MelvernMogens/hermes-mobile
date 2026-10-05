package id.melvern.hermesmobile.core.repo

import id.melvern.hermesmobile.core.auth.DashboardAuth
import id.melvern.hermesmobile.core.store.ConnectionSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull

/**
 * /api/mobile-limits (server/mobile_limits.py): limit plan per provider (angka yang
 * sama dengan `/usage` di Hermes — sesi 5 jam, mingguan) + RAM Mac.
 */
class LimitsRepo(private val settings: ConnectionSettings) {

    data class Window(val label: String, val usedPercent: Double, val resetsAtEpochMs: Long?)
    data class Plan(val provider: String, val label: String, val plan: String?, val windows: List<Window>, val note: String?)
    data class Ram(val usedBytes: Long, val totalBytes: Long, val usedPercent: Double, val swapUsedBytes: Long?)
    data class Limits(val plans: List<Plan>, val ram: Ram?)

    suspend fun fetch(): Limits? = withContext(Dispatchers.IO) {
        val base = settings.baseUrl.trim().trimEnd('/')
        val auth = DashboardAuth(base)
        if (!auth.ensureLogin(settings.username, settings.password)) return@withContext null
        auth.getJson("$base/api/mobile-limits")?.let { parse(it) }
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        private fun str(o: JsonObject, k: String): String? =
            (o[k] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }

        private fun isoToEpochMs(s: String?): Long? = try {
            s?.let { java.time.OffsetDateTime.parse(it).toInstant().toEpochMilli() }
        } catch (_: Throwable) { null }

        /** Pure — di-unit-test. null = body rusak. */
        fun parse(body: String): Limits? = try {
            val root = json.parseToJsonElement(body).jsonObject
            val plans = root["plans"]?.jsonArray?.mapNotNull { e ->
                val o = e.jsonObject
                val windows = o["windows"]?.jsonArray?.mapNotNull { w ->
                    val wo = w.jsonObject
                    val pct = (wo["used_percent"] as? JsonPrimitive)?.doubleOrNull ?: return@mapNotNull null
                    Window(str(wo, "label") ?: "Usage", pct.coerceIn(0.0, 100.0), isoToEpochMs(str(wo, "resets_at")))
                }.orEmpty()
                if (windows.isEmpty()) null
                else Plan(str(o, "provider") ?: "", str(o, "label") ?: str(o, "provider") ?: "Provider", str(o, "plan"), windows, str(o, "note"))
            }.orEmpty()
            val ram = (root["ram"] as? JsonObject)?.let { r ->
                val used = (r["used_bytes"] as? JsonPrimitive)?.longOrNull
                val total = (r["total_bytes"] as? JsonPrimitive)?.longOrNull
                if (used == null || total == null || total <= 0) null
                else Ram(used, total, (r["used_percent"] as? JsonPrimitive)?.doubleOrNull ?: (used * 100.0 / total),
                    (r["swap_used_bytes"] as? JsonPrimitive)?.longOrNull)
            }
            Limits(plans, ram)
        } catch (_: Throwable) { null }

        /** "resets in 3h 20m" / "resets in 6d" — singkat, tanpa detik. */
        fun resetIn(epochMs: Long?, nowMs: Long = System.currentTimeMillis()): String? {
            epochMs ?: return null
            val mins = ((epochMs - nowMs) / 60_000).coerceAtLeast(0)
            return when {
                mins < 1 -> "resets now"
                mins < 60 -> "resets in ${mins}m"
                mins < 48 * 60 -> if (mins % 60 == 0L) "resets in ${mins / 60}h" else "resets in ${mins / 60}h ${mins % 60}m"
                else -> ((mins % (24 * 60)) / 60).let { h -> if (h == 0L) "resets in ${mins / (24 * 60)}d" else "resets in ${mins / (24 * 60)}d ${h}h" }
            }
        }

        fun gb(bytes: Long): String = "%.1f GB".format(java.util.Locale.US, bytes / 1024.0 / 1024.0 / 1024.0)
    }
}
