package com.sensoria.app.data.ble

/**
 * Enum representing the type of sensor protocol/implementation.
 * Used to route data processing to the appropriate handler.
 */
enum class SensorType {
    /** Current UUID-based sensor implementation (existing sensors) */
    CURRENT,
    
    /** Sensoria sensor using D20 protocol */
    SENSORIA_D20,
    
    /** Sensoria sensor using E20 protocol */
    SENSORIA_E20,
    
    /** Sensoria sensor using Stream V1 protocol (0x5A header) */
    SENSORIA_STREAM_V1
}

