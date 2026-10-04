package id.melvern.hermesmobile.core.repo

import id.melvern.hermesmobile.core.notify.FleetBus
import id.melvern.hermesmobile.core.notify.NotifPolicy
import id.melvern.hermesmobile.core.rpc.GatewayClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.coroutines.launch

/**
 * M18: status satu bot di Fleet dashboard.
 * RUNNING = ada session live (Bot Chat / session manusia / worker) dengan
 * status != idle; IDLE = session live tapi semua idle; OFFLINE = gak ada.
 */
enum class BotStatus { RUNNING, IDLE, OFFLINE }

data class BotCard(
    val name: String,
    /** displayName kalau ada, else nama profile. */
    val label: String,
    val model: String?,
    val isDefault: Boolean,
    val status: BotStatus,
    /** canonical "Bot Chat" stored id (null = profile belum punya Bot Chat). */
    val botChatStoredId: String?,
)

/** Satu bar dolar usage (UsageBar contract: *_display pre-formatted server). */
data class UsageBarUi(
    val label: String,
    val spent: String,
    val remaining: String,
    val total: String,
    val pctUsed: Int?,
    val fill: Float,
)

data class UsageUi(
    val available: Boolean,
    val plan: UsageBarUi? = null,
    val topup: UsageBarUi? = null,
    val planName: String? = null,
    val renews: String? = null,
) {
    companion object { val UNAVAILABLE = UsageUi(available = false) }
}

/**
 * M18: pure logic fleet dashboard — mapping profile × active_list → kartu bot,
 * sorting, dan parse usage.bars. Tanpa dependensi Android (unit-testable).
 */
object FleetLogic {

    /**
     * Mapping session → profile via session_key (stored id) dari row profile:
     * canonical Bot Chat (id + resolved_id), last_session (session manusia
     * terbaru), worker_session (worker kanban/tool). Fallback runtime id
     * kalau session_key kosong.
     */
    fun buildCards(
        profiles: List<MetaRepo.ProfileRow>,
        active: List<NotifPolicy.LiveRow>,
    ): List<BotCard> {
        val byStored = active.filter { it.sessionKey.isNotBlank() }.groupBy { it.sessionKey }
        val byRuntime = active.filter { it.sessionKey.isBlank() }.associateBy { it.sid }
        return sort(profiles.map { p ->
            val keys = listOfNotNull(
                p.canonicalSession?.id,
                p.canonicalSession?.resolvedId,
                p.lastSession?.id,
                p.workerSession?.id,
            ).filter { it.isNotBlank() }.distinct()
            val rows = keys.flatMap { k -> byStored[k] ?: listOfNotNull(byRuntime[k]) }
                .distinctBy { it.sid }
            BotCard(
                name = p.name,
                label = p.displayName.ifBlank { p.name },
                model = p.model?.takeIf { it.isNotBlank() },
                isDefault = p.isDefault,
                status = when {
                    rows.isEmpty() -> BotStatus.OFFLINE
                    rows.any { it.status != "idle" } -> BotStatus.RUNNING
                    else -> BotStatus.IDLE
                },
                botChatStoredId = p.canonicalSession?.id?.takeIf { it.isNotBlank() },
            )
        })
    }

    /** Default dulu, sisanya abjad (case-insensitive). */
    fun sort(bots: List<BotCard>): List<BotCard> =
        bots.sortedWith(compareByDescending<BotCard> { it.isDefault }.thenBy { it.label.lowercase() })

    fun runningCount(bots: List<BotCard>): Int = bots.count { it.status == BotStatus.RUNNING }

    /**
     * Parse result usage.bars. Fail-open: available=false (atau field hilang)
     * → UNAVAILABLE — PEMANGGIL tidak crash, UI render fallback text.
     */
    fun parseUsage(res: JsonObject): UsageUi {
        val available = res["available"]?.jsonPrimitive?.booleanOrNull ?: false
        if (!available) return UsageUi.UNAVAILABLE
        fun bar(key: String, label: String): UsageBarUi? = (res[key] as? JsonObject)?.let { o ->
            fun s(k: String) = o[k]?.jsonPrimitive?.contentOrNull ?: ""
            UsageBarUi(
                label = label,
                spent = s("spent_display"),
                remaining = s("remaining_display"),
                total = s("total_display"),
                pctUsed = o["pct_used"]?.jsonPrimitive?.intOrNull,
                fill = (o["fill_fraction"]?.jsonPrimitive?.doubleOrNull ?: 0.0)
                    .toFloat().coerceIn(0f, 1f),
            )
        }
        return UsageUi(
            available = true,
            plan = bar("plan_bar", "Plan"),
            topup = bar("topup_bar", "Top-up"),
            planName = res["plan_name"]?.jsonPrimitive?.contentOrNull,
            renews = res["renews_display"]?.jsonPrimitive?.contentOrNull,
        )
    }
}

