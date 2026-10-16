package pixiviz.mobile

import android.content.Context
import pixiviz.mobile.data.auth.AuthRepository
import pixiviz.mobile.data.auth.SecureTokenStore
import pixiviz.mobile.data.local.HistoryDao
import pixiviz.mobile.data.local.PixivizDatabase
import pixiviz.mobile.data.remote.Network
import pixiviz.mobile.data.remote.PixivJson
import pixiviz.mobile.data.remote.PixivRepository
import pixiviz.mobile.data.remote.RelayConfig
import pixiviz.mobile.data.settings.ImageChannel
import pixiviz.mobile.data.settings.Settings
import pixiviz.mobile.data.settings.SettingsStore
import okhttp3.OkHttpClient

/**
 * Hand-rolled dependency container (no annotation processor), so the cloud build stays
 * fast and the object graph is obvious.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    val settingsStore = SettingsStore(appContext)
    val config = RelayConfig()
    val tokenStore = SecureTokenStore(appContext)

    private val apiClient: OkHttpClient = Network.apiClient(config)

    /** Shared with Coil so images reuse the same referer / app-key handling. */
    val imageClient: OkHttpClient = Network.imageClient(config)

    private val api = Network.createApi(apiClient)

    val auth = AuthRepository(tokenStore, config, apiClient, PixivJson)

    val repository = PixivRepository(config, api)

    private val database = PixivizDatabase.build(appContext)
    val history: HistoryDao = database.history()

    /** Pushes persisted preferences into the volatile relay config + auth state. */
    fun applySettings(settings: Settings) {
        val url = settings.workerUrl.ifBlank { BuildConfig.DEFAULT_WORKER_URL }
        val key = settings.appKey.ifBlank { BuildConfig.DEFAULT_APP_KEY }
        config.workerUrl = url
        config.appKey = key
        config.imageChannelDirect = settings.imageChannel == ImageChannel.DIRECT
    }

    fun isConfigured(settings: Settings): Boolean =
        settings.workerUrl.ifBlank { BuildConfig.DEFAULT_WORKER_URL }.isNotBlank()
}
