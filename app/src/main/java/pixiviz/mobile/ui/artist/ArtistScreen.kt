package pixiviz.mobile.ui.artist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import pixiviz.mobile.ui.common.CapsuleButton
import pixiviz.mobile.data.remote.UserDetail
import pixiviz.mobile.data.settings.GridLayout
import pixiviz.mobile.ui.LocalApp
import pixiviz.mobile.ui.LocalSettings
import pixiviz.mobile.ui.common.PagedIllustGrid
import pixiviz.mobile.ui.common.PixivImage
import pixiviz.mobile.ui.common.rememberIllustPaging
import pixiviz.mobile.ui.nav.BottomBarReserve

@Composable
fun ArtistScreen(
    userId: Long,
    onBack: () -> Unit,
    onOpenIllust: (Long) -> Unit,
) {
    val app = LocalApp.current
    val settings = LocalSettings.current
    val columns = if (settings.gridLayout == GridLayout.DOUBLE) 2 else 1

    val scope = rememberCoroutineScope()
    var detail by remember { mutableStateOf<UserDetail?>(null) }
    var followed by remember { mutableStateOf(false) }
    var followStatus by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(userId) {
        detail = runCatching { app.repository.userDetail(userId).user }.getOrNull()
        followed = detail?.isFollowed == true
    }

    val paging = rememberIllustPaging(key = "artist-$userId") {
        app.repository.userIllustsLoader(userId)
    }

    PagedIllustGrid(
        items = paging,
        columns = columns,
        onOpenIllust = onOpenIllust,
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = BottomBarReserve),
        header = {
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(66.dp)
                            .clip(CircleShape),
                    ) {
                        PixivImage(
                            rawUrl = detail?.profileImageUrls?.medium.orEmpty(),
                            contentDescription = detail?.name,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = detail?.name ?: "加载中…",
                            style = MaterialTheme.typography.headlineMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "@${detail?.account.orEmpty()}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        followStatus?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                    CapsuleButton(
                        text = if (followed) "已关注" else "关注",
                        selected = followed,
                        onClick = {
                            scope.launch {
                                app.repository.setFollow(userId, !followed)
                                    .onSuccess { followed = !followed; followStatus = null }
                                    .onFailure { followStatus = it.message }
                            }
                        },
                    )
                }
                detail?.profile?.let { profile ->
                    if (profile.introduction.isNotBlank()) {
                        Text(
                            text = profile.introduction,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        },
    )
}
