package com.sensars.eurostars.ui.components

import androidx.compose.ui.graphics.Color

/**
 * Utility functions for pressure heatmap visualization.
 * 
 * Baseline: ~725 ± 20 (raw sensor value when no pressure applied)
 * Color mapping based on kPa ranges:
 * - Blue: <50 kPa (No risk)
 * - Green: 50-150 kPa (No risk)
 * - Yellow: 150-250 kPa (Monitor)
 * - Orange: 250-400 kPa (Risk increasing)
 * - Red: 400-600+ kPa (Ulcer-risk zone)
 */
object HeatmapUtils {
    // Baseline value from sensors (raw sensor reading when no pressure)
    const val BASELINE_RAW = 725L
    
    // Conversion factor: raw sensor units to kPa
    // This may need calibration based on actual sensor characteristics
    // Assuming linear relationship: kPa = (raw - baseline) * conversion_factor
    private const val RAW_TO_KPA_FACTOR = 0.1f // Adjust based on sensor calibration
    
    /**
     * Convert raw sensor value to kPa.
     * Subtracts baseline to get actual pressure above baseline.
     */
    fun rawToKpa(rawValue: Long): Float {
        val pressureAboveBaseline = (rawValue - BASELINE_RAW).coerceAtLeast(0)
        return pressureAboveBaseline * RAW_TO_KPA_FACTOR
    }
    
    /**
     * Map kPa value to color based on risk zones.
     */
    fun kpaToColor(kpa: Float): Color {
        return when {
            kpa < 50f -> Color(0xFF2196F3)      // Blue - No risk
            kpa < 100f -> Color(0xFF4CAF50)     // Green - No risk
            kpa < 150f -> Color(0xFFFFEB3B)     // Yellow - Monitor
            kpa < 200f -> Color(0xFFFF9800)     // Orange - Risk increasing
            else -> Color(0xFFF44336)           // Red - Ulcer-risk zone
        }
    }
    
    /**
     * Get color for raw sensor value (converts to kPa first).
     */
    fun rawValueToColor(rawValue: Long): Color {
        val kpa = rawToKpa(rawValue)
        return kpaToColor(kpa)
    }
    
    /**
     * Get color intensity/alpha based on pressure (for gradient effects).
     * Returns alpha value between 0.3 and 1.0
     */
    fun getColorAlpha(kpa: Float): Float {
        return when {
            kpa < 50f -> 0.3f + (kpa / 50f) * 0.2f  // 0.3 to 0.5 for blue
            kpa < 150f -> 0.5f + ((kpa - 50f) / 100f) * 0.2f  // 0.5 to 0.7 for green
            kpa < 250f -> 0.7f + ((kpa - 150f) / 100f) * 0.15f  // 0.7 to 0.85 for yellow
            kpa < 400f -> 0.85f + ((kpa - 250f) / 150f) * 0.1f  // 0.85 to 0.95 for orange
            else -> 0.95f + ((kpa - 400f).coerceAtMost(200f) / 200f) * 0.05f  // 0.95 to 1.0 for red
        }.coerceIn(0.3f, 1.0f)
    }
}

