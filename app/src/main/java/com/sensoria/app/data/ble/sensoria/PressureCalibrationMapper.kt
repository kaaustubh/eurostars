package com.sensoria.app.data.ble.sensoria

/**
 * Maps raw Sensoria ADC values (0-1023) to calibrated pressure units.
 * Separates raw data ingestion from unit conversion logic.
 */
object PressureCalibrationMapper {
    
    /**
     * Convert raw ADC value to Pascals.
     * 
     * Protocol spec defines ADC as 10-bit raw (0..1023).
     * Units are calibration-layer logic.
     * 
     * Current provisional scaling:
     * kPa = raw / 10.0
     * Pa = kPa * 1000.0
     * 
     * @param rawAdc Raw 10-bit ADC value
     * @return Calibrated pressure in Pascals
     */
    fun mapRawToPascals(rawAdc: Long): Double {
        // Provisional scaling logic
        return (rawAdc.toDouble() / 10.0) * 1000.0
    }
}
