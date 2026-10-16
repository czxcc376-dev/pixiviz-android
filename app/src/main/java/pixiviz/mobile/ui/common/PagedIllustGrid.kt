package pixiviz.mobile.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import kotlinx.coroutines.flow.Flow
import pixiviz.mobile.data.remote.Illust
import pixiviz.mobile.data.remote.IllustLoader
import pixiviz.mobile.data.remote.IllustPagingSource

@Composable
fun rememberIllustPaging(key: Any?, loader: () -> IllustLoader): LazyPagingItems<Illust> {
    val scope = rememberCoroutineScope()
    val flow: Flow<PagingData<Illust>> = remember(key) {
        Pager(
            config = PagingConfig(pageSize = 30, initialLoadSize = 30, enablePlaceholders = false),
            pagingSourceFactory = { IllustPagingSource(loader()) },
        ).flow.cachedIn(scope)
    }
    return flow.collectAsLazyPagingItems()
}

@Composable
fun ShimmerGrid(columns: Int = 2, rows: Int = 3) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        repeat(rows) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                repeat(columns) {
                    Column(Modifier.weight(1f)) {
                        ShimmerBox(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(0.74f),
                            shape = RoundedCornerShape(18.dp),
                        )
                        Box(Modifier.padding(top = 6.dp)) {
                            ShimmerBox(
                                modifier = Modifier
                                    .fillMaxWidth(0.7f)
                                    .aspectRatio(9f),
                                shape = RoundedCornerShape(6.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Paged grid with the screen chrome folded in as full-span slots, so headers, skeleton,
 * empty/error states and the append footer all live inside one scroll container.
 */
@Composable
fun PagedIllustGrid(
    items: LazyPagingItems<Illust>,
    columns: Int,
    onOpenIllust: (Long) -> Unit,
    modifier: Modifier = Modifier,
    state: LazyGridState = rememberLazyGridState(),
    contentPadding: PaddingValues = PaddingValues(12.dp),
    emptyContent: (@Composable () -> Unit)? = null,
    header: (@Composable () -> Unit)? = null,
) {
    val refresh = items.loadState.refresh
    val append = items.loadState.append

    LazyVerticalGrid(
        columns = GridCells.Fixed(columns.coerceIn(1, 3)),
        state = state,
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (header != null) {
            item(span = { GridItemSpan(maxLineSpan) }) { header() }
        }

        items(
            count = items.itemCount,
            key = items.itemKey { it.id },
        ) { index ->
            items[index]?.let { illust ->
                IllustCard(illust = illust, onOpen = onOpenIllust)
            }
        }

        if (refresh is LoadState.Error) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                ErrorState(
                    message = refresh.error.message ?: "加载失败",
                    onRetry = { items.retry() },
                )
            }
        } else if (refresh is LoadState.Loading && items.itemCount == 0) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(Modifier.padding(vertical = 8.dp)) { ShimmerGrid(columns) }
            }
        } else if (items.itemCount == 0 && refresh is LoadState.NotLoading) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(Modifier.fillMaxWidth().padding(top = 40.dp)) {
                    emptyContent?.invoke() ?: EmptyState("这里还没有内容")
                }
            }
        }

        if (append is LoadState.Loading) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    text = "加载中…",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(20.dp),
                )
            }
        }
    }
}
