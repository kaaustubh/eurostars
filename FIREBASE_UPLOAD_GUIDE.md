# Firebase App Distribution Guide for Sensoria App

This guide will help you upload the Sensoria app to Firebase App Distribution.

## Prerequisites

1. **Firebase CLI installed and authenticated**
   ```bash
   # Install Firebase CLI (if not already installed)
   npm install -g firebase-tools
   
   # Login to Firebase
   firebase login
   ```

2. **Firebase Project Setup**
   - The app is already configured with Firebase project: `sensars-eurostars-a496a`
   - The app ID for Sensoria is: `1:159286426351:android:e9d37b2de426a797eab91a`
   - Package name: `com.sensoria.app`

3. **Configure Testers in Firebase Console**
   - Go to [Firebase Console](https://console.firebase.google.com/)
   - Select your project: `sensars-eurostars-a496a`
   - Navigate to **App Distribution** in the left menu
   - Create tester groups or add individual testers

## Upload Methods

### Method 1: Using the Upload Script (Recommended)

```bash
./upload-to-firebase.sh
```

This script will:
1. Clean the project
2. Build a release APK
3. Upload it to Firebase App Distribution

### Method 2: Manual Gradle Commands

```bash
# Build the release APK
./gradlew clean assembleRelease

# Upload to Firebase App Distribution
./gradlew appDistributionUploadRelease
```

### Method 3: Using Firebase CLI

```bash
# Build the release APK first
./gradlew assembleRelease

# Upload using Firebase CLI
firebase appdistribution:distribute app/build/outputs/apk/release/app-release.apk \
  --app 1:159286426351:android:e9d37b2de426a797eab91a \
  --groups "testers" \
  --release-notes "Sensoria app release"
```

## Configuration

The Firebase App Distribution is configured in `app/build.gradle.kts`:

```kotlin
firebaseAppDistribution {
    appId = "1:159286426351:android:e9d37b2de426a797eab91a"
    releaseNotes = "Sensoria app release"
    groups = "testers"
    // Optional: Add testers directly
    // testers = "email1@example.com,email2@example.com"
}
```

You can customize:
- `releaseNotes`: Change the release notes for each upload
- `groups`: Change the tester group name
- `testers`: Add specific tester emails (comma-separated)

## Troubleshooting

### Authentication Issues
If you get authentication errors:
```bash
firebase login
firebase projects:list  # Verify you can see your projects
```

### App ID Issues
If the app ID is incorrect, check `app/google-services.json` for the correct `mobilesdk_app_id` for package `com.sensoria.app`.

### Signing Issues
Make sure your signing configuration is set up correctly in `app/build.gradle.kts` with the proper keystore file and passwords.

## Next Steps

After uploading:
1. Testers will receive an email invitation (if configured)
2. They can download the app from the Firebase App Distribution dashboard
3. You can track downloads and feedback in the Firebase Console
