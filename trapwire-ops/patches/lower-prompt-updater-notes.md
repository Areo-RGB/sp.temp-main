# Lower-prompt updater patch

Run this from the repo root:

```powershell
.\trapwire-ops\scripts\windows\apply-lower-prompt-updater.ps1
```

It applies three changes:

1. Writes `android/app/src/main/res/xml/trapwire_file_paths.xml`
2. Adds an AndroidX `FileProvider` inside `<application>` in `AndroidManifest.xml`
3. Replaces `DebugUpdater.kt` so the app downloads the APK internally and opens Android's package installer

Android still requires user confirmation for a normal app install. This is the least-prompt route without root/device-owner/MDM.

After applying:

```powershell
.\trapwire-ops\scripts\windows\build-debug.ps1
.\trapwire-ops\scripts\windows\install-all-usb.ps1
```

On each device, the first OTA update may ask you to allow installs from Trapwire. Run this to jump directly to that settings screen on all connected devices:

```powershell
.\trapwire-ops\scripts\windows\setup-install-permission-screen.ps1
```
