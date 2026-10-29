package com.neochildclinic.feature.statistics.presentation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neochildclinic.core.designsystem.*
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

data class BarLineSeries(
    val label: String,
    val values: List<Float>,
    val color: Color,
    val isLine: Boolean = false
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BarLineChart(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String? = null,
    labels: List<String>,
    series: List<BarLineSeries>,
    yLabel: String = ""
) {
    Column(
        modifier = modifier
            .clip(ChartContainerShape)
            .background(MaterialTheme.colorScheme.surface)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.75f),
                shape = ChartContainerShape
            )
            .padding(Spacing.card)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (subtitle != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (yLabel.isNotBlank()) {
                Text(
                    text = yLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(Spacing.md))

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            series.forEach { s ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (s.isLine) {
                        Canvas(modifier = Modifier.size(Spacing.md, 6.dp)) {
                            drawLine(
                                color = s.color,
                                start = Offset(0f, size.height / 2),
                                end = Offset(size.width, size.height / 2),
                                strokeWidth = 2.dp.toPx()
                            )
                            drawCircle(
                                color = s.color,
                                radius = 3.dp.toPx(),
                                center = Offset(size.width / 2, size.height / 2)
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .size(Spacing.md, 6.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(s.color)
                        )
                    }
                    Spacer(modifier = Modifier.width(Spacing.xs))
                    Text(
                        text = s.label,
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
        }

        if (labels.isEmpty() || series.isEmpty()) {
            Spacer(modifier = Modifier.height(Spacing.xl))
            Text(
                text = "No chart data for this period.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = Spacing.xl),
                textAlign = TextAlign.Center
            )
            return@Column
        }

        val allValues = series.flatMap { it.values }
        val rawMax = allValues.maxOrNull() ?: 0f
        val rawMin = allValues.minOrNull() ?: 0f
        val axisMin = min(rawMin, 0f)
        val axisMax = max(rawMax, 0f).let { if (it == axisMin) it + 1f else it }
        val axisMid = axisMin + (axisMax - axisMin) / 2f

        val barSeries = series.filter { !it.isLine }
        val lineSeries = series.filter { it.isLine }
        val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
        val zeroLineColor = LocalCustomColors.current.outlineStrong.copy(alpha = 0.8f)
        val labelColor = MaterialTheme.colorScheme.onSurfaceVariant

        // A raw Canvas exposes nothing to TalkBack. Describe the same data the chart draws.
        val chartDescription = remember(series, labels) {
            val parts = series.joinToString("; ") { s ->
                val total = s.values.fold(0f) { acc, value -> acc + value }
                val peak = s.values.maxOrNull() ?: 0f
                "${s.label}: total ${total.formatForSpeech()}, peak ${peak.formatForSpeech()}"
            }
            "Chart. $title. $parts. Period ${labels.first()} to ${labels.last()}."
        }

        Row(verticalAlignment = Alignment.Top) {
            Column(
                modifier = Modifier
                    .width(38.dp)
                    .height(210.dp)
                    .padding(end = Spacing.xs),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End
            ) {
                AxisLabel(axisMax.axisText(), labelColor)
                AxisLabel(axisMid.axisText(), labelColor)
                AxisLabel(axisMin.axisText(), labelColor)
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(210.dp)
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .semantics { contentDescription = chartDescription }
                ) {
                    val left = 4.dp.toPx()
                    val right = size.width - 4.dp.toPx()
                    val top = 4.dp.toPx()
                    val bottom = size.height - 4.dp.toPx()
                    val chartWidth = (right - left).coerceAtLeast(1f)
                    val chartHeight = (bottom - top).coerceAtLeast(1f)

                    fun yAt(value: Float): Float {
                        val ratio = (value - axisMin) / (axisMax - axisMin)
                        return bottom - ratio * chartHeight
                    }

                    for (i in 0..4) {
                        val y = bottom - (i * chartHeight / 4f)
                        drawLine(
                            color = gridColor,
                            start = Offset(left, y),
                            end = Offset(right, y),
                            strokeWidth = 1.dp.toPx()
                        )
                    }

                    if (axisMin < 0f && axisMax > 0f) {
                        val zeroY = yAt(0f)
                        drawLine(
                            color = zeroLineColor,
                            start = Offset(left, zeroY),
                            end = Offset(right, zeroY),
                            strokeWidth = 1.5.dp.toPx()
                        )
                    }

                    val groupWidth = chartWidth / labels.size.coerceAtLeast(1)
                    val barCount = barSeries.size
                    val usableBarWidth = groupWidth * 0.62f
                    val barWidth = if (barCount > 0) usableBarWidth / barCount else 0f
                    val zeroY = yAt(0f)

                    barSeries.forEachIndexed { seriesIdx, s ->
                        s.values.forEachIndexed { barIdx, value ->
                            val x = left + barIdx * groupWidth + (groupWidth - usableBarWidth) / 2f + seriesIdx * barWidth
                            val valueY = yAt(value)
                            val topY = min(valueY, zeroY)
                            val barHeight = abs(valueY - zeroY).coerceAtLeast(1f)
                            drawRect(
                                color = s.color.copy(alpha = 0.85f),
                                topLeft = Offset(x, topY),
                                size = Size(barWidth.coerceAtLeast(1f), barHeight)
                            )
                        }
                    }

                    lineSeries.forEach { s ->
                        val path = Path()
                        s.values.forEachIndexed { idx, value ->
                            val x = left + idx * groupWidth + groupWidth / 2f
                            val y = yAt(value)
                            if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
                            drawCircle(
                                color = s.color,
                                radius = 3.dp.toPx(),
                                center = Offset(x, y)
                            )
                        }
                        drawPath(
                            path = path,
                            color = s.color,
                            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(Spacing.sm))

        val visibleIndices = remember(labels) {
            if (labels.size <= 8) {
                labels.indices.toList()
            } else {
                val step = ceil(labels.size / 6.0).toInt().coerceAtLeast(1)
                (labels.indices step step).toList().let { indices ->
                    if (indices.lastOrNull() != labels.lastIndex) indices + labels.lastIndex else indices
                }
            }
        }

        if (labels.size <= 8) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 38.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                labels.forEach { label ->
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 38.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                visibleIndices.forEach { index ->
                    Text(
                        text = labels[index],
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun AxisLabel(text: String, color: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        fontSize = 9.sp,
        color = color,
        maxLines = 1
    )
}

private fun Float.axisText(): String =
    if (abs(this - this.toLong()) < 0.05f) toLong().toString()
    else String.format(Locale.US, "%.1f", this)

/** Speaks a chart value without a trailing ".0" on whole numbers ("3", not "3.0"). */
private fun Float.formatForSpeech(): String =
    if (this % 1f == 0f) toLong().toString() else String.format(Locale.US, "%.2f", this)
