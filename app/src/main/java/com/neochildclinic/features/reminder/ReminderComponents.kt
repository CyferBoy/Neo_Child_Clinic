package com.neochildclinic.features.reminder

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun FilterTabRow(
    filters: List<String>,
    selectedFilter: String,
    onFilterChanged: (String) -> Unit
) {
    ScrollableTabRow(
        selectedTabIndex = filters.indexOf(selectedFilter),
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.primary,
        edgePadding = 0.dp,
        divider = {}
    ) {
        filters.forEach { filter ->
            Tab(
                selected = selectedFilter == filter,
                onClick = { onFilterChanged(filter) },
                text = { Text(filter, style = MaterialTheme.typography.labelLarge) }
            )
        }
    }
}
