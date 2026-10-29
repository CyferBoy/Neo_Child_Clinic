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

data class ChartDataPoint(val label: String, val values: List<Float>)

@Composable
fun TrendChart(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String? = null,
    data: List<ChartDataPoint>,
    seriesLabels: List<String>,
    seriesColors: List<Color>
) {
    Column(
        modifier = modifier
            .clip(ChartContainerShape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.75f), ChartContainerShape)
            .padding(Spacing.card)
    ) {
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

        Spacer(modifier = Modifier.height(Spacing.md))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            seriesLabels.forEachIndexed { index, label ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(Spacing.md, 6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(seriesColors.getOrElse(index) { MaterialTheme.colorScheme.primary })
                    )
                    Spacer(modifier = Modifier.width(Spacing.xs))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
        }

        if (data.isEmpty()) {
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

        val maxVal = data.flatMap { it.values }.maxOrNull()?.coerceAtLeast(1f) ?: 1f
        val midVal = maxVal / 2f
        val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
        val labelColor = MaterialTheme.colorScheme.onSurfaceVariant

        // Screen readers cannot see a Canvas; spell the trend out. Totals come from the same
        // data the lines are drawn from, so the description can never disagree with the chart.
        val trendDescription = remember(title, data, seriesLabels) {
            val summary = seriesLabels.indices.joinToString("; ") { s ->
                val total = data.fold(0f) { acc, point ->
                    acc + (point.values.getOrNull(s) ?: 0f)
                }
                "${seriesLabels[s]} total ${total.formatChartValue()}"
            }
            val first = data.firstOrNull()?.label.orEmpty()
            val last = data.lastOrNull()?.label.orEmpty()
            "$title. $summary. Period $first to $last."
        }

        Row(verticalAlignment = Alignment.Top) {
            Column(
                modifier = Modifier
                    .width(34.dp)
                    .height(190.dp)
                    .padding(end = Spacing.xs),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End
            ) {
                AxisLabel(maxVal.formatChartValue(), labelColor)
                AxisLabel(midVal.formatChartValue(), labelColor)
                AxisLabel("0", labelColor)
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(190.dp)
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .semantics { contentDescription = trendDescription }
                ) {
                    val left = 4.dp.toPx()
                    val right = size.width - 4.dp.toPx()
                    val top = 4.dp.toPx()
                    val bottom = size.height - 4.dp.toPx()
                    val chartWidth = (right - left).coerceAtLeast(1f)
                    val chartHeight = (bottom - top).coerceAtLeast(1f)

                    for (i in 0..4) {
                        val y = bottom - (i * chartHeight / 4f)
                        drawLine(
                            color = gridColor,
                            start = Offset(left, y),
                            end = Offset(right, y),
                            strokeWidth = 1.dp.toPx()
                        )
                    }

                    val step = if (data.size > 1) chartWidth / (data.size - 1) else chartWidth
                    fun xAt(index: Int): Float =
                        if (data.size == 1) left + chartWidth / 2f else left + index * step
                    fun yAt(value: Float): Float =
                        bottom - (value.coerceAtLeast(0f) / maxVal) * chartHeight

                    // A quiet area under the first series gives the trend depth without
                    // competing with the other lines.
                    val areaColor = seriesColors.firstOrNull() ?: Color.Transparent
                    val area = Path()
                    area.moveTo(xAt(0), bottom)
                    data.forEachIndexed { pointIdx, point ->
                        area.lineTo(xAt(pointIdx), yAt(point.values.getOrNull(0) ?: 0f))
                    }
                    area.lineTo(xAt(data.lastIndex), bottom)
                    area.close()
                    drawPath(area, color = areaColor.copy(alpha = 0.08f))

                    for (seriesIdx in seriesLabels.indices) {
                        val seriesColor = seriesColors.getOrElse(seriesIdx) { Color.Gray }
                        val path = Path()
                        data.forEachIndexed { pointIdx, point ->
                            val x = xAt(pointIdx)
                            val y = yAt(point.values.getOrNull(seriesIdx) ?: 0f)
                            if (pointIdx == 0) path.moveTo(x, y) else path.lineTo(x, y)
                            drawCircle(
                                color = seriesColor,
                                radius = 3.dp.toPx(),
                                center = Offset(x, y)
                            )
                        }
                        drawPath(
                            path = path,
                            color = seriesColor,
                            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(Spacing.sm))

        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 34.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            data.forEach { point ->
                Text(
                    text = point.label,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
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

private fun Float.formatChartValue(): String =
    if (this % 1f == 0f) toLong().toString()
    else String.format(Locale.US, "%.1f", this)
