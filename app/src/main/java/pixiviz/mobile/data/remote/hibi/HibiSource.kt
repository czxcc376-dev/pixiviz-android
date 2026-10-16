package pixiviz.mobile.data.remote.hibi

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException
import java.time.LocalDate
import pixiviz.mobile.data.remote.Illust
import pixiviz.mobile.data.remote.IllustListResponse
import pixiviz.mobile.data.remote.IllustLoader
import pixiviz.mobile.data.remote.PixivApi
import pixiviz.mobile.data.remote.RelayException
import pixiviz.mobile.data.remote.TrendingTag
import pixiviz.mobile.data.remote.UserDetailResponse

/**
 * A HibiAPI-compatible pixiv relay - the same kind of backend the original Pixiviz used.
 *
 * Verified against a live instance with a browser User-Agent (a plain curl UA gets caught by
 * the Cloudflare challenge in front of it):
 *
 *   rank?mode=&date=&page=&size=          -> {illusts, next_url}
 *   search?word=&mode=&order=&page=&size= -> {illusts, next_url}
 *   related?id=&page=&size=               -> {illusts, next_url}
 *   member_illust?id=&illust_type=&page=  -> {user, illusts, next_url}
 *   member?id=                            -> {user, profile}
 *   illust?id=                            -> {illust}
 *   search_user?word=                     -> {user_previews, next_url}
 *   tags                                  -> {trend_tags}
 *
 * The payloads match pixiv's app-api shape, so the existing DTOs are reused as-is. Ranking
 * modes named `*_r18` answer 200 here, which is what makes adult content reachable without
 * signing in: the relay holds its own account.
 *
 * Paging is by page number: the `next_url` these endpoints return points at app-api, which
 * would need a token, so it is deliberately not followed.
 */
class HibiSource(
    private val api: PixivApi,
    private val baseUrl: () -> String,
) {

    private fun base(): String = baseUrl().trimEnd('/')

    private fun url(path: String, params: Map<String, String>): String {
        val query = params.entries
            .filter { it.value.isNotBlank() }
            .joinToString("&") { "${it.key}=${it.value}" }
        return if (query.isEmpty()) base() + path else base() + path + "?" + query
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

    fun rankLoader(mode: String, dateIso: String?): IllustLoader = IllustLoader { next ->
        call {
            val page = pageOf(next)
            val params = mutableMapOf(
                "mode" to mode,
                "page" to page.toString(),
                "size" to PAGE_SIZE.toString(),
            )
            dateIso?.let { params["date"] = it.replace("-", "") }
            api.illustList(url("/rank", params)).let {
                IllustListResponse(illusts = it.illusts, nextUrl = nextPage(it.illusts.size, page))
            }
        }
    }

    fun searchLoader(word: String, sort: String = "date_desc", duration: String? = null): IllustLoader =
        IllustLoader { next ->
            call {
                if (word.isBlank()) return@call IllustListResponse()
                val page = pageOf(next)
                val params = mutableMapOf(
                    "word" to enc(word),
                    "mode" to "partial_match_for_tags",
                    "order" to sort,
                    "page" to page.toString(),
                    "size" to PAGE_SIZE.toString(),
                )
                duration?.takeIf { it.isNotBlank() }?.let { params["duration"] = it }
                val result = api.illustList(url("/search", params))
                IllustListResponse(result.illusts, nextPage(result.illusts.size, page))
            }
        }

    fun relatedLoader(illustId: Long): IllustLoader = IllustLoader { next ->
        call {
            val page = pageOf(next)
            val result = api.illustList(
                url("/related", mapOf("id" to illustId.toString(), "page" to page.toString(), "size" to PAGE_SIZE.toString())),
            )
            IllustListResponse(result.illusts, nextPage(result.illusts.size, page))
        }
    }

    fun userIllustsLoader(userId: Long): IllustLoader = IllustLoader { next ->
        call {
            val page = pageOf(next)
            val result = api.illustList(
                url(
                    "/member_illust",
                    mapOf(
                        "id" to userId.toString(),
                        "illust_type" to "illust",
                        "page" to page.toString(),
                        "size" to PAGE_SIZE.toString(),
                    ),
                ),
            )
            IllustListResponse(result.illusts, nextPage(result.illusts.size, page))
        }
    }

    fun discoverLoader(): IllustLoader = rankLoader("day", null)

    suspend fun illustDetail(illustId: Long): Illust = call {
        api.illustDetail(url("/illust", mapOf("id" to illustId.toString()))).illust
    }

    suspend fun userDetail(userId: Long): UserDetailResponse = call {
        api.userDetail(url("/member", mapOf("id" to userId.toString())))
    }

    suspend fun trendingTags(): List<TrendingTag> = call {
        api.trendingTags(url("/tags", emptyMap())).trendTags
    }

    suspend fun searchUser(word: String): List<pixiviz.mobile.data.remote.UserPreview> = call {
        api.searchUser(url("/search_user", mapOf("word" to enc(word)))).userPreviews
    }

    /** HibiAPI exposes no autocomplete route; the tag list is used instead. */
    suspend fun autocomplete(): List<pixiviz.mobile.data.remote.AutocompleteTag> = emptyList()

    /** A full page came back, so there is probably another one. */
    private fun nextPage(size: Int, page: Int): String? = if (size >= PAGE_SIZE) (page + 1).toString() else null

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

    /** Falls back to the closest supported mode when a key has no exact counterpart. */
    fun resolve(key: String): String = if (key in supported) key else when {
        key.startsWith("week") && key.contains("r18") -> "week_r18"
        key.contains("r18") -> "day_r18"
        key.startsWith("week") -> "week"
        else -> "day"
    }

    fun todayIso(): String = LocalDate.now().minusDays(1).toString()
}
