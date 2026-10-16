package pixiviz.mobile.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

enum class UiThemeMode { SYSTEM, LIGHT, DARK }

/** Explicit black used behind edge-to-edge content on OLED panels. */
val OledBlack = Color(0xFF000000)

@Composable
fun PixivizTheme(
    mode: UiThemeMode = UiThemeMode.SYSTEM,
    pureBlack: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dark = when (mode) {
        UiThemeMode.SYSTEM -> isSystemInDarkTheme()
        UiThemeMode.LIGHT -> false
        UiThemeMode.DARK -> true
    }

    val colors = when {
        dark && pureBlack -> DarkColorsPureBlack
        dark -> DarkColors
        else -> LightColors
    }

    // In dark themes glass needs a slightly stronger tint and border to stay legible.
    val tokens = if (dark) {
        GlassTokens(
            blurRadius = 26.dp,
            tintAlpha = if (pureBlack) 0.62f else 0.5f,
            borderAlphaTop = 0.30f,
            borderAlphaBottom = 0.04f,
            noise = 0.035f,
        )
    } else {
        GlassTokens(
            blurRadius = 22.dp,
            tintAlpha = 0.62f,
            borderAlphaTop = 0.55f,
            borderAlphaBottom = 0.10f,
            noise = 0.02f,
        )
    }

    CompositionLocalProvider(LocalGlassTokens provides tokens) {
        MaterialTheme(
            colorScheme = colors,
            typography = PixivizTypography,
            shapes = PixivizShapes,
            content = content,
        )
    }
}
