package id.melvern.hermesmobile.core.repo

import id.melvern.hermesmobile.core.rpc.GatewayClient
import id.melvern.hermesmobile.core.rpc.RpcException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

/**
 * M4: RPC pembantu model picker, profile switcher, dan aksi session.
 * Semua nama method + field disalin dari gateway-contract.openrpc.json —
 * jangan ngarang endpoint (pelajaran M-brief: contract = source of truth).
 */
class MetaRepo(private val client: GatewayClient) {

    // ── model.options ──────────────────────────────────────────────────

    @Serializable
    data class ModelProviderRow(
        val slug: String,
        val name: String = "",
        val models: List<String> = emptyList(),
        @SerialName("is_current") val isCurrent: Boolean? = null,
        val authenticated: Boolean? = null,
        @SerialName("featured_models") val featuredModels: List<String>? = null,
    )

    data class ModelOptions(
        val providers: List<ModelProviderRow>,
        val model: String,
        val provider: String,
    )

    /**
     * `model.options` — inventory provider/model. `session_id` bikin hasil
     * layered over provider live session (field `model`/`provider` terisi).
     */
    suspend fun modelOptions(sessionId: String? = null, profile: String? = null): ModelOptions {
        val res = client.call("model.options", buildJsonObject {
            if (sessionId != null) put("session_id", sessionId)
            if (profile != null && profile != "default") put("profile", profile)
        })
        val providers = res["providers"]?.jsonArray?.mapNotNull { el ->
            try { lenientJson.decodeFromJsonElement(ModelProviderRow.serializer(), el.jsonObject) } catch (_: Throwable) { null }
        } ?: emptyList()
        return ModelOptions(
            providers = providers,
            model = res["model"]?.jsonPrimitive?.contentOrNull ?: "",
            provider = res["provider"]?.jsonPrimitive?.contentOrNull ?: "",
        )
    }

    data class ModelSwitch(
        val value: String,
        val deferred: Boolean,
        val confirmRequired: Boolean,
        val confirmMessage: String,
    )

    /**
     * Switch the model of a LIVE session — the exact call the desktop composer
     * makes (`config.set key=model value="<model> --provider <slug> --session"`).
     * `--session` keeps the pick scoped to this chat; it never rewrites the profile
     * default. A pick during a running turn comes back `deferred` and lands at the
     * next turn. Expensive models answer `confirm_required` → retry with confirm.
     */
    suspend fun switchModel(sessionId: String, model: String, provider: String, confirm: Boolean = false): ModelSwitch {
        val res = client.call("config.set", buildJsonObject {
            put("session_id", sessionId)
            put("key", "model")
            put("value", "$model --provider $provider --session")
            if (confirm) put("confirm_expensive_model", true)
        }, timeoutMs = 90_000)
        return ModelSwitch(
            value = res["value"]?.jsonPrimitive?.contentOrNull ?: model,
            deferred = res["deferred"]?.jsonPrimitive?.booleanOrNull ?: false,
            confirmRequired = res["confirm_required"]?.jsonPrimitive?.booleanOrNull ?: false,
            confirmMessage = res["confirm_message"]?.jsonPrimitive?.contentOrNull ?: "",
        )
    }

    // ── M9 (item 2): reasoning effort (config.get/config.set key=reasoning) ──

    /**
     * Upaya reasoning aktif utk session (fallback config global).
     * `config.get key=reasoning` → {value, display}; value "none" = thinking
     * mati → null (chip gak ada yang aktif).
     */
    suspend fun reasoningEffort(sessionId: String?): String? {
        val res = client.call("config.get", buildJsonObject {
            put("key", "reasoning")
            if (!sessionId.isNullOrBlank()) put("session_id", sessionId)
        })
        val v = res["value"]?.jsonPrimitive?.contentOrNull ?: return null
        return v.takeIf { it.isNotBlank() && it != "none" }
    }

    /** `config.set key=reasoning value=<low|medium|high|max>` — session-scoped. */
    suspend fun setReasoningEffort(sessionId: String, effort: String) {
        client.call("config.set", buildJsonObject {
            put("session_id", sessionId)
            put("key", "reasoning")
            put("value", effort)
        })
    }

    // ── profiles.list ──────────────────────────────────────────────────

    @Serializable
    data class ProfileRow(
        val name: String,
        val path: String = "",
        @SerialName("is_default") val isDefault: Boolean = false,
        val model: String? = null,
        val provider: String? = null,
        val description: String = "",
        @SerialName("display_name") val displayName: String = "",
        /** M18: hanya terisi include_sessions=true. */
        @SerialName("last_session") val lastSession: ProfileSessionPreview? = null,
        @SerialName("worker_session") val workerSession: ProfileWorkerSession? = null,
        @SerialName("canonical_session") val canonicalSession: ProfileCanonicalSession? = null,
    )

