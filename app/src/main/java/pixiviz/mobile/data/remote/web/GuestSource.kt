package pixiviz.mobile.data.remote.web

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException
import pixiviz.mobile.data.remote.AutocompleteTag
import pixiviz.mobile.data.remote.Illust
import pixiviz.mobile.data.remote.IllustListResponse
import pixiviz.mobile.data.remote.IllustLoader
import pixiviz.mobile.data.remote.ImageUrls
import pixiviz.mobile.data.remote.MetaPage
import pixiviz.mobile.data.remote.MetaSinglePage
import pixiviz.mobile.data.remote.RelayException
import pixiviz.mobile.data.remote.TrendingTag
import pixiviz.mobile.data.remote.UserDetail
import pixiviz.mobile.data.remote.UserDetailResponse

/**
 * Browsing without a pixiv account.
 *
 * Uses only pixiv's public web endpoints, which answer without any cookie. Pagination is a
 * page counter (the loader key is the page number as a string) because these endpoints do
 * not hand back cursor links.
 *
 * R-18 is out of reach here: adult ranking modes answer 403 and adult works are filtered
 * server-side, which is why signing in stays worthwhile.
 */
class GuestSource(private val api: WebApi) {

    private suspend fun <T> call(block: suspend () -> T): T = withContext(Dispatchers.IO) {
        try {
            block()
        } catch (e: HttpException) {
            throw mapHttp(e)
        } catch (e: IOException) {
            throw RelayException.Network(e)
        }
    }

    private fun mapHttp(e: HttpException): RelayException {
        val body = runCatching { e.response()?.errorBody()?.string() }.getOrNull().orEmpty()
        return when {
            e.code() == 403 -> RelayException.Http(403, "pixiv 拒绝了这次请求（该内容可能需要登录）")
            e.code() == 404 -> RelayException.Http(404, "接口不存在或作品已删除")
            else -> RelayException.Http(e.code(), "HTTP ${e.code()} ${body.take(120)}")
        }
    }

    fun discoveryLoader(): IllustLoader = IllustLoader { _ ->
        call {
            val body = api.discovery(WebUrlsBuilder.discovery()).body
            IllustListResponse(illusts = body?.illusts.orEmpty().map { it.toIllust() })
        }
    }

    fun rankLoader(webMode: String): IllustLoader = IllustLoader { next ->
        call {
            val page = next?.toIntOrNull() ?: 1
            val data = api.ranking(WebUrlsBuilder.ranking(webMode, page))
            IllustListResponse(
                illusts = data.contents.map { it.toIllust() },
                nextUrl = data.next.takeIf { it.isNotBlank() && it != "0" },
            )
        }
    }

    fun searchLoader(word: String, mode: String = "all", order: String = "date_d"): IllustLoader =
        IllustLoader { next ->
            call {
                if (word.isBlank()) return@call IllustListResponse()
                val page = next?.toIntOrNull() ?: 1
                val env = api.search(WebUrlsBuilder.search(word, page, mode, order))
                val group = env.body?.illustManga
                IllustListResponse(
                    illusts = group?.data.orEmpty().map { it.toIllust() },
                    nextUrl = if (group == null || group.lastPage) null else (page + 1).toString(),
                )
            }
        }

    fun userWorksLoader(userId: Long): IllustLoader = IllustLoader { _ ->
        call {
            val body = api.userWorks(WebUrlsBuilder.userWorks(userId)).body
            val works = body?.illusts.orEmpty().values + body?.manga.orEmpty().values
            IllustListResponse(illusts = works.map { it.toIllust() })
        }
    }

    suspend fun illustDetail(illustId: Long): Illust = call {
        val body = api.illustDetail(WebUrlsBuilder.illustDetail(illustId)).body
            ?: throw RelayException.Http(404, "作品不存在")
        val pages = runCatching {
            api.illustPages(WebUrlsBuilder.illustPages(illustId)).body.orEmpty()
        }.getOrDefault(emptyList())

        val base = body.toIllust()
        if (pages.isEmpty()) {
            base.copy(metaSinglePage = MetaSinglePage(base.imageUrls.original))
        } else {
            base.copy(metaPages = pages.map { MetaPage(ImageUrls(medium = it.urls.regular, large = it.urls.regular, original = it.urls.original)) })
        }
    }

    suspend fun related(illustId: Long): List<Illust> = call {
        api.recommendInit(WebUrlsBuilder.recommendInit(illustId)).body?.illusts.orEmpty()
            .map { it.toIllust() }
    }

    suspend fun userDetail(userId: Long): UserDetailResponse = call {
        val detail = api.user(WebUrlsBuilder.user(userId)).body?.toUserDetail() ?: UserDetail()
        UserDetailResponse(user = detail, profile = detail.profile)
    }

    suspend fun trendingTags(): List<TrendingTag> = call {
        api.suggestions(WebUrlsBuilder.suggestions()).body?.toTrendingTags().orEmpty()
            .map { (tag, translated) -> TrendingTag(tag = tag, translatedName = translated) }
    }

    /** The web suggestion endpoint has a different shape; autocomplete stays a signed-in nicety. */
    suspend fun autocomplete(): List<AutocompleteTag> = emptyList()

    /** pixiv exposes no public user-search endpoint on the web surface. */
    suspend fun searchUser(): List<pixiviz.mobile.data.remote.UserPreview> = emptyList()
}
