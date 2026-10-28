package com.neochildclinic.feature.dashboard.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neochildclinic.core.designsystem.Spacing
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
            listState.animateScrollToItem((selectedIdx - 2).coerceAtLeast(0))
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
                dayName = date.format(dayFormat).uppercase(),
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
    Card(
        onClick = onClick,
        modifier = Modifier
            .width(50.dp)
            .height(66.dp),
        shape = RoundedCornerShape(15.dp),
        border = BorderStroke(
            width = 1.dp,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
        ),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
            contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = dayName,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp,
                color = if (isSelected) Color.White.copy(alpha = 0.9f) else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = dayDate,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
            )
            Box(
                modifier = Modifier.size(5.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .size(4.dp)
                            .background(Color.White, CircleShape)
                    )
                } else if (hasData) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                    )
                }
            }
        }
    }
}
