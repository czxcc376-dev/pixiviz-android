package pixiviz.mobile.ui.glass

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import pixiviz.mobile.ui.theme.LocalGlassTokens

/**
 * Single entry point for the "liquid glass" material used across the app.
 *
 * True backdrop blur needs `RenderEffect`, which exists from Android 12 (API 31) only.
 * On Android 11 Haze degrades to a scrim derived from `fallbackTint`, and we still draw
 * our own specular border + noise so the surface keeps reading as glass.
 *
 * All glass in the UI must go through this file, so the implementation stays swappable.
 */

@Composable
fun rememberHazeState(): HazeState = remember { HazeState() }

/** Marks scrolling content as the backdrop source. Apply once, to the content. */
fun Modifier.liquidGlassSource(state: HazeState): Modifier = hazeSource(state = state)

@Composable
fun LiquidGlassSurface(
    hazeState: HazeState,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.extraLarge,
    blurRadius: Dp? = null,
    tint: Color = Color.Unspecified,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val tokens = LocalGlassTokens.current
    val scheme = MaterialTheme.colorScheme
    val resolvedTint = if (tint == Color.Unspecified) {
        scheme.surface.copy(alpha = tokens.tintAlpha)
    } else {
        tint
    }

    val style = HazeStyle(
        backgroundColor = Color.Transparent,
        tints = listOf(HazeTint(resolvedTint)),
        blurRadius = blurRadius ?: tokens.blurRadius,
        noiseFactor = tokens.noise,
        fallbackTint = HazeTint(resolvedTint),
    )
    val border = Brush.verticalGradient(
        listOf(
            Color.White.copy(alpha = tokens.borderAlphaTop),
            Color.White.copy(alpha = tokens.borderAlphaBottom),
        ),
    )

    Box(
        modifier = modifier
            .clip(shape)
            .hazeEffect(state = hazeState, style = style)
            .border(width = 1.dp, brush = border, shape = shape),
        content = content,
    )
}
