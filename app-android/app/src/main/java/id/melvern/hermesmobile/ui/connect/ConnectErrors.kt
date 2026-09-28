package id.melvern.hermesmobile.ui.connect

import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * M8: pesan error Connect yang manusiawi (brief D). Pure — di-unit-test.
 * Menelusuri cause chain karena OkHttp sering membungkus exception jaringan.
 */
object ConnectErrors {
    const val UNREACHABLE = "Can't reach your Mac. Check Tailscale is on (same account on both devices)."
    const val BAD_CREDENTIALS = "Wrong username or password."
    const val MISSING_FIELDS = "Enter the server address, username and password."
    private val HTTP_CODE = Regex("""HTTP (\d{3})""")

    fun message(t: Throwable): String {
        val chain = generateSequence(t) { it.cause }.take(8).toList()
        if (chain.any { it is UnknownHostException || it is NoRouteToHostException || it is ConnectException || it is SocketTimeoutException }) {
            return UNREACHABLE
        }
        val text = chain.mapNotNull { it.message }.joinToString(" ")
        if (text.contains("Wrong username or password", ignoreCase = true) || HTTP_CODE.find(text)?.groupValues?.get(1) == "401") {
            return BAD_CREDENTIALS
        }
        val code = HTTP_CODE.find(text)?.groupValues?.get(1)
        return if (code != null) "Couldn't connect (HTTP $code). Is Hermes running on your Mac?"
        else "Couldn't connect. Is Hermes running on your Mac?"
    }

    /** Auto prefix https:// kalau user tidak menulis scheme. */
    fun normalizeUrl(raw: String): String {
        val t = raw.trim().trimEnd('/')
        if (t.isEmpty()) return t
        return if (t.startsWith("http://", true) || t.startsWith("https://", true)) t else "https://$t"
    }
}
