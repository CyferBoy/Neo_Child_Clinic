package com.neochildclinic.core.ui

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

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
    shape: RoundedCornerShape = RoundedCornerShape(12.dp),
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

/**
 * A generic sort button that opens a menu with provided options.
 * Shows a Toast message when a sort option is selected.
 */
@Composable
fun <T> SortButton(
    options: List<Pair<T, String>>,
    onSortSelected: (T, String) -> Unit,
    iconColor: Color = MaterialTheme.colorScheme.onPrimary,
) {
    var expanded by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                Icons.AutoMirrored.Filled.Sort,
                contentDescription = "Sort",
                tint = iconColor
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { (option, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        onSortSelected(option, label)
                        expanded = false
                        Toast.makeText(context, label, Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
    }
}
