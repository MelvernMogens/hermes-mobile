package id.melvern.hermesmobile

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.navigation.NavBackStackEntry
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import id.melvern.hermesmobile.ui.chat.ArtifactsScreen
import id.melvern.hermesmobile.ui.chat.ChatScreen
import id.melvern.hermesmobile.ui.connect.ConnectScreen
import id.melvern.hermesmobile.ui.sessions.SessionsScreen
import id.melvern.hermesmobile.ui.theme.HermesTheme
import id.melvern.hermesmobile.ui.theme.Ink
import id.melvern.hermesmobile.ui.theme.Motion
import id.melvern.hermesmobile.ui.theme.rememberReduceMotion

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Splash: platform SplashScreen (values-v31/themes.xml) bg #0B0B0C + logo;
        // < API 31 cukup windowBackground gelap. Tanpa library tambahan.
        super.onCreate(savedInstanceState)
        val app = application as HermesApp
        // M8: edge-to-edge, bar transparan, ikon terang (tema selalu gelap)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        setContent {
            HermesTheme { AppNav(app) }
        }
    }
}

@Composable
fun AppNav(app: HermesApp) {
    val nav = rememberNavController()
    val settings by app.settings.collectAsState()
    val reduce = rememberReduceMotion()
    val slidePx = with(LocalDensity.current) { Motion.NavSlide.roundToPx() }
    // Shared axis X: slide 24dp + fade, 220ms, emphasized. Back = kebalikan.
    val enter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        if (reduce) EnterTransition.None
        else slideInHorizontally(tween(Motion.NavMs, easing = Motion.EmphasizedDecelerate)) { slidePx } +
            fadeIn(tween(Motion.NavMs, easing = Motion.EmphasizedDecelerate))
    }
    val exit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        if (reduce) ExitTransition.None
        else slideOutHorizontally(tween(Motion.NavMs, easing = Motion.EmphasizedAccelerate)) { -slidePx } +
            fadeOut(tween(Motion.NavMs / 2, easing = Motion.EmphasizedAccelerate))
    }
    val popEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        if (reduce) EnterTransition.None
        else slideInHorizontally(tween(Motion.NavMs, easing = Motion.EmphasizedDecelerate)) { -slidePx } +
            fadeIn(tween(Motion.NavMs, easing = Motion.EmphasizedDecelerate))
    }
    val popExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        if (reduce) ExitTransition.None
        else slideOutHorizontally(tween(Motion.NavMs, easing = Motion.EmphasizedAccelerate)) { slidePx } +
            fadeOut(tween(Motion.NavMs / 2, easing = Motion.EmphasizedAccelerate))
    }
    Box(Modifier.fillMaxSize().background(Ink.Bg)) {
        // settings == null → canvas gelap polos sekejap (DataStore load)
        val s = settings ?: return@Box
        NavHost(
            navController = nav,
            startDestination = if (s.configured) "sessions" else "connect",
            enterTransition = enter, exitTransition = exit,
            popEnterTransition = popEnter, popExitTransition = popExit,
        ) {
            composable("connect") { ConnectScreen(app, onConnected = { nav.navigate("sessions") { popUpTo("connect") { inclusive = true } } }) }
            composable("sessions") { SessionsScreen(app, onOpen = { id -> nav.navigate("chat/$id") }) }
            // M13: artifacts per chat — parse client-side dari TranscriptCache.
            composable("artifacts/{sessionId}") { entry ->
                val raw = entry.arguments?.getString("sessionId") ?: return@composable
                // arg = "storedId|t=<encoded title>"
                val segs = raw.split("|")
                val storedId = segs.first()
                val chatTitle = segs.getOrNull(1)
                    ?.takeIf { it.startsWith("t=") }
                    ?.substring(2)
                    ?.let { runCatching { Uri.decode(it) }.getOrNull() }
                    ?: ""
                ArtifactsScreen(app, storedId, chatTitle, onBack = { nav.popBackStack() })
            }
            composable("chat/{sessionId}") { entry ->
                // arg = "storedId" | "storedId|runtimeId" (chat baru) |
                // "storedId|t=<encoded title>" (dari list).
                val raw = entry.arguments?.getString("sessionId") ?: return@composable
                val segs = raw.split("|")
                val storedId = segs.first()
                val second = segs.getOrNull(1)
                val initialTitle = second
                    ?.takeIf { it.startsWith("t=") }
                    ?.substring(2)
                    ?.let { runCatching { Uri.decode(it) }.getOrNull() }
                val runtime = second?.takeIf { initialTitle == null }
                ChatScreen(
                    app, storedId, runtime, initialTitle,
                    onBack = { nav.popBackStack() },
                    onOpenChat = { arg -> nav.navigate("chat/$arg") { popUpTo("sessions") } },
                    // M13: artifacts route — live title dari ChatScreen (bisa berubah
                    // via event session.title), subtitle meta di ArtifactsScreen.
                    onOpenArtifacts = { liveTitle ->
                        nav.navigate("artifacts/${storedId}|t=${Uri.encode(liveTitle)}")
                    },
                )
            }
        }
    }
}
