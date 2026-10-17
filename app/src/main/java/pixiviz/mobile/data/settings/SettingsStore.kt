package pixiviz.mobile.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** How adult works are treated in the UI. */
enum class NsfwMode { SHOW, BLUR, HIDE }

/** Which route image bytes take. */
enum class ImageChannel { DIRECT, RELAY }

/** UI theme selection. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Card layout used by the grids. */
enum class GridLayout { SINGLE, DOUBLE }

data class Settings(
    /** HibiAPI-compatible relay. This is the default data source, and it serves R-18. */
    val backendUrl: String = "",
    val imageProxyHosts: String = "",
    val workerUrl: String = "",
    val appKey: String = "",
    val nsfwMode: NsfwMode = NsfwMode.BLUR,
    val nsfwAgeConfirmed: Boolean = false,
    val imageChannel: ImageChannel = ImageChannel.DIRECT,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val pureBlack: Boolean = false,
    val gridLayout: GridLayout = GridLayout.DOUBLE,
    val showAiWorks: Boolean = true,
    val lastRankMode: String = "day",
    val searchHistory: List<String> = emptyList(),
    /** Hide the status bar app-wide (the image viewer is always immersive). */
    val immersiveFullscreen: Boolean = true,
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "pixiviz_settings")

/**
 * Persisted preferences. Deliberately contains no credentials — pixiv tokens live in
 * [pixiviz.mobile.data.auth.SecureTokenStore], sealed by the Android Keystore.
 */
class SettingsStore(context: Context) {

    private val store = context.applicationContext.dataStore

    val settings: Flow<Settings> = store.data.map { prefs ->
        Settings(
            backendUrl = prefs[KEY_BACKEND_URL] ?: "",
            imageProxyHosts = prefs[KEY_IMAGE_PROXY] ?: "",
            workerUrl = prefs[KEY_WORKER_URL] ?: "",
            appKey = prefs[KEY_APP_KEY] ?: "",
            nsfwMode = prefs[KEY_NSFW_MODE].toEnum<NsfwMode>(NsfwMode.BLUR),
            nsfwAgeConfirmed = prefs[KEY_NSFW_CONFIRMED] ?: false,
            imageChannel = prefs[KEY_IMAGE_CHANNEL].toEnum<ImageChannel>(ImageChannel.DIRECT),
            themeMode = prefs[KEY_THEME_MODE].toEnum<ThemeMode>(ThemeMode.SYSTEM),
            pureBlack = prefs[KEY_PURE_BLACK] ?: false,
            gridLayout = prefs[KEY_GRID_LAYOUT].toEnum<GridLayout>(GridLayout.DOUBLE),
            showAiWorks = prefs[KEY_SHOW_AI] ?: true,
            lastRankMode = prefs[KEY_LAST_RANK] ?: "day",
            searchHistory = prefs[KEY_SEARCH_HISTORY]?.split('\n')?.filter { it.isNotBlank() }.orEmpty(),
            immersiveFullscreen = prefs[KEY_IMMERSIVE] ?: true,
        )
    }

    suspend fun setImageProxyHosts(value: String) = store.edit { it[KEY_IMAGE_PROXY] = value.trim() }

    suspend fun setBackendUrl(value: String) = store.edit { it[KEY_BACKEND_URL] = value.trim() }
    suspend fun setWorkerUrl(value: String) = store.edit { it[KEY_WORKER_URL] = value.trim() }
    suspend fun setAppKey(value: String) = store.edit { it[KEY_APP_KEY] = value.trim() }
    suspend fun setNsfwMode(value: NsfwMode) = store.edit { it[KEY_NSFW_MODE] = value.name }
    suspend fun setNsfwAgeConfirmed(value: Boolean) = store.edit { it[KEY_NSFW_CONFIRMED] = value }
    suspend fun setImageChannel(value: ImageChannel) = store.edit { it[KEY_IMAGE_CHANNEL] = value.name }
    suspend fun setThemeMode(value: ThemeMode) = store.edit { it[KEY_THEME_MODE] = value.name }
    suspend fun setPureBlack(value: Boolean) = store.edit { it[KEY_PURE_BLACK] = value }
    suspend fun setGridLayout(value: GridLayout) = store.edit { it[KEY_GRID_LAYOUT] = value.name }
    suspend fun setShowAiWorks(value: Boolean) = store.edit { it[KEY_SHOW_AI] = value }
    /** Keeps the most recent 20 distinct queries. */
    suspend fun setImmersiveFullscreen(value: Boolean) = store.edit { it[KEY_IMMERSIVE] = value }

    suspend fun addSearchHistory(word: String) {
        val clean = word.trim()
        if (clean.isEmpty()) return
        store.edit { prefs ->
            val current = prefs[KEY_SEARCH_HISTORY]?.split('\n')?.filter { it.isNotBlank() }.orEmpty()
            prefs[KEY_SEARCH_HISTORY] = (listOf(clean) + current.filter { it != clean }).take(20).joinToString("\n")
        }
    }

    suspend fun clearSearchHistory() = store.edit { it.remove(KEY_SEARCH_HISTORY) }

    suspend fun setLastRankMode(value: String) = store.edit { it[KEY_LAST_RANK] = value }

    private companion object {
        val KEY_BACKEND_URL = stringPreferencesKey("backend_url")
        val KEY_IMAGE_PROXY = stringPreferencesKey("image_proxy_hosts")
        val KEY_WORKER_URL = stringPreferencesKey("worker_url")
        val KEY_APP_KEY = stringPreferencesKey("app_key")
        val KEY_NSFW_MODE = stringPreferencesKey("nsfw_mode")
        val KEY_NSFW_CONFIRMED = booleanPreferencesKey("nsfw_age_confirmed")
        val KEY_IMAGE_CHANNEL = stringPreferencesKey("image_channel")
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        val KEY_PURE_BLACK = booleanPreferencesKey("pure_black")
        val KEY_GRID_LAYOUT = stringPreferencesKey("grid_layout")
        val KEY_SHOW_AI = booleanPreferencesKey("show_ai_works")
        val KEY_LAST_RANK = stringPreferencesKey("last_rank_mode")
        val KEY_SEARCH_HISTORY = stringPreferencesKey("search_history")
        val KEY_IMMERSIVE = booleanPreferencesKey("immersive_fullscreen")
    }
}

private inline fun <reified T : Enum<T>> String?.toEnum(fallback: T): T =
    if (this == null) fallback else runCatching { enumValueOf<T>(this) }.getOrDefault(fallback)
