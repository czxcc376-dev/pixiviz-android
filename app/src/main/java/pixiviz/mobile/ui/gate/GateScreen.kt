package pixiviz.mobile.ui.gate

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
import kotlinx.coroutines.launch
import pixiviz.mobile.ui.LocalApp
import pixiviz.mobile.ui.LocalSettings
import pixiviz.mobile.ui.common.CapsuleButton

/**
 * First-run gate. Normally skipped because CI bakes the Worker URL and access key into
 * BuildConfig; it exists so a locally built APK, or a rotated key, can be configured
 * without a rebuild.
 */
@Composable
fun GateScreen() {
    val app = LocalApp.current
    val settings = LocalSettings.current
    val scope = rememberCoroutineScope()

    var url by remember { mutableStateOf(settings.workerUrl) }
    var key by remember { mutableStateOf(settings.appKey) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("连接你的中转", style = MaterialTheme.typography.headlineMedium)
        Text(
            "pixiv 已关闭游客授权，公共代理也已失效，因此数据需要经你自己的 Cloudflare Worker 中转。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("Worker 地址") },
            placeholder = { Text("https://pixiviz-relay.<子域>.workers.dev") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = key,
            onValueChange = { key = it },
            label = { Text("访问密钥（X-App-Key）") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        CapsuleButton(
            text = "保存并继续",
            onClick = {
                scope.launch {
                    app.settingsStore.setWorkerUrl(url)
                    app.settingsStore.setAppKey(key)
                }
            },
        )
    }
}
