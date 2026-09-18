package com.neochildclinic.core.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Standard Pull to Refresh component to be used across the app.
 * Wraps Material3 1.3.0+ API (PullToRefreshBox) with a custom horizontal-line
 * indicator instead of the default circular spinner.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppPullToRefresh(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val state = rememberPullToRefreshState()

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        state = state,
        modifier = modifier,
        indicator = {
            HorizontalLineRefreshIndicator(
                state = state,
                isRefreshing = isRefreshing,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        },
        content = content
    )
}

/**
 * A 4dp horizontal line at the top of the pull container that grows with the pull
 * distance (distanceFraction 0 -> 1), turns primary once the refresh threshold is
 * reached, pulses while a refresh is in flight, and briefly shows as a solid line
 * after a refresh completes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HorizontalLineRefreshIndicator(
    state: PullToRefreshState,
    isRefreshing: Boolean,
    modifier: Modifier = Modifier
) {
    val fraction = state.distanceFraction.coerceAtLeast(0f)

    var wasRefreshing by remember { mutableStateOf(false) }
    var showUpdated by remember { mutableStateOf(false) }

    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            wasRefreshing = true
            showUpdated = false
        } else if (wasRefreshing) {
            showUpdated = true
            delay(900)
            showUpdated = false
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "ptrAlpha")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 450), RepeatMode.Reverse),
        label = "ptrPulse"
    )

    val lineFraction = when {
        isRefreshing || showUpdated || fraction >= 1f -> 1f
        else -> fraction.coerceIn(0f, 1f)
    }
    val gradient = Brush.horizontalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.tertiary,
            MaterialTheme.colorScheme.secondary
        )
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(4.dp)
            .graphicsLayer {
                scaleX = lineFraction
                alpha = if (isRefreshing) pulseAlpha else 1f
                transformOrigin = TransformOrigin(0.5f, 0.5f)
            }
            .background(gradient)
    )
}