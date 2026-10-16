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
    class NeedsSignIn : RelayException("这个内容需要登录 pixiv 才能浏览")

    class Unauthorized : RelayException("登录已失效，或中转拒绝了请求")

    class Http(val code: Int, message: String) : RelayException(message)

    class Network(cause: Throwable) : RelayException("网络请求失败：${cause.message}", cause)
}

/** True when a failure means "throw the stored credential away and use another source". */
private fun Throwable.isCredentialRejection(): Boolean =
    this is RelayException.Unauthorized ||
        (this as? RelayException.Http)?.code == 400 ||
        (this as? RelayException.Http)?.code == 401

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
 * Single entry point for content, with sources in priority order:
 *
 *  1. signed in  -> app-api directly from the device (own account)
 *  2. relay set  -> HibiAPI-compatible backend, which also serves R-18 with no account
 *  3. otherwise  -> pixiv's public web endpoints (no R-18, ranking approximated)
 *
 * If a signed-in call is rejected the stored credential is dropped and the request is
 * replayed against the relay, so a stale token can never turn the whole app into errors.
 */
class PixivRepository(
    private val config: RelayConfig,
    private val api: PixivApi,
    private val hibi: HibiSource,
    private val guest: GuestSource,
    private val signedIn: () -> Boolean,
    private val backendAvailable: () -> Boolean,
    private val onAuthFailure: () -> Unit,
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

    fun adultContentAvailable(): Boolean = signedIn() || backendAvailable()

    /** Replays a rejected signed-in request against the next-best source. */
    private fun withFallback(app: IllustLoader, fallback: () -> IllustLoader): IllustLoader =
        IllustLoader { next ->
            try {
                app.load(next)
            } catch (e: RelayException) {
                if (!e.isCredentialRejection()) throw e
                onAuthFailure()
                fallback().load(next)
            }
        }

    private fun relayOrGuestRank(mode: String, date: String?): IllustLoader {
        val rankMode = RankModes.byKey(mode)
        if (backendAvailable()) return hibi.rankLoader(HibiRankModes.resolve(rankMode.key), date)
        if (rankMode.adult) return IllustLoader { throw RelayException.NeedsSignIn() }
        return guest.rankLoader(rankMode.webMode)
    }

    private fun relayOrGuestSearch(word: String, sort: String, duration: String?): IllustLoader =
        if (backendAvailable()) {
            hibi.searchLoader(word, sort, duration)
        } else {
            guest.searchLoader(word, mode = "all", order = if (sort == "popular_desc") "popular_d" else "date_d")
        }

    private fun relayOrGuestUserIllusts(userId: Long): IllustLoader =
        if (backendAvailable()) hibi.userIllustsLoader(userId) else guest.userWorksLoader(userId)

    private fun relayOrGuestRecommended(): IllustLoader =
        if (backendAvailable()) hibi.discoverLoader() else guest.discoveryLoader()

    // ---------------------------------------------------------------- loaders

    fun rankLoader(mode: String, date: String? = null): IllustLoader {
        if (!signedIn()) return relayOrGuestRank(mode, date)
        val app = IllustLoader { next ->
            call {
                api.illustList(
                    next ?: build(
                        "/v1/illust/ranking",
                        buildMap {
                            put("mode", mode)
                            put("filter", "for_android")
                            if (!date.isNullOrBlank()) put("date", date)
                        },
                    ),
                )
            }
        }
        return withFallback(app) { relayOrGuestRank(mode, date) }
    }

    fun recommendedLoader(): IllustLoader {
        if (!signedIn()) return relayOrGuestRecommended()
        val app = IllustLoader { next ->
            call {
                api.illustList(
                    next ?: build(
                        "/v1/illust/recommended",
                        mapOf("filter" to "for_android", "include_ranking_illusts" to "true"),
                    ),
                )
            }
        }
        return withFallback(app) { relayOrGuestRecommended() }
    }

    fun searchLoader(
        word: String,
        searchTarget: String = "partial_match_for_tags",
        sort: String = "date_desc",
        duration: String? = null,
    ): IllustLoader {
        if (!signedIn()) return relayOrGuestSearch(word, sort, duration)
        val app = IllustLoader { next ->
            call {
                api.illustList(
                    next ?: build(
                        "/v2/search/illust",
                        buildMap {
                            put("word", word)
                            put("search_target", searchTarget)
                            put("sort", sort)
                            put("filter", "for_android")
                            if (!duration.isNullOrBlank()) put("duration", duration)
                        },
                    ),
                )
            }
        }
        return withFallback(app) { relayOrGuestSearch(word, sort, duration) }
    }

    fun userIllustsLoader(userId: Long): IllustLoader {
        if (!signedIn()) return relayOrGuestUserIllusts(userId)
        val app = IllustLoader { next ->
            call {
                api.illustList(
                    next ?: build(
                        "/v1/user/illusts",
                        mapOf("user_id" to userId.toString(), "filter" to "for_android"),
                    ),
                )
            }
        }
        return withFallback(app) { relayOrGuestUserIllusts(userId) }
    }

    fun relatedLoader(illustId: Long): IllustLoader {
        if (!signedIn()) {
            return if (backendAvailable()) {
                hibi.relatedLoader(illustId)
            } else {
                IllustLoader { IllustListResponse(illusts = guest.related(illustId)) }
            }
        }
        val app = IllustLoader {
            call {
                api.illustList(
                    build(
                        "/v2/illust/related",
                        mapOf("illust_id" to illustId.toString(), "filter" to "for_android"),
                    ),
                )
            }
        }
        return withFallback(app) {
            if (backendAvailable()) hibi.relatedLoader(illustId) else IllustLoader { IllustListResponse() }
        }
    }

    // ---------------------------------------------------------------- singles

    private suspend fun <T> single(appCall: suspend () -> T, fallback: suspend () -> T): T {
        if (!signedIn()) return fallback()
        return try {
            appCall()
        } catch (e: RelayException) {
            if (!e.isCredentialRejection()) throw e
            onAuthFailure()
            fallback()
        }
    }

    suspend fun illustDetail(illustId: Long): Illust = single(
        appCall = {
            call {
                api.illustDetail(build("/v1/illust/detail", mapOf("illust_id" to illustId.toString()))).illust
            }
        },
        fallback = { if (backendAvailable()) hibi.illustDetail(illustId) else guest.illustDetail(illustId) },
    )

    suspend fun userDetail(userId: Long): UserDetailResponse = single(
        appCall = {
            call {
                api.userDetail(
                    build("/v1/user/detail", mapOf("user_id" to userId.toString(), "filter" to "for_android")),
                )
            }
        },
        fallback = { if (backendAvailable()) hibi.userDetail(userId) else guest.userDetail(userId) },
    )

    suspend fun trendingTags(): List<TrendingTag> = single(
        appCall = {
            call {
                api.trendingTags(build("/v1/trending-tags/illust", mapOf("filter" to "for_android"))).trendTags
            }
        },
        fallback = { if (backendAvailable()) hibi.trendingTags() else guest.trendingTags() },
    )

    suspend fun autocomplete(word: String): List<AutocompleteTag> = single(
        appCall = { call { api.autocomplete(build("/v2/search/autocomplete", mapOf("word" to word))).tags } },
        fallback = { if (backendAvailable()) hibi.autocomplete() else guest.autocomplete() },
    )

    suspend fun searchUser(word: String): List<UserPreview> = single(
        appCall = {
            call {
                api.searchUser(build("/v1/search/user", mapOf("word" to word, "filter" to "for_android")))
                    .userPreviews
            }
        },
        fallback = { if (backendAvailable()) hibi.searchUser(word) else guest.searchUser() },
    )

    // ---------------------------------------------------------------- helpers

    // ---------------------------------------------------------------- write actions

    /**
     * Bookmarking and following go to app-api with the user's own token; the public relay
     * cannot do them. [restrict] is "public" or "private".
     */
    suspend fun setBookmark(illustId: Long, bookmarked: Boolean, restrict: String = "public") = call {
        if (!signedIn()) throw RelayException.NeedsSignIn()
        if (bookmarked) {
            api.postForm(
                build("/v2/illust/bookmark/add"),
                mapOf("illust_id" to illustId.toString(), "restrict" to restrict),
            ).close()
        } else {
            api.delete(build("/v1/illust/bookmark/delete", mapOf("illust_id" to illustId.toString()))).close()
        }
    }

    suspend fun setFollow(userId: Long, follow: Boolean) = call {
        if (!signedIn()) throw RelayException.NeedsSignIn()
        if (follow) {
            api.postForm(
                build("/v2/user/follow", mapOf("user_id" to userId.toString())),
                mapOf("restrict" to "public"),
            ).close()
        } else {
            api.delete(build("/v1/user/follow/delete", mapOf("user_id" to userId.toString()))).close()
        }
    }

    suspend fun backendHealth(): Result<String> = hibi.healthCheck()

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
