package pixiviz.mobile.data.remote

import androidx.paging.PagingSource
import androidx.paging.PagingState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException
import pixiviz.mobile.BuildConfig
import pixiviz.mobile.data.remote.hibi.HibiRankModes
import pixiviz.mobile.data.remote.hibi.HibiSource
import pixiviz.mobile.data.remote.web.GuestSource

/** Errors surfaced to the UI, already classified so screens can message them precisely. */
sealed class RelayException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    /** Requested something the active source cannot serve. */
    class NeedsSignIn : RelayException("这个内容需要登录 pixiv 才能浏览")

    /** Credentials were rejected or a relay refused the request. */
    class Unauthorized : RelayException("登录已失效，或中转拒绝了请求")

    class Http(val code: Int, message: String) : RelayException(message)

    class Network(cause: Throwable) : RelayException("网络请求失败：${cause.message}", cause)
}

/**
 * Ranking modes.
 *
 * `key` is what pixiv's app-api calls it, which HibiAPI also accepts; `webMode` is the
 * equivalent on the public web ranking. `adult` modes are only offered where the active
 * source can actually serve them.
 */
data class RankMode(
    val key: String,
    val label: String,
    val webMode: String,
    val adult: Boolean = false,
)

object RankModes {
    val all: List<RankMode> = listOf(
        RankMode("day", "日榜", "daily"),
        RankMode("week", "周榜", "weekly"),
        RankMode("month", "月榜", "monthly"),
        RankMode("day_male", "男性向", "daily_male"),
        RankMode("day_female", "女性向", "daily_female"),
        RankMode("week_original", "原创周榜", "weekly_original"),
        RankMode("week_rookie", "新人周榜", "weekly_rookie"),
        RankMode("day_manga", "漫画日榜", "daily_manga"),
        RankMode("day_ai", "AI 日榜", "daily_ai"),
        RankMode("day_r18", "R18 日榜", "daily_r18", adult = true),
        RankMode("day_male_r18", "R18 男性向", "male_r18", adult = true),
        RankMode("day_female_r18", "R18 女性向", "female_r18", adult = true),
        RankMode("week_r18", "R18 周榜", "weekly_r18", adult = true),
        RankMode("week_r18g", "R18-G 周榜", "r18g", adult = true),
        RankMode("day_r18_ai", "R18-AI 日榜", "daily_r18", adult = true),
        RankMode("day_r18_manga", "R18 漫画日榜", "daily_r18", adult = true),
        RankMode("week_r18_manga", "R18 漫画周榜", "weekly_r18", adult = true),
    )

    fun byKey(key: String): RankMode = all.firstOrNull { it.key == key } ?: all.first()
}

fun interface IllustLoader {
    suspend fun load(nextUrl: String?): IllustListResponse
}

class IllustPagingSource(private val loader: IllustLoader) : PagingSource<String, Illust>() {

    override suspend fun load(params: LoadParams<String>): LoadResult<String, Illust> = try {
        val response = loader.load(params.key)
        LoadResult.Page(data = response.illusts, prevKey = null, nextKey = response.nextUrl)
    } catch (e: RelayException) {
        LoadResult.Error(e)
    } catch (e: IOException) {
        LoadResult.Error(RelayException.Network(e))
    }

    override fun getRefreshKey(state: PagingState<String, Illust>): String? = null
}

/**
 * Single entry point for content, with three sources in priority order:
 *
 *  1. signed in  -> app-api directly, from the device (own account, own R-18 settings)
 *  2. relay set  -> a HibiAPI-compatible backend, which serves R-18 without an account
 *  3. otherwise  -> pixiv's public web endpoints (no R-18, ranking approximated)
 *
 * Traffic never goes through Cloudflare Workers: pixiv answers that egress with a block page.
 */
