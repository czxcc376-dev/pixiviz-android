package pixiviz.mobile.data.remote.web

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import pixiviz.mobile.data.remote.Illust
import pixiviz.mobile.data.remote.ImageUrls
import pixiviz.mobile.data.remote.Tag
import pixiviz.mobile.data.remote.UserBrief
import pixiviz.mobile.data.remote.UserDetail
import pixiviz.mobile.data.remote.UserProfile

/**
 * Shapes of pixiv's public web endpoints (www.pixiv.net/ajax and ranking.php).
 *
 * Verified reachable from the device with no cookie and no login:
 *   /ajax/illust/{id}, /ajax/illust/{id}/pages, /ajax/illust/discovery,
 *   /ajax/search/artworks/{word}, /ranking.php?format=json,
 *   /ajax/user/{id}, /ajax/user/{id}/profile/top,
 *   /ajax/illust/{id}/recommend/init?limit=N, /ajax/search/suggestion?mode=all
 *
 * Adult ranking modes answer 403 here; R-18 needs a signed-in token.
 */

@Serializable
data class WebTagTranslation(val en: String? = null, val zh: String? = null, val ja: String? = null)

@Serializable
data class WebTag(val tag: String = "", val translation: WebTagTranslation? = null)

@Serializable
data class WebTags(val tags: List<WebTag> = emptyList())

@Serializable
data class WebUrls(
    val mini: String = "",
    val thumb: String = "",
    val small: String = "",
    val regular: String = "",
    val original: String = "",
)

@Serializable
data class WebEnvelope<T>(val error: Boolean = false, val message: String = "", val body: T? = null)

@Serializable
data class WebIllustBody(
    val illustId: String = "",
    val illustTitle: String = "",
    val illustComment: String = "",
    val createDate: String = "",
    val illustType: Int = 0,
    val urls: WebUrls = WebUrls(),
    val userId: String = "",
    val userName: String = "",
    val userAccount: String = "",
    val tags: WebTags = WebTags(),
    val pageCount: Int = 1,
    val width: Int = 0,
    val height: Int = 0,
    val xRestrict: Int = 0,
    val illustAIType: Int = 0,
    val bookmarkCount: Int = 0,
    val likeCount: Int = 0,
    val viewCount: Int = 0,
    val isBookmarked: Boolean = false,
)

@Serializable
data class WebPageEntry(val urls: WebUrls = WebUrls(), val width: Int = 0, val height: Int = 0)

@Serializable
data class WebContentType(
    val sexual: Int = 0,
    val lo: Boolean = false,
    val grotesque: Boolean = false,
)

@Serializable
data class WebRankEntry(
    @SerialName("illust_id") val illustId: Long = 0L,
    val title: String = "",
    val url: String = "",
    @SerialName("user_id") val userId: Long = 0L,
    @SerialName("user_name") val userName: String = "",
    @SerialName("profile_img") val profileImg: String = "",
    val width: Int = 0,
    val height: Int = 0,
    val tags: List<String> = emptyList(),
    val rank: Int = 0,
    @SerialName("illust_page_count") val illustPageCount: Int = 1,
    @SerialName("illust_content_type") val illustContentType: WebContentType = WebContentType(),
)

@Serializable
data class WebRanking(
    val contents: List<WebRankEntry> = emptyList(),
    val next: String = "",
    val date: String = "",
    val mode: String = "",
)

@Serializable
data class WebWork(
    val id: String = "",
    val title: String = "",
    val illustType: Int = 0,
    val xRestrict: Int = 0,
    val url: String = "",
    val tags: List<String> = emptyList(),
    val userId: String = "",
    val userName: String = "",
    val width: Int = 0,
    val height: Int = 0,
    val pageCount: Int = 1,
    val bookmarkCount: Int = 0,
    val isBookmarked: Boolean = false,
    val createDate: String = "",
    val description: String = "",
)

@Serializable
data class WebSearchGroup(val data: List<WebWork> = emptyList(), val lastPage: Boolean = true)

@Serializable
data class WebSearchBody(
    val illustManga: WebSearchGroup = WebSearchGroup(),
    val manga: WebSearchGroup = WebSearchGroup(),
)

@Serializable
data class WebDiscoveryBody(val illusts: List<WebWork> = emptyList(), val nextIds: List<String> = emptyList())

