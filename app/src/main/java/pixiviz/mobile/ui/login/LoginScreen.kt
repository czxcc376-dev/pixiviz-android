package pixiviz.mobile.ui.login

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
 * Optional pixiv sign-in.
 *
 * Browsing works without it - this only unlocks R-18 and personal content. One PKCE attempt
 * is generated per press and its verifier is reused for whichever path finishes the flow.
 *
 * Capture is deliberately belt-and-braces: pixiv's callback page redirects to the `pixiv://`
 * scheme, and depending on the WebView build that hand-off reaches only some of the four
 * navigation hooks, so all of them are wired to the same handler.
 */
@Composable
fun LoginScreen(onDone: () -> Unit = {}) {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    val lastError by app.auth.lastError.collectAsStateWithLifecycle()

    var attempt by remember { mutableStateOf<Pair<String, String>?>(null) }
    var webMode by remember { mutableStateOf(false) }
    var manual by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }

    val capturedCode = remember { mutableStateOf<String?>(null) }
    val lastUrl = remember { mutableStateOf<String?>(null) }

    fun submit(code: String) {
        val verifier = attempt?.first ?: return
        if (status == "正在换取令牌…") return
        status = "正在换取令牌…"
        scope.launch {
            val result = app.auth.exchangeCode(code, verifier)
            status = result.fold({ null }, { it.message ?: "登录失败" })
            if (result.isSuccess) {
                webMode = false
                onDone()
            }
        }
    }

    LaunchedEffect(capturedCode.value) {
        capturedCode.value?.let { submit(it) }
    }

    if (webMode && attempt != null) {
        WebLogin(
            loginUrl = attempt!!.second,
            lastUrl = lastUrl,
            capturedCode = capturedCode,
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
        Text("登录 pixiv（可选）", style = MaterialTheme.typography.headlineMedium)
        Text(
            "不登录也能浏览推荐、排行榜、搜索、画师和作品详情。登录后才会解锁 18+ 内容与个性化推荐。\n\n" +
                "凭据只以 Keystore 加密形式保存在本机，换设备需重新登录；请求直接发往 pixiv，不经过任何中转服务器。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        CapsuleButton(
            text = "使用 pixiv 账号登录",
            onClick = {
                attempt = app.auth.beginLogin()
                manual = ""
                status = null
                capturedCode.value = null
                lastUrl.value = ""
                webMode = true
            },
        )

        if (attempt != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CapsuleButton(
                    text = "填入刚访问的地址",
                    selected = false,
                    onClick = { manual = lastUrl.value.orEmpty() },
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
            OutlinedTextField(
                value = manual,
                onValueChange = { manual = it },
                label = { Text("回调地址或授权码") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "登录成功后页面会跳到一个打不开的地址，这是正常现象；授权码会被自动截获。" +
                    "若没截获到，把地址栏内容粘贴到上面提交即可。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        (status ?: lastError)?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        }

        CapsuleButton(text = "暂不登录，直接浏览", selected = false, onClick = onDone)
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun WebLogin(
    loginUrl: String,
    lastUrl: MutableStateHolder,
    capturedCode: MutableStateHolder,
    onCancel: () -> Unit,
) {
    BackHandler { onCancel() }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CapsuleButton(text = "取消登录", selected = false, onClick = onCancel)
        }
        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            factory = { context ->
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.userAgentString =
                        "Mozilla/5.0 (Linux; Android 11; Pixel 4) AppleWebKit/537.36 " +
                            "(KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

                    fun inspect(url: String?): Boolean {
                        if (url.isNullOrBlank()) return false
                        lastUrl.value = url
                        val code = Regex("[?&]code=([^&\\s]+)").find(url)?.groupValues?.get(1)
                        if (code != null) {
                            capturedCode.value = code
                            return true
                        }
                        return url.startsWith("pixiv://")
                    }

                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView,
                            request: WebResourceRequest,
                        ): Boolean = inspect(request.url.toString())

                        @Deprecated("Deprecated in Java")
                        override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean =
                            inspect(url)

                        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                            inspect(url)
                        }

                        override fun onPageFinished(view: WebView, url: String) {
                            inspect(url)
                        }

                        override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
                            inspect(url)
                        }
                    }
                    loadUrl(loginUrl)
                }
            },
        )
    }
}

/** Small indirection so the non-composable WebView factory can write Compose state. */
typealias MutableStateHolder = androidx.compose.runtime.MutableState<String?>
