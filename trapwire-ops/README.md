# Trapwire Android Ops Kit

Small helper kit for the stuff that is annoying to repeat manually:

- build debug APK
- install on every connected Android device
- inspect USB / wireless ADB devices
- enable classic `adb tcpip 5555`
- connect to wireless ADB endpoints
- publish a debug APK to GitHub Releases manually
- configure GitHub Actions to reuse your local debug signing key
- apply a lower-prompt in-app APK updater patch

The scripts are Windows-first because this project/devices were being handled from Windows paths.

## Quick start

Unzip this folder anywhere, then from PowerShell:

```powershell
cd C:\Users\paul\Documents\.projects\sp.temp-main
.\trapwire-ops\scripts\windows\devices.ps1
.\trapwire-ops\scripts\windows\build-debug.ps1
.\trapwire-ops\scripts\windows\install-all-usb.ps1
```

Or copy the `trapwire-ops` folder from this ZIP into the repo root and use the `.bat` wrappers by double-clicking / running them.

## Prerequisites

- Android SDK platform-tools available on PATH (`adb`)
- Java 17+ for Gradle
- PowerShell 5+ or PowerShell 7+
- Optional: GitHub CLI (`gh`) for release publishing and secret setup

## Important Android update reality check

A normal personal Android app **cannot silently replace itself**. Android requires at least the system install confirmation, and sometimes one-time permission for “install unknown apps.”

This kit gets you close to the minimum prompt path:

1. App detects a newer APK from the `debug-latest` GitHub Release.
2. App downloads the APK.
3. Android package installer opens.
4. You confirm install.

For true zero-touch install across five devices, you need one of these:

- ADB connected devices: `install-all-usb.ps1`
- Root/system app
- Device-owner / MDM management
- Play/App Distribution style managed deployment

## Recommended setup

1. Install the current APK on all devices over USB once:

```powershell
.\trapwire-ops\scripts\windows\install-all-usb.ps1
```

2. Configure GitHub Actions signing so GitHub-built APKs can update the locally installed APK:

```powershell
.\trapwire-ops\scripts\windows\configure-github-debug-signing.ps1 -Repo paziske/sp.temp-main
```

3. Copy or apply the workflow patch from:

```text
trapwire-ops/patches/debug-workflow-keystore-step.yml
```

4. Apply the lower-prompt updater patch:

```powershell
.\trapwire-ops\scripts\windows\apply-lower-prompt-updater.ps1
```

5. Push to GitHub. Devices can update from inside the app after the release publishes.

## Script map

| Script | Purpose |
|---|---|
| `devices.ps1` | Show connected ADB devices with model, Android version, and Wi-Fi IP. |
| `build-debug.ps1` | Build `android/app/build/outputs/apk/debug/app-debug.apk`. |
| `install-all-usb.ps1` | Build, then install APK on every USB-connected authorized device. |
| `install-all.ps1` | Install on every authorized ADB device, including wireless. |
| `enable-adb-tcpip.ps1` | Run `adb tcpip 5555` on USB devices and print `adb connect` targets. |
| `connect-wireless.ps1` | Connect to explicit wireless endpoints or endpoints in `config/wireless-targets.txt`. |
| `pair-wireless.ps1` | Run `adb pair host:port code` for Android Wireless Debugging. |
| `start-app.ps1` | Launch Trapwire on connected devices. |
| `clear-app-data.ps1` | Clear Trapwire app data on connected devices. |
| `publish-debug-release.ps1` | Build and upload `trapwire-debug.apk` + metadata to GitHub Release. |
| `configure-github-debug-signing.ps1` | Upload your local debug keystore as GitHub secrets for compatible OTA updates. |
| `apply-lower-prompt-updater.ps1` | Writes manifest/FileProvider/updater code for the lower-prompt APK installer path. |

## Typical repeated workflows

### Install current local build on all USB devices

```powershell
.\trapwire-ops\scripts\windows\install-all-usb.ps1
```

### Try classic wireless ADB after USB plug-in

```powershell
.\trapwire-ops\scripts\windows\enable-adb-tcpip.ps1
# unplug device, then:
.\trapwire-ops\scripts\windows\connect-wireless.ps1 -Targets 192.168.178.30:5555,192.168.178.99:5555
```

### Publish a debug APK manually without waiting for Actions

```powershell
.\trapwire-ops\scripts\windows\publish-debug-release.ps1 -Repo paziske/sp.temp-main
```

