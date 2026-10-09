package id.melvern.hermesmobile

import id.melvern.hermesmobile.core.store.SettingsStore
import id.melvern.hermesmobile.core.store.ConnectionSettings
import kotlinx.coroutines.launch
import id.melvern.hermesmobile.ui.overview.LimitsScreen
import id.melvern.hermesmobile.ui.overview.FilesScreen
import id.melvern.hermesmobile.ui.overview.SettingsScreen
import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.navigation.NavBackStackEntry
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import id.melvern.hermesmobile.core.notify.AppNotifier
import id.melvern.hermesmobile.ui.layout.WinSize
import id.melvern.hermesmobile.ui.layout.LocalWinSize
import id.melvern.hermesmobile.ui.layout.isExpanded
import id.melvern.hermesmobile.ui.layout.HomeShell
import id.melvern.hermesmobile.ui.layout.HomeTabs
import id.melvern.hermesmobile.ui.overview.OverviewScreen
import id.melvern.hermesmobile.ui.chat.ArtifactsScreen
import id.melvern.hermesmobile.ui.chat.ChatScreen
import id.melvern.hermesmobile.ui.connect.ConnectScreen
import id.melvern.hermesmobile.ui.sessions.SessionsScreen
import id.melvern.hermesmobile.ui.theme.HermesTheme
import id.melvern.hermesmobile.ui.theme.Ink
import id.melvern.hermesmobile.ui.theme.Motion
import id.melvern.hermesmobile.ui.theme.rememberReduceMotion

@kotlin.OptIn(androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi::class)
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Splash: platform SplashScreen (values-v31/themes.xml) bg #0B0B0C + logo;
        // < API 31 cukup windowBackground gelap. Tanpa library tambahan.
        super.onCreate(savedInstanceState)
        val app = application as HermesApp
        // M14: permission notif (Android 13+) — sekali, saat app pertama dibuka
        // post-install (bukan saat connect: connect bisa jalan dari auto-start).
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
        }
        // M8: edge-to-edge, bar transparan, ikon terang (tema selalu gelap)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        // M14: extra dari tap notif — baca SEKALI di onCreate (onNewIntent → flow).
        id.melvern.hermesmobile.core.store.AppPrefs.loadBlocking(this)
        // Share dari app lain → simpan di ShareInbox, Chats menampilkan pemilih tujuan.
        id.melvern.hermesmobile.core.share.ShareInbox.offer(intent, this)
        setContent {
            // M15: adaptive — WindowSizeClass dihitung di sini (satu-satunya tempat),
            // disimpan ke state app + CompositionLocal; screens baca dari situ.
            val windowSizeClass = calculateWindowSizeClass(this)
            // M15: two-pane HANYA benar-benar layar lebar. Terukur: tablet portrait
            // 1600px@276dpi = 928dp dan phone landscape modern ~914dp — keduanya
            // masuk Expanded kanonik (>=840) padahal bukan target two-pane.
            // Threshold praktis: Expanded = sizeClass Expanded DAN width >= 1000dp
            // (Tab S8 landscape 1463dp masuk; portrait 928dp turun ke Medium).
            val widthDp = resources.configuration.screenWidthDp
            val winSize = when (windowSizeClass.widthSizeClass) {
                WindowWidthSizeClass.Expanded -> if (widthDp >= 1000) WinSize.Expanded else WinSize.Medium
                WindowWidthSizeClass.Medium -> WinSize.Medium
                else -> WinSize.Compact
            }
            LaunchedEffect(winSize) { app.windowSize.value = winSize }
            CompositionLocalProvider(LocalWinSize provides winSize) {
                HermesTheme {
                    AppNav(app, notifOpenChat = intent?.getStringExtra(AppNotifier.EXTRA_OPEN_CHAT))
                }
            }
        }
    }  // onCreate

    /** M14: notif tap → intent baru (FLAG_CLEAR_TOP) — baca extra chat setiap kali. */
    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        (application as HermesApp).offerOpenChat(intent.getStringExtra(AppNotifier.EXTRA_OPEN_CHAT))
        if (id.melvern.hermesmobile.core.share.ShareInbox.offer(intent, this)) {
            (application as HermesApp).offerHome()
        }
    }
}

