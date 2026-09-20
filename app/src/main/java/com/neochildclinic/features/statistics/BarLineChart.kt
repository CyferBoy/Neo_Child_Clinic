package com.neochildclinic.features.statistics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class BarLineSeries(
    val label: String,
    val values: List<Float>,
    val color: Color,
    val isLine: Boolean = false
)

@Composable
fun BarLineChart(
    modifier: Modifier = Modifier,
    title: String,
    labels: List<String>,
    series: List<BarLineSeries>,
    yLabel: String = "₹K"
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            series.forEach { s ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (s.isLine) {
                        Canvas(modifier = Modifier.size(12.dp, 6.dp)) {
                            drawLine(color = s.color, start = Offset(0f, size.height / 2), end = Offset(size.width, size.height / 2), strokeWidth = 2.dp.toPx())
                            drawCircle(color = s.color, radius = 3.dp.toPx(), center = Offset(size.width / 2, size.height / 2))
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .size(12.dp, 6.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(s.color)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = s.label,
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        val barSeries = series.filter { !s -> s.isLine }
        val lineSeries = series.filter { s -> s.isLine }
        val allValues = series.flatMap { it.values }
        val maxVal = allValues.maxOrNull()?.coerceAtLeast(0.01f) ?: 1f
        val barCount = barSeries.size

        Box(modifier = Modifier.fillMaxWidth().height(220.dp)) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val width = size.width
                val height = size.height
                val barGroupWidth = width / labels.size
                val barWidth = if (barCount > 0) (barGroupWidth * 0.6f) / barCount else 0f
                val barSpacing = barGroupWidth * 0.1f

                for (i in 0..4) {
                    val y = height - (i * height / 4)
                    drawLine(
                        color = Color.LightGray.copy(alpha = 0.3f),
                        start = Offset(0f, y),
                        end = Offset(width, y),
                        strokeWidth = 1.dp.toPx()
                    )
                }

                barSeries.forEachIndexed { seriesIdx, s ->
                    s.values.forEachIndexed { barIdx, value ->
                        val x = barIdx * barGroupWidth + barSpacing + seriesIdx * barWidth
                        val barHeight = (value / maxVal) * height
                        drawRect(
                            color = s.color.copy(alpha = 0.8f),
                            topLeft = Offset(x, height - barHeight),
                            size = Size(barWidth, barHeight)
                        )
                    }
                }

                lineSeries.forEach { s ->
                    val path = Path()
                    s.values.forEachIndexed { idx, value ->
                        val x = idx * barGroupWidth + barGroupWidth / 2
                        val y = height - (value / maxVal) * height
                        if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
                        drawCircle(color = s.color, radius = 3.dp.toPx(), center = Offset(x, y))
                    }
                    drawPath(path = path, color = s.color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            labels.forEach { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
