package com.sensars.eurostars.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.sensars.eurostars.R

/**
 * Taxel position data structure.
 * Positions are normalized (0.0 to 1.0) relative to foot outline.
 */
data class TaxelPosition(
    val index: Int, // 0-17 (T1-T18, but 0-indexed)
    val x: Float,   // 0.0 (left) to 1.0 (right)
    val y: Float    // 0.0 (toes) to 1.0 (heel)
)

/**
 * Taxel positions for left foot (mirror for right foot).
 * Based on the provided sensor layout image with 18 taxels (T1-T18).
 * Left foot: x=0.0 is lateral (pinky) side, x=1.0 is medial (big toe) side.
 * y=0.0 is toes (top), y=1.0 is heel (bottom).
 *
 * Layout aligned visually with the provided foot outline image.
 * Corrected specific spread-out taxels (T4, T9, T10, T11, T13, T14, T15, T18).
 */
private val LEFT_FOOT_TAXEL_POSITIONS = listOf(
    // Top row - Toe area (T1-T4)
    TaxelPosition(0, 0.72f, 0.19f),  // T1 - Medial (Big Toe)
    TaxelPosition(1, 0.58f, 0.21f),  // T2
    TaxelPosition(2, 0.44f, 0.24f),  // T3
    TaxelPosition(3, 0.33f, 0.28f),  // T4 - Lateral (Pinky Toe) - Moved Right (Inwards)
    
    // Second row - Ball of foot (T5-T8)
    TaxelPosition(4, 0.68f, 0.33f),  // T5 - Medial
    TaxelPosition(5, 0.55f, 0.33f),  // T6
    TaxelPosition(6, 0.42f, 0.35f),  // T7
    TaxelPosition(7, 0.29f, 0.39f),  // T8 - Lateral
    
    // Mid-foot Lateral Edge (T9-T10)
    TaxelPosition(8, 0.29f, 0.47f),  // T9 - Moved Right (Inwards)
    TaxelPosition(9, 0.29f, 0.55f),  // T10 - Moved Right (Inwards)
    
    // Heel Upper Section (T11-T12)
    TaxelPosition(10, 0.34f, 0.66f), // T11 - Lateral Heel - Moved Right (Inwards)
    TaxelPosition(11, 0.64f, 0.66f), // T12 - Medial Heel
    
    // Heel Middle Section (T13-T14)
    TaxelPosition(12, 0.34f, 0.75f), // T13 - Lateral Heel - Moved Right (Inwards)
    TaxelPosition(13, 0.61f, 0.75f), // T14 - Medial Heel - Moved Left (Inwards)
    
    // Heel Bottom Cluster (Cross/X Pattern: T15, T16, T17, T18)
    TaxelPosition(14, 0.48f, 0.83f), // T15 - Top of X - Moved Down (Tightened)
    TaxelPosition(15, 0.60f, 0.86f), // T16 - Medial of X (Right)
    TaxelPosition(16, 0.48f, 0.90f), // T17 - Bottom of X
    TaxelPosition(17, 0.38f, 0.86f)  // T18 - Lateral of X (Left) - Moved Right (Inwards)
)

/**
 * Get taxel positions for the specified foot side.
 * Right foot is mirrored horizontally.
 */
private fun getTaxelPositions(isLeftFoot: Boolean): List<TaxelPosition> {
    return if (isLeftFoot) {
        LEFT_FOOT_TAXEL_POSITIONS
    } else {
        // Mirror horizontally for right foot
        LEFT_FOOT_TAXEL_POSITIONS.map { 
            TaxelPosition(it.index, 1.0f - it.x, it.y) 
        }
    }
}

// Foot outline is now drawn using an image asset instead of programmatically

/**
 * Draw a taxel circle at the specified position with the given color and alpha.
 */
private fun DrawScope.drawTaxel(
    position: TaxelPosition,
    color: Color,
    alpha: Float,
    size: Size,
    taxelRadius: Float
) {
    val x = position.x * size.width
    val y = position.y * size.height
    
    // For a heatmap effect, we use a radial gradient that fades out
    // Use the calculated alpha to reflect pressure intensity
    val brush = Brush.radialGradient(
        colors = listOf(
            color.copy(alpha = alpha * 0.9f), // Center: High opacity (scaled by calculated alpha)
            color.copy(alpha = alpha * 0.5f), // Mid: Medium opacity
            color.copy(alpha = 0.0f)  // Edge: Transparent
        ),
        center = Offset(x, y),
        radius = taxelRadius * 2.5f // Larger radius for blending overlap
    )
    
    drawCircle(
        brush = brush,
        radius = taxelRadius * 2.5f, // Larger radius to match gradient
        center = Offset(x, y)
    )
}

/**
 * Foot heatmap composable that displays 18 taxels with pressure-based colors.
 * Uses an image asset for the foot outline.
 * 
 * @param pressureData Map of taxel index (0-17) to raw sensor value
 * @param isLeftFoot Whether this is the left foot (true) or right foot (false)
 * @param modifier Modifier for the composable
 */
@Composable
fun FootHeatmap(
    pressureData: Map<Int, Long>,
    isLeftFoot: Boolean,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        // Draw foot outline image
        // For right foot, flip horizontally using scale modifier
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
        
        // Overlay taxels on top of the image
        Canvas(modifier = Modifier.fillMaxSize()) {
            val size = this.size
            val taxelRadius = size.minDimension * 0.03f // 3% of smallest dimension
            
            // Get taxel positions for this foot
            val taxelPositions = getTaxelPositions(isLeftFoot)
            
            // Draw each taxel
            taxelPositions.forEach { position ->
                val rawValue = pressureData[position.index] ?: HeatmapUtils.BASELINE_RAW
                val color = HeatmapUtils.rawValueToColor(rawValue)
                val kpa = HeatmapUtils.rawToKpa(rawValue)
                val alpha = HeatmapUtils.getColorAlpha(kpa)
                
                // Only draw if there's actual pressure (above baseline) or if we want to show baseline
                // For now, always draw but use the calculated alpha
                drawTaxel(
                    position = position,
                    color = color,
                    alpha = alpha,
                    size = size,
                    taxelRadius = taxelRadius
                )
            }
        }
    }
}

