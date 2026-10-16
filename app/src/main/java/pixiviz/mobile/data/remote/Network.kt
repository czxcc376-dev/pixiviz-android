package pixiviz.mobile.data.remote

import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import retrofit2.Retrofit
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.GET
import retrofit2.http.Url
import java.util.concurrent.TimeUnit
import okhttp3.MediaType.Companion.toMediaType

/**
 * Live relay configuration. Read on every request by the interceptors so that changing the
 * Worker URL / access key in Settings takes effect without rebuilding the HTTP stack.
 */
class RelayConfig {
    @Volatile var workerUrl: String = ""
    @Volatile var appKey: String = ""
    @Volatile var accessToken: String? = null
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

/** Adds the relay access key, and the user access token when one is available on-device. */
private class RelayAuthInterceptor(private val config: RelayConfig) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val builder = original.newBuilder()
        if (config.appKey.isNotBlank()) builder.header("X-App-Key", config.appKey)
        config.accessToken?.takeIf { it.isNotBlank() }?.let {
            builder.header("Authorization", "Bearer $it")
        }
        return chain.proceed(builder.build())
    }
}

/** i.pximg.net rejects requests without a pixiv referer; the Worker path needs the app key. */
private class ImageHeaderInterceptor(private val config: RelayConfig) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val host = original.url.host
        val builder = original.newBuilder().header("Accept", "image/*,*/*;q=0.8")
        when {
            host.endsWith("pximg.net") -> builder.header("Referer", "https://www.pixiv.net/")
            config.workerUrl.contains(host) && config.appKey.isNotBlank() ->
                builder.header("X-App-Key", config.appKey)
        }
        return chain.proceed(builder.build())
    }
}

object Network {

    fun apiClient(config: RelayConfig): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(RelayAuthInterceptor(config))
        .build()

    fun imageClient(config: RelayConfig): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(40, TimeUnit.SECONDS)
        .addInterceptor(ImageHeaderInterceptor(config))
        .build()

    fun createApi(client: OkHttpClient): PixivApi = Retrofit.Builder()
        .baseUrl("https://relay.invalid/")
        .client(client)
        .addConverterFactory(PixivJson.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(PixivApi::class.java)
}

/**
 * Every call takes an absolute URL. Paths are assembled against the configured Worker, and
 * pagination links returned by pixiv (`next_url`) are rewritten onto the same Worker.
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
