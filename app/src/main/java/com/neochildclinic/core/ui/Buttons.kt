package com.neochildclinic.core.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.RowScope
import androidx.compose.ui.Alignment

/**
 * Shows a one-off message in the nearest Scaffold's Snackbar.
 *
 * Replaces the old `MessageEffect`, which used [Toast]. A Toast is the wrong
 * surface here: it auto-dismisses on a timer, so a failed save or a dropped
 * upload can vanish before a clinician reads it, it is unstyled in dark mode, and
 * it carries no severity. A Snackbar is an announced live region that respects
 * the theme and can be held for as long as the message needs.
 *
 * Requires a `SnackbarHost(snackbarHostState)` on the screen's Scaffold -
 * without one `showSnackbar` is a no-op and the message is silently dropped.
 *
 * [duration] defaults to [SnackbarDuration.Long]: pass [SnackbarDuration.Short]
 * for routine success confirmations, and keep the default for errors.
 */
@Composable
fun ShowSnackbar(
    message: String?,
    hostState: SnackbarHostState,
    duration: SnackbarDuration = SnackbarDuration.Long,
    onShown: () -> Unit = {}
) {
    LaunchedEffect(message) {
        if (message != null) {
            hostState.showSnackbar(message, duration = duration)
            onShown()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackTopAppBar(
    title: @Composable () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    actions: @Composable RowScope.() -> Unit = {},
    colors: TopAppBarColors = TopAppBarDefaults.topAppBarColors(
        containerColor = MaterialTheme.colorScheme.background,
        titleContentColor = MaterialTheme.colorScheme.onBackground,
        navigationIconContentColor = MaterialTheme.colorScheme.onBackground
    )
) {
    TopAppBar(
        title = title,
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        },
        actions = actions,
        scrollBehavior = scrollBehavior,
        modifier = modifier,
        colors = colors
    )
}

/**
 * Standardized Button with width constraints for better UI on large screens.
 */
@Composable
fun StandardButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary,
    shape: Shape = MaterialTheme.shapes.medium,
    isLoading: Boolean = false,
    content: @Composable RowScope.() -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
        enabled = enabled && !isLoading,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        shape = shape
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = contentColor,
                strokeWidth = 2.dp
            )
        } else {
            content()
        }
    }
}
