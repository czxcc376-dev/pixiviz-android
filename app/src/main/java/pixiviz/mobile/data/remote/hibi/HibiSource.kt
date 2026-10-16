package pixiviz.mobile.data.remote.hibi

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException
import java.time.LocalDate
import pixiviz.mobile.data.remote.AutocompleteTag
import pixiviz.mobile.data.remote.Illust
import pixiviz.mobile.data.remote.IllustListResponse
import pixiviz.mobile.data.remote.IllustLoader
import pixiviz.mobile.data.remote.PixivApi
import pixiviz.mobile.data.remote.RelayConfig
import pixiviz.mobile.data.remote.RelayException
import pixiviz.mobile.data.remote.TrendingTag
import pixiviz.mobile.data.remote.UserDetailResponse
import pixiviz.mobile.data.remote.UserPreview

/**
 * A HibiAPI-compatible pixiv relay - the same kind of backend the original Pixiviz used.
 *
 * Verified against a live instance with a browser User-Agent (a plain curl UA hits the
 * Cloudflare challenge in front of it):
 *
 *   rank?mode=&date=&page=&size=          -> {illusts, next_url}
 *   search?word=&mode=&order=&page=&size= -> {illusts, next_url}
 *   related?id=&page=&size=               -> {illusts, next_url}
 *   member_illust?id=&illust_type=&page=  -> {user, illusts, next_url}
 *   member?id= / illust?id= / search_user?word= / tags
 *
 * Response bodies match pixiv's app-api shape, so the existing DTOs are reused unchanged.
 * `*_r18` ranking modes answer 200, which is how adult content is reachable with no account.
 *
 * Two behavioural notes learned the hard way:
 *  - `date` must be omitted for "current" rankings. Sending yesterday's date makes pixiv
 *    reject the call (the relay surfaces that as a 500), so it is only sent when the user
 *    explicitly stepped back to an earlier period, and a retry without it happens anyway.
 *  - A single public instance is not reliable; endpoints are rotated on failure.
 */
