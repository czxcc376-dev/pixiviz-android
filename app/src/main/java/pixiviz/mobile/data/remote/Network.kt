package pixiviz.mobile.data.remote

import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Url
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import pixiviz.mobile.data.remote.web.WebApi

/**
 * Runtime relay configuration.
 *
 * pixiv blocks Cloudflare's egress outright (\u30d6\u30ed\u30c3\u30af\u3055\u308c\u307e\u3057\u305f), so API traffic goes straight
 * from the device to pixiv; the relay list is used only for the HibiAPI-compatible backend.
 * Several endpoints can be listed and they are rotated automatically when one fails - the
 * same idea as the original project's multi-line probe-and-failover, which matters because
 * a single public instance is neither reliable nor under our control.
 */
class RelayConfig {

    @Volatile var backendUrls: List<String> = emptyList()

    @Volatile var workerUrl: String = ""
    @Volatile var appKey: String = ""
    @Volatile var accessToken: String? = null
    @Volatile var imageChannelDirect: Boolean = true

    private var index = 0

    /** Currently preferred backend, or empty when the relay is disabled. */
    fun backendUrl(): String = backendUrls.getOrElse(index) { "" }

    /** Moves to the next configured backend; returns false when there is nowhere else to go. */
    fun rotateBackend(): Boolean {
        if (backendUrls.size <= 1) return false
        index = (index + 1) % backendUrls.size
        return true
    }

    fun resetBackend() {
        index = 0
    }

    fun baseUrl(): String = workerUrl.trimEnd('/')
}

val PixivJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
    explicitNulls = false
    encodeDefaults = true
}

/** Headers of the official Android client, needed by app-api and the OAuth endpoint. */
const val APP_UA = "PixivAndroidApp/5.0.234 (Android 11; Pixel 4)"
const val APP_OS_VERSION = "11"
const val APP_VERSION = "5.0.234"
const val ACCEPT_LANGUAGE = "zh-CN"

/** A browser-shaped UA is required by i.pximg.net and by the relays. */
const val BROWSER_UA =
    "Mozilla/5.0 (Linux; Android 11; Pixel 4) AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/124.0.0.0 Mobile Safari/537.36"

private const val HASH_SALT =
    "28c1fdd170a5204386cb1313c7077b34f83e4aaf4aa829ce78c231e05b0bae2c"

private val CLIENT_TIME_FORMAT = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US).apply {
    timeZone = TimeZone.getTimeZone("UTC")
}

fun clientTime(): String = CLIENT_TIME_FORMAT.format(Date())

fun md5Hex(input: String): String =
    MessageDigest.getInstance("MD5").digest(input.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

/**
 * One HTTP stack serves three very different upstreams, so headers are chosen per host.
 *
 * Order matters: the relay check comes before the generic fallback, otherwise a relay would
 * receive pixiv's Referer, which some instances answer with a 400.
 */
private class HostHeadersInterceptor(private val config: RelayConfig) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val host = original.url.host
        val builder = original.newBuilder()
        val isRelay = config.backendUrls.any { it.contains(host) }
        val isWorker = config.workerUrl.isNotBlank() && config.workerUrl.contains(host)

        when {
            isRelay -> {
                builder.header("User-Agent", BROWSER_UA)
                builder.header("Accept-Language", ACCEPT_LANGUAGE)
            }

            isWorker -> {
                if (config.appKey.isNotBlank()) builder.header("X-App-Key", config.appKey)
                builder.header("User-Agent", BROWSER_UA)
            }

            host.endsWith("app-api.pixiv.net") || host.endsWith("oauth.secure.pixiv.net") -> {
                val time = clientTime()
                builder.header("User-Agent", APP_UA)
                builder.header("App-OS", "android")
                builder.header("App-OS-Version", APP_OS_VERSION)
                builder.header("App-Version", APP_VERSION)
                builder.header("X-Client-Time", time)
                builder.header("X-Client-Hash", md5Hex(time + HASH_SALT))
                builder.header("Accept-Language", ACCEPT_LANGUAGE)
                config.accessToken?.takeIf { it.isNotBlank() }?.let {
                    builder.header("Authorization", "Bearer $it")
                }
            }

            host.endsWith("pximg.net") -> {
                builder.header("Referer", "https://www.pixiv.net/")
                builder.header("User-Agent", BROWSER_UA)
            }

            else -> {
                // pixiv's own public web endpoints: browser-shaped, no cookie needed.
                builder.header("User-Agent", BROWSER_UA)
                builder.header("Referer", "https://www.pixiv.net/")
                builder.header("Accept-Language", ACCEPT_LANGUAGE)
            }
        }
        return chain.proceed(builder.build())
    }
}

object Network {

    fun sharedClient(config: RelayConfig): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .addInterceptor(HostHeadersInterceptor(config))
        .build()

    private fun retrofit(client: OkHttpClient): Retrofit = Retrofit.Builder()
        .baseUrl("https://absolute-url.invalid/")
        .client(client)
        .addConverterFactory(PixivJson.asConverterFactory("application/json".toMediaType()))
        .build()

    fun createAppApi(client: OkHttpClient): PixivApi = retrofit(client).create(PixivApi::class.java)

    fun createWebApi(client: OkHttpClient): WebApi = retrofit(client).create(WebApi::class.java)
}

/**
 * Signed-in calls always take an absolute URL. With a token we talk to app-api (own account);
 * otherwise the HibiAPI-compatible relay or pixiv's public web endpoints.
 */
interface PixivApi {

    @GET
    suspend fun illustList(@Url url: String): IllustListResponse

    @GET
    suspend fun illustDetail(@Url url: String): IllustDetailResponse

    @GET
    suspend fun trendingTags(@Url url: String): TrendingTagsResponse

    @GET
    suspend fun autocomplete(@Url url: String): AutocompleteResponse

    @GET
    suspend fun searchUser(@Url url: String): SearchUserResponse

    @GET
    suspend fun userDetail(@Url url: String): UserDetailResponse
}
