package id.melvern.hermesmobile.core.repo

import id.melvern.hermesmobile.core.store.ConnectionSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.net.URLEncoder

/**
 * v28: reasoning-effort levels PER MODEL (proxy `/api/mobile-efforts`, built from Hermes'
 * own request builders). Every model gets Off (when it can turn thinking off), its native
 * levels, and Ultra — Hermes' top tier, which runs as the model's strongest level.
 * Unknown routes / proxy down → [Menu.fallback]: the whole ladder, like the desktop.
 */
class EffortRepo(private val settings: ConnectionSettings) {

    data class Level(val word: String, val runsAs: String?)

    data class Menu(
        val levels: List<Level>,
        /** false = the model has no effort dial (thinking on/off only, or no thinking) */
        val dial: Boolean,
        val canOff: Boolean,
        /** false = Hermes couldn't tell, so this is the full ladder */
        val known: Boolean,
    ) {
        companion object {
            val fallback = Menu(
                LADDER.map { Level(it, it) }, dial = true, canOff = true, known = false,
            )
        }
    }

    private fun base() = settings.baseUrl.trim().trimEnd('/')
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    suspend fun menu(provider: String, model: String): Menu? = withContext(Dispatchers.IO) {
        if (model.isBlank()) return@withContext null
        val key = "$provider/$model"
        cache[key]?.let { return@withContext it }
        val auth = id.melvern.hermesmobile.core.auth.SharedAuth.get(base(), settings.username, settings.password)
            ?: return@withContext null
        val body = auth.getJson("${base()}/api/mobile-efforts?provider=${enc(provider)}&model=${enc(model)}")
            ?: return@withContext null
        parse(body)?.also { if (it.known) cache[key] = it }
    }

    companion object {
        /** Hermes' ladder, weakest → strongest (`none` = thinking off, handled separately). */
        val LADDER = listOf("minimal", "low", "medium", "high", "xhigh", "max", "ultra")

        private val cache = java.util.concurrent.ConcurrentHashMap<String, Menu>()
        private val json = Json { ignoreUnknownKeys = true }

        fun label(word: String): String = when (word) {
            "none" -> "Off"
            "minimal" -> "Minimal"
            "low" -> "Low"
            "medium" -> "Medium"
            "high" -> "High"
            "xhigh" -> "XHigh"
            "max" -> "Max"
            "ultra" -> "Ultra"
            else -> word.replaceFirstChar { it.uppercase() }
        }

        /**
         * The chips to show: native levels in ladder order + Ultra (always, when there is a dial).
         * A level the model doesn't have is hidden — picking it would silently run as another.
         */
        fun chips(menu: Menu): List<Level> {
            if (!menu.dial) return emptyList()
            val native = menu.levels.filter { it.word != "ultra" && it.runsAs == it.word }
            val ultra = menu.levels.firstOrNull { it.word == "ultra" } ?: Level("ultra", null)
            return native + ultra
        }

        /** The model's strongest native level (ladder order). */
        fun strongest(menu: Menu): String? =
            menu.levels.filter { it.word != "ultra" && it.runsAs == it.word }.maxByOrNull { LADDER.indexOf(it.word) }?.word

        /** False when Ultra runs WEAKER than the model's strongest level (legacy budget Claude). */
        fun ultraIsStrongest(menu: Menu): Boolean {
            val runs = menu.levels.firstOrNull { it.word == "ultra" }?.runsAs ?: return true
            val top = strongest(menu) ?: return true
            return LADDER.indexOf(runs) >= LADDER.indexOf(top)
        }

        /** The chip a stored effort lights up: its own chip, else the native level it runs as. */
        fun selectedChip(menu: Menu, effort: String?): String? {
            if (effort.isNullOrBlank()) return null
            if (effort == "none") return "none"
            val shown = chips(menu).map { it.word }
            if (effort in shown) return effort
            return menu.levels.firstOrNull { it.word == effort }?.runsAs?.takeIf { it in shown }
        }

        fun parse(body: String): Menu? = try {
            val o = json.parseToJsonElement(body).jsonObject
            val levels = o["levels"]?.jsonArray?.mapNotNull { el ->
                val l = el.jsonObject
                val word = (l["level"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
                val native = (l["native"] as? JsonPrimitive)?.booleanOrNull ?: false
                val runsAs = (l["runs_as"] as? JsonPrimitive)?.contentOrNull
                Level(word, if (native) word else runsAs)
            }.orEmpty()
            Menu(
                levels = levels,
                dial = (o["dial"] as? JsonPrimitive)?.booleanOrNull ?: levels.isNotEmpty(),
                canOff = (o["can_off"] as? JsonPrimitive)?.booleanOrNull ?: true,
                known = (o["known"] as? JsonPrimitive)?.booleanOrNull ?: false,
            )
        } catch (_: Throwable) { null }
    }
}
