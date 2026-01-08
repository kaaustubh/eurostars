# Release Notes

All notable changes to this project will be documented in this file.

---

## [0.0.1] - 2025-01-XX

### New Features
- **Sensoria Sensor Support**: Added support for Sensoria D20 and E20 sensor protocols
  - Automatic protocol detection during pairing
  - Support for both D20 (8 ADC channels) and E20 (6 ADC channels) protocols
- **Dynamic Data Display**: Sensor data display adapts based on sensor type
  - Current sensors: 18 pressure taxels
  - Sensoria D20: 8 ADC channels
  - Sensoria E20: 6 ADC channels
- **Sensor Type Display**: Shows detected sensor type in pairing screen details

### Technical Improvements
- **Package Refactoring**: Migrated from `com.sensars.eurostars` to `com.sensoria.app`
- **App Rebranding**: Changed app name to "Sensoria"
- **Sensoria Protocol Parsers**: Implemented D20 and E20 protocol parsers with bit-packed data handling
- **Protocol Detector**: Automatic protocol detection from GATT services and packet structure
- **Data Handler Integration**: Sensoria sensors integrate seamlessly with existing data flow

### Bug Fixes
- Fixed package import errors across the codebase
- Resolved compilation errors in navigation and protocol detection
- Improved BLE scanning to detect all devices (not just UUID-filtered)

### Improvements
- **Walking Mode Support for Sensoria Sensors**: Walking mode now fully supports Sensoria D20 and E20 sensors
  - Sensoria sensor data is now captured during walking sessions
  - CSV files are generated with correct channel counts (6 for E20, 8 for D20, 18 for CURRENT sensors)
  - CSV format adapts automatically based on connected sensor types
- **Gait Analysis Tab**: Limited to session history view only
  - Navigation to gait analysis detail screen is disabled (feature not yet implemented)
  - Users can view session history but cannot access detail view

---
