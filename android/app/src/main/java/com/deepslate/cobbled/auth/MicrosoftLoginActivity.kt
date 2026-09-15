package com.deepslate.cobbled.auth

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.deepslate.cobbled.core.AuthException
import com.deepslate.cobbled.core.CobbledJson
import com.deepslate.cobbled.core.McAccount
import com.deepslate.cobbled.core.MicrosoftAuthClient
import com.deepslate.cobbled.ui.theme.Accent
import com.deepslate.cobbled.ui.theme.CobbledTheme
import com.deepslate.cobbled.ui.theme.SlateBg
import com.deepslate.cobbled.ui.theme.SlateInk
import com.deepslate.cobbled.ui.theme.SlateMuted
import kotlinx.coroutines.launch

class MicrosoftLoginActivity : ComponentActivity() {
    private val auth = MicrosoftAuthClient()
    private var exchanging by mutableStateOf(false)
    private var status by mutableStateOf("Sign in with the Microsoft account that owns Java Edition.")
    private var finished = false

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            CobbledTheme {
                LoginScreen(
                    url = auth.authorizeUrl().toString(),
                    exchanging = exchanging,
                    status = status,
                    onClose = { cancel("Microsoft login was closed before finishing.") },
                    onUrl = ::handleUrl,
                )
            }
        }
    }

    private fun handleUrl(url: String): Boolean {
        if (finished) return true
        val code = try {
            auth.codeFromRedirect(url)
        } catch (err: AuthException) {
            fail(err.message ?: "Microsoft login failed.")
            return true
        } ?: return false
        finished = true
        exchanging = true
        status = "Contacting Xbox and Minecraft services…"
        lifecycleScope.launch {
            try {
                val account = auth.loginWithCode(code)
                val data = Intent().putExtra(
                    EXTRA_ACCOUNT,
                    CobbledJson.encodeToString(McAccount.serializer(), account),
                )
                setResult(Activity.RESULT_OK, data)
                finish()
            } catch (err: Exception) {
                fail(err.message ?: "Microsoft login failed.")
            }
        }
        return true
    }

    private fun fail(message: String) {
        setResult(Activity.RESULT_CANCELED, Intent().putExtra(EXTRA_ERROR, message))
        finish()
    }

    private fun cancel(message: String) {
        if (finished) {
            finish()
            return
        }
        fail(message)
    }

    companion object {
        const val EXTRA_ACCOUNT = "account_json"
        const val EXTRA_ERROR = "error"
        private const val CHROME_UA =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Composable
    private fun LoginScreen(
        url: String,
        exchanging: Boolean,
        status: String,
        onClose: () -> Unit,
        onUrl: (String) -> Boolean,
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
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            Text(
                text = status,
                color = SlateMuted,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            Box(Modifier.fillMaxSize()) {
                if (!exchanging) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { context ->
                            WebView(context).apply {
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.cacheMode = WebSettings.LOAD_DEFAULT
                                settings.userAgentString = CHROME_UA
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
                                loadUrl(url)
                            }
                        },
                    )
                }
                if (exchanging) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = Accent)
                    }
                }
            }
        }
    }
}
