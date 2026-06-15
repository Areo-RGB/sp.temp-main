# Trapwire Racing

Native Android/Kotlin Jetpack Compose racing timer app.

## Source of truth

The app lives under android/ and does not depend on the removed Vite/React/WebView prototype.

Core Android pieces:

- Jetpack Compose UI
- CameraX trapwire detection
- native microphone starter-beep recognition
- Firebase Realtime Database online mode
- Google Nearby Connections offline mode
- GitHub debug-latest updater

## Build locally

cd android
gradle :app:assembleDebug

APK output: android/app/build/outputs/apk/debug/app-debug.apk

## Useful scripts

trapwire-ops/scripts/windows/devices.ps1
trapwire-ops/scripts/windows/build-debug.ps1
trapwire-ops/scripts/windows/install-all-usb.ps1
trapwire-ops/scripts/windows/start-app.ps1

Trigger GitHub Actions debug release/version bump:

trapwire-ops/scripts/windows/bump-debug-version-and-run-workflow.ps1 -Wait
