package pixiviz.mobile.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import pixiviz.mobile.BuildConfig
import pixiviz.mobile.data.settings.GridLayout
import pixiviz.mobile.data.settings.ImageChannel
import pixiviz.mobile.data.settings.NsfwMode
import pixiviz.mobile.data.settings.Settings
import pixiviz.mobile.data.settings.ThemeMode
import pixiviz.mobile.ui.LocalApp
import pixiviz.mobile.ui.LocalSettings
import pixiviz.mobile.ui.common.CapsuleButton
import pixiviz.mobile.ui.login.LoginScreen
import pixiviz.mobile.ui.nav.BottomBarReserve

@Composable
fun SettingsScreen() {
    val app = LocalApp.current
    val settings = LocalSettings.current
    val scope = rememberCoroutineScope()
    val signedIn by app.auth.signedIn.collectAsStateWithLifecycle()
    val downloadTasks by app.downloader.tasks.collectAsStateWithLifecycle()

    var workerUrl by remember(settings.workerUrl) { mutableStateOf(settings.workerUrl) }
    var appKey by remember(settings.appKey) { mutableStateOf(settings.appKey) }
    var ageDialog by remember { mutableStateOf<NsfwMode?>(null) }
    var showLogin by remember { mutableStateOf(false) }
    var updateInfo by remember { mutableStateOf<String?>(null) }
    var backendUrl by remember(settings.backendUrl) { mutableStateOf(settings.backendUrl) }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    if (showLogin) {
        LoginScreen(onDone = { showLogin = false })
        return
    }

    if (ageDialog != null) {
        AlertDialog(
            onDismissRequest = { ageDialog = null },
            title = { Text("确认年龄") },
            text = {
                Text(
                    "开启后将显示可能包含成人向（R18）的内容。请确认你已年满 18 周岁，" +
                        "且所在地法律法规允许浏览此类内容。该设置可随时关闭。",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val mode = ageDialog ?: NsfwMode.BLUR
                    scope.launch {
                        app.settingsStore.setNsfwAgeConfirmed(true)
                        app.settingsStore.setNsfwMode(mode)
                    }
                    ageDialog = null
                }) { Text("我已满 18 岁") }
            },
            dismissButton = {
                TextButton(onClick = { ageDialog = null }) { Text("取消") }
            },
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 16.dp,
            bottom = BottomBarReserve,
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column {
                Text("我的", style = MaterialTheme.typography.displaySmall)
                Text(
                    text = if (signedIn) "已登录 pixiv" else "未登录",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            Section("账号") {
                Text(
                    text = if (signedIn) {
                        "已登录：使用你自己的账号直连 pixiv，18+ 与个性化推荐按你的账号设置生效。"
                    } else {
                        "未登录也能浏览推荐、排行榜、搜索、画师与作品详情；18+ 由下面的数据源提供。"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (signedIn) {
                        CapsuleButton(
                            text = "退出登录",
                            selected = false,
                            onClick = { scope.launch { app.auth.signOut() } },
                        )
                    } else {
                        CapsuleButton(text = "登录 pixiv", onClick = { showLogin = true })
                    }
                }
                if (!signedIn) {
                    Text(
                        "18+ 内容由数据源提供：默认中转可出，直连网页接口则没有。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item {
            Section("18+ 内容") {
                Text(
                    "控制成人向作品如何出现；R18 能否获取取决于「数据源」一节，未登录时需要中转。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NsfwMode.entries.forEach { mode ->
                        val label = when (mode) {
                            NsfwMode.SHOW -> "全部显示"
                            NsfwMode.BLUR -> "模糊"
                            NsfwMode.HIDE -> "完全隐藏"
                        }
                        CapsuleButton(
                            text = label,
                            selected = settings.nsfwMode == mode,
                            onClick = {
                                when {
                                    mode == NsfwMode.HIDE -> scope.launch {
                                        app.settingsStore.setNsfwMode(mode)
                                    }
                                    settings.nsfwAgeConfirmed -> scope.launch {
                                        app.settingsStore.setNsfwMode(mode)
                                    }
                                    else -> ageDialog = mode
                                }
                            },
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CapsuleButton(
                        text = "R18 可达性自检",
                        selected = false,
                        onClick = {
                            busy = true
                            status = null
                            scope.launch {
                                status = withContext(Dispatchers.IO) {
                                    runCatching {
                                        val count = app.repository.rankLoader("day_r18", null)
                                            .load(null).illusts.size
                                        "R18 榜单可用，返回 $count 条"
                                    }.getOrElse { "R18 不可用：${it.message}" }
                                }
                                busy = false
                            }
                        },
                    )
                }
                if (busy) Text("检测中…", style = MaterialTheme.typography.labelSmall)
            }
        }

        item {
            Section("外观") {
                Segmented(
                    values = ThemeMode.entries.toList(),
                    selected = settings.themeMode,
                    label = {
                        when (it) {
                            ThemeMode.SYSTEM -> "跟随系统"
                            ThemeMode.LIGHT -> "浅色"
                            ThemeMode.DARK -> "深色"
                        }
                    },
                    onSelect = { scope.launch { app.settingsStore.setThemeMode(it) } },
                )
                ToggleRow("纯黑模式（OLED）", settings.pureBlack) {
                    scope.launch { app.settingsStore.setPureBlack(it) }
                }
                Segmented(
                    values = GridLayout.entries.toList(),
                    selected = settings.gridLayout,
                    label = { if (it == GridLayout.DOUBLE) "双列" else "单列" },
                    onSelect = { scope.launch { app.settingsStore.setGridLayout(it) } },
                )
            }
        }

        item {
            Section("图片通道") {
                Text(
                    "直连由 App 自带 Referer 访问 i.pximg.net，省 Worker 额度且更快；" +
                        "走 Worker 会隐藏设备 IP，但消耗每日免费请求。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Segmented(
                    values = ImageChannel.entries.toList(),
                    selected = settings.imageChannel,
                    label = { if (it == ImageChannel.DIRECT) "直连" else "走 Worker" },
                    onSelect = { scope.launch { app.settingsStore.setImageChannel(it) } },
                )
            }
        }

        item {
            Section("内容") {
                ToggleRow("显示 AI 生成作品", settings.showAiWorks) {
                    scope.launch { app.settingsStore.setShowAiWorks(it) }
                }
            }
        }

        item {
            Section("数据源") {
                Text(
                    "默认走 HibiAPI 兼容的中转：无需登录即可出 R18，和原 Pixiviz 用的是同一类后端。" +
                        "留空则退回 pixiv 公开网页接口（推荐与搜索可用，但没有 R18、榜单为近似结果）。" +
                        "登录 pixiv 后一律改用你自己的账号直连。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = backendUrl,
                    onValueChange = { backendUrl = it },
                    label = { Text("中转地址（HibiAPI 兼容，可填多个）") },
                    placeholder = { Text(BuildConfig.DEFAULT_BACKEND_URL) },
                    supportingText = { Text("多个地址用换行或逗号分隔；某个挂了会自动切下一个。") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CapsuleButton(text = "保存数据源", onClick = {
                        scope.launch {
                            app.settingsStore.setBackendUrl(backendUrl)
                            app.applySettings(settings.copy(backendUrl = backendUrl.trim()))
                            status = "数据源已保存"
                        }
                    })
                    CapsuleButton(text = "诊断", selected = false, onClick = {
                        busy = true
                        scope.launch {
                            val report = StringBuilder()
                            report.append("登录: ")
                                .append(if (signedIn) "是（用你自己的账号直连）" else "否")
                                .append('\n')
                            report.append("数据源: ")
                                .append(app.config.backendUrl().ifBlank { "未配置，用 pixiv 公开网页接口" })
                                .append('\n')
                            app.repository.backendHealth()
                                .onSuccess { report.append("中转体检: ").append(it) }
                                .onFailure { report.append("中转体检: 失败 · ").append(it.message) }
                            report.append('\n')
                            runCatching { app.repository.trendingTags() }
                                .onSuccess { report.append("热门标签: ").append(it.size).append(" 条") }
                                .onFailure { report.append("热门标签: 失败 · ").append(it.message) }
                            report.append('\n')
                            runCatching { app.repository.rankLoader("day", null).load(null) }
                                .onSuccess { report.append("日榜: ").append(it.illusts.size).append(" 条") }
                                .onFailure { report.append("日榜: 失败 · ").append(it.message) }
                            report.append('\n')
                            runCatching { app.repository.rankLoader("day_r18", null).load(null) }
                                .onSuccess { report.append("R18 日榜: ").append(it.illusts.size).append(" 条") }
                                .onFailure { report.append("R18 日榜: 失败 · ").append(it.message) }
                            status = report.toString()
                            busy = false
                        }
                    })
                    CapsuleButton(text = "恢复默认", selected = false, onClick = {
                        scope.launch {
                            app.settingsStore.setBackendUrl("")
                            app.applySettings(settings.copy(backendUrl = ""))
                            backendUrl = ""
                            status = "已恢复默认中转"
                        }
                    })
                }
            }
        }

        item {
            Section("连接") {
                OutlinedTextField(
                    value = workerUrl,
                    onValueChange = { workerUrl = it },
                    label = { Text("Worker 地址") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = appKey,
                    onValueChange = { appKey = it },
                    label = { Text("访问密钥") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CapsuleButton(text = "保存", onClick = {
                        scope.launch {
                            app.settingsStore.setWorkerUrl(workerUrl)
                            app.settingsStore.setAppKey(appKey)
                            app.applySettings(
                                settings.copy(workerUrl = workerUrl.trim(), appKey = appKey.trim()),
                            )
                            status = "已保存"
                        }
                    })
                    CapsuleButton(text = "体检 Worker", selected = false, onClick = {
                        busy = true
                        scope.launch {
                            status = withContext(Dispatchers.IO) {
                                runCatching {
                                    val url = workerUrl.trimEnd('/').ifBlank {
                                        BuildConfig.DEFAULT_WORKER_URL.trimEnd('/')
                                    }
                                    val request = Request.Builder().url("$url/health").build()
                                    app.httpClient.newCall(request).execute().use { response ->
                                        "HTTP ${response.code} ${response.body?.string()?.take(160).orEmpty()}"
                                    }
                                }.getOrElse { "失败：${it.message}" }
                            }
                            busy = false
                        }
                    })
                    CapsuleButton(text = "退出登录", selected = false, onClick = {
                        scope.launch { app.auth.signOut() }
                    })
                }
                status?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        item {
            Section("下载") {
                Text(
                    "成品图保存到相册的 Pictures/Pixiviz；Android 10+ 走 MediaStore，不需要任何存储权限。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (downloadTasks.isEmpty()) {
                    Text("暂无下载任务", style = MaterialTheme.typography.labelSmall)
                } else {
                    downloadTasks.takeLast(6).forEach { task ->
                        val suffix = task.message?.let { " · $it" }.orEmpty()
                        Text(
                            "${task.state.name} · ${task.title.take(18)} (${task.page}/${task.pageCount})$suffix",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    CapsuleButton(text = "清除已结束的任务", selected = false, onClick = {
                        app.downloader.clearFinished()
                    })
                }
            }
        }

        item {
            Section("更新") {
                Text("版本 ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyMedium)
                CapsuleButton(text = "检查更新", selected = false, onClick = {
                    busy = true
                    scope.launch {
                        updateInfo = withContext(Dispatchers.IO) {
                            runCatching {
                                val request = Request.Builder()
                                    .url("https://api.github.com/repos/czxcc376-dev/pixiviz-android/releases/latest")
                                    .build()
                                app.httpClient.newCall(request).execute().use { response ->
                                    val body = response.body?.string().orEmpty()
                                    val tag = Regex("\"tag_name\"\\s*:\\s*\"([^\"]+)\"")
                                        .find(body)?.groupValues?.get(1)
                                    if (tag == null) "检查失败：HTTP ${response.code}"
                                    else if (tag == "v${BuildConfig.VERSION_NAME}") "已是最新版本 $tag"
                                    else "有新版本 $tag（当前 v${BuildConfig.VERSION_NAME}）\nhttps://github.com/czxcc376-dev/pixiviz-android/releases/latest"
                                }
                            }.getOrElse { "检查失败：${it.message}" }
                        }
                        busy = false
                    }
                })
                updateInfo?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        item {
            Section("关于") {
                Text("版本 ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = "数据经自建 Cloudflare Worker 中转并直连 pixiv App API；" +
                        "本项目受 asadahimeka/pixiviz 形态启发重新实现，Apache-2.0。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        content()
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun <T> Segmented(
    values: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        values.forEach { value ->
            CapsuleButton(
                text = label(value),
                selected = value == selected,
                onClick = { onSelect(value) },
            )
        }
    }
}