@Serializable
data class WebRecommendBody(
    val illusts: List<WebWork> = emptyList(),
    val nextIds: List<String> = emptyList(),
    val details: List<WebWork> = emptyList(),
)

@Serializable
data class WebUserBody(
    val userId: String = "",
    val name: String = "",
    val image: String = "",
    val comment: String = "",
    val webpage: String? = null,
    val gender: String = "",
    val region: String = "",
    val introduction: String = "",
    val totalIllusts: Int = 0,
    val totalManga: Int = 0,
    val twitterUrl: String? = null,
    val isFollowed: Boolean = false,
)

@Serializable
data class WebUserWorksBody(
    val illusts: Map<String, WebWork> = emptyMap(),
    val manga: Map<String, WebWork> = emptyMap(),
)

@Serializable
data class WebSuggestBody(
    val popularTags: List<WebTag> = emptyList(),
    val recommendTags: List<WebTag> = emptyList(),
)

// ---------------------------------------------------------------------------
// Mappers into the app's own model, so screens do not care which backend served them.
// ---------------------------------------------------------------------------

private fun String?.asLong(): Long = this?.toLongOrNull() ?: 0L

private fun WebTag.label(): String =
    translation?.zh?.takeIf { it.isNotBlank() }
        ?: translation?.en?.takeIf { it.isNotBlank() }
        ?: tag

internal fun WebWork.toIllust(): Illust = Illust(
    id = id.asLong(),
    title = title,
    type = if (illustType == 1) "manga" else "illust",
    imageUrls = ImageUrls(squareMedium = url, medium = url, large = url, original = url),
    user = UserBrief(id = userId.asLong(), name = userName),
    tags = tags.map { Tag(name = it) },
    createDate = createDate,
    pageCount = pageCount,
    width = width,
    height = height,
    xRestrict = xRestrict,
    totalBookmarks = bookmarkCount.toLong(),
    isBookmarked = isBookmarked,
)

internal fun WebRankEntry.toIllust(): Illust = Illust(
    id = illustId,
    title = title,
    imageUrls = ImageUrls(squareMedium = url, medium = url, large = url, original = url),
    user = UserBrief(id = userId, name = userName, profileImageUrls = ImageUrls(medium = profileImg)),
    tags = tags.map { Tag(name = it) },
    pageCount = illustPageCount,
    width = width,
    height = height,
    xRestrict = if (illustContentType.sexual > 0) 1 else 0,
)

internal fun WebIllustBody.toIllust(): Illust = Illust(
    id = illustId.asLong(),
    title = illustTitle,
    type = if (illustType == 1) "manga" else "illust",
    imageUrls = ImageUrls(
        squareMedium = urls.small.takeIf { it.isNotBlank() } ?: urls.thumb,
        medium = urls.regular.takeIf { it.isNotBlank() } ?: urls.small,
        large = urls.regular,
        original = urls.original,
    ),
    caption = illustComment,
    user = UserBrief(id = userId.asLong(), name = userName, account = userAccount),
    tags = tags.tags.map { Tag(name = it.tag, translatedName = it.label().takeIf { l -> l != it.tag }) },
    createDate = createDate,
    pageCount = pageCount,
    width = width,
    height = height,
    xRestrict = xRestrict,
    illustAiType = illustAIType,
    totalBookmarks = bookmarkCount.toLong(),
    isBookmarked = isBookmarked,
    metaPages = emptyList(),
)

internal fun WebPages(pages: List<WebPageEntry>): List<ImageUrls> =
    pages.map { ImageUrls(medium = it.urls.regular, large = it.urls.regular, original = it.urls.original) }

internal fun WebUserBody.toUserDetail(): UserDetail = UserDetail(
    id = userId.asLong(),
    name = name,
    account = "",
    comment = comment,
    profileImageUrls = ImageUrls(medium = image),
    profile = UserProfile(
        webpage = webpage,
        gender = gender,
        region = region,
        introduction = introduction,
        totalIllusts = totalIllusts,
        totalManga = totalManga,
        twitterUrl = twitterUrl,
    ),
    isFollowed = isFollowed,
)

internal fun WebSuggestBody.toTrendingTags(): List<Pair<String, String?>> =
    (recommendTags + popularTags)
        .distinctBy { it.tag }
        .map { it.tag to it.label().takeIf { label -> label != it.tag } }
