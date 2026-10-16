package pixiviz.mobile.ui.rank

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pixiviz.mobile.data.remote.RankModes
import pixiviz.mobile.data.settings.GridLayout
import pixiviz.mobile.data.settings.NsfwMode
import pixiviz.mobile.ui.LocalApp
import pixiviz.mobile.ui.LocalSettings
import pixiviz.mobile.ui.common.CapsuleButton
import pixiviz.mobile.ui.common.PagedIllustGrid
import pixiviz.mobile.ui.common.rememberIllustPaging
import pixiviz.mobile.ui.nav.BottomBarReserve
import java.time.LocalDate

@Composable
fun RankScreen(onOpenIllust: (Long) -> Unit) {
    val app = LocalApp.current
    val settings = LocalSettings.current
    val columns = if (settings.gridLayout == GridLayout.DOUBLE) 2 else 1

    // Adult ranking modes stay hidden until the age gate has been confirmed and content
    // is not set to be hidden outright.
    val signedIn by app.auth.signedIn.collectAsStateWithLifecycle()
    // Adult modes depend on the active source: a relay serves them without an account,
    // pixiv's public web endpoints never do.
    val adultServable = app.repository.adultContentAvailable()
    val adultVisible = adultServable && settings.nsfwAgeConfirmed && settings.nsfwMode != NsfwMode.HIDE
    val modes = remember(adultVisible) {
        RankModes.all.filter { adultVisible || !it.adult }
    }

    var mode by remember { mutableStateOf(settings.lastRankMode) }
    var offset by remember { mutableIntStateOf(0) }

    LaunchedEffect(modes) {
        if (modes.none { it.key == mode }) mode = modes.first().key
    }

    val step = when {
        mode.startsWith("month") -> 30
        mode.startsWith("week") -> 7
        else -> 1
    }
    val date = remember(offset, step) { LocalDate.now().minusDays((offset * step).toLong()) }

    // Only pin a date once the user steps back: pixiv rejects an explicit "current" date,
    // which surfaced as a 500 from the relay.
    val paging = rememberIllustPaging(key = "rank-$mode-$offset") {
        app.repository.rankLoader(mode, if (offset == 0) null else date.toString())
    }

    PagedIllustGrid(
        items = paging,
        columns = columns,
        onOpenIllust = onOpenIllust,
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = BottomBarReserve),
        header = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "排行榜",
                    style = MaterialTheme.typography.displaySmall,
                    modifier = Modifier.padding(start = 4.dp, top = 10.dp),
                )

                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp),
                ) {
                    items(items = modes, key = { it.key }) { item ->
                        CapsuleButton(
                            text = item.label,
                            selected = item.key == mode,
                            onClick = { mode = item.key },
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CapsuleButton(
                        text = "‹ 上一期",
                        selected = false,
                        onClick = { offset += 1 },
                    )
                    CapsuleButton(
                        text = if (offset == 0) date.toString() else "$date（回到最新）",
                        selected = offset > 0,
                        onClick = { offset = 0 },
                    )
                }

                if (!signedIn && !adultServable) {
                    Text(
                        text = "未登录时榜单用「热门标签 + 按热度排序」近似；登录后才是 pixiv 官方榜单。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }

                if (!adultVisible) {
                    Text(
                        text = when {
                            !adultServable ->
                                "当前数据源拿不到 R18 榜：在「我的 → 数据源」里填一个中转地址，或登录 pixiv。"
                            !settings.nsfwAgeConfirmed ->
                                "R18 榜需要先在「我的 → 18+ 内容」里确认年龄。"
                            else -> "R18 榜单已按设置隐藏。"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            }
        },
    )
}