    @Serializable
    data class ProfileSessionPreview(
        val id: String,
        val title: String = "",
        val preview: String = "",
        @SerialName("started_at") val startedAt: Double = 0.0,
        @SerialName("last_active") val lastActive: Double = 0.0,
        @SerialName("message_count") val messageCount: Int = 0,
    )

    @Serializable
    data class ProfileWorkerSession(
        val id: String,
        val source: String = "",
        val title: String = "",
        @SerialName("last_active") val lastActive: Double = 0.0,
    )

    /** Canonical "Bot Chat" profile (contract ProfileCanonicalSession). */
    @Serializable
    data class ProfileCanonicalSession(
        val id: String,
        @SerialName("resolved_id") val resolvedId: String = "",
        @SerialName("root_title") val rootTitle: String = "",
        val title: String = "",
        val preview: String = "",
        @SerialName("started_at") val startedAt: Double = 0.0,
        @SerialName("last_active") val lastActive: Double = 0.0,
        @SerialName("message_count") val messageCount: Int = 0,
    )

    suspend fun profiles(): List<ProfileRow> = profiles(includeSessions = false)

    /**
     * M18: include_sessions=true → tiap row bawa canonical_session (Bot Chat
     * registry row), last_session, worker_session — fleet dashboard map status
     * per profile tanpa N call lanjutan.
     */
    suspend fun profiles(includeSessions: Boolean): List<ProfileRow> {
        val res = client.call("profiles.list", buildJsonObject {
            if (includeSessions) put("include_sessions", true)
        })
        return res["profiles"]?.jsonArray?.mapNotNull { el ->
            try { lenientJson.decodeFromJsonElement(ProfileRow.serializer(), el.jsonObject) } catch (_: Throwable) { null }
        } ?: emptyList()
    }

    // ── M5: file/image attach (methods_prompt.py) ──────────────────────

    data class FileAttachResult(
        val refText: String,
        val name: String,
        val path: String,
    )

    /**
     * `file.attach` — file non-gambar via `data_url` (base64 data:...).
     * Balik `ref_text` "@file:..." yang HARUS diselipin ke prompt.submit.
     */
    suspend fun attachFileDataUrl(sessionId: String, dataUrl: String, name: String, profile: String? = null): FileAttachResult {
        val res = client.call("file.attach", buildJsonObject {
            put("session_id", sessionId)
            put("data_url", dataUrl)
            put("name", name)
            if (profile != null && profile != "default") put("profile", profile)
        }, timeoutMs = 60_000)
        return FileAttachResult(
            refText = res["ref_text"]?.jsonPrimitive?.contentOrNull
                ?: throw RpcException(-1, "file.attach: no ref_text"),
            name = res["name"]?.jsonPrimitive?.contentOrNull ?: name,
            path = res["path"]?.jsonPrimitive?.contentOrNull ?: "",
        )
    }

    data class ImageAttachResult(
        val attached: Boolean,
        val path: String,
    )

    /**
     * `image.attach_bytes` — gambar via base64. Server men-queue gambar buat
     * submit BERIKUTNYA (gak butuh ref di prompt); `path` = lokasi di Mac
     * (dipakai buat render balik via /api/media).
     */
    suspend fun attachImageBytes(sessionId: String, base64: String, filename: String, ext: String, profile: String? = null): ImageAttachResult {
        val res = client.call("image.attach_bytes", buildJsonObject {
            put("session_id", sessionId)
            put("content_base64", base64)
            put("filename", filename)
            if (ext.isNotBlank()) put("ext", ext)
            if (profile != null && profile != "default") put("profile", profile)
        }, timeoutMs = 60_000)
        return ImageAttachResult(
            attached = res["attached"]?.jsonPrimitive?.booleanOrNull ?: false,
            path = res["path"]?.jsonPrimitive?.contentOrNull ?: "",
        )
    }

    // ── profiles.get_asset (M5c: avatar per-profile) ───────────────────

    data class ProfileAsset(
        val found: Boolean,
        val mime: String? = null,
        val size: Int? = null,
        /** Hasil parse data URL (base64 → bytes); null kalau found=false. */
        val bytes: ByteArray? = null,
    )

