package id.melvern.hermesmobile.ui.connect

import id.melvern.hermesmobile.core.auth.AuthException
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException
import java.net.UnknownHostException

class ConnectErrorsTest {
    @Test fun unknownHost_wrapped_isUnreachable() {
        val e = IOException("boom", UnknownHostException("your-mac.tailnet.ts.net"))
        assertEquals(ConnectErrors.UNREACHABLE, ConnectErrors.message(e))
    }

    @Test fun wrongPassword_fromAuth_isCredentials() {
        assertEquals(ConnectErrors.BAD_CREDENTIALS, ConnectErrors.message(AuthException("Wrong username or password")))
    }

    @Test fun http401_isCredentials() {
        assertEquals(ConnectErrors.BAD_CREDENTIALS, ConnectErrors.message(AuthException("login: HTTP 401")))
    }

    @Test fun otherHttp_carriesCode() {
        assertEquals(
            "Couldn't connect (HTTP 502). Is Hermes running on your Mac?",
            ConnectErrors.message(AuthException("ws-ticket: HTTP 502 — log in again")),
        )
    }

    @Test fun normalizeUrl_addsHttpsOnlyWhenSchemeMissing() {
        assertEquals("https://mac.ts.net", ConnectErrors.normalizeUrl(" mac.ts.net/ "))
        assertEquals("http://10.0.2.2:8790", ConnectErrors.normalizeUrl("http://10.0.2.2:8790"))
    }
}
