package com.neochildclinic.core.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

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