    /**
     * `profiles.get_asset` — avatar profile sebagai data URL.
     * PERHATIAN: field params-nya `name` (nama profile), BUKAN `profile` —
     * terverifikasi kontrak (ProfilesGetAssetParams) + probe Megatron 28 Sep.
     * found=false BUKAN error (profil tanpa avatar → placeholder inisial).
     */
    suspend fun profileAvatar(name: String): ProfileAsset {
        val res = client.call("profiles.get_asset", buildJsonObject {
            put("name", name)
            put("asset", "avatar")
        })
        val found = res["found"]?.jsonPrimitive?.booleanOrNull ?: false
        // M7 fix: probe 28 Sep — data bisa RAW base64 TANPA prefix "data:"
        // (WEBP, magic VP8X). substringAfter(',', "") pada string tanpa koma
        // = "" → bytes null → placeholder ungu selamanya. Parse dua bentuk.
        val raw = res["data"]?.jsonPrimitive?.contentOrNull
        val b64 = when {
            raw == null -> null
            raw.startsWith("data:") -> raw.substringAfter(',', "").trim()
            else -> raw.trim()
        }?.takeIf { it.isNotEmpty() }
        val bytes = b64?.let { decodeB64Tolerant(it) }
            // M7 fix: magic-byte gate — cuma format yang bisa di-decode
            // BitmapFactory. SVG dsb → null → inisial (bukan kotak kosong).
            ?.takeIf { isDecodableImage(it) }
        return ProfileAsset(
            found = found,
            mime = res["mime"]?.jsonPrimitive?.contentOrNull,
            size = res["size"]?.jsonPrimitive?.intOrNull,
            bytes = if (found) bytes else null,
        )
    }

    // ── Aksi session (context menu) ────────────────────────────────────
    suspend fun renameSession(runtimeId: String, title: String, profile: String? = null) {
        client.call("session.title", buildJsonObject {
            put("session_id", runtimeId); put("title", title)
            if (profile != null && profile != "default") put("profile", profile)
        })
    }

    data class BranchOutcome(val runtimeId: String, val storedId: String, val title: String)

    /** `session.branch` — fork session live; butuh history (≥1 pesan). */
    suspend fun branchSession(runtimeId: String, profile: String? = null): BranchOutcome {
        val res = client.call("session.branch", buildJsonObject {
            put("session_id", runtimeId)
            if (profile != null && profile != "default") put("profile", profile)
        }, timeoutMs = 60_000)
        return BranchOutcome(
            runtimeId = res["session_id"]?.jsonPrimitive?.contentOrNull
                ?: throw RpcException(-1, "session.branch: no session_id"),
            storedId = res["stored_session_id"]?.jsonPrimitive?.contentOrNull ?: "",
            title = res["title"]?.jsonPrimitive?.contentOrNull ?: "",
        )
    }

    /** `session.set_hidden` — hidden=true keluar dari list default (tetap resumable). */
    suspend fun hideSession(sessionId: String, profile: String? = null) {
        client.call("session.set_hidden", buildJsonObject {
            put("session_id", sessionId); put("hidden", true)
            if (profile != null && profile != "default") put("profile", profile)
        })
    }

    /** `session.delete` — hapus stored session + transcripts; ditolak kalau live. */
    suspend fun deleteSession(storedId: String, profile: String? = null) {
        client.call("session.delete", buildJsonObject {
            put("session_id", storedId)
            if (profile != null && profile != "default") put("profile", profile)
        })
    }

    companion object {
        private val lenientJson = Json { ignoreUnknownKeys = true }
    }
}

// ── M7: helper decode base64 toleran + gate magic bytes ────────────

/**
 * Decode base64 toleran: MIME alphabet → URL alphabet fallback →
 * padding '=' ditambah kalau len % 4 != 0 (beberapa producer strip padding).
 * Null kalau dua dekoder gagal.
 */
internal fun decodeB64Tolerant(b64: String): ByteArray? {
    val padded = if (b64.length % 4 != 0) b64 + "=".repeat(4 - b64.length % 4) else b64
    return try { java.util.Base64.getDecoder().decode(padded) }
    catch (_: Throwable) {
        try { java.util.Base64.getUrlDecoder().decode(padded) } catch (_: Throwable) { null }
    }
}

/**
 * Gate format gambar via magic bytes — hanya yang bisa di-decode
 * BitmapFactory yang dipakai (JPEG/PNG/WEBP/GIF/BMP). SVG dsb → false.
 */
internal fun isDecodableImage(bytes: ByteArray): Boolean {
    if (bytes.size < 12) return false
    fun be32(o: Int) = ((bytes[o].toInt() and 0xFF) shl 24) or ((bytes[o + 1].toInt() and 0xFF) shl 16) or
        ((bytes[o + 2].toInt() and 0xFF) shl 8) or (bytes[o + 3].toInt() and 0xFF)
    return when {
        // PNG: 89 50 4E 47
        bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() -> true
        // JPEG: FF D8 FF
        bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() -> true
        // WEBP: "RIFF"...."WEBP"
        be32(0) == 0x52494646 && be32(8) == 0x57454250 -> true
        // GIF87a/GIF89a
        be32(0) == 0x47494638 -> true
        // BMP: "BM"
        bytes[0] == 0x42.toByte() && bytes[1] == 0x4D.toByte() -> true
        else -> false
    }
}

