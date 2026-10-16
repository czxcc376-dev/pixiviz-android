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
    // pixiv answers 403 for adult ranking modes without a token, so they only
    // appear once a session exists.
    val adultVisible = signedIn && settings.nsfwAgeConfirmed && settings.nsfwMode != NsfwMode.HIDE
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

    val paging = rememberIllustPaging(key = "rank-$mode-$offset") {
        app.repository.rankLoader(mode, date.toString())
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

                if (!adultVisible) {
                    Text(
                        text = if (!signedIn) {
                            "R18 榜单需要登录 pixiv：未登录请求会被 pixiv 直接拒绝。"
                        } else {
                            "R18 榜单已按设置隐藏；在「我的 → 18+ 内容」里开启后会出现。"
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
