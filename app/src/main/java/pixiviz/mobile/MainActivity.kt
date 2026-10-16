package pixiviz.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pixiviz.mobile.data.settings.Settings
import pixiviz.mobile.data.settings.ThemeMode
import pixiviz.mobile.ui.LocalApp
import pixiviz.mobile.ui.LocalSettings
import pixiviz.mobile.ui.nav.AppRoot
import pixiviz.mobile.ui.theme.PixivizTheme
import pixiviz.mobile.ui.theme.UiThemeMode

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val container = (application as PixivizApp).container
        var ready = false
        splash.setKeepOnScreenCondition { !ready }

        setContent {
            val settings by container.settingsStore.settings
                .collectAsStateWithLifecycle(initialValue = Settings())

            LaunchedEffect(settings) { container.applySettings(settings) }
            ready = true

            // Restore a sealed refresh token once, so an existing session keeps working.
            LaunchedEffect(Unit) { container.auth.restore() }

            PixivizTheme(
                mode = settings.themeMode.toUiMode(),
                pureBlack = settings.pureBlack,
            ) {
                CompositionLocalProvider(
                    LocalApp provides container,
                    LocalSettings provides settings,
                ) {
                    Surface(modifier = Modifier.fillMaxSize(), color = Color.Transparent) {
                        AppRoot()
                    }
                }
            }
        }
    }
}

private fun ThemeMode.toUiMode(): UiThemeMode = when (this) {
    ThemeMode.SYSTEM -> UiThemeMode.SYSTEM
    ThemeMode.LIGHT -> UiThemeMode.LIGHT
    ThemeMode.DARK -> UiThemeMode.DARK
}
