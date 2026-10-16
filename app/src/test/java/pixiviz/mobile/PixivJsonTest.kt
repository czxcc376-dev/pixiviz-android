package pixiviz.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pixiviz.mobile.data.remote.IllustListResponse
import pixiviz.mobile.data.remote.PixivJson
import pixiviz.mobile.ui.common.downscaleToSquare

/**
 * pixiv returns numeric fields as JSON strings on some endpoints and as numbers on others,
 * so these fixtures deliberately mix both shapes.
 */
class PixivJsonTest {

    private val rankingFixture = """
    {
      "illusts": [
        {
          "id": "94067980",
          "title": "夜の海",
          "type": "illust",
          "image_urls": {
            "square_medium": "https://i.pximg.net/c/240x240/img-master/img/2021/09/01/00/00/00/94067980_p0_square1200.jpg",
            "medium": "https://i.pximg.net/c/540x540_70/img-master/img/2021/09/01/00/00/00/94067980_p0_master1200.jpg",
            "large": "https://i.pximg.net/c/720x720/img-master/img/2021/09/01/00/00/00/94067980_p0_master1200.jpg",
            "original": "https://i.pximg.net/img-original/img/2021/09/01/00/00/00/94067980_p0.jpg"
          },
          "caption": "コメント",
          "restrict": 0,
          "user": { "id": "12345", "name": "作者", "account": "author", "profile_image_urls": { "medium": "https://i.pximg.net/user-profile/img/x.jpg" }, "is_followed": false },
          "tags": [ { "name": "オリジナル", "translated_name": "原创" } ],
          "create_date": "2021-09-01T00:00:00+09:00",
          "page_count": 3,
          "width": 1200,
          "height": 1600,
          "x_restrict": 1,
          "illust_ai_type": 2,
          "total_view": "5000",
          "total_bookmarks": "321",
          "is_bookmarked": false,
          "visible": true,
          "sanity_level": 4,
          "meta_single_page": {},
          "meta_pages": [
            { "image_urls": { "original": "https://i.pximg.net/img-original/img/2021/09/01/00/00/00/94067980_p0.jpg" } },
            { "image_urls": { "original": "https://i.pximg.net/img-original/img/2021/09/01/00/00/00/94067980_p1.jpg" } },
            { "image_urls": { "original": "https://i.pximg.net/img-original/img/2021/09/01/00/00/00/94067980_p2.jpg" } }
          ]
        }
      ],
      "next_url": "https://app-api.pixiv.net/v1/illust/ranking?mode=day&filter=for_android&offset=30"
    }
    """.trimIndent()

    @Test
    fun parses_mixed_string_and_numeric_fields() {
        val parsed = PixivJson.decodeFromString(IllustListResponse.serializer(), rankingFixture)
        val illust = parsed.illusts.single()

        assertEquals(94067980L, illust.id)
        assertEquals(12345L, illust.user.id)
        assertEquals(3, illust.pageCount)
        assertEquals(1200, illust.width)
        assertEquals(5000L, illust.totalView)
        assertEquals(321L, illust.totalBookmarks)
        assertEquals("原创", illust.tags.single().translatedName)
        assertEquals("https://app-api.pixiv.net/v1/illust/ranking?mode=day&filter=for_android&offset=30", parsed.nextUrl)
    }

    @Test
    fun adult_and_ai_flags_follow_pixiv_semantics() {
        val illust = PixivJson.decodeFromString(IllustListResponse.serializer(), rankingFixture).illusts.single()
        assertTrue(illust.isAdult)
        assertTrue(illust.isAiGenerated)
        assertFalse(illust.isManga)
    }

    @Test
    fun pages_are_taken_from_meta_pages_when_present() {
        val illust = PixivJson.decodeFromString(IllustListResponse.serializer(), rankingFixture).illusts.single()
        assertEquals(3, illust.pages.size)
        assertTrue(illust.pages.first().original.endsWith("94067980_p0.jpg"))
    }

    @Test
    fun single_page_illusts_still_expose_one_page() {
        val single = """
        { "illusts": [ { "id": 1, "meta_single_page": { "original_image_url": "https://i.pximg.net/img-original/img/a.jpg" },
          "image_urls": { "medium": "https://i.pximg.net/c/540x540_70/img-master/img/a.jpg" } } ] }
        """.trimIndent()
        val illust = PixivJson.decodeFromString(IllustListResponse.serializer(), single).illusts.single()
        assertEquals(1, illust.pages.size)
        assertEquals("https://i.pximg.net/img-original/img/a.jpg", illust.pages.first().original)
    }

    @Test
    fun downscale_rewrites_master_and_original_urls_to_the_square_variant() {
        val master = "https://i.pximg.net/c/540x540_70/img-master/img/2021/09/01/00/00/00/94067980_p0_master1200.jpg"
        assertEquals(
            "https://i.pximg.net/c/240x240/img-master/img/2021/09/01/00/00/00/94067980_p0_square1200.jpg",
            downscaleToSquare(master),
        )

        val original = "https://i.pximg.net/img-original/img/2021/09/01/00/00/00/94067980_p2.jpg"
        assertEquals(
            "https://i.pximg.net/c/240x240/img-master/img/2021/09/01/00/00/00/94067980_p2_square1200.jpg",
            downscaleToSquare(original),
        )
    }

    @Test
    fun downscale_leaves_unrecognised_urls_untouched() {
        val raw = "https://example.invalid/whatever.png"
        assertEquals(raw, downscaleToSquare(raw))
    }
}
