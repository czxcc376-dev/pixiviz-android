package pixiviz.mobile.ui

import androidx.compose.runtime.staticCompositionLocalOf
import pixiviz.mobile.AppContainer
import pixiviz.mobile.data.settings.Settings

/** Ambient dependency container. Provided once in MainActivity. */
val LocalApp = staticCompositionLocalOf<AppContainer> { error("AppContainer not provided") }

/** Latest persisted preferences. Provided once in MainActivity. */
val LocalSettings = staticCompositionLocalOf { Settings() }
