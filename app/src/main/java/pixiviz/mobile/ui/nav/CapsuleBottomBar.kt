package pixiviz.mobile.ui.nav

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Leaderboard
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import pixiviz.mobile.ui.glass.LiquidGlassSurface

data class BottomDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

val BottomDestinations: List<BottomDestination> = listOf(
    BottomDestination(Routes.HOME, "首页", Icons.Rounded.Home),
    BottomDestination(Routes.RANK, "排行", Icons.Rounded.Leaderboard),
    BottomDestination(Routes.SEARCH, "搜索", Icons.Rounded.Search),
    BottomDestination(Routes.HISTORY, "历史", Icons.Rounded.History),
    BottomDestination(Routes.SETTINGS, "我的", Icons.Rounded.Settings),
)

/**
 * Floating capsule tab bar. The selected item grows a spring-animated capsule indicator and
 * reveals its label; the bar itself is the liquid-glass material sitting over the content.
 */
@Composable
fun CapsuleBottomBar(
    currentRoute: String?,
    onSelect: (String) -> Unit,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val scheme = MaterialTheme.colorScheme
    val barInsets = WindowInsets.navigationBars.asPaddingValues()

    LiquidGlassSurface(
        hazeState = hazeState,
        shape = CircleShape,
        modifier = modifier
            .padding(horizontal = 18.dp)
            .padding(bottom = barInsets.calculateBottomPadding() + 12.dp),
        content = {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                BottomDestinations.forEach { destination ->
                    val selected = currentRoute == destination.route
                    val indicator by animateColorAsState(
                        targetValue = if (selected) scheme.primary.copy(alpha = 0.22f) else Color.Transparent,
                        label = "tabIndicator",
                    )
                    Row(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(indicator)
                            .clickable {
                                if (!selected) {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onSelect(destination.route)
                                }
                            }
                            .padding(horizontal = 14.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = destination.icon,
                            contentDescription = destination.label,
                            tint = if (selected) scheme.primary else scheme.onSurfaceVariant,
                            modifier = Modifier.size(21.dp),
                        )
                        AnimatedVisibility(
                            visible = selected,
                            enter = expandHorizontally() + fadeIn(),
                            exit = shrinkHorizontally() + fadeOut(),
                        ) {
                            Text(
                                text = destination.label,
                                style = MaterialTheme.typography.labelLarge,
                                color = scheme.primary,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        },
    )
}
