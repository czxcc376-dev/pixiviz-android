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
 * Only the optional image relay still points at the Worker.
 *
 * pixiv blocks Cloudflare's egress IPs outright (\u30d6\u30ed\u30c3\u30af\u3055\u308c\u307e\u3057\u305f), so API traffic
 * cannot be relayed there; it goes straight from the device, which is also what keeps the
 * account risk low - the pxve-api project lost its upstream account to server-side relaying.
 */
class RelayConfig {
    @Volatile var workerUrl: String = ""
    @Volatile var appKey: String = ""
    @Volatile var accessToken: String? = null
    /** HibiAPI-compatible relay base. The default source, and it serves R-18. */
    @Volatile var backendUrl: String = ""
    @Volatile var imageChannelDirect: Boolean = true

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

/** i.pximg.net rejects requests without a pixiv referer. */
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
 * One HTTP stack serves two very different upstreams, so headers are chosen per host
 * instead of building two clients with duplicated retry/cache policy.
 */
private class HostHeadersInterceptor(private val config: RelayConfig) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val host = original.url.host
        val builder = original.newBuilder()

        when {
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

            config.workerUrl.isNotBlank() && config.workerUrl.contains(host) -> {
                if (config.appKey.isNotBlank()) builder.header("X-App-Key", config.appKey)
            }

            else -> {
                // www.pixiv.net public web endpoints: no cookie and no login are required,
                // but they do expect a browser-shaped request.
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
 * Signed-in calls always take an absolute URL. With a token we talk to app-api (which is the
 * only surface that returns R-18 works); without one, the guest source talks to www.pixiv.net.
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