class PixivRepository(
    private val config: RelayConfig,
    private val api: PixivApi,
    private val hibi: HibiSource,
    private val guest: GuestSource,
    private val signedIn: () -> Boolean,
    private val backendAvailable: () -> Boolean,
) {

    private fun appBase(): String = BuildConfig.PIXIV_APP_API_BASE.trimEnd('/')

    private fun build(path: String, params: Map<String, String> = emptyMap()): String {
        val query = params.entries
            .filter { it.value.isNotBlank() }
            .joinToString("&") { "${it.key}=${urlEncode(it.value)}" }
        return if (query.isEmpty()) appBase() + path else appBase() + path + "?" + query
    }

    private suspend fun <T> call(block: suspend () -> T): T = withContext(Dispatchers.IO) {
        try {
            block()
        } catch (e: HttpException) {
            throw when (e.code()) {
                401 -> RelayException.Unauthorized()
                403 -> RelayException.NeedsSignIn()
                404 -> RelayException.Http(404, "作品不存在或已删除")
                else -> RelayException.Http(e.code(), "HTTP ${e.code()}")
            }
        } catch (e: IOException) {
            throw RelayException.Network(e)
        }
    }

    /** True when adult ranking modes can be served by whatever source is active. */
    fun adultContentAvailable(): Boolean = signedIn() || backendAvailable()

    // ---------------------------------------------------------------- loaders

    fun rankLoader(mode: String, date: String? = null): IllustLoader {
        val rankMode = RankModes.byKey(mode)
        if (!signedIn()) {
            if (backendAvailable()) {
                val isoDate = date ?: if (rankMode.key.startsWith("day")) HibiRankModes.todayIso() else null
                return hibi.rankLoader(HibiRankModes.resolve(rankMode.key), isoDate)
            }
            if (rankMode.adult) return IllustLoader { throw RelayException.NeedsSignIn() }
            return guest.rankLoader(rankMode.webMode)
        }
        return IllustLoader { next ->
            call {
                val url = next ?: build(
                    "/v1/illust/ranking",
                    buildMap {
                        put("mode", mode)
                        put("filter", "for_android")
                        if (!date.isNullOrBlank()) put("date", date)
                    },
                )
                api.illustList(url)
            }
        }
    }

    fun recommendedLoader(): IllustLoader {
        if (!signedIn()) {
            if (backendAvailable()) return hibi.discoverLoader()
            return guest.discoveryLoader()
        }
        return IllustLoader { next ->
            call {
                api.illustList(
                    next ?: build(
                        "/v1/illust/recommended",
                        mapOf("filter" to "for_android", "include_ranking_illusts" to "true"),
                    ),
                )
            }
        }
    }

    fun searchLoader(
        word: String,
        searchTarget: String = "partial_match_for_tags",
        sort: String = "date_desc",
        duration: String? = null,
    ): IllustLoader {
        if (!signedIn()) {
            if (backendAvailable()) return hibi.searchLoader(word, sort, duration)
            val webOrder = if (sort == "popular_desc") "popular_d" else "date_d"
            return guest.searchLoader(word, mode = "all", order = webOrder)
        }
        return IllustLoader { next ->
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
    }

    fun userIllustsLoader(userId: Long): IllustLoader {
        if (!signedIn()) {
            if (backendAvailable()) return hibi.userIllustsLoader(userId)
            return guest.userWorksLoader(userId)
        }
        return IllustLoader { next ->
            call {
                api.illustList(
                    next ?: build(
                        "/v1/user/illusts",
                        mapOf("user_id" to userId.toString(), "filter" to "for_android"),
                    ),
                )
            }
        }
    }

    fun relatedLoader(illustId: Long): IllustLoader = IllustLoader { next ->
        when {
            !signedIn() && backendAvailable() -> hibi.relatedLoader(illustId).load(next)
            !signedIn() -> IllustListResponse(illusts = guest.related(illustId))
            else -> call {
                api.illustList(
                    build(
                        "/v2/illust/related",
                        mapOf("illust_id" to illustId.toString(), "filter" to "for_android"),
                    ),
                )
            }
        }
    }

    // ---------------------------------------------------------------- singles

    suspend fun illustDetail(illustId: Long): Illust = when {
        signedIn() -> call {
            api.illustDetail(build("/v1/illust/detail", mapOf("illust_id" to illustId.toString()))).illust
        }
        backendAvailable() -> hibi.illustDetail(illustId)
        else -> guest.illustDetail(illustId)
    }

    suspend fun userDetail(userId: Long): UserDetailResponse = when {
        signedIn() -> call {
            api.userDetail(
                build("/v1/user/detail", mapOf("user_id" to userId.toString(), "filter" to "for_android")),
            )
        }
        backendAvailable() -> hibi.userDetail(userId)
        else -> guest.userDetail(userId)
    }

    suspend fun trendingTags(): List<TrendingTag> = when {
        signedIn() -> call {
            api.trendingTags(build("/v1/trending-tags/illust", mapOf("filter" to "for_android"))).trendTags
        }
        backendAvailable() -> hibi.trendingTags()
        else -> guest.trendingTags()
    }

    suspend fun autocomplete(word: String): List<AutocompleteTag> = when {
        signedIn() -> call { api.autocomplete(build("/v2/search/autocomplete", mapOf("word" to word))).tags }
        backendAvailable() -> hibi.autocomplete()
        else -> guest.autocomplete()
    }

    suspend fun searchUser(word: String): List<UserPreview> = when {
        signedIn() -> call {
            api.searchUser(build("/v1/search/user", mapOf("word" to word, "filter" to "for_android")))
                .userPreviews
        }
        backendAvailable() -> hibi.searchUser(word)
        else -> guest.searchUser()
    }

    // ---------------------------------------------------------------- helpers

    /**
     * DIRECT keeps i.pximg.net and lets the interceptor add the referer, which is the fastest
     * path and verified to work from a device. RELAY routes bytes through the Worker when a
     * user wants their IP hidden from pixiv's CDN.
     */
    fun imageUrl(raw: String, preferOriginal: Boolean = false): String {
        if (raw.isBlank() || config.imageChannelDirect || config.baseUrl().isBlank()) return raw
        val tail = raw.substringAfter("://", "").substringAfter('/', "")
        return config.baseUrl() + "/img/" + tail
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
