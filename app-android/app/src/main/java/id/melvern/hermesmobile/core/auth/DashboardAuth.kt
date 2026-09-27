package id.melvern.hermesmobile.core.auth

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.JavaNetCookieJar
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.CookieManager
import java.net.CookiePolicy
import java.util.concurrent.TimeUnit

/**
 * Login native-app ke backend gated: password-login (basic provider) → cookie → ws-ticket.
 * Ticket single-use 30 detik untuk upgrade WS. Ini jalur resmi dashboard_auth
 * (hermes_cli/dashboard_auth/routes.py: /auth/password-login + /api/auth/ws-ticket).
 */
class DashboardAuth(private val baseUrl: String) {
    private val json = Json { ignoreUnknownKeys = true }
    private val cookieJar = CookieManager().apply { setCookiePolicy(CookiePolicy.ACCEPT_ALL) }
    private val http = OkHttpClient.Builder()
        .cookieJar(JavaNetCookieJar(cookieJar))
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    data class Session(val username: String, val provider: String)

    /** Login + mint ticket. Throw AuthException kalau gagal. */
    suspend fun loginTicket(username: String, password: String): String = withContext(Dispatchers.IO) {
        // 1) providers (untuk temukan id provider password)
        val provReq = Request.Builder().url("$baseUrl/api/auth/providers").build()
        val provBody = http.newCall(provReq).execute().use { resp ->
            if (!resp.isSuccessful) throw AuthException("providers: HTTP ${resp.code}")
            resp.body?.string() ?: throw AuthException("providers: empty")
        }
        val providers = json.parseToJsonElement(provBody).jsonObject["providers"]?.let {
            (it as? kotlinx.serialization.json.JsonArray) ?: throw AuthException("providers: malformed")
        } ?: throw AuthException("providers: none")
        var provider: String? = null
        for (el in providers) {
            val obj = el.jsonObject
            val id = obj["name"]?.jsonPrimitive?.contentOrNull2() ?: obj["id"]?.jsonPrimitive?.contentOrNull2()
            val supports = obj["supports_password"]?.jsonPrimitive?.booleanOrNull2()
            if (supports == true || id == "basic") { provider = id; break }
        }
        if (provider == null) provider = providers.firstOrNull()?.jsonObject?.get("name")?.jsonPrimitive?.contentOrNull2()
        provider ?: throw AuthException("no auth provider")

        // 2) password login (set cookie session)
        val loginBody = buildJsonObject {
            put("username", JsonPrimitive(username))
            put("password", JsonPrimitive(password))
            put("provider", JsonPrimitive(provider))
        }.toString()
        val loginReq = Request.Builder()
            .url("$baseUrl/auth/password-login")
            .post(loginBody.toRequestBody("application/json".toMediaType()))
            .build()
        http.newCall(loginReq).execute().use { resp ->
            if (resp.code == 401) throw AuthException("Wrong username or password")
            if (resp.code == 429) throw AuthException("Too many attempts — try again later")
            if (!resp.isSuccessful) throw AuthException("login: HTTP ${resp.code}")
        }

        // 3) ws ticket
        val ticketReq = Request.Builder()
            .url("$baseUrl/api/auth/ws-ticket")
            .post("{}".toRequestBody("application/json".toMediaType()))
            .build()
        http.newCall(ticketReq).execute().use { resp ->
            if (!resp.isSuccessful) throw AuthException("ws-ticket: HTTP ${resp.code} — log in again")
            val body = resp.body?.string() ?: throw AuthException("ws-ticket: empty")
            json.parseToJsonElement(body).jsonObject["ticket"]?.jsonPrimitive?.contentOrNull2()
                ?: throw AuthException("ws-ticket: no ticket")
        }
    }

    /** Health check publik. */
    suspend fun health(): Boolean = withContext(Dispatchers.IO) {
        try {
            http.newCall(Request.Builder().url("$baseUrl/api/health").build()).execute().use { it.isSuccessful }
        } catch (_: Throwable) { false }
    }

    /**
     * M5: pastikan cookie login ada (password-login saja, tanpa minta tiket WS) —
     * dipanggil MediaRepo sebelum GET /api/media. Return true kalau cookie siap.
     */
    suspend fun ensureLogin(username: String, password: String): Boolean = withContext(Dispatchers.IO) {
        try {
            // providers → provider password (sama seperti loginTicket)
            val provReq = Request.Builder().url("$baseUrl/api/auth/providers").build()
            val provBody = http.newCall(provReq).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext false
                resp.body?.string() ?: return@withContext false
            }
            val providers = json.parseToJsonElement(provBody).jsonObject["providers"]
                ?.let { it as? kotlinx.serialization.json.JsonArray } ?: return@withContext false
            var provider: String? = null
            for (el in providers) {
                val obj = el.jsonObject
                val id = obj["name"]?.jsonPrimitive?.contentOrNull2() ?: obj["id"]?.jsonPrimitive?.contentOrNull2()
                val supports = obj["supports_password"]?.jsonPrimitive?.booleanOrNull2()
                if (supports == true || id == "basic") { provider = id; break }
            }
            val loginBody = buildJsonObject {
                put("username", JsonPrimitive(username))
                put("password", JsonPrimitive(password))
                put("provider", JsonPrimitive(provider ?: "basic"))
            }.toString()
            val loginReq = Request.Builder()
                .url("$baseUrl/auth/password-login")
                .post(loginBody.toRequestBody("application/json".toMediaType()))
                .build()
            http.newCall(loginReq).execute().use { resp ->
                // 409/200 = sudah login (cookie masih hidup) — keduanya OK
                resp.isSuccessful || resp.code == 409
            }
        } catch (_: Throwable) { false }
    }

    /** M5: GET auth-gated (cookie ikut via cookieJar client). Return body JSON atau null. */
    suspend fun getJson(url: String): String? = withContext(Dispatchers.IO) {
        try {
            http.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                resp.body?.string()
            }
        } catch (_: Throwable) { null }
    }
}

class AuthException(message: String) : Exception(message)

// helper kecil supaya chained-call di atas ringkas
private fun kotlinx.serialization.json.JsonPrimitive.contentOrNull2(): String? =
    if (this.isString) this.content else null
private fun kotlinx.serialization.json.JsonPrimitive.booleanOrNull2(): Boolean? =
    this.content?.let { c -> when (c.lowercase()) { "true" -> true; "false" -> false; else -> null } }
