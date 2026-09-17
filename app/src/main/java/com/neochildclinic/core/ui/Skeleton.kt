package com.neochildclinic.core.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Shimmer-based skeleton placeholders, used everywhere in the app that used to show a
 * centered [androidx.compose.material3.CircularProgressIndicator] while a screen's
 * initial content was loading. A skeleton mirrors the shape of the content that's about
 * to appear (list rows, cards, tiles) instead of blocking the screen with a spinner -
 * it reads as "content is arriving" rather than "everything stopped".
 *
 * Building blocks: [SkeletonBox] / [SkeletonLine] / [SkeletonCircle] compose into
 * anything; [SkeletonListItem] / [SkeletonList] cover the common "row with an avatar
 * and two lines of text" list screens (patients, staff, inventory, expenses, search);
 * [SkeletonCard] covers dashboard-tile / chart-shaped blocks (Statistics, Dashboard).
 *
 * Small inline loaders - a spinner inside a Save button, a "loading more" spinner at
 * the bottom of a paged list - are intentionally left as `CircularProgressIndicator`.
 * Skeletons are for "I don't know what's about to appear yet, but I know its shape";
 * a determinate in-progress action doesn't need that.
 */
@Composable
fun rememberShimmerBrush(): Brush {
    val baseColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val highlightColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f)
    val transition = rememberInfiniteTransition(label = "skeletonShimmer")
    val translateAnim by transition.animateFloat(
        initialValue = -500f,
        targetValue = 1500f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerTranslate"
    )
    return Brush.linearGradient(
        colors = listOf(baseColor, highlightColor, baseColor),
        start = Offset(translateAnim - 400f, 0f),
        end = Offset(translateAnim, 400f)
    )
}

/** Lowest-level shimmering placeholder shape. Prefer [SkeletonLine]/[SkeletonCircle]/[SkeletonCard] below unless you need a custom shape. */
@Composable
fun SkeletonBox(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(6.dp),
    brush: Brush = rememberShimmerBrush()
) {
    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .clip(shape)
            .background(brush)
    )
}

/** A shimmering text-line placeholder. */
@Composable
fun SkeletonLine(
    modifier: Modifier = Modifier,
    height: Dp = 14.dp,
    widthFraction: Float = 1f,
    brush: Brush = rememberShimmerBrush()
) {
    SkeletonBox(
        modifier = modifier
            .fillMaxWidth(widthFraction)
            .height(height),
        shape = RoundedCornerShape(4.dp),
        brush = brush
    )
}

/** A shimmering circular placeholder, for avatars/icons. */
@Composable
fun SkeletonCircle(
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    brush: Brush = rememberShimmerBrush()
) {
    SkeletonBox(modifier = modifier.size(size), shape = CircleShape, brush = brush)
}

/**
 * One shimmering list row: optional leading avatar circle + a title line + a shorter
 * subtitle line, optionally with a small trailing chip. Matches the shape of most list
 * rows in this app (patient cards, staff rows, inventory rows, expense rows, search
 * results) closely enough to read as "this list is about to look like this".
 */
@Composable
fun SkeletonListItem(
    modifier: Modifier = Modifier,
    showLeadingIcon: Boolean = true,
    showTrailing: Boolean = false,
    cardShaped: Boolean = false
) {
    val brush = rememberShimmerBrush()
    val rowContent: @Composable () -> Unit = {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showLeadingIcon) {
                SkeletonCircle(size = 44.dp, brush = brush)
                Spacer(modifier = Modifier.width(16.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                SkeletonLine(widthFraction = 0.55f, height = 16.dp, brush = brush)
                Spacer(modifier = Modifier.height(8.dp))
                SkeletonLine(widthFraction = 0.35f, height = 12.dp, brush = brush)
            }
            if (showTrailing) {
                Spacer(modifier = Modifier.width(12.dp))
                SkeletonBox(
                    modifier = Modifier.size(width = 56.dp, height = 24.dp),
                    shape = RoundedCornerShape(12.dp),
                    brush = brush
                )
            }
        }
    }

    if (cardShaped) {
        androidx.compose.foundation.layout.Box(
            modifier = modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
        ) { rowContent() }
    } else {
        androidx.compose.foundation.layout.Box(modifier = modifier.fillMaxWidth()) { rowContent() }
    }
}

/** Fills a screen with [count] shimmering rows - the default replacement for a centered spinner on any list screen. */
@Composable
fun SkeletonList(
    modifier: Modifier = Modifier,
    count: Int = 6,
    showLeadingIcon: Boolean = true,
    showTrailing: Boolean = false,
    cardShaped: Boolean = false,
    spacing: Dp = 8.dp,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    Column(modifier = modifier.padding(contentPadding)) {
        repeat(count) { index ->
            SkeletonListItem(showLeadingIcon = showLeadingIcon, showTrailing = showTrailing, cardShaped = cardShaped)
            if (index != count - 1) Spacer(modifier = Modifier.height(spacing))
        }
    }
}

/** A shimmering block for card/tile-shaped content - dashboard tiles, chart areas, summary panels. */
@Composable
fun SkeletonCard(
    modifier: Modifier = Modifier,
    height: Dp = 100.dp,
    shape: Shape = RoundedCornerShape(16.dp)
) {
    SkeletonBox(
        modifier = modifier
            .fillMaxWidth()
            .height(height),
        shape = shape
    )
}
