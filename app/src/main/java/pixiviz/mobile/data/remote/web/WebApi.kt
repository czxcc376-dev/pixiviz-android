package pixiviz.mobile.data.remote.web

import pixiviz.mobile.BuildConfig
import retrofit2.http.GET
import retrofit2.http.Url

/**
 * pixiv's public web surface. Every call takes an absolute URL because pagination is
 * expressed as page numbers or ids rather than opaque cursor links.
 *
 * These endpoints were verified reachable from the device with no cookie and no login.
 */
interface WebApi {

    @GET
    suspend fun illustDetail(@Url url: String): WebEnvelope<WebIllustBody>

    @GET
    suspend fun illustPages(@Url url: String): WebEnvelope<List<WebPageEntry>>

    /** `ranking.php?format=json` answers with the payload directly, not wrapped. */
    @GET
    suspend fun ranking(@Url url: String): WebRanking

    @GET
    suspend fun search(@Url url: String): WebEnvelope<WebSearchBody>

    @GET
    suspend fun discovery(@Url url: String): WebEnvelope<WebDiscoveryBody>

    @GET
    suspend fun recommendInit(@Url url: String): WebEnvelope<WebRecommendBody>

    @GET
    suspend fun user(@Url url: String): WebEnvelope<WebUserBody>

    @GET
    suspend fun userWorks(@Url url: String): WebEnvelope<WebUserWorksBody>

    @GET
    suspend fun suggestions(@Url url: String): WebEnvelope<WebSuggestBody>
}

object WebUrlsBuilder {

    val base: String = BuildConfig.PIXIV_WEB_BASE.trimEnd('/')

    fun illustDetail(id: Long) = "$base/ajax/illust/$id"

    fun illustPages(id: Long) = "$base/ajax/illust/$id/pages"

    fun ugoiraMeta(id: Long) = "$base/ajax/illust/$id/ugoira_meta"

    fun recommendInit(id: Long, limit: Int = 18) = "$base/ajax/illust/$id/recommend/init?limit=$limit"

    fun discovery(mode: String = "all", limit: Int = 30) =
        "$base/ajax/illust/discovery?mode=$mode&limit=$limit"

    /** Web ranking modes are `daily`, `weekly`, `monthly`, `daily_male`, `daily_r18`, ... */
    fun ranking(mode: String, page: Int, date: String? = null): String {
        val dateParam = if (date.isNullOrBlank()) "" else "&date=$date"
        return "$base/ranking.php?format=json&mode=$mode&p=$page$dateParam"
    }

    fun search(
        word: String,
        page: Int = 1,
        mode: String = "all",
        order: String = "date_d",
        target: String = "s_tag",
        type: String = "artworks",
    ): String {
        val encoded = java.net.URLEncoder.encode(word, "UTF-8").replace("+", "%20")
        return "$base/ajax/search/$type/$encoded?word=$encoded&p=$page&mode=$mode&order=$order&s_mode=$target"
    }

    fun user(userId: Long) = "$base/ajax/user/$userId?full=1"

    fun userWorks(userId: Long) = "$base/ajax/user/$userId/profile/top"

    fun suggestions() = "$base/ajax/search/suggestion?mode=all"

    /** App builds pagination links itself, so the paging key can be a bare page number. */
    fun withPage(url: String, page: Int): String =
        if (url.contains("p=")) url.replace(Regex("([?&])p=\\d+"), "$1p=$page") else "$url&p=$page"
}
