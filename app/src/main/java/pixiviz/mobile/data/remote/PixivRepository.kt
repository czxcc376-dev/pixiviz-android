package pixiviz.mobile.data.remote

import androidx.paging.PagingSource
import androidx.paging.PagingState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import retrofit2.HttpException
import java.io.IOException
import pixiviz.mobile.BuildConfig
import pixiviz.mobile.data.remote.web.GuestSource

/** Errors surfaced to the UI, already classified so screens can message them precisely. */
sealed class RelayException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    /** Requested something that needs a pixiv account. */
    class NeedsSignIn : RelayException("这个内容需要登录 pixiv 才能浏览")

    /** The stored token was rejected - the UI should send the user back to sign-in. */
    class Unauthorized : RelayException("登录已失效，请重新登录")

    class Http(val code: Int, message: String) : RelayException(message)

    class Network(cause: Throwable) : RelayException("网络请求失败：${cause.message}", cause)
}

/**
 * Ranking modes for both surfaces.
 *
 * `webMode` is the equivalent used by the public web ranking (only needed when browsing
 * without an account); `requiresLogin` marks modes that answer 403 for guests, which is
 * every R-18 mode.
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
 * Single entry point for content. Signed-in traffic goes to app-api (the only surface that
 * returns R-18); otherwise the public web surface is used, so the app is usable with no
 * account at all.
 */
class PixivRepository(
    private val config: RelayConfig,
    private val api: PixivApi,
    private val guest: GuestSource,
    private val signedIn: () -> Boolean,
) {

    private fun appBase(): String = BuildConfig.PIXIV_APP_API_BASE.trimEnd('/')

    private fun build(path: String, params: Map<String, String> = emptyMap()): String {
        val query = params.entries
            .filter { it.value.isNotBlank() }
            .joinToString("&") { "${it.key}=${urlEncode(it.value)}" }
        return if (query.isEmpty()) appBase() + path else appBase() + path + "?" + query
    }

    /** app-api hands back absolute next-page URLs; they already point at the right host. */
    private fun rewrite(nextUrl: String): String = nextUrl

    private suspend fun <T> call(block: suspend () -> T): T = withContext(Dispatchers.IO) {
        try {
            block()
        } catch (e: HttpException) {
            throw mapHttp(e)
        } catch (e: IOException) {
            throw RelayException.Network(e)
        }
    }

    private fun mapHttp(e: HttpException): RelayException = when (e.code()) {
        401 -> RelayException.Unauthorized()
        403 -> RelayException.NeedsSignIn()
        404 -> RelayException.Http(404, "作品不存在或已删除")
        else -> RelayException.Http(e.code(), "HTTP ${e.code()}")
    }

    // ---------------------------------------------------------------- loaders

    fun rankLoader(mode: String, date: String? = null): IllustLoader {
        val rankMode = RankModes.byKey(mode)
        if (!signedIn()) {
            if (rankMode.adult) {
                return IllustLoader { throw RelayException.NeedsSignIn() }
            }
            return guest.rankLoader(rankMode.webMode)
        }
        return IllustLoader { next ->
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
    }

    fun recommendedLoader(): IllustLoader {
        if (!signedIn()) return guest.discoveryLoader()
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
        if (!signedIn()) return guest.userWorksLoader(userId)
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

    fun relatedLoader(illustId: Long): IllustLoader = IllustLoader { _ ->
        if (!signedIn()) {
            IllustListResponse(illusts = guest.related(illustId))
        } else {
            call {
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

    suspend fun illustDetail(illustId: Long): Illust =
        if (signedIn()) {
            call {
                api.illustDetail(build("/v1/illust/detail", mapOf("illust_id" to illustId.toString()))).illust
            }
        } else {
            guest.illustDetail(illustId)
        }

    suspend fun userDetail(userId: Long): UserDetailResponse =
        if (signedIn()) {
            call {
                api.userDetail(
                    build("/v1/user/detail", mapOf("user_id" to userId.toString(), "filter" to "for_android")),
                )
            }
        } else {
            guest.userDetail(userId)
        }

    suspend fun trendingTags(): List<TrendingTag> =
        if (signedIn()) {
            call {
                api.trendingTags(build("/v1/trending-tags/illust", mapOf("filter" to "for_android"))).trendTags
            }
        } else {
            guest.trendingTags()
        }

    suspend fun autocomplete(word: String): List<AutocompleteTag> =
        if (signedIn()) {
            call { api.autocomplete(build("/v2/search/autocomplete", mapOf("word" to word))).tags }
        } else {
            guest.autocomplete()
        }

    suspend fun searchUser(word: String): List<UserPreview> =
        if (signedIn()) {
            call {
                api.searchUser(build("/v1/search/user", mapOf("word" to word, "filter" to "for_android")))
                    .userPreviews
            }
        } else {
            guest.searchUser()
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
