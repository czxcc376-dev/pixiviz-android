package pixiviz.mobile.data.remote

import androidx.paging.PagingSource
import androidx.paging.PagingState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import retrofit2.HttpException
import java.io.IOException

/** Errors surfaced to the UI, already classified so screens can message them precisely. */
sealed class RelayException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    /** The Worker has no pixiv credential and the device has no token either. */
    class NoCredential : RelayException("尚未登录 pixiv，也没有可用的服务端凭据")

    /** The stored token was rejected - the UI should send the user back to sign-in. */
    class Unauthorized : RelayException("登录已失效，请重新登录")

    /** Access key rejected by the Worker. */
    class BadAppKey : RelayException("Worker 访问密钥不正确")

    class Http(val code: Int, message: String) : RelayException(message)

    class Network(cause: Throwable) : RelayException("网络请求失败：${cause.message}", cause)
}

/** Ranking modes. `adult` entries are hidden until the NSFW gate is unlocked. */
data class RankMode(val key: String, val label: String, val adult: Boolean = false)

object RankModes {
    val all: List<RankMode> = listOf(
        RankMode("day", "日榜"),
        RankMode("week", "周榜"),
        RankMode("month", "月榜"),
        RankMode("day_male", "男性向"),
        RankMode("day_female", "女性向"),
        RankMode("week_original", "原创周榜"),
        RankMode("week_rookie", "新人周榜"),
        RankMode("day_manga", "漫画日榜"),
        RankMode("day_ai", "AI 日榜"),
        RankMode("day_r18", "R18 日榜", adult = true),
        RankMode("day_male_r18", "R18 男性向", adult = true),
        RankMode("day_female_r18", "R18 女性向", adult = true),
        RankMode("week_r18", "R18 周榜", adult = true),
        RankMode("week_r18g", "R18-G 周榜", adult = true),
        RankMode("day_r18_ai", "R18-AI 日榜", adult = true),
        RankMode("day_r18_manga", "R18 漫画日榜", adult = true),
        RankMode("week_r18_manga", "R18 漫画周榜", adult = true),
    )

    fun byKey(key: String): RankMode = all.firstOrNull { it.key == key } ?: all.first()
}

/** Loader abstraction so one paging source can drive ranking, search and artist grids. */
fun interface IllustLoader {
    suspend fun load(nextUrl: String?): IllustListResponse
}

class IllustPagingSource(private val loader: IllustLoader) : PagingSource<String, Illust>() {

    override suspend fun load(params: LoadParams<String>): LoadResult<String, Illust> = try {
        val response = loader.load(params.key)
        LoadResult.Page(
            data = response.illusts,
            prevKey = null,
            nextKey = response.nextUrl,
        )
    } catch (e: RelayException) {
        LoadResult.Error(e)
    } catch (e: IOException) {
        LoadResult.Error(RelayException.Network(e))
    }

    override fun getRefreshKey(state: PagingState<String, Illust>): String? = null
}

