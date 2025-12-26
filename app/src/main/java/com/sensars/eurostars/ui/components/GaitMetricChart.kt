package com.sensars.eurostars.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Configuration for a gait metric chart with color zones
 */
data class MetricChartConfig(
    val title: String,
    val minValue: Double,
    val maxValue: Double,
    val greenMin: Double,
    val greenMax: Double,
    val yellowMin1: Double,
    val yellowMax1: Double,
    val yellowMin2: Double,
    val yellowMax2: Double,
    val valueFormat: (Double) -> String = { String.format("%.2f", it) }
)

@OptIn(ExperimentalTextApi::class)
@Composable
fun GaitMetricChart(
    config: MetricChartConfig,
    patientValue: Double?,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Metric title (left side)
        Text(
            text = config.title,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier
                .width(180.dp)
                .padding(top = 8.dp),
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
        )
        
        // Chart (right side)
        Column(modifier = Modifier.weight(1f)) {
            if (patientValue == null) {
                Text(
                    text = "No data available",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(32.dp)
                        .background(Color.LightGray, RoundedCornerShape(4.dp))
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val barWidth = size.width
                        val barHeight = size.height

                        // Draw color zones
                        drawZone(Color.Red, config.minValue, config.yellowMin1, config.minValue, config.maxValue, barWidth, barHeight)
                        drawZone(Color(0xFFFF9800), config.yellowMin1, config.yellowMax1, config.minValue, config.maxValue, barWidth, barHeight) // Orange
                        drawZone(Color.Green, config.greenMin, config.greenMax, config.minValue, config.maxValue, barWidth, barHeight)
                        drawZone(Color(0xFFFF9800), config.yellowMin2, config.yellowMax2, config.minValue, config.maxValue, barWidth, barHeight) // Orange
                        drawZone(Color.Red, config.yellowMax2, config.maxValue, config.minValue, config.maxValue, barWidth, barHeight)

                        // Draw patient's value indicator (white line) - made thicker
                        val range = (config.maxValue - config.minValue)
                        val valueForIndicator = when {
                            // Special-case: when value is 0, always show it at the start of the bar (per UX request)
                            patientValue == 0.0 -> config.minValue
                            else -> patientValue.coerceIn(config.minValue, config.maxValue)
                        }
                        val patientX = if (range <= 0.0) {
                            0f
                        } else {
                            ((valueForIndicator - config.minValue) / range).toFloat() * barWidth
                        }
                        drawLine(
                            color = Color.White,
                            start = Offset(patientX, 0f),
                            end = Offset(patientX, barHeight),
                            strokeWidth = 4.dp.toPx() // Increased from 2.dp to 4.dp for better visibility
                        )

                        // Draw min/max labels
                        val minText = config.valueFormat(config.minValue)
                        val maxText = config.valueFormat(config.maxValue)
                        val minTextLayout = textMeasurer.measure(AnnotatedString(minText))
                        val maxTextLayout = textMeasurer.measure(AnnotatedString(maxText))
                        
                        drawText(
                            textMeasurer = textMeasurer,
                            text = minText,
                            style = TextStyle(color = Color.Black, fontSize = 10.sp),
                            topLeft = Offset(4.dp.toPx(), (barHeight - minTextLayout.size.height) / 2)
                        )
                        drawText(
                            textMeasurer = textMeasurer,
                            text = maxText,
                            style = TextStyle(color = Color.Black, fontSize = 10.sp),
                            topLeft = Offset(barWidth - maxTextLayout.size.width - 4.dp.toPx(), (barHeight - maxTextLayout.size.height) / 2)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Patient's value text
                Text(
                    text = "Patient's Value: ${config.valueFormat(patientValue)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalTextApi::class)
private fun DrawScope.drawZone(
    color: Color,
    zoneMin: Double,
    zoneMax: Double,
    overallMin: Double,
    overallMax: Double,
    barWidth: Float,
    barHeight: Float
) {
    val startX = ((zoneMin - overallMin) / (overallMax - overallMin)).toFloat() * barWidth
    val endX = ((zoneMax - overallMin) / (overallMax - overallMin)).toFloat() * barWidth
    drawRect(
        color = color,
        topLeft = Offset(startX, 0f),
        size = Size(endX - startX, barHeight)
    )
}

