package pixiviz.mobile.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Brand seed inherited from the original Pixiviz web app. */
val BrandSeed = Color(0xFF8F77B5)

/** Tuning for the glass material, so density can be themed without touching components. */
data class GlassTokens(
    val blurRadius: Dp,
    val tintAlpha: Float,
    val borderAlphaTop: Float,
    val borderAlphaBottom: Float,
    val noise: Float,
)

val LocalGlassTokens = staticCompositionLocalOf {
    GlassTokens(
        blurRadius = 24.dp,
        tintAlpha = 0.55f,
        borderAlphaTop = 0.34f,
        borderAlphaBottom = 0.05f,
        noise = 0.035f,
    )
}

internal val DarkColors = darkColorScheme(
    primary = Color(0xFFC6B2EA),
    onPrimary = Color(0xFF2A1B4A),
    primaryContainer = Color(0xFF43306B),
    onPrimaryContainer = Color(0xFFE9DFFB),
    secondary = Color(0xFFC9C2DA),
    onSecondary = Color(0xFF312E3F),
    secondaryContainer = Color(0xFF484455),
    onSecondaryContainer = Color(0xFFE6E0F2),
    tertiary = Color(0xFFE0BBD0),
    onTertiary = Color(0xFF402737),
    background = Color(0xFF0B0B0F),
    onBackground = Color(0xFFE8E5F0),
    surface = Color(0xFF121218),
    onSurface = Color(0xFFE8E5F0),
    surfaceVariant = Color(0xFF23222B),
    onSurfaceVariant = Color(0xFFA9A3B8),
    outline = Color(0xFF4A4756),
    outlineVariant = Color(0xFF2C2A34),
    error = Color(0xFFF98080),
    onError = Color(0xFF3A0B0B),
    scrim = Color(0xCC000000),
)

internal val DarkColorsPureBlack = DarkColors.copy(
    background = Color(0xFF000000),
    surface = Color(0xFF000000),
    surfaceVariant = Color(0xFF15151A),
)

internal val LightColors = lightColorScheme(
    primary = Color(0xFF6B4FA8),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE9DFFB),
    onPrimaryContainer = Color(0xFF241245),
    secondary = Color(0xFF625B71),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE8DEF8),
    onSecondaryContainer = Color(0xFF1E192B),
    tertiary = Color(0xFF7D5260),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFFBF9FF),
    onBackground = Color(0xFF1B1722),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1B1722),
    surfaceVariant = Color(0xFFEEEAF6),
    onSurfaceVariant = Color(0xFF4A4557),
    outline = Color(0xFF7A7389),
    outlineVariant = Color(0xFFD6D0E0),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
)

internal val PixivizTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 30.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.4).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 25.sp,
        lineHeight = 31.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 13.5.sp,
        lineHeight = 20.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 12.5.sp,
        lineHeight = 16.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
    ),
)

internal val PixivizShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp),
)
