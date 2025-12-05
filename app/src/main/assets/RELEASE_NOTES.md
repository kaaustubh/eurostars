# Release Notes

All notable changes to this project will be documented in this file.

---

## [0.0.6(6)] - 2025-12-05

### New Features
- **Walk Mode Heatmap**: Real-time visualization of plantar pressure with radial gradients
  - Heatmap dynamically reflects pressure intensity (kPa)
  - Accurate taxel mapping for left and right feet
- **Calibration Screens**: Dedicated screens for calibrating left and right foot sensors
- **Session Management**:
  - "Stop Session" dialog with options to Upload or Discard data
  - Sessions are now uploaded in **CSV format** for easier analysis
- **Session History**: Added "Gait Analysis" tab showing session history
  - History accurately syncs with cloud storage
  - Retry logic for failed uploads
- **Anonymous Authentication**: Secure patient data uploads without requiring email login

### Improvements
- **BLE Optimization**: Connection priority set to LOW_POWER for improved battery life
- **Connection Reliability**: Enhanced auto-reconnection and stability logic
- **Data Accuracy**: Heatmap colors are calibrated to specific pressure thresholds (Blue to Red)

### Technical
- Migrated session data upload from JSON to CSV
- Refactored session history to use Firebase Storage as the single source of truth
- Implemented anonymous Firebase authentication for patient role

---

## [0.0.5(5)] - 2025-11-24

### New Features
- **RSSI Indicator**: Added Received Signal Strength Indicator (RSSI) display for paired sensors
  - RSSI shown in dBm units in sensor pairing sections
  - Displays both in collapsed header view and expanded details section
  - RSSI captured during device scanning and stored with pairing information
  - Periodic RSSI updates for connected sensors (every 5 seconds)
  - Helps users monitor signal strength and connection quality

### Improvements
- **Accelerometer and Gyroscope Data Fix**: Fixed issue where only X-axis accelerometer data was visible, then X and Y, but not Z
  - Implemented IMU state tracking to accumulate X, Y, Z components for complete samples
  - Fixed accelerometer Z-axis UUID from F100B003 to F100B000 to match sensor hardware
  - Enabled sequential descriptor writes for IMU characteristics to ensure all axes receive data
  - All three axes (X, Y, Z) now properly receive and display data for both accelerometer and gyroscope
- **Individual Sensor Unpairing**: Fixed issue where individual "Unpair" button didn't work correctly
  - Corrected order of operations to clear pairing data first, then disconnect sensor
  - Ensures UI updates immediately when unpairing a single sensor
  - Individual unpair buttons now work as expected, matching "Clear all pairings" behavior
- **Walk Mode UI Refinement**: Removed redundant instruction text from Walk Mode screen
  - Cleaned up UI by removing duplicate "Press Start to begin a recording session" text
  - Improved screen clarity with less redundant information

### Technical
- Implemented ImuState data class to track X, Y, Z components for accelerometer and gyroscope
- Fixed accelerometer Z-axis UUID in BleUuids.kt (F100B000 instead of F100B003)
- Enabled sequential descriptor writes for IMU characteristics via descriptorWriteQueue
- Added RSSI to PairedSensor data structure and PairingRepository
- Implemented RSSI handler registration in SensorConnectionManager
- Fixed RSSI callback scope issue in onRssiRead handler
- Enhanced BleRepository to capture RSSI from scan results
- Added periodic RSSI reading for connected sensors via readRemoteRssi()
- Corrected clearPairing() method order to ensure proper UI state updates
- Set version to 0.0.5(5) with versionCode 5

---

## [0.0.4(4)] - 2025-11-21

### New Features
- **Auto-Reconnect Sensors**: Sensors automatically reconnect on app restart if pairing information exists
  - Automatically attempts to reconnect to previously paired sensors when app launches
  - Works seamlessly in the background without user intervention
  - Handles cases where sensors are temporarily unavailable (off, out of range) gracefully

### Improvements
- **Real-Time Connection Status Updates**: Pairing tab now automatically updates when sensors disconnect or reconnect
  - UI reflects connection status changes immediately without requiring tab switching
  - Connection state updates in real-time when sensors are switched off, disconnected, or go out of range
  - Bluetooth state monitoring ensures accurate connection status display
- **All 18 Taxels Data Reception**: Fixed issue where only taxel 0 was receiving data
  - Implemented sequential descriptor write queue to properly enable notifications for all 18 pressure taxels
  - All taxels now successfully receive data when pressure is applied
  - Improved BLE notification enablement reliability with proper callback coordination
- **Reduced Log Noise**: Removed verbose logging for cleaner development experience
  - Removed service discovery logs, descriptor write success logs, and data reception logs
  - Kept only essential error and warning logs for debugging
  - Significantly reduced log output while maintaining error visibility

### Technical
- Added disconnection handler registration system for GATT connections
- Implemented sequential descriptor write queue with callback-based processing
- Enhanced SensorConnectionManager with disconnection handler management
- Improved BleRepository to notify SensorConnectionManager about disconnections
- Fixed UI observation to properly recompose when connection states change
- Added periodic Bluetooth state checking to trigger UI updates
- Improved connection state management for both pairing and auto-reconnect flows
- Set version to 0.0.4(4) with versionCode 4

