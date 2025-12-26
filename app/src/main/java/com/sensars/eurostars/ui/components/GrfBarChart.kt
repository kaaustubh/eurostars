package com.sensars.eurostars.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.*
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sensars.eurostars.ui.theme.SensarsEurostarsTheme

@OptIn(ExperimentalTextApi::class)
@Composable
fun GrfBarChart(
    grfValue: Double?,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    
    val title = "Ground Reaction Force (Normalized to Body Weight)"
    val minValue = 0.0
    val maxValue = 2.0
    val greenMin = 1.2
    val greenMax = 1.4
    val yellowMin1 = 1.0
    val yellowMax1 = 1.2
    val yellowMin2 = 1.4
    val yellowMax2 = 1.6

    Column(modifier = modifier.padding(16.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        if (grfValue == null) {
            Text(
                text = "No GRF data available for this session.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 16.dp)
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .background(Color.LightGray, RoundedCornerShape(4.dp))
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val barWidth = size.width
                    val barHeight = size.height

                    // Draw color zones
                    drawZone(Color.Red, 0.0, yellowMin1, minValue, maxValue, barWidth, barHeight)
                    drawZone(Color.Yellow, yellowMin1, yellowMax1, minValue, maxValue, barWidth, barHeight)
                    drawZone(Color.Green, greenMin, greenMax, minValue, maxValue, barWidth, barHeight)
                    drawZone(Color.Yellow, yellowMin2, yellowMax2, minValue, maxValue, barWidth, barHeight)
                    drawZone(Color.Red, yellowMax2, maxValue, minValue, maxValue, barWidth, barHeight)

                    // Draw patient's value indicator
                    val patientX = ((grfValue - minValue) / (maxValue - minValue)).toFloat() * barWidth
                    drawLine(
                        color = Color.White,
                        start = Offset(patientX, 0f),
                        end = Offset(patientX, barHeight),
                        strokeWidth = 2.dp.toPx()
                    )

                    // Draw min/max labels
                    val minText = "0.0"
                    val maxText = "2.0"
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

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Patient's Value: ${String.format("%.2f", grfValue)}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
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

@Preview(showBackground = true)
@Composable
fun GrfBarChartPreview() {
    SensarsEurostarsTheme {
        GrfBarChart(
            grfValue = 1.35
        )
    }
}