class HibiSource(
    private val api: PixivApi,
    private val config: RelayConfig,
) {

    /** Runs [block] against the preferred endpoint, rotating to the next one on failure. */
    private suspend fun <T> attempt(block: suspend (String) -> T): T {
        val tried = mutableSetOf<String>()
        var last: Throwable? = null
        while (true) {
            val base = config.backendUrl()
            if (base.isBlank()) throw RelayException.Http(0, "还没有配置数据源")
            try {
                return block(base)
            } catch (e: Throwable) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                last = e
                tried += base
                val moved = config.rotateBackend()
                if (!moved || config.backendUrl() in tried) throw e
            }
        }
    }

    private fun url(base: String, path: String, params: Map<String, String>): String {
        val query = params.entries
            .filter { it.value.isNotBlank() }
            .joinToString("&") { "${it.key}=${it.value}" }
        return base.trimEnd('/') + path + if (query.isEmpty()) "" else "?$query"
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

    private fun pageOf(next: String?): Int = next?.toIntOrNull() ?: 1

    private fun nextPage(size: Int, page: Int): String? =
        if (size >= PAGE_SIZE) (page + 1).toString() else null

    /** Ranking. `dateIso` is only honoured when the caller explicitly asked for a past period. */
    fun rankLoader(mode: String, dateIso: String?): IllustLoader = IllustLoader { next ->
        call {
            val page = pageOf(next)
            attempt { base ->
                val params = mutableMapOf(
                    "mode" to mode,
                    "page" to page.toString(),
                    "size" to PAGE_SIZE.toString(),
                )
                dateIso?.takeIf { it.isNotBlank() }?.let { params["date"] = it.replace("-", "") }
                try {
                    api.illustList(url(base, "/rank", params))
                } catch (e: Exception) {
                    if (dateIso == null) throw e
                    // A stale or out-of-range date makes pixiv reject the whole call.
                    params.remove("date")
                    api.illustList(url(base, "/rank", params))
                }
            }.let { IllustListResponse(it.illusts, nextPage(it.illusts.size, page)) }
        }
    }

    fun searchLoader(word: String, sort: String = "date_desc", duration: String? = null): IllustLoader =
        IllustLoader { next ->
            call {
                if (word.isBlank()) return@call IllustListResponse()
                val page = pageOf(next)
                attempt { base ->
                    val params = mutableMapOf(
                        "word" to enc(word),
                        "mode" to "partial_match_for_tags",
                        "order" to sort,
                        "page" to page.toString(),
                        "size" to PAGE_SIZE.toString(),
                    )
                    duration?.takeIf { it.isNotBlank() }?.let { params["duration"] = it }
                    api.illustList(url(base, "/search", params))
                }.let { IllustListResponse(it.illusts, nextPage(it.illusts.size, page)) }
            }
        }

    fun relatedLoader(illustId: Long): IllustLoader = IllustLoader { next ->
        call {
            val page = pageOf(next)
            attempt { base ->
                api.illustList(
                    url(base, "/related", mapOf("id" to illustId.toString(), "page" to page.toString(), "size" to PAGE_SIZE.toString())),
                )
            }.let { IllustListResponse(it.illusts, nextPage(it.illusts.size, page)) }
        }
    }

    fun userIllustsLoader(userId: Long): IllustLoader = IllustLoader { next ->
        call {
            val page = pageOf(next)
            attempt { base ->
                api.illustList(
                    url(
                        base,
                        "/member_illust",
                        mapOf(
                            "id" to userId.toString(),
                            "illust_type" to "illust",
                            "page" to page.toString(),
                            "size" to PAGE_SIZE.toString(),
                        ),
                    ),
                )
            }.let { IllustListResponse(it.illusts, nextPage(it.illusts.size, page)) }
        }
    }

    /** The relay has no dedicated "recommended" feed; the daily ranking stands in for it. */
    fun discoverLoader(): IllustLoader = rankLoader("day", null)

    suspend fun illustDetail(illustId: Long): Illust = call {
        attempt { base -> api.illustDetail(url(base, "/illust", mapOf("id" to illustId.toString()))).illust }
    }

    suspend fun userDetail(userId: Long): UserDetailResponse = call {
        attempt { base -> api.userDetail(url(base, "/member", mapOf("id" to userId.toString()))) }
    }

    suspend fun trendingTags(): List<TrendingTag> = call {
        attempt { base -> api.trendingTags(url(base, "/tags", emptyMap())).trendTags }
    }

    suspend fun searchUser(word: String): List<UserPreview> = call {
        attempt { base -> api.searchUser(url(base, "/search_user", mapOf("word" to enc(word)))).userPreviews }
    }

    /** HibiAPI exposes no autocomplete route. */
    suspend fun autocomplete(): List<AutocompleteTag> = emptyList()

    /** One live request against the active endpoint, for the in-app diagnostics panel. */
    suspend fun healthCheck(): Result<String> = runCatching {
        val base = config.backendUrl()
        val tags = call { attempt { it2 -> api.trendingTags(url(it2, "/tags", emptyMap())).trendTags } }
        "可用 · $base · 热门标签 ${tags.size} 条"
    }

    private fun enc(value: String): String =
        java.net.URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    private companion object {
        const val PAGE_SIZE = 30
    }
}

/** Ranking modes accepted by `/api/pixiv/rank`. */
object HibiRankModes {
    private val supported = setOf(
        "day", "week", "month", "day_male", "day_female",
        "week_original", "week_rookie", "day_manga", "day_ai",
        "day_r18", "week_r18", "day_male_r18", "day_female_r18", "week_r18g",
        "day_r18_ai", "week_r18_ai", "day_r18_manga", "week_r18_manga",
    )

    fun resolve(key: String): String = if (key in supported) key else when {
        key.startsWith("week") && key.contains("r18") -> "week_r18"
        key.contains("r18") -> "day_r18"
        key.startsWith("week") -> "week"
        else -> "day"
    }

    fun isoDaysAgo(days: Long): String = LocalDate.now().minusDays(days).toString()
}
