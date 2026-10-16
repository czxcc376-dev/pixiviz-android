package pixiviz.mobile.ui.common

import android.os.Build
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import pixiviz.mobile.data.settings.NsfwMode
import pixiviz.mobile.ui.LocalApp
import pixiviz.mobile.ui.LocalSettings

/**
 * Renders a pixiv image, resolving the URL through the repository so the configured
 * image channel is honoured.
 *
 * Adult works in BLUR mode load the 240x240 variant first, which already reads as heavily
 * blurred once scaled up, and additionally get a real `RenderEffect` blur on Android 12+.
 * Android 11 has no `RenderEffect`, so the surrogate alone carries the effect there.
 */
@Composable
fun PixivImage(
    rawUrl: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    adult: Boolean = false,
    blurRadius: Dp = 34.dp,
) {
    val app = LocalApp.current
    val settings = LocalSettings.current
    val hideAdult = adult && settings.nsfwMode == NsfwMode.HIDE
    if (hideAdult) {
        BlockedPlaceholder(modifier)
        return
    }
    val blurred = adult && settings.nsfwMode == NsfwMode.BLUR
    val model = remember(rawUrl, blurred, settings.imageChannel) {
        val url = if (blurred) downscaleToSquare(rawUrl) else rawUrl
        app.repository.imageUrl(url)
    }

    Box(modifier) {
        AsyncImage(
            model = model,
            contentDescription = contentDescription,
            contentScale = contentScale,
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (blurred && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        Modifier.blur(blurRadius)
                    } else {
                        Modifier
                    },
                ),
        )
        if (blurred) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.22f)),
            )
        }
    }
}

@Composable
private fun BlockedPlaceholder(modifier: Modifier) {
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "已隐藏",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Rewrites any pixiv image URL to its 240x240 square variant.
 *
 * Matching on the `YYYY/MM/DD/HH/MM/SS/file` tail is intentional: `img-original/img/...`
 * still contains the substring `/img/`, so prefix trimming silently eats a path segment.
 */
internal fun downscaleToSquare(raw: String): String {
    val match = DATE_PATH.find(raw) ?: return raw
    val datePath = match.groupValues[1]
    val base = match.groupValues[2].substringBeforeLast('.')
    val stem = base.replace(SIZE_SUFFIX, "")
    if (stem.isBlank()) return raw
    return "https://i.pximg.net/c/240x240/img-master/img/$datePath/${stem}_square1200.jpg"
}

private val DATE_PATH = Regex("([0-9]{4}/[0-9]{2}/[0-9]{2}/[0-9]{2}/[0-9]{2}/[0-9]{2})/([^/]+)$")

/**
 * Only the trailing size marker is stripped. Trimming at the last underscore instead would
 * turn `<id>_p2.jpg` into `<id>`, i.e. page 0 of a different work.
 */
private val SIZE_SUFFIX = Regex("_(master|square)[0-9]+$")

@Composable
fun ShimmerBox(modifier: Modifier = Modifier, shape: RoundedCornerShape = RoundedCornerShape(18.dp)) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400), RepeatMode.Restart),
        label = "shimmerProgress",
    )
    val base = MaterialTheme.colorScheme.surfaceVariant
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.linearGradient(
                    colors = listOf(base, base.copy(alpha = 0.45f), base),
                    start = androidx.compose.ui.geometry.Offset(progress * 900f - 500f, 0f),
                    end = androidx.compose.ui.geometry.Offset(progress * 900f, 500f),
                ),
            ),
    )
}

@Composable
fun EmptyState(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        if (subtitle != null) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
fun ErrorState(
    message: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("出错了", style = MaterialTheme.typography.titleMedium)
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (onRetry != null) {
            CapsuleButton(text = "重试", onClick = onRetry)
        }
    }
}

@Composable
fun CapsuleButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = true,
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(if (selected) scheme.primary.copy(alpha = 0.18f) else scheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) scheme.primary else scheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 10.dp),
    )
}

/** Horizontal sequence of capsule segments — used for rank modes, search tabs, filters. */
@Composable
fun <T> CapsuleSegmentedRow(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp),
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(contentPadding),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { option ->
            CapsuleButton(
                text = label(option),
                onClick = { onSelect(option) },
                selected = option == selected,
            )
        }
    }
}

@Composable
fun Divider8() {
    Box(Modifier.height(8.dp))
}
