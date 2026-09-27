package id.melvern.hermesmobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import id.melvern.hermesmobile.ui.chat.ChatScreen
import id.melvern.hermesmobile.ui.connect.ConnectScreen
import id.melvern.hermesmobile.ui.sessions.SessionsScreen
import id.melvern.hermesmobile.ui.theme.F
import id.melvern.hermesmobile.ui.theme.HermesTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as HermesApp
        setContent {
            HermesTheme { AppNav(app) }
        }
    }
}

@Composable
fun AppNav(app: HermesApp) {
    val nav = rememberNavController()
    val settings by app.settings.collectAsState()
    Box(Modifier.fillMaxSize().background(F.Bg)) {
        when (val s = settings) {
            null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = F.Vermillion)
            }
            else -> NavHost(navController = nav, startDestination = if (s.configured) "sessions" else "connect") {
                composable("connect") { ConnectScreen(app, onConnected = { nav.navigate("sessions") { popUpTo("connect") { inclusive = true } } }) }
                composable("sessions") { SessionsScreen(app, onOpen = { id -> nav.navigate("chat/$id") }) }
                composable("chat/{sessionId}") { entry ->
                    ChatScreen(app, entry.arguments?.getString("sessionId") ?: return@composable)
                }
            }
        }
    }
}