---

## [0.0.3(3)] - 2025-11-15

### New Features
- **Walk Mode UI**: Redesigned Walk Mode screen with start/stop controls, session status tracking, and dual foot heatmap placeholders
- **Sensor Pairing Validation**: Walk Mode Start button now validates that both left and right foot sensors are paired before allowing session start
- **BLE Sensor Pairing**: Complete Bluetooth Low Energy (BLE) pairing flow for left and right foot sensors
  - Device scanning and discovery interface
  - Connection management with device information retrieval
  - Persistent pairing storage using DataStore
  - Pairing status display in Pairing tab
- **Patient Dashboard Navigation**: Implemented tab-based navigation for patient dashboard (Walk Mode, Gait Analysis, Pairing)
- **Auto-Generated Patient IDs**: Clinician app now automatically generates sequential patient IDs with leading zeros preserved
- **Release Notes Display**: Added release notes viewer accessible from patient Pairing tab and clinician settings

### Improvements
- **Walk Mode UX**: Dynamic status messages indicating which sensors need pairing before starting a session
- **Pairing Flow**: Streamlined BLE pairing process with prerequisite checks (Bluetooth status, permissions)
- **Error Handling**: Enhanced Bluetooth error handling with user-friendly messages and settings deep-linking
- **Patient ID Management**: Automatic patient ID generation prevents manual entry errors and ensures sequential numbering
- **UI Polish**: Improved spacing, margins, and visual hierarchy across pairing and walk mode screens

### Technical
- Integrated Firebase Crashlytics for crash reporting and analytics
- Created BleRepository for abstracted BLE operations (scanning, connecting, permission checks)
- Implemented PairingRepository for persistent sensor pairing storage
- Added BluetoothPairingViewModel for managing BLE pairing state and operations
- Created SensorGattManager for GATT notification setup across all sensor characteristics
- Added SensorDataStreams for handling real-time sensor data (pressure, accelerometer, gyroscope, temperature, time)
- Enhanced PatientsViewModel with nextPatientId calculation logic
- Updated PatientsRepository to support patient ID overwriting for consistency
- Improved navigation structure with PatientDashboardNavHost for nested tab navigation
- Set version to 0.0.3(3) with versionCode 3

---

## [0.0.2(2)] - 2025-11-01

### New Features
- **Patient Login**: Implemented patient authentication screen where patients can log in using their Clinical Study ID
- **Smart ID Matching**: Automatic numeric ID matching (e.g., entering "1" matches patient ID "0001")
- **Patient Session Management**: Patients remain logged in after app restart with persistent session storage
- **Patient Logout**: Added logout functionality for patient role with confirmation dialog
- **Calendar Date Picker**: Replaced text input with interactive calendar picker for "Date of last Ulcer" field

### Improvements
- **Date Input UX**: Date picker automatically formats input to YYYY/MM/DD format
- **Date Validation**: Date picker prevents selection of future dates to ensure data accuracy
- **Mobile-Optimized Patient Screens**: Patient login and home screens designed for optimal mobile compatibility
- **Error Handling**: Improved error messages for patient authentication failures and network issues

### Technical
- Added PatientAuthViewModel for patient authentication logic and session management
- Implemented PatientAuthViewModelFactory for dependency injection
- Enhanced navigation to include PATIENT_HOME route
- Updated PatientHomeScreen with logout functionality
- Improved patient ID lookup with fallback mechanism for numeric matching
- Set version to 0.0.2(2) with versionCode 2

---

## [0.0.2] - 2025-11-01

### New Features
- Added Neuropathic Leg field: Dropdown selection (Right, Left, Both) to identify which leg(s) are affected
- Added Date of last Ulcer field: Date input with format YYYY/MM/DD, with option to select "N/A"
- Added Ulcer Active field: Dropdown selection (Yes, No, Healed, N/A) to track ulcer status

### Improvements
- Patient creation form is now vertically scrollable to accommodate all fields
- Removed "Origin of pain" section from patient card display for cleaner interface

### Technical
- Updated PatientData model to include new medical fields
- Enhanced PatientsRepository to persist new fields to Firestore
- Improved form validation for date format (YYYY/MM/DD)
- Configured release signing for production builds
- Set version to 0.0.2

---

## [0.0.1] - 2025-10-31

### New Features
- Implemented persistent clinician login: Clinicians remain logged in after app restart
- Added splash screen with automatic role-based navigation
- Role selector screen for choosing between Clinician and Patient roles

### Improvements
- Added logout button in clinician tab bar with confirmation dialog
- Fixed login race condition by ensuring role persistence completes before navigation
- Improved authentication flow with Firebase Auth integration

### Technical
- Implemented SessionRepository and RoleRepository for persistent storage
- Added SplashRoute to handle automatic navigation based on stored role
- Enhanced AuthViewModel with role persistence on successful login
- Added logout functionality that clears both Firebase session and stored role
- Configured Firebase authentication and Firestore integration
- Set version to 0.0.1

---
