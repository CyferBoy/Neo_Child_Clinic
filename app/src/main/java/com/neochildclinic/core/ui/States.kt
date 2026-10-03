package com.neochildclinic.core.ui

import com.neochildclinic.core.designsystem.*

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * No content. Always offer the action that creates it - "No patients yet - Add patient",
 * never a bare sentence.
 *
 * Deliberately does NOT fillMaxSize: the previous version did, which breaks when used
 * as a LazyColumn item (unbounded height). Callers own the height.
 *
 * Note: this replaces the old single-string EmptyState. Pass a `title` and a `message`
 * instead of one blob of text.
 */
@Composable
fun EmptyState(
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    title: String,
    message: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    StateLayout(modifier = modifier, icon = icon, container = MaterialTheme.colorScheme.surfaceVariant, onContainer = MaterialTheme.colorScheme.onSurfaceVariant) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        if (!message.isNullOrBlank()) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(
                    text = actionLabel,
                    modifier = Modifier.padding(start = Spacing.sm)
                )
            }
        }
    }
}

/**
 * A load failed. Always give a way out - a failure with no recovery path is a dead end.
 *
 * Use this for LOAD failures. For a failed ACTION (a save, a delete) a Snackbar is
 * correct instead: transient feedback for a discrete event. Neither should use a Toast.
 */
@Composable
fun ErrorState(
    message: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
    retryLabel: String = "Try again"
) {
    StateLayout(
        modifier = modifier,
        icon = Icons.Default.ErrorOutline,
        container = MaterialTheme.colorScheme.errorContainer,
        onContainer = MaterialTheme.colorScheme.onErrorContainer
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        if (onRetry != null) {
            TextButton(onClick = onRetry) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(text = retryLabel, modifier = Modifier.padding(start = Spacing.sm))
            }
        }
    }
}

@Composable
private fun StateLayout(
    modifier: Modifier = Modifier,
    icon: ImageVector?,
    container: androidx.compose.ui.graphics.Color,
    onContainer: androidx.compose.ui.graphics.Color,
    content: @Composable () -> Unit
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(container, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    // Decorative: the title/message below carries the meaning.
                    contentDescription = null,
                    tint = onContainer,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
        content()
    }
}
