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

    // ── profiles.list ──────────────────────────────────────────────────

    @Serializable
    data class ProfileRow(
        val name: String,
        @SerialName("is_default") val isDefault: Boolean = false,
        val model: String? = null,
        val provider: String? = null,
        val description: String = "",
        @SerialName("display_name") val displayName: String = "",
    )

    suspend fun profiles(): List<ProfileRow> {
        val res = client.call("profiles.list", buildJsonObject { })
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
        // data URL "data:image/png;base64,XXXX" → bytes.
        // Fallback URL-alphabet decoder: beberapa producer data URL pakai base64url.
        val bytes = res["data"]?.jsonPrimitive?.contentOrNull
            ?.substringAfter(',', "")
            ?.takeIf { it.isNotBlank() }
            ?.let { b64 ->
                try { java.util.Base64.getDecoder().decode(b64) }
                catch (_: Throwable) {
                    try { java.util.Base64.getUrlDecoder().decode(b64) } catch (_: Throwable) { null }
                }
            }
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
