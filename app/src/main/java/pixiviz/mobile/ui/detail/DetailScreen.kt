package pixiviz.mobile.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import pixiviz.mobile.data.local.HistoryEntry
import pixiviz.mobile.data.remote.Illust
import pixiviz.mobile.ui.LocalApp
import pixiviz.mobile.ui.LocalSettings
import pixiviz.mobile.ui.common.CapsuleButton
import pixiviz.mobile.ui.common.ErrorState
import pixiviz.mobile.ui.common.PixivImage
import pixiviz.mobile.ui.common.ShimmerBox
import pixiviz.mobile.ui.nav.BottomBarReserve

@Composable
fun DetailScreen(
    illustId: Long,
    onBack: () -> Unit,
    onOpenArtist: (Long) -> Unit,
    onOpenIllust: (Long) -> Unit,
) {
    val app = LocalApp.current
    val settings = LocalSettings.current

    val scope = rememberCoroutineScope()
    var illust by remember { mutableStateOf<Illust?>(null) }
    var bookmarked by remember { mutableStateOf(false) }
    var actionStatus by remember { mutableStateOf<String?>(null) }
    var related by remember { mutableStateOf<List<Illust>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(illustId) {
        error = null
        runCatching { app.repository.illustDetail(illustId) }
            .onSuccess { loaded ->
                illust = loaded
                bookmarked = loaded.isBookmarked
                app.history.upsert(
                    HistoryEntry(
                        illustId = loaded.id,
                        title = loaded.title,
                        thumbnailUrl = loaded.imageUrls.medium,
                        userName = loaded.user.name,
                        userId = loaded.user.id,
                        pageCount = loaded.pageCount,
                        xRestrict = loaded.xRestrict,
                        viewedAt = System.currentTimeMillis(),
                    ),
                )
                related = runCatching {
                    app.repository.relatedLoader(illustId).load(null).illusts
                }.getOrDefault(emptyList())
            }
            .onFailure { error = it.message }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = BottomBarReserve),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
            }
            Text(
                text = illust?.title ?: "作品详情",
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            illust?.let { current ->
                IconButton(onClick = {
                    scope.launch {
                        app.repository.setBookmark(current.id, !bookmarked)
                            .onSuccess { bookmarked = !bookmarked; actionStatus = if (bookmarked) "已收藏" else "已取消收藏" }
                            .onFailure { actionStatus = it.message }
                    }
                }) {
                    Icon(
                        imageVector = if (bookmarked) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                        contentDescription = "收藏",
                        tint = if (bookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = {
                    actionStatus = "已加入下载队列，完成后可在相册 Pictures/Pixiviz 找到"
                    scope.launch {
                        app.downloader.downloadIllust(current) { url -> app.repository.imageUrl(url) }
                    }
                }) {
                    Icon(Icons.Rounded.Download, contentDescription = "下载原图")
                }
            }
        }

        actionStatus?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }

        when {
            error != null -> ErrorState(message = error!!)
            illust == null -> Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.75f)
                    .padding(12.dp),
            ) { ShimmerBox(modifier = Modifier.fillMaxSize()) }

            else -> {
                val current = illust!!
                PageViewer(illust = current)
                MetaBlock(
                    illust = current,
                    onOpenArtist = onOpenArtist,
                    related = related,
                    onOpenIllust = onOpenIllust,
                )
            }
        }

        if (settings.nsfwMode != pixiviz.mobile.data.settings.NsfwMode.SHOW && illust?.isAdult == true) {
            Text(
                text = "这是 R18 作品，当前以模糊方式显示。可在「我的 → 18+ 内容」里调整可见范围。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun PageViewer(illust: Illust) {
    val pages = remember(illust.id) { illust.pages }
    val pagerState = rememberPagerState(pageCount = { pages.size })
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(
                if (illust.width > 0 && illust.height > 0) {
                    (illust.width.toFloat() / illust.height.toFloat()).coerceIn(0.4f, 1.8f)
                } else {
                    0.72f
                },
            ),
    ) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            val url = pages.getOrNull(page)?.large?.ifBlank { pages[page].medium }
                ?: pages.getOrNull(page)?.medium.orEmpty()
            PixivImage(
                rawUrl = url,
                contentDescription = illust.title,
                adult = illust.isAdult,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(illust.id) {
                        detectTapGestures(
                            onDoubleTap = {
                                if (scale > 1f) {
                                    scale = 1f
                                    offset = Offset.Zero
                                } else {
                                    scale = 2.2f
                                }
                            },
                        )
                    }
                    .transformable(
                        state = rememberTransformableState { zoomChange, panChange, _ ->
                            scale = (scale * zoomChange).coerceIn(1f, 5f)
                            if (scale > 1f) offset += panChange
                        },
                    )
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    },
            )
        }

        if (pages.size > 1) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(12.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text(
                    text = "${pagerState.currentPage + 1} / ${pages.size}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
    }
}

@Composable
private fun MetaBlock(
    illust: Illust,
    onOpenArtist: (Long) -> Unit,
    related: List<Illust>,
    onOpenIllust: (Long) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme

    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onOpenArtist(illust.user.id) },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape),
            ) {
                PixivImage(
                    rawUrl = illust.user.profileImageUrls.medium,
                    contentDescription = illust.user.name,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Column(Modifier.weight(1f)) {
                Text(illust.user.name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Text(
                    text = "@${illust.user.account}",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                )
            }
            Text(
                text = "查看主页 ›",
                style = MaterialTheme.typography.labelLarge,
                color = scheme.primary,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Stat("浏览", illust.totalView.toString())
            Stat("收藏", illust.totalBookmarks.toString())
            Stat("页数", illust.pageCount.toString())
        }

        if (illust.tags.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(items = illust.tags, key = { it.name }) { tag ->
                    CapsuleButton(
                        text = tag.translatedName?.takeIf { it.isNotBlank() } ?: tag.name,
                        selected = false,
                        onClick = {},
                    )
                }
            }
        }

        if (illust.caption.isNotBlank()) {
            Text(
                text = illust.caption.replace(Regex("<[^>]+>"), "").trim(),
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
                maxLines = 6,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Text(text = illust.createDate.take(19).replace("T", " "), style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)

        if (related.isNotEmpty()) {
            Text("相关作品", style = MaterialTheme.typography.titleLarge)
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 4.dp),
            ) {
                items(items = related, key = { it.id }) { item ->
                    Column(
                        modifier = Modifier
                            .size(width = 128.dp, height = 190.dp)
                            .clip(MaterialTheme.shapes.large)
                            .clickable { onOpenIllust(item.id) },
                    ) {
                        PixivImage(
                            rawUrl = item.imageUrls.medium,
                            contentDescription = item.title,
                            adult = item.isAdult,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column {
        Text(value, style = MaterialTheme.typography.titleMedium)
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
