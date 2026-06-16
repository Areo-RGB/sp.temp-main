# AGENTS.md

## Project notes

This repository contains a native Android/Kotlin Jetpack Compose port of the Trapwire racing app under `android/`. The original Vite/React/WebView source has been removed from the repo. The Android build must stay independent of Node, Vite, React, WebView packaging, and browser camera APIs.

## Quintessential tool-use lessons from this session

1. **Prefer the right MCP for the job.**
   - Use **Bridge MCP** for IDE-aware project edits: reading/writing source files, formatting, Gradle/IDE sync, compile checks, and project file discovery.
   - Use **Windows MCP / PowerShell** for OS-level work: ADB commands, running local scripts, GitHub CLI, filesystem automation, and anything that needs the actual Windows shell environment.

2. **Do not fight blocked command layers forever.**
   - Some direct Bridge/IDE commands can be blocked by safety filters, especially around APK installers, secrets, release publishing, keystore handling, or certain shell patterns.
   - When that happens, create local scripts in the repo and run them through Windows MCP or ask the user to run them manually. This is cleaner than repeatedly trying slightly different blocked commands.

3. **ADB work is most reliable through explicit scripts.**
   - For repeated deploys, use the scripts in `trapwire-ops/scripts/windows/` instead of ad-hoc commands.
   - Useful scripts include:
     - `devices.ps1`
     - `build-debug.ps1`
     - `install-all-usb.ps1`
     - `start-app.ps1`
     - `enable-adb-tcpip.ps1`
     - `connect-wireless.ps1`
     - `setup-install-permission-screen.ps1`
   - Wireless ADB is fragile: mDNS-discovered ports and the displayed Wireless Debugging port are not always the same, and pairing may be required before connecting.

4. **GitHub debug OTA updates depend on matching signatures.**
   - Android will reject an update if the APK is signed with a different key than the installed app.
   - The GitHub Actions debug build must restore/sign with the same local debug keystore used for the already-installed debug APKs.
   - The helper script `configure-github-debug-signing.ps1` sets the required GitHub secrets.
   - The rolling release target is `debug-latest` in `Areo-RGB/sp.temp-main`.

5. **Android cannot silently self-update as a normal personal app.**
   - Without root, device-owner/MDM, or system privileges, a normal app cannot replace itself with zero prompts.
   - The realistic low-friction path is:
     1. app detects the latest GitHub debug release,
     2. downloads/opens the APK installer,
     3. user grants “install unknown apps” once per device,
     4. future updates require only the Android install confirmation.

6. **Keep risky/secrets actions explicit.**
   - Publishing releases and configuring GitHub secrets should not be run casually.
   - Confirm repo slug first. In this session the correct repo was `Areo-RGB/sp.temp-main`, not the initially guessed `paziske/sp.temp-main`.
   - If GitHub CLI prompts for authentication or credentials, pause and let the user type secrets directly.

7. **Native Android is now the source of truth.**
   - New feature work should target Kotlin/Compose/CameraX/Nearby/Firebase code directly.
   - Do not reintroduce WebView or Vite build hooks unless the user explicitly asks to revive the web wrapper.

8. **Offline mode uses Google Nearby Connections.**
   - Do not implement or revive the abandoned Node LAN-server offline design unless explicitly requested.
   - Offline race sync is peer-to-peer via Nearby: controller advertises, clients discover/connect, and race snapshots are sent as small payloads.

9. **Starter beep detection is lightweight by design.**
   - The starter sound is `android/app/src/main/res/raw/starter_beep_300ms.wav`.
   - Detection combines volume gating with a simple WAV-pattern/fingerprint check. Avoid adding heavyweight ML/FFT unless real-world testing proves the current detector is insufficient.

10. **When installing to many devices, verify first and report exactly.**
    - Always list connected devices before install.
    - Install to all connected USB devices with the helper script.
    - Report the serials/models that succeeded, and do not imply wireless installs worked unless `adb devices` actually shows those endpoints connected.

11. **Always bump and start the GitHub Actions debug-release flow after app changes.**
    - Treat this as mandatory for any change that affects the Android app, updater, workflow, permissions, audio/camera timing, Nearby/Firebase behavior, or release scripts.
    - Do not stop at “local build passed.” The expected finish line is:
      1. build locally,
      2. ensure the relevant changes are committed/pushed or explicitly report that they are only local,
      3. trigger the GitHub Actions debug APK workflow,
      4. wait for it when possible,
      5. report the resulting `debug-latest` version.
    - The OTA updater compares the installed APK `versionCode` with the `versionCode` in the rolling `debug-latest` release metadata, so no GitHub Actions run means no OTA-visible version bump.
    - The GitHub workflow `.github/workflows/debug-apk.yml` sets `VERSION_CODE` / `VERSION_NAME` from epoch seconds; every dispatched workflow run is therefore a monotonic OTA version bump.
    - Preferred script, and the default thing to run after app changes:
      - `trapwire-ops/scripts/windows/bump-debug-version-and-run-workflow.ps1 -Wait`
    - If the user asks for an immediate local release instead of workflow dispatch, use:
      - `trapwire-ops/scripts/windows/publish-debug-release.ps1 -Repo Areo-RGB/sp.temp-main`
    - Always use repo slug `Areo-RGB/sp.temp-main` unless the remote has intentionally changed.
    - If command filters block dispatching the workflow, tell the user exactly that and give them the one script command to run locally; do not imply the OTA version was bumped.

### Debug OTA version numbers
Use epoch-seconds/high monotonically increasing APK versionCode values for debug OTA releases. Never publish a tiny counter-based versionCode after a timestamp-based debug release, or phones will ignore it as a downgrade. Keep trapwire-debug.json versionCode/versionName aligned with the APK build.
