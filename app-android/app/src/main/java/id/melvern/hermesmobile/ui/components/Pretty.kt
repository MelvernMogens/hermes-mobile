package id.melvern.hermesmobile.ui.components

/**
 * Teks tampilan dari data mentah server — slug API & token internal gak boleh
 * bocor ke UI. Pure Kotlin (unit-test).
 */
object Pretty {
    private val FAMILY = linkedMapOf(
        "claude" to "Claude", "opus" to "Opus", "sonnet" to "Sonnet", "haiku" to "Haiku",
        "gpt" to "GPT", "glm" to "GLM", "gemini" to "Gemini", "flash" to "Flash",
        "pro" to "Pro", "mini" to "mini", "codex" to "Codex", "kimi" to "Kimi",
        "qwen" to "Qwen", "deepseek" to "DeepSeek", "llama" to "Llama", "grok" to "Grok",
        "turbo" to "Turbo", "air" to "Air", "max" to "Max", "nano" to "nano",
    )

    /**
     * "claude-opus-5-5" → "Opus 5.5" · "glm-5.3-flash" → "GLM 5.3 Flash" ·
     * "gpt-5.1-codex" → "GPT 5.1 Codex". Prefix "claude-" dibuang (Opus/Sonnet
     * sudah cukup jelas). Provider prefix "zai/" dll juga dibuang.
     */
    fun model(raw: String?): String {
        val s = raw?.trim()?.substringAfterLast('/')?.lowercase().orEmpty()
        if (s.isEmpty()) return ""
        val parts = s.split('-', '_', ' ').filter { it.isNotEmpty() }.toMutableList()
        if (parts.firstOrNull() == "claude" && parts.size > 1) parts.removeAt(0)
        val out = mutableListOf<String>()
        var i = 0
        while (i < parts.size) {
            val p = parts[i]
            // "5" "5" (claude-opus-5-5) → "5.5"; tanggal 8 digit dibuang
            if (p.all { it.isDigit() } && p.length >= 6) { i++; continue }
            if (p.all { it.isDigit() } && i + 1 < parts.size && parts[i + 1].all { it.isDigit() } && parts[i + 1].length <= 2) {
                out += "$p.${parts[i + 1]}"; i += 2; continue
            }
            val fam = FAMILY[p]
            out += when {
                fam != null -> fam
                p.first().isDigit() -> p
                else -> p.replaceFirstChar { it.uppercase() }
            }
            i++
        }
        return out.joinToString(" ")
    }

    /** "default" → "Hermes"; nama bot lowercase → Title Case ("pm" & "qa" & "ops" → kapital penuh). */
    fun profile(raw: String?): String {
        val s = raw?.trim().orEmpty()
        if (s.isEmpty() || s == "default") return "Hermes"
        if (s.length <= 2) return s.uppercase()
        return s.replaceFirstChar { it.uppercase() }
    }

    private val MENTION = Regex("""@(url|file|folder|image|dir):`?([^`\s]+)`?""")
    private val MD_LINK = Regex("""!?\[([^\]]*)\]\([^)\s]*\)?""")
    private val BARE_URL = Regex("""https?://(?:www\.)?([^/\s)]+)[^\s)]*""")

    /**
     * Preview satu baris: token mention internal (@url:`…`, @file:…) jadi nama
     * yang kebaca (domain / nama file), markdown & whitespace diratakan.
     */
    fun preview(raw: String?): String {
        var s = raw?.replace('\n', ' ')?.trim().orEmpty()
        if (s.isEmpty()) return ""
        s = MENTION.replace(s) { m ->
            val kind = m.groupValues[1]; val v = m.groupValues[2]
            when (kind) {
                "url" -> {
                    val bare = v.removePrefix("https://").removePrefix("http://").removePrefix("www.").trimEnd('/')
                    val host = bare.substringBefore('/')
                    val tail = bare.substringAfter('/', "").substringAfterLast('/')
                    if (tail.isBlank()) host else "$host/…/$tail"
                }
                else -> v.trimEnd('/').substringAfterLast('/')
            }
        }
        // [label](url) → label ; URL polos → domain
        s = s.replace(MD_LINK, "$1")
        s = s.replace(BARE_URL, "$1")
        s = s.replace(Regex("[*_`#>|]+"), "").replace(Regex("\\s+"), " ").trim()
        return s.trimEnd('-', ',', ':', ' ')
    }
}
