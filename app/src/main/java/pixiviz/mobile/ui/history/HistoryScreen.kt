package pixiviz.mobile.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import pixiviz.mobile.data.local.HistoryEntry
import pixiviz.mobile.data.settings.NsfwMode
import pixiviz.mobile.ui.LocalApp
import pixiviz.mobile.ui.LocalSettings
import pixiviz.mobile.ui.common.CapsuleButton
import pixiviz.mobile.ui.common.EmptyState
import pixiviz.mobile.ui.common.PixivImage
import pixiviz.mobile.ui.nav.BottomBarReserve
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(onOpenIllust: (Long) -> Unit) {
    val app = LocalApp.current
    val settings = LocalSettings.current
    val scope = rememberCoroutineScope()
    val entries by app.history.recent(300).collectAsStateWithLifecycle(initialValue = emptyList())
    val formatter = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text("浏览历史", style = MaterialTheme.typography.displaySmall)
                Text(
                    text = "${entries.size} 条记录 · 仅保存在本机",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (entries.isNotEmpty()) {
                CapsuleButton(
                    text = "清空",
                    selected = false,
                    onClick = { scope.launch { app.history.clear() } },
                )
            }
        }

        if (entries.isEmpty()) {
            EmptyState(
                title = "还没有浏览记录",
                subtitle = "看过的作品会自动记在这里，随时可以回到原图",
            )
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 4.dp,
                bottom = BottomBarReserve,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(items = entries, key = { it.illustId }) { entry: HistoryEntry ->
                HistoryRow(
                    entry = entry,
                    blurred = settings.nsfwMode != NsfwMode.SHOW && entry.xRestrict > 0,
                    hidden = settings.nsfwMode == NsfwMode.HIDE && entry.xRestrict > 0,
                    dateLabel = formatter.format(Date(entry.viewedAt)),
                    onClick = { onOpenIllust(entry.illustId) },
                )
            }
        }
    }
}

@Composable
private fun HistoryRow(
    entry: HistoryEntry,
    blurred: Boolean,
    hidden: Boolean,
    dateLabel: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(width = 64.dp, height = 64.dp)
                .clip(MaterialTheme.shapes.small),
        ) {
            PixivImage(
                rawUrl = entry.thumbnailUrl,
                contentDescription = entry.title,
                adult = hidden || blurred,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = entry.title.ifBlank { "无标题" },
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${entry.userName} · $dateLabel",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        if (entry.pageCount > 1) {
            Text(
                text = "${entry.pageCount}P",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
