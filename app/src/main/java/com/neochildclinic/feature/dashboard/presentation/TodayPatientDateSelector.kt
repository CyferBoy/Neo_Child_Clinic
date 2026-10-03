package com.neochildclinic.feature.dashboard.presentation

import com.neochildclinic.core.designsystem.*

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neochildclinic.core.designsystem.LocalCustomColors
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun HorizontalDateSelector(
    selectedDate: String,
    datesWithData: Set<String>,
    onDateSelected: (String) -> Unit
) {
    
    val dayFormat = remember { DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH) }
    val dateFormat = remember { DateTimeFormatter.ofPattern("d", Locale.ENGLISH) }

    val daysInMonth = remember(selectedDate) {
        val selected = try {
            java.time.LocalDate.parse(selectedDate)
        } catch (_: java.time.format.DateTimeParseException) {
            java.time.LocalDate.now()
        }
        val currentMonth = selected.monthValue
        var cursor = selected.withDayOfMonth(1)

        val days = mutableListOf<java.time.LocalDate>()
        while (cursor.monthValue == currentMonth) {
            days.add(cursor)
            cursor = cursor.plusDays(1)
        }
        days
    }

    val listState = rememberLazyListState()

    LaunchedEffect(selectedDate) {
        val selectedIdx = daysInMonth.indexOfFirst { it.toString() == selectedDate }
        if (selectedIdx >= 0) {
            listState.animateScrollToItem(selectedIdx)
        }
    }

    LazyRow(
        state = listState,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.sm),
        contentPadding = PaddingValues(horizontal = Spacing.lg),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        items(daysInMonth) { date ->
            val dateStr = date.toString()
            val isSelected = dateStr == selectedDate
            val hasData = datesWithData.contains(dateStr)

            DateItem(
                dayName = date.format(dayFormat),
                dayDate = date.format(dateFormat),
                isSelected = isSelected,
                hasData = hasData,
                onClick = { onDateSelected(dateStr) }
            )
        }
    }
}

@Composable
internal fun DateItem(
    dayName: String,
    dayDate: String,
    isSelected: Boolean,
    hasData: Boolean,
    onClick: () -> Unit
) {
    val customColors = LocalCustomColors.current
    Card(
        onClick = onClick,
        modifier = Modifier.width(50.dp),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
            contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) Spacing.xs else 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = Spacing.sm),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(dayName, fontSize = 10.sp, fontWeight = FontWeight.Normal)
            Text(dayDate, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Box(
                modifier = Modifier.height(10.dp),
                contentAlignment = Alignment.Center
            ) {
                if (hasData && !isSelected) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            // Was SuccessGreen #4CAF50 (2.71:1). This is the verified content token (7.68:1).
                            .background(customColors.textGreen, CircleShape)
                    )
                }
            }
        }
    }
}