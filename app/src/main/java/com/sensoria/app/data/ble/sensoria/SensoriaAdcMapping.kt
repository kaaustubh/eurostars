package com.sensoria.app.data.ble.sensoria

/**
 * Maps Sensoria ADC channels (s0-s7) to taxel indices (0-17).
 * 
 * Sensoria sensors have 8 analog channels (s0 through s7) that need to be mapped
 * to the 18 taxel positions used in the application.
 * 
 * This mapping is sensor-specific and may need to be configured per sensor model.
 * Default mapping assumes a linear or configured arrangement.
 */
object SensoriaAdcMapping {
    
    /**
     * Default mapping: maps 8 ADC channels to 18 taxel indices.
     * Channels that don't map to a taxel will be ignored.
     * 
     * This is a placeholder mapping - should be configured based on actual sensor layout.
     * For sensors with fewer than 18 taxels, some indices will be unused.
     * 
     * Format: Map<ADC channel index (0-7), List of taxel indices>
     * A channel can map to multiple taxels if needed, or to a single taxel.
     */
    private val defaultMapping: Map<Int, List<Int>> = mapOf(
        // Example mapping - adjust based on actual sensor configuration
        0 to listOf(0),   // s0 -> taxel 0
        1 to listOf(1),   // s1 -> taxel 1
        2 to listOf(2),   // s2 -> taxel 2
        3 to listOf(3),   // s3 -> taxel 3
        4 to listOf(4),   // s4 -> taxel 4
        5 to listOf(5),   // s5 -> taxel 5
        6 to listOf(6),   // s6 -> taxel 6
        7 to listOf(7)    // s7 -> taxel 7
        // Note: Taxels 8-17 are not mapped by default
        // This can be extended if the sensor has more channels or different mapping
    )
    
    /**
     * Get taxel indices for a given ADC channel.
     * 
     * @param adcChannel ADC channel index (0-7, corresponding to s0-s7)
     * @return List of taxel indices that this channel maps to, or empty list if not mapped
     */
    fun getTaxelIndices(adcChannel: Int): List<Int> {
        if (adcChannel < 0 || adcChannel > 7) {
            android.util.Log.w("SensoriaAdcMapping", "Invalid ADC channel: $adcChannel (must be 0-7)")
            return emptyList()
        }
        return defaultMapping[adcChannel] ?: emptyList()
    }
    
    /**
     * Get all mapped taxel indices (all taxels that have an ADC channel mapping).
     * 
     * @return Set of all taxel indices that are mapped from ADC channels
     */
    fun getAllMappedTaxels(): Set<Int> {
        return defaultMapping.values.flatten().toSet()
    }
    
    /**
     * Check if an ADC channel is mapped to any taxel.
     * 
     * @param adcChannel ADC channel index (0-7)
     * @return true if the channel is mapped, false otherwise
     */
    fun isChannelMapped(adcChannel: Int): Boolean {
        return adcChannel in 0..7 && defaultMapping.containsKey(adcChannel)
    }
    
    /**
     * Configure a custom mapping for ADC channels to taxels.
     * This allows runtime configuration if needed.
     * 
     * @param mapping Map of ADC channel index to list of taxel indices
     */
    fun configureMapping(mapping: Map<Int, List<Int>>) {
        // For now, this is a placeholder - in a full implementation,
        // you might want to store this in preferences or a config file
        android.util.Log.d("SensoriaAdcMapping", "Custom mapping configured: $mapping")
        // TODO: Implement persistent storage of custom mapping if needed
    }
}

