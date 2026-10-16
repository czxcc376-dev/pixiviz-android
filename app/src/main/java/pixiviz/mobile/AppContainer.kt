package pixiviz.mobile

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import pixiviz.mobile.data.auth.AuthRepository
import pixiviz.mobile.data.auth.SecureTokenStore
import pixiviz.mobile.data.local.HistoryDao
import pixiviz.mobile.data.local.PixivizDatabase
import pixiviz.mobile.data.remote.Network
import pixiviz.mobile.data.remote.PixivJson
import pixiviz.mobile.data.remote.PixivRepository
import pixiviz.mobile.data.remote.RelayConfig
import pixiviz.mobile.data.remote.hibi.HibiSource
import pixiviz.mobile.data.remote.web.GuestSource
import pixiviz.mobile.data.settings.ImageChannel
import pixiviz.mobile.data.settings.Settings
import pixiviz.mobile.data.settings.SettingsStore

/**
 * Hand-rolled dependency container (no annotation processor), so the cloud build stays fast
 * and the object graph is obvious.
 *
 * Note there is no "is it configured" gate any more: the app browses pixiv's public web
 * surface on its own, and signing in only adds R-18 and personal content.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    val settingsStore = SettingsStore(appContext)
    val config = RelayConfig()
    val tokenStore = SecureTokenStore(appContext)

    /** One HTTP stack: headers are chosen per upstream host. Coil shares it too. */
    val httpClient: OkHttpClient = Network.sharedClient(config)

    private val appApi = Network.createAppApi(httpClient)
    private val webApi = Network.createWebApi(httpClient)

    val guest = GuestSource(webApi)

    /** Reads through the volatile config so changing the relay in Settings takes effect live. */
    val hibi = HibiSource(appApi, config)

    val auth = AuthRepository(tokenStore, config, httpClient, PixivJson)

    val repository = PixivRepository(
        config = config,
        api = appApi,
        hibi = hibi,
        guest = guest,
        signedIn = { auth.signedIn.value },
        backendAvailable = { config.backendUrl().isNotBlank() },
        onAuthFailure = {
            auth.invalidateSession()
            scope.launch { auth.forgetStoredToken() }
        },
    )

    private val database = PixivizDatabase.build(appContext)
    val history: HistoryDao = database.history()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Pushes persisted preferences into the volatile relay config. */
    fun applySettings(settings: Settings) {
        // Several endpoints may be listed (comma or newline separated) and are rotated on failure.
        val endpoints = settings.backendUrl.ifBlank { BuildConfig.DEFAULT_BACKEND_URL }
            .split(',', '\n')
            .map { it.trim() }
            .filter { it.isNotBlank() }
        if (config.backendUrls != endpoints) {
            config.backendUrls = endpoints
            config.resetBackend()
        }
        config.workerUrl = settings.workerUrl.ifBlank { BuildConfig.DEFAULT_WORKER_URL }
        config.appKey = settings.appKey.ifBlank { BuildConfig.DEFAULT_APP_KEY }
        config.imageChannelDirect = settings.imageChannel == ImageChannel.DIRECT
    }
}