@Composable
fun AppNav(app: HermesApp, notifOpenChat: String? = null) {
    val nav = rememberNavController()
    val settings by app.settings.collectAsState()
    val reduce = rememberReduceMotion()
    val slidePx = with(LocalDensity.current) { Motion.NavSlide.roundToPx() }

    // M18: tab aktif (Chats=0 / Overview=1) — rememberSaveable: survive rotate;
    // deep-link notif selalu balik ke tab Chats.
    var homeTab by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableIntStateOf(HomeTabs.CHATS) }
    // M18: chat yang diminta dari Overview (expanded → inline two-pane M15).
    var pendingChatSel by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf<String?>(null) }
    val ws = id.melvern.hermesmobile.ui.layout.currentWinSize()

    // M14: buka chat yang diminta dari tap notif (intent extra / onNewIntent flow).
    fun openChatFromNotif(storedId: String) {
        homeTab = HomeTabs.CHATS
        nav.navigate("chat/$storedId") { launchSingleTop = true }
    }
    LaunchedEffect(Unit) { notifOpenChat?.let { openChatFromNotif(it) } }
    LaunchedEffect(app) {
        app.openChatRequests.collect { openChatFromNotif(it) }
    }
    LaunchedEffect(app) {
        app.homeRequests.collect {
            homeTab = HomeTabs.CHATS
            nav.popBackStack("sessions", inclusive = false)
        }
    }

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
            composable("sessions") {
                // M18: home 2 tab — Chats (perilaku M15 persis) + Overview.
                HomeShell(
                    tab = homeTab,
                    onTab = { homeTab = it },
                    chats = {
                        SessionsScreen(
                            app,
                            onOpen = { id -> nav.navigate("chat/$id") },
                            // expanded: buka dari Overview → selection inline
                            initialSelection = if (ws.isExpanded) pendingChatSel else null,
                        )
                    },
                    overview = {
                        OverviewScreen(app, onOpenChat = { arg ->
                            if (ws.isExpanded) {
                                // two-pane M15 tetap: selection di pane kanan.
                                homeTab = HomeTabs.CHATS
                                pendingChatSel = arg
                            } else {
                                homeTab = HomeTabs.CHATS
                                nav.navigate("chat/$arg") { popUpTo("sessions") }
                            }
                        })
                    },
                    limits = { LimitsScreen(app, onOpenFiles = { nav.navigate("files") }) },
                    settings = {
                        SettingsScreen(app, onSignOut = {
                            app.disconnect()
                            // kosongkan kredensial tersimpan (URL disisakan biar login ulang gampang)
                            val keep = ConnectionSettings(baseUrl = s.baseUrl, username = s.username)
                            app.appScope.launch { SettingsStore.save(app, keep) }
                            app.settings.value = keep
                            homeTab = HomeTabs.CHATS
                            nav.navigate("connect") { popUpTo(0) }
                        })
                    },
                )
            }
            // v28: clip bin — semua file dari semua chat/bot.
            composable("files") {
                FilesScreen(
                    app,
                    onBack = { nav.popBackStack() },
                    onOpenChat = { arg: String -> nav.navigate("chat/$arg") },
                )
            }
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
                val runtime = second?.takeIf { initialTitle == null && !it.startsWith("p=") }
                // v27: segmen "p=<profile>" — chat milik profile lain (tugas bot)
                val chatProfile = segs.firstOrNull { it.startsWith("p=") }?.substring(2)?.takeIf { it.isNotBlank() }
                ChatScreen(
                    app, storedId, runtime, initialTitle,
                    profileOverride = chatProfile,
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
