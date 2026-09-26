package com.neochildclinic.core.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.neochildclinic.domain.model.AuditLog
import com.neochildclinic.features.audit.AuditLogItem

/**
 * Standardized Delete Confirmation Dialog to maintain UI consistency across the app.
 */
@Composable
fun DeleteConfirmationDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    title: String = "Delete Confirmation",
    message: String = "Are you sure you want to delete this item? This action cannot be undone.",
    // While a deletion is actually running (e.g. the vaccination-delete transaction),
    // both actions are disabled so a slow tap-to-dismiss race or a rapid double-tap on
    // Delete can't fire the delete transaction twice or dismiss the dialog mid-flight.
    isDeleting: Boolean = false
) {
    if (show) {
        AlertDialog(
            onDismissRequest = { if (!isDeleting) onDismiss() },
            title = { Text(title) },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = onConfirm, enabled = !isDeleting) {
                    if (isDeleting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.error
                        )
                    } else {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss, enabled = !isDeleting) {
                    Text("Cancel")
                }
            }
        )
    }
}

/**
 * Dialog to display a patient's audit history.
 *
 * This is fed by an online-only pager (see PatientAuditLogPager) - there is no local cache to
 * fall back on, so [isLoading]/[error] reflect the network call directly. Older entries are
 * fetched a page at a time as the user scrolls near the bottom of the list; [onLoadMore] should
 * be wired to that pager's loadMore().
 */
@Composable
fun AuditLogDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    logs: List<AuditLog>,
    isLoading: Boolean = false,
    isLoadingMore: Boolean = false,
    hasMore: Boolean = false,
    error: String? = null,
    onLoadMore: () -> Unit = {}
) {
    if (show) {
        Dialog(onDismissRequest = onDismiss) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = MaterialTheme.shapes.large
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Audit History",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    when {
                        isLoading && logs.isEmpty() -> {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                        error != null && logs.isEmpty() -> {
                            Text(
                                text = "Couldn't load history: $error",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(vertical = 32.dp)
                            )
                        }
                        logs.isEmpty() -> {
                            Text(
                                text = "No history recorded yet.",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(vertical = 32.dp)
                            )
                        }
                        else -> {
                            val listState = rememberLazyListState()

                            // Fire loadMore() once the user has scrolled within 5 items of the
                            // end. This is the only trigger for paging - there's no "Load more"
                            // button - so the dialog keeps extending itself while there's still
                            // a next page (hasMore) and nothing is already in flight.
                            LaunchedEffect(listState, logs.size, hasMore) {
                                snapshotFlow {
                                    val layout = listState.layoutInfo
                                    val lastVisible = layout.visibleItemsInfo.lastOrNull()?.index ?: -1
                                    lastVisible >= logs.size - 5
                                }.collect { shouldLoadMore ->
                                    if (shouldLoadMore) onLoadMore()
                                }
                            }

                            LazyColumn(
                                state = listState,
                                modifier = Modifier
                                    .weight(1f, fill = false)
                                    .heightIn(max = 420.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(logs, key = { it.id }) { log ->
                                    AuditLogItem(log)
                                }

                                if (isLoadingMore) {
                                    item(key = "audit_history_loading_more") {
                                        Box(
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            CircularProgressIndicator(modifier = Modifier.size(20.dp))
                                        }
                                    }
                                }

                                if (error != null && !isLoadingMore) {
                                    item(key = "audit_history_error") {
                                        Text(
                                            text = "Couldn't load more: $error",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.padding(vertical = 8.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Close")
                    }
                }
            }
        }
    }
}
