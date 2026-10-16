package pixiviz.mobile.data.remote

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive

/**
 * pixiv's app API is inconsistent: the same field can come back as a JSON number or as a
 * quoted string depending on endpoint and version. These serializers accept both instead of
 * failing the whole response.
 */
object FlexLong : KSerializer<Long> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("pixiviz.FlexLong", PrimitiveKind.LONG)

    override fun deserialize(decoder: Decoder): Long {
        val json = decoder as? JsonDecoder ?: return decoder.decodeLong()
        val element = json.decodeJsonElement()
        return (element as? JsonPrimitive)?.content?.toLongOrNull() ?: 0L
    }

    override fun serialize(encoder: Encoder, value: Long) = encoder.encodeLong(value)
}

object FlexInt : KSerializer<Int> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("pixiviz.FlexInt", PrimitiveKind.INT)

    override fun deserialize(decoder: Decoder): Int {
        val json = decoder as? JsonDecoder ?: return decoder.decodeInt()
        val element = json.decodeJsonElement()
        return (element as? JsonPrimitive)?.content?.toIntOrNull() ?: 0
    }

    override fun serialize(encoder: Encoder, value: Int) = encoder.encodeInt(value)
}

object FlexBool : KSerializer<Boolean> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("pixiviz.FlexBool", PrimitiveKind.BOOLEAN)

    override fun deserialize(decoder: Decoder): Boolean {
        val json = decoder as? JsonDecoder ?: return decoder.decodeBoolean()
        val element = json.decodeJsonElement()
        val raw = (element as? JsonPrimitive)?.content ?: return false
        return raw == "true" || raw == "1"
    }

    override fun serialize(encoder: Encoder, value: Boolean) = encoder.encodeBoolean(value)
}

@Serializable
data class ImageUrls(
    @SerialName("square_medium") val squareMedium: String = "",
    val medium: String = "",
    val large: String = "",
    val original: String = "",
)

@Serializable
data class Tag(
    val name: String = "",
    @SerialName("translated_name") val translatedName: String? = null,
)

@Serializable
data class UserBrief(
    @Serializable(with = FlexLong::class) val id: Long = 0L,
    val name: String = "",
    val account: String = "",
    @SerialName("profile_image_urls") val profileImageUrls: ImageUrls = ImageUrls(),
    @SerialName("is_followed") val isFollowed: Boolean = false,
)

@Serializable
data class MetaPage(
    @SerialName("image_urls") val imageUrls: ImageUrls = ImageUrls(),
)

@Serializable
data class MetaSinglePage(
    @SerialName("original_image_url") val originalImageUrl: String? = null,
)

@Serializable
data class Illust(
    @Serializable(with = FlexLong::class) val id: Long = 0L,
    val title: String = "",
    val type: String = "illust",
    @SerialName("image_urls") val imageUrls: ImageUrls = ImageUrls(),
    val caption: String = "",
    @Serializable(with = FlexInt::class) val restrict: Int = 0,
    val user: UserBrief = UserBrief(),
    val tags: List<Tag> = emptyList(),
    @SerialName("create_date") val createDate: String = "",
    @Serializable(with = FlexInt::class) @SerialName("page_count") val pageCount: Int = 1,
    @Serializable(with = FlexInt::class) val width: Int = 0,
    @Serializable(with = FlexInt::class) val height: Int = 0,
    @Serializable(with = FlexInt::class) @SerialName("x_restrict") val xRestrict: Int = 0,
    @Serializable(with = FlexInt::class) @SerialName("illust_ai_type") val illustAiType: Int = 0,
    @Serializable(with = FlexLong::class) @SerialName("total_view") val totalView: Long = 0L,
    @Serializable(with = FlexLong::class) @SerialName("total_bookmarks") val totalBookmarks: Long = 0L,
    @Serializable(with = FlexBool::class) @SerialName("is_bookmarked") val isBookmarked: Boolean = false,
    @Serializable(with = FlexBool::class) val visible: Boolean = true,
    @Serializable(with = FlexInt::class) @SerialName("sanity_level") val sanityLevel: Int = 0,
    @SerialName("meta_single_page") val metaSinglePage: MetaSinglePage? = null,
    @SerialName("meta_pages") val metaPages: List<MetaPage> = emptyList(),
) {
    val isAdult: Boolean get() = xRestrict > 0
    val isAiGenerated: Boolean get() = illustAiType == 2
    val isManga: Boolean get() = type == "manga"

    /** All page image sets, tolerating the single-page and multi-page shapes. */
    val pages: List<ImageUrls>
        get() = when {
            metaPages.isNotEmpty() -> metaPages.map { it.imageUrls }
            metaSinglePage?.originalImageUrl != null ->
                listOf(imageUrls.copy(original = metaSinglePage.originalImageUrl))
            else -> listOf(imageUrls)
        }
}

@Serializable
data class IllustListResponse(
    val illusts: List<Illust> = emptyList(),
    @SerialName("next_url") val nextUrl: String? = null,
)

@Serializable
data class IllustDetailResponse(val illust: Illust = Illust())

@Serializable
data class TrendingTag(
    val tag: String = "",
    @SerialName("translated_name") val translatedName: String? = null,
    val illust: Illust? = null,
)

@Serializable
data class TrendingTagsResponse(
    @SerialName("trend_tags") val trendTags: List<TrendingTag> = emptyList(),
)

@Serializable
data class AutocompleteTag(
    val name: String = "",
    @SerialName("translated_name") val translatedName: String? = null,
)

@Serializable
data class AutocompleteResponse(val tags: List<AutocompleteTag> = emptyList())

@Serializable
data class UserPreview(
    val user: UserBrief = UserBrief(),
    val illusts: List<Illust> = emptyList(),
)

@Serializable
data class SearchUserResponse(
    @SerialName("user_previews") val userPreviews: List<UserPreview> = emptyList(),
    @SerialName("next_url") val nextUrl: String? = null,
)

@Serializable
data class ProfileImageUrls(val medium: String = "")

@Serializable
data class UserProfile(
    val webpage: String? = null,
    val gender: String = "",
    val birth: String = "",
    val region: String = "",
    val introduction: String = "",
    @SerialName("total_illusts") val totalIllusts: Int = 0,
    @SerialName("total_manga") val totalManga: Int = 0,
    @SerialName("total_novels") val totalNovels: Int = 0,
    val twitterUrl: String? = null,
)

@Serializable
data class UserDetail(
    @Serializable(with = FlexLong::class) val id: Long = 0L,
    val name: String = "",
    val account: String = "",
    val comment: String = "",
    @SerialName("profile_image_urls") val profileImageUrls: ImageUrls = ImageUrls(),
    val profile: UserProfile = UserProfile(),
    @SerialName("is_followed") val isFollowed: Boolean = false,
)

@Serializable
data class UserDetailResponse(
    val user: UserDetail = UserDetail(),
    val profile: UserProfile = UserProfile(),
)
