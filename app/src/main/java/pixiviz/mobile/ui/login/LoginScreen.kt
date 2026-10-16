package pixiviz.mobile.ui.login

import android.annotation.SuppressLint
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import pixiviz.mobile.ui.LocalApp
import pixiviz.mobile.ui.common.CapsuleButton

/**
 * Device-side pixiv sign-in.
 *
 * One PKCE attempt is generated per press, and its verifier is reused for whichever path
 * completes the flow (in-app WebView capture or a manually pasted code) — the challenge and
 * the verifier must come from the same attempt or pixiv rejects the exchange.
 */
@Composable
fun LoginScreen() {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    val lastError by app.auth.lastError.collectAsStateWithLifecycle()

    // verifier to login-url, generated once per attempt
    var attempt by remember { mutableStateOf<Pair<String, String>?>(null) }
    var webMode by remember { mutableStateOf(false) }
    var manual by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }

    fun submit(code: String) {
        val verifier = attempt?.first ?: return
        status = "正在换取令牌…"
        scope.launch {
            val result = app.auth.exchangeCode(code, verifier)
            status = result.fold({ null }, { it.message ?: "登录失败" })
        }
    }

    attempt?.takeIf { webMode }?.let { pair ->
        WebLogin(
            loginUrl = pair.second,
            onCode = { code -> submit(code) },
            onCancel = { webMode = false },
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("登录 pixiv", style = MaterialTheme.typography.headlineMedium)
        Text(
            "pixiv 已关闭游客授权，浏览任何内容都需要账号令牌。凭据只以 Keystore 加密形式保存在本机，换设备需重新登录。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        CapsuleButton(
            text = "使用 pixiv 账号登录",
            onClick = {
                attempt = app.auth.beginLogin()
                manual = ""
                status = null
                webMode = true
            },
        )

        if (attempt != null) {
            Text(
                "登录成功后页面会跳到一个打不开的地址，这是正常现象，授权码会被自动截获。" +
                    "若没截获到，把地址栏内容整段粘贴到下面提交即可。",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = manual,
                onValueChange = { manual = it },
                label = { Text("回调地址或授权码") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
            CapsuleButton(
                text = "手动提交",
                selected = false,
                onClick = {
                    val code = app.auth.extractCode(manual)
                    if (code == null) status = "没能从这段内容里找到 code" else submit(code)
                },
            )
        }

        (status ?: lastError)?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun WebLogin(
    loginUrl: String,
    onCode: (String) -> Unit,
    onCancel: () -> Unit,
) {
    BackHandler { onCancel() }

    Column(Modifier.fillMaxSize()) {
        CapsuleButton(
            text = "取消登录",
            selected = false,
            modifier = Modifier.padding(16.dp),
            onClick = onCancel,
        )
        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            factory = { context ->
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView,
                            request: WebResourceRequest,
                        ): Boolean {
                            val url = request.url.toString()
                            val code = Regex("[?&]code=([^&\\s]+)").find(url)?.groupValues?.get(1)
                            if (code != null) {
                                onCode(code)
                                return true
                            }
                            // pixiv finally redirects to its custom scheme; swallow it.
                            return url.startsWith("pixiv://")
                        }
                    }
                    loadUrl(loginUrl)
                }
            },
        )
    }
}
