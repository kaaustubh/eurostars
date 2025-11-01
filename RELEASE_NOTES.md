# Release Notes

All notable changes to this project will be documented in this file.

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