/**
 * M18: cache fleet cards process-wide — badge tab Overview (MainActivity)
 * dan OverviewScreen baca dari sini. publish saat OverviewRepo.fetchBots;
 * recomputeFrom memakai snapshot active_list poller M14 (tanpa RPC baru).
 */
object BotFleet {
    private val _bots = MutableStateFlow<List<BotCard>?>(null)
    val bots: StateFlow<List<BotCard>?> = _bots

    @Volatile var profileRows: List<MetaRepo.ProfileRow>? = null
        private set

    fun publish(cards: List<BotCard>, rows: List<MetaRepo.ProfileRow>) {
        profileRows = rows
        _bots.value = cards
    }

    /**
     * Recompute status dari snapshot poller M14. Kalau profiles.list belum
     * pernah di-fetch (Overview belum dibuka), fetch di sini — badge bottom bar
     * harus hidup bahkan sebelum tab Overview pernah dibuka.
     */
    fun recomputeFrom(active: List<NotifPolicy.LiveRow>) {
        val rows = profileRows
        if (rows == null) {
            pendingRefresh.value = true
            return
        }
        _bots.value = FleetLogic.buildCards(rows, active)
    }

    /** OverviewScreen set ini sekali sebelum screen pertama dibuka (badge). */
    val pendingRefresh = MutableStateFlow(false)

    fun ensureProfiles(client: GatewayClient) {
        if (profileRows != null) return
        appScope.launch {
            try {
                OverviewRepo(client).fetchBots()
            } catch (_: Throwable) {
            }
        }
    }

    /** appScope — diset dari HermesApp (ciclus lifetime proses). */
    lateinit var appScope: kotlinx.coroutines.CoroutineScope
}

/**
 * M18: data Overview screen — semua dari RPC yang udah ada, tanpa poller baru.
 */
class OverviewRepo(private val client: GatewayClient) {

    /**
     * profiles.list include_sessions=true (canonical Bot Chat + previews)
     * × active_list. Active list diambil dari FleetBus (snapshot poller M14);
     * direct call cuma fallback sekali kalau poller belum pernah jalan.
     */
    suspend fun fetchBots(): List<BotCard> {
        val rows = MetaRepo(client).profiles(includeSessions = true)
        // FleetBus snapshot poller M14; direct fetch fallback kalau poller belum
        // pernah jalan (StateFlow mulai emptyList, bukan null — cek isEmpty).
        val snapshot = FleetBus.live.value
        val active = if (snapshot.isEmpty()) fetchActiveDirect() else snapshot
        val cards = FleetLogic.buildCards(rows, active)
        BotFleet.publish(cards, rows)
        return cards
    }

    private suspend fun fetchActiveDirect(): List<NotifPolicy.LiveRow> = try {
        val res = client.call("session.active_list", buildJsonObject { })
        (res["sessions"] as? JsonArray)?.mapNotNull { el ->
            val o = el.jsonObject
            NotifPolicy.LiveRow(
                sid = o["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null,
                sessionKey = o["session_key"]?.jsonPrimitive?.contentOrNull ?: "",
                status = o["status"]?.jsonPrimitive?.contentOrNull ?: "idle",
                messageCount = o["message_count"]?.jsonPrimitive?.intOrNull ?: 0,
                preview = "",
                title = "",
            )
        } ?: emptyList()
    } catch (_: Throwable) { emptyList() }

    /** usage.bars — fail-open: error/unavailable → UsageUi.UNAVAILABLE. */
    suspend fun usage(): UsageUi = try {
        FleetLogic.parseUsage(client.call("usage.bars", buildJsonObject { }))
    } catch (_: Throwable) { UsageUi.UNAVAILABLE }
}
