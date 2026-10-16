package pixiviz.mobile.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import pixiviz.mobile.data.remote.TrendingTag
import pixiviz.mobile.data.settings.GridLayout
import pixiviz.mobile.ui.LocalApp
import pixiviz.mobile.ui.LocalSettings
import pixiviz.mobile.ui.common.CapsuleButton
import pixiviz.mobile.ui.common.PagedIllustGrid
import pixiviz.mobile.ui.common.rememberIllustPaging
import pixiviz.mobile.ui.nav.BottomBarReserve

@Composable
fun HomeScreen(
    onOpenIllust: (Long) -> Unit,
    onOpenArtist: (Long) -> Unit,
    onOpenRank: () -> Unit,
    onSearchTag: (String) -> Unit,
) {
    val app = LocalApp.current
    val settings = LocalSettings.current
    val columns = if (settings.gridLayout == GridLayout.DOUBLE) 2 else 1

    var tags by remember { mutableStateOf<List<TrendingTag>>(emptyList()) }
    LaunchedEffect(Unit) {
        tags = runCatching { app.repository.trendingTags() }.getOrDefault(emptyList())
    }

    val paging = rememberIllustPaging("home-recommended") { app.repository.recommendedLoader() }

    PagedIllustGrid(
        items = paging,
        columns = columns,
        onOpenIllust = onOpenIllust,
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = BottomBarReserve),
        header = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "发现",
                    style = MaterialTheme.typography.displaySmall,
                    modifier = Modifier.padding(start = 4.dp, top = 10.dp),
                )
                Text(
                    text = "来自 pixiv 的推荐",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp),
                )

                if (tags.isNotEmpty()) {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                    ) {
                        items(items = tags, key = { it.tag }) { tag ->
                            CapsuleButton(
                                text = tag.translatedName?.takeIf { it.isNotBlank() } ?: tag.tag,
                                selected = false,
                                onClick = { onSearchTag(tag.tag) },
                            )
                        }
                    }
                }

                CapsuleButton(
                    text = "查看排行榜",
                    onClick = onOpenRank,
                    modifier = Modifier.padding(start = 4.dp, top = 6.dp, bottom = 6.dp),
                )
            }
        },
    )
}
