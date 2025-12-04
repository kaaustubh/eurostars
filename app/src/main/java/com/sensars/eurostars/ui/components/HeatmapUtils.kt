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
    // Increased sensitivity for better visibility during testing
    private const val RAW_TO_KPA_FACTOR = 0.5f 
    
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
     * Thresholds lowered for better visibility with current sensor values.
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
            kpa < 10f -> 0.4f + (kpa / 10f) * 0.2f   // 0.4 to 0.6 for blue
            kpa < 30f -> 0.6f + ((kpa - 10f) / 20f) * 0.2f   // 0.6 to 0.8 for green
            kpa < 60f -> 0.8f + ((kpa - 30f) / 30f) * 0.1f   // 0.8 to 0.9 for yellow
            kpa < 100f -> 0.9f + ((kpa - 60f) / 40f) * 0.1f  // 0.9 to 1.0 for orange
            else -> 1.0f                                     // 1.0 for red
        }.coerceIn(0.4f, 1.0f)
    }
}

