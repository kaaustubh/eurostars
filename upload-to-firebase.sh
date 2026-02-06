#!/bin/bash

# Script to build and upload Sensoria app to Firebase App Distribution
# Make sure you're authenticated with Firebase CLI: firebase login

set -e

echo "🚀 Building Sensoria app for Firebase App Distribution..."

# Build the release APK
./gradlew clean assembleRelease

echo "✅ Build completed successfully!"

# Upload to Firebase App Distribution
echo "📤 Uploading to Firebase App Distribution..."
./gradlew appDistributionUploadRelease

echo "✅ Upload completed! Check Firebase Console for distribution status."
echo ""
echo "Note: Make sure you have:"
echo "  1. Authenticated with Firebase CLI (run: firebase login)"
echo "  2. Configured tester groups in Firebase Console"
echo "  3. Set up the correct app ID in app/build.gradle.kts"
