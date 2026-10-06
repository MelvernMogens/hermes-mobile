package id.melvern.hermesmobile.ui.overview

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import id.melvern.hermesmobile.HermesApp
import id.melvern.hermesmobile.core.repo.MacRepo
import id.melvern.hermesmobile.ui.components.QuietIconButton
import id.melvern.hermesmobile.ui.theme.Dim
import id.melvern.hermesmobile.ui.theme.Ink
import id.melvern.hermesmobile.ui.theme.Type

/**
 * v24 Web preview: buka dev server di Mac (lewat preview proxy tailnet) di WebView.
 * Cookie login app ditanam ke CookieManager (host sama) — proxy menolak tanpa login.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebPreviewScreen(app: HermesApp, url: String, title: String, onClose: () -> Unit) {
    val ctx = LocalContext.current
    var ready by remember { mutableStateOf(false) }
    var progress by remember { mutableIntStateOf(0) }
    var pageTitle by remember { mutableStateOf(title) }
    var web by remember { mutableStateOf<WebView?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(url) {
        // Auth lewat tiket sekali-pakai di URL: origin preview balas Set-Cookie sendiri.
        // (Tidak menanam cookie manual — beda port/scheme bikin cookie gak terkirim.)
        val cm = CookieManager.getInstance()
        cm.setAcceptCookie(true)
        ready = true
    }
    BackHandler { if (web?.canGoBack() == true) web?.goBack() else onClose() }

    Column(Modifier.fillMaxSize().background(Ink.Bg).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(Dim.TopBar).padding(end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            QuietIconButton(Icons.Rounded.Close, "Close preview", onClick = onClose)
            Column(Modifier.weight(1f)) {
                Text(pageTitle.ifBlank { "Preview" }, style = Type.Title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(web?.url?.let { java.net.URI(it).path }?.ifBlank { "/" } ?: "Loading…", style = Type.Meta, maxLines = 1)
            }
            QuietIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = Ink.Text2, onClick = { if (web?.canGoBack() == true) web?.goBack() })
            QuietIconButton(Icons.Rounded.Refresh, "Reload", tint = Ink.Text2, onClick = { web?.reload() })
            QuietIconButton(Icons.Rounded.OpenInBrowser, "Open in browser", tint = Ink.Text2, onClick = {
                runCatching { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(web?.url ?: url))) }
            })
        }
        if (progress in 1..99) LinearProgressIndicator(
            progress = { progress / 100f }, modifier = Modifier.fillMaxWidth().height(2.dp), color = Ink.Text, trackColor = Ink.Bg,
        )
        Box(Modifier.fillMaxSize()) {
            error?.let { Text(it, style = Type.Callout.copy(color = Ink.Text3), modifier = Modifier.padding(24.dp)) }
            if (ready) AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { c ->
                    WebView(c).apply {
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.loadWithOverviewMode = true
                        settings.useWideViewPort = true
                        setBackgroundColor(android.graphics.Color.BLACK)
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = false
                            override fun onPageFinished(view: WebView, u: String?) { view.title?.takeIf { it.isNotBlank() && !it.startsWith("http") }?.let { pageTitle = it } }
                        }
                        webChromeClient = object : android.webkit.WebChromeClient() {
                            override fun onProgressChanged(view: WebView, p: Int) { progress = p }
                        }
                        loadUrl(url)
                        web = this
                    }
                },
            )
        }
    }
}
