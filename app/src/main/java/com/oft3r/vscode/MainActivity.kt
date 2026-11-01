package com.oft3r.vscode

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

class AndroidVmStarter(private val context: Context) : VmStarter {
    override fun startTerminalVm(): Boolean {
        val candidatePackages = listOf(
            "com.android.virtualization.terminal",
            "com.google.android.virtualization.terminal"
        )
        for (pkg in candidatePackages) {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(pkg)
            if (launchIntent != null) {
                return try {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launchIntent)
                    true
                } catch (e: Exception) {
                    false
                }
            }
        }
        return false
    }
}

class MainActivity : ComponentActivity() {

    private lateinit var viewModel: LauncherViewModel
    private var webViewRef: WebView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewModel = LauncherViewModel(
            probe = HttpServerProbe(),
            vmStarter = AndroidVmStarter(applicationContext)
        )

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val wv = webViewRef
                if (wv != null && wv.canGoBack()) {
                    wv.goBack()
                } else {
                    moveTaskToBack(true)
                }
            }
        })

        viewModel.connectAndLaunch()

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFF007ACC),
                    background = Color(0xFF1E1E1E),
                    surface = Color(0xFF252526)
                )
            ) {
                val state by viewModel.uiState.collectAsState()
                VSCodeLauncherScreen(
                    uiState = state,
                    onRetry = { viewModel.connectAndLaunch() },
                    onOpenTerminal = { viewModel.openTerminalManually() },
                    onWebViewCreated = { webViewRef = it }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val currentState = viewModel.uiState.value.connectionState
        if (currentState == ConnectionState.TIMEOUT) {
            viewModel.connectAndLaunch()
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun VSCodeLauncherScreen(
    uiState: LauncherUiState,
    onRetry: () -> Unit,
    onOpenTerminal: () -> Unit,
    onWebViewCreated: (WebView) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1E1E1E))
    ) {
        when (uiState.connectionState) {
            ConnectionState.READY -> {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { context ->
                        WebView(context).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            setBackgroundColor(android.graphics.Color.parseColor("#1E1E1E"))
                            CookieManager.getInstance().setAcceptCookie(true)
                            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                databaseEnabled = true
                                allowFileAccess = true
                                allowContentAccess = true
                                javaScriptCanOpenWindowsAutomatically = true
                                mediaPlaybackRequiresUserGesture = false
                                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                cacheMode = WebSettings.LOAD_DEFAULT
                                setSupportZoom(true)
                                builtInZoomControls = false
                                displayZoomControls = false
                                useWideViewPort = true
                                loadWithOverviewMode = true
                                setSupportMultipleWindows(true)
                            }
                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    request: android.webkit.WebResourceRequest?
                                ): Boolean {
                                    val uri = request?.url ?: return false
                                    val host = uri.host ?: ""
                                    if (host == "localhost" || host == "127.0.0.1") {
                                        return false
                                    }
                                    return try {
                                        val externalIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        context.startActivity(externalIntent)
                                        true
                                    } catch (e: Exception) {
                                        false
                                    }
                                }
                            }
                            webChromeClient = object : WebChromeClient() {
                                override fun onPermissionRequest(request: android.webkit.PermissionRequest?) {
                                    request?.grant(request.resources)
                                }

                                override fun onCreateWindow(
                                    view: WebView?,
                                    isDialog: Boolean,
                                    isUserGesture: Boolean,
                                    resultMsg: android.os.Message?
                                ): Boolean {
                                    val transport = resultMsg?.obj as? WebView.WebViewTransport ?: return false
                                    val popupWebView = WebView(context).apply {
                                        webViewClient = object : WebViewClient() {
                                            override fun shouldOverrideUrlLoading(
                                                v: WebView?,
                                                req: android.webkit.WebResourceRequest?
                                            ): Boolean {
                                                val uri = req?.url ?: return false
                                                val host = uri.host ?: ""
                                                if (host != "localhost" && host != "127.0.0.1") {
                                                    try {
                                                        val externalIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                        }
                                                        context.startActivity(externalIntent)
                                                    } catch (_: Exception) {
                                                    }
                                                    return true
                                                }
                                                return false
                                            }
                                        }
                                    }
                                    transport.webView = popupWebView
                                    resultMsg.sendToTarget()
                                    return true
                                }
                            }
                            isFocusable = true
                            isFocusableInTouchMode = true
                            requestFocus()
                            loadUrl(uiState.serverUrl)
                            onWebViewCreated(this)
                        }
                    }
                )
            }

            ConnectionState.CHECKING,
            ConnectionState.WAKING_VM,
            ConnectionState.TIMEOUT -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Visual Studio Code",
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    if (uiState.connectionState != ConnectionState.TIMEOUT) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(44.dp),
                            color = Color(0xFF007ACC),
                            strokeWidth = 3.dp
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                    }
                    Text(
                        text = uiState.statusMessage,
                        color = Color(0xFFCCCCCC),
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(horizontalArrangement = Arrangement.Center) {
                        Button(
                            onClick = onRetry,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF007ACC),
                                contentColor = Color.White
                            )
                        ) {
                            Text("Reintentar ahora")
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        OutlinedButton(onClick = onOpenTerminal) {
                            Text("Abrir Terminal", color = Color(0xFF3794FF))
                        }
                    }
                }
            }
        }
    }
}
