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

---
