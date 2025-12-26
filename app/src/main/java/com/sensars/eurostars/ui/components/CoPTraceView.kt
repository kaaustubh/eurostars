package com.sensars.eurostars.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.sensars.eurostars.R

@Composable
fun CoPTraceView(
    isLeftFoot: Boolean,
    trace: List<android.graphics.PointF>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = if (isLeftFoot) "Left Foot CoP Trace" else "Right Foot CoP Trace",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        Box(
            modifier = Modifier
                .size(200.dp, 300.dp)
                .padding(8.dp)
        ) {
            // Draw foot outline
            Image(
                painter = painterResource(id = R.drawable.foot_outline_left),
                contentDescription = if (isLeftFoot) "Left foot outline" else "Right foot outline",
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (!isLeftFoot) {
                            Modifier.scale(scaleX = -1f, scaleY = 1f)
                        } else {
                            Modifier
                        }
                    ),
                contentScale = ContentScale.Fit,
                alignment = Alignment.Center
            )
            
            // Overlay CoP trace
            Canvas(modifier = Modifier.fillMaxSize()) {
                if (trace.isNotEmpty()) {
                    val path = Path()
                    val size = this.size
                    
                    // Start point
                    val firstPoint = trace.first()
                    path.moveTo(firstPoint.x * size.width, firstPoint.y * size.height)
                    
                    // Draw lines between subsequent points
                    for (i in 1 until trace.size) {
                        val point = trace[i]
                        path.lineTo(point.x * size.width, point.y * size.height)
                    }
                    
                    // Draw the path
                    drawPath(
                        path = path,
                        color = Color.Red,
                        style = Stroke(width = 2.dp.toPx())
                    )
                    
                    // Draw start and end points
                    drawCircle(
                        color = Color.Green,
                        radius = 4.dp.toPx(),
                        center = androidx.compose.ui.geometry.Offset(
                            trace.first().x * size.width,
                            trace.first().y * size.height
                        )
                    )
                    
                    drawCircle(
                        color = Color.Blue,
                        radius = 4.dp.toPx(),
                        center = androidx.compose.ui.geometry.Offset(
                            trace.last().x * size.width,
                            trace.last().y * size.height
                        )
                    )
                }
            }
        }
    }
}

