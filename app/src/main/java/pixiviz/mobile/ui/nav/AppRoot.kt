package pixiviz.mobile.ui.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import pixiviz.mobile.ui.artist.ArtistScreen
import pixiviz.mobile.ui.detail.DetailScreen
import pixiviz.mobile.ui.glass.liquidGlassSource
import pixiviz.mobile.ui.glass.rememberHazeState
import pixiviz.mobile.ui.history.HistoryScreen
import pixiviz.mobile.ui.home.HomeScreen
import pixiviz.mobile.ui.rank.RankScreen
import pixiviz.mobile.ui.search.SearchScreen
import pixiviz.mobile.ui.settings.SettingsScreen

object Routes {
    const val HOME = "home"
    const val RANK = "rank"
    const val SEARCH = "search?word={word}"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
    const val DETAIL = "detail/{illustId}"
    const val ARTIST = "artist/{userId}"

    fun search(word: String): String =
        "search?word=" + java.net.URLEncoder.encode(word, "UTF-8").replace("+", "%20")

    fun detail(illustId: Long) = "detail/$illustId"

    fun artist(userId: Long) = "artist/$userId"
}

/** Extra bottom padding screens reserve so content clears the floating capsule bar. */
val BottomBarReserve = 104.dp

@Composable
fun AppRoot() {
    val navController = rememberNavController()
    val hazeState = rememberHazeState()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier
                .fillMaxSize()
                .liquidGlassSource(hazeState),
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    onOpenIllust = { navController.navigate(Routes.detail(it)) },
                    onOpenArtist = { navController.navigate(Routes.artist(it)) },
                    onOpenRank = { navController.navigate(Routes.RANK) },
                    onSearchTag = { navController.navigate(Routes.search(it)) },
                )
            }
            composable(Routes.RANK) {
                RankScreen(onOpenIllust = { navController.navigate(Routes.detail(it)) })
            }
            composable(
                route = Routes.SEARCH,
                arguments = listOf(
                    navArgument("word") {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                ),
            ) { entry ->
                SearchScreen(
                    onOpenIllust = { navController.navigate(Routes.detail(it)) },
                    onOpenArtist = { navController.navigate(Routes.artist(it)) },
                    initialWord = entry.arguments?.getString("word").orEmpty(),
                )
            }
            composable(Routes.HISTORY) {
                HistoryScreen(onOpenIllust = { navController.navigate(Routes.detail(it)) })
            }
            composable(Routes.SETTINGS) {
                SettingsScreen()
            }
            composable(
                route = Routes.DETAIL,
                arguments = listOf(navArgument("illustId") { type = NavType.LongType }),
            ) { entry ->
                DetailScreen(
                    illustId = entry.arguments?.getLong("illustId") ?: 0L,
                    onBack = { navController.popBackStack() },
                    onOpenArtist = { navController.navigate(Routes.artist(it)) },
                    onOpenIllust = { navController.navigate(Routes.detail(it)) },
                )
            }
            composable(
                route = Routes.ARTIST,
                arguments = listOf(navArgument("userId") { type = NavType.LongType }),
            ) { entry ->
                ArtistScreen(
                    userId = entry.arguments?.getLong("userId") ?: 0L,
                    onBack = { navController.popBackStack() },
                    onOpenIllust = { navController.navigate(Routes.detail(it)) },
                )
            }
        }

        if (currentRoute in BottomDestinations.map { it.route }) {
            CapsuleBottomBar(
                currentRoute = currentRoute,
                hazeState = hazeState,
                onSelect = { route ->
                    val target = if (route == Routes.SEARCH) Routes.search("") else route
                    navController.navigate(target) {
                        popUpTo(Routes.HOME) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}
