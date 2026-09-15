package com.deepslate.cobbled.auth

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import com.deepslate.cobbled.CobbledApplication
import com.deepslate.cobbled.MainActivity
import com.deepslate.cobbled.core.AuthException
import com.deepslate.cobbled.core.CobbledJson
import com.deepslate.cobbled.core.DeviceCodeSession
import com.deepslate.cobbled.core.McAccount
import com.deepslate.cobbled.core.MicrosoftAuthClient
import com.deepslate.cobbled.ui.theme.Accent
import com.deepslate.cobbled.ui.theme.CobbledTheme
import com.deepslate.cobbled.ui.theme.DisplayFont
import com.deepslate.cobbled.ui.theme.SlateBg
import com.deepslate.cobbled.ui.theme.SlateInk
import com.deepslate.cobbled.ui.theme.SlateMuted
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class MicrosoftLoginActivity : ComponentActivity() {
    private val auth = MicrosoftAuthClient()
    private var mode by mutableStateOf(Mode.WEB)
    private var exchanging by mutableStateOf(false)
    private var status by mutableStateOf("Sign in with the Microsoft account that owns Java Edition.")
    private var device by mutableStateOf<DeviceCodeSession?>(null)
    private var finished = false
    private var pollJob: Job? = null
    private var webGeneration by mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        handleUri(intent?.data)
        setContent {
            CobbledTheme {
                LoginScreen(
                    mode = mode,
                    exchanging = exchanging,
                    status = status,
                    device = device,
                    webGeneration = webGeneration,
                    authorizeUrl = auth.authorizeUrl(),
                    onClose = { cancel("Microsoft login was closed before finishing.") },
                    onUrl = ::handleUrl,
                    onWeb = {
                        pollJob?.cancel()
                        mode = Mode.WEB
                        status = "Sign in with the Microsoft account that owns Java Edition."
                    },
                    onCode = ::startDeviceCode,
                    onChrome = ::openChrome,
                    onOpenLink = {
                        val uri = Uri.parse(device?.verificationUri ?: "https://www.microsoft.com/link")
                        startActivity(Intent(Intent.ACTION_VIEW, uri))
                    },
                    onSwitchAccount = {
                        CookieManager.getInstance().removeAllCookies {
                            webGeneration += 1
                        }
                    },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleUri(intent.data)
    }

    private fun handleUri(uri: Uri?): Boolean {
        val url = uri?.toString() ?: return false
        return handleUrl(url)
    }

    private fun handleUrl(url: String): Boolean {
        if (finished) return true
        if (!auth.isLoginRedirect(url)) return false
        if (auth.isCancelRedirect(url)) {
            fail("Microsoft login was cancelled.")
            return true
        }
        val code = try {
            auth.codeFromRedirect(url)
        } catch (err: AuthException) {
            fail(err.message ?: "Microsoft login failed.")
            return true
        } ?: return false
        finished = true
        exchanging = true
        mode = Mode.WEB
        status = "Contacting Xbox and Minecraft services…"
        lifecycleScope.launch {
            try {
                succeed(auth.loginWithXboxCode(code))
            } catch (err: Exception) {
                finished = false
                exchanging = false
                status = err.message ?: "Microsoft login failed. Try Chrome or a login code."
            }
        }
        return true
    }

    private fun startDeviceCode() {
        pollJob?.cancel()
        mode = Mode.CODE
        exchanging = true
        status = "Asking Microsoft for a login code…"
        pollJob = lifecycleScope.launch {
            try {
                val session = auth.startDeviceCode()
                device = session
                exchanging = false
                status = session.message.ifBlank {
                    "Open the Microsoft link, enter this code, then wait here."
                }
                val account = auth.pollDeviceCode(session)
                succeed(account)
            } catch (err: Exception) {
                exchanging = false
                status = err.message ?: "Microsoft login failed. Try Chrome or a new login code."
            }
        }
    }

    private fun openChrome() {
        val tabs = CustomTabsIntent.Builder().setShowTitle(true).build()
        tabs.launchUrl(this, Uri.parse(auth.authorizeUrl()))
        status = "Finish signing in in Chrome. Cobbled will catch the Xbox redirect."
    }

    private fun succeed(account: McAccount) {
        val app = application as CobbledApplication
        app.container.store.saveAccount(account)
        val json = CobbledJson.encodeToString(McAccount.serializer(), account)
        if (callingActivity != null) {
            setResult(Activity.RESULT_OK, Intent().putExtra(EXTRA_ACCOUNT, json))
            finish()
            return
        }
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(EXTRA_ACCOUNT, json),
        )
        finish()
    }

    private fun fail(message: String) {
        setResult(Activity.RESULT_CANCELED, Intent().putExtra(EXTRA_ERROR, message))
        finish()
    }

    private fun cancel(message: String) {
        pollJob?.cancel()
        if (finished) {
            finish()
            return
        }
        fail(message)
    }

    companion object {
        const val EXTRA_ACCOUNT = "account_json"
        const val EXTRA_ERROR = "error"
    }

    enum class Mode { WEB, CODE }

    @SuppressLint("SetJavaScriptEnabled")
    @Composable
    private fun LoginScreen(
        mode: Mode,
        exchanging: Boolean,
        status: String,
        device: DeviceCodeSession?,
        webGeneration: Int,
        authorizeUrl: String,
        onClose: () -> Unit,
        onUrl: (String) -> Boolean,
        onWeb: () -> Unit,
        onCode: () -> Unit,
        onChrome: () -> Unit,
        onOpenLink: () -> Unit,
        onSwitchAccount: () -> Unit,
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .background(SlateBg)
                .statusBarsPadding(),
        ) {
            TextButton(onClick = onClose, modifier = Modifier.padding(horizontal = 8.dp)) {
                Text("Cancel", color = Accent)
            }
            Text(
                text = "Microsoft login",
                color = SlateInk,
                fontFamily = DisplayFont,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            Text(
                text = status,
                color = SlateMuted,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            Row(Modifier.padding(horizontal = 12.dp)) {
                TextButton(onClick = onWeb) { Text("On this screen", color = if (mode == Mode.WEB) Accent else SlateMuted) }
                TextButton(onClick = onChrome) { Text("Chrome", color = Accent) }
                TextButton(onClick = onCode) { Text("Login code", color = if (mode == Mode.CODE) Accent else SlateMuted) }
            }
            if (mode == Mode.WEB) {
                TextButton(onClick = onSwitchAccount, modifier = Modifier.padding(horizontal = 8.dp)) {
                    Text("Use a different account", color = SlateMuted)
                }
            }
            Box(Modifier.fillMaxSize()) {
                if (mode == Mode.CODE) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = device?.userCode ?: "······",
                            color = SlateInk,
                            fontFamily = DisplayFont,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 36.sp,
                            letterSpacing = 4.sp,
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(device?.verificationUri ?: "microsoft.com/link", color = Accent)
                        Spacer(Modifier.height(16.dp))
                        TextButton(onClick = onOpenLink) { Text("Open Microsoft login", color = Accent) }
                        if (exchanging || device != null) {
                            Spacer(Modifier.height(20.dp))
                            CircularProgressIndicator(color = Accent)
                            Spacer(Modifier.width(8.dp))
                            Text("Waiting for Microsoft…", color = SlateMuted)
                        }
                    }
                } else if (!exchanging) {
                    key(webGeneration) {
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { context ->
                                WebView(context).apply {
                                    settings.javaScriptEnabled = true
                                    settings.domStorageEnabled = true
                                    CookieManager.getInstance().setAcceptCookie(true)
                                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                                    webViewClient = object : WebViewClient() {
                                        override fun shouldOverrideUrlLoading(
                                            view: WebView,
                                            request: WebResourceRequest,
                                        ): Boolean = onUrl(request.url.toString())

                                        @Deprecated("Deprecated in Java")
                                        override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean =
                                            onUrl(url)

                                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                            url?.let(onUrl)
                                        }
                                    }
                                    loadUrl(authorizeUrl)
                                }
                            },
                        )
                    }
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Accent)
                    }
                }
            }
        }
    }
}