class PixivRepository(
    private val config: RelayConfig,
    private val api: PixivApi,
) {

    private fun base(): String = config.baseUrl()

    private fun build(path: String, params: Map<String, String> = emptyMap()): String {
        if (base().isBlank()) throw RelayException.Http(0, "还没有配置 Worker 地址")
        val query = params.entries
            .filter { it.value.isNotBlank() }
            .joinToString("&") { "${it.key}=${urlEncode(it.value)}" }
        return if (query.isEmpty()) base() + path else base() + path + "?" + query
    }

    /** pixiv hands back absolute app-api URLs for the next page; re-point them at the Worker. */
    private fun rewrite(nextUrl: String): String {
        val parsed = nextUrl.toHttpUrlOrNull() ?: return nextUrl
        val query = parsed.encodedQuery
        return if (query.isNullOrBlank()) base() + parsed.encodedPath else base() + parsed.encodedPath + "?" + query
    }

    private suspend fun <T> call(block: suspend () -> T): T = withContext(Dispatchers.IO) {
        try {
            block()
        } catch (e: HttpException) {
            throw mapHttp(e)
        } catch (e: IOException) {
            throw RelayException.Network(e)
        }
    }

    private suspend fun mapHttp(e: HttpException): RelayException {
        val code = e.code()
        val body = runCatching { e.response()?.errorBody()?.string() }.getOrNull().orEmpty()
        val kind = Regex("\"error\"\\s*:\\s*\"([^\"]+)\"").find(body)?.groupValues?.get(1)
        return when {
            code == 401 && kind == "no_credential" -> RelayException.NoCredential()
            code == 401 && kind == "unauthorized" -> RelayException.BadAppKey()
            code == 401 -> RelayException.Unauthorized()
            else -> RelayException.Http(code, "HTTP $code")
        }
    }

    fun rankLoader(mode: String, date: String? = null): IllustLoader = IllustLoader { next ->
        call {
            val url = if (next != null) {
                rewrite(next)
            } else {
                build(
                    "/v1/illust/ranking",
                    buildMap {
                        put("mode", mode)
                        put("filter", "for_android")
                        if (!date.isNullOrBlank()) put("date", date)
                    },
                )
            }
            api.illustList(url)
        }
    }

    fun recommendedLoader(): IllustLoader = IllustLoader { next ->
        call {
            val url = next ?: build(
                "/v1/illust/recommended",
                mapOf("filter" to "for_android", "include_ranking_illusts" to "true"),
            )
            api.illustList(url)
        }
    }

    fun relatedLoader(illustId: Long): IllustLoader = IllustLoader { _ ->
        call {
            api.illustList(
                build(
                    "/v2/illust/related",
                    mapOf("illust_id" to illustId.toString(), "filter" to "for_android"),
                ),
            )
        }
    }

    fun searchLoader(
        word: String,
        searchTarget: String = "partial_match_for_tags",
        sort: String = "date_desc",
        duration: String? = null,
    ): IllustLoader = IllustLoader { next ->
        call {
            val url = next ?: build(
                "/v2/search/illust",
                buildMap {
                    put("word", word)
                    put("search_target", searchTarget)
                    put("sort", sort)
                    put("filter", "for_android")
                    if (!duration.isNullOrBlank()) put("duration", duration)
                },
            )
            api.illustList(url)
        }
    }

    fun userIllustsLoader(userId: Long): IllustLoader = IllustLoader { next ->
        call {
            val url = next ?: build(
                "/v1/user/illusts",
                mapOf("user_id" to userId.toString(), "filter" to "for_android"),
            )
            api.illustList(url)
        }
    }

    suspend fun trendingTags(): List<TrendingTag> = call {
        api.trendingTags(build("/v1/trending-tags/illust", mapOf("filter" to "for_android"))).trendTags
    }

    suspend fun autocomplete(word: String): List<AutocompleteTag> = call {
        api.autocomplete(build("/v2/search/autocomplete", mapOf("word" to word))).tags
    }

    suspend fun searchUser(word: String): List<UserPreview> = call {
        api.searchUser(build("/v1/search/user", mapOf("word" to word, "filter" to "for_android")))
            .userPreviews
    }

    suspend fun illustDetail(illustId: Long): Illust = call {
        api.illustDetail(build("/v1/illust/detail", mapOf("illust_id" to illustId.toString()))).illust
    }

    suspend fun userDetail(userId: Long): UserDetailResponse = call {
        api.userDetail(build("/v1/user/detail", mapOf("user_id" to userId.toString(), "filter" to "for_android")))
    }

    /**
     * Route an image URL according to the configured channel.
     * DIRECT keeps i.pximg.net and lets the OkHttp interceptor add the referer;
     * RELAY sends bytes through the Worker so the device IP never reaches pixiv.
     */
    fun imageUrl(raw: String, preferOriginal: Boolean = false): String {
        if (raw.isBlank()) return raw
        val resolved = if (preferOriginal && raw.contains("img-master")) raw else raw
        if (!config.imageChannelDirect) {
            val tail = resolved.substringAfter("://", "").substringAfter('/', "")
            return base() + "/img/" + tail
        }
        return resolved
    }

    /** Accepts a pixiv URL or a bare id and returns the id, or null. */
    fun parseIllustId(input: String): Long? {
        val trimmed = input.trim()
        trimmed.toLongOrNull()?.let { return it }
        return Regex("illust_id=(\\d+)").find(trimmed)?.groupValues?.get(1)?.toLongOrNull()
            ?: Regex("artworks/(\\d+)").find(trimmed)?.groupValues?.get(1)?.toLongOrNull()
    }

    private companion object {
        fun urlEncode(value: String): String =
            java.net.URLEncoder.encode(value, "UTF-8").replace("+", "%20")
    }
}
