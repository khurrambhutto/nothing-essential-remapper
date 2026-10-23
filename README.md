# Nothing Essential Detector

A free, open-source Android prototype for observing the physical Essential button on a CMF Phone 2 Pro. It does not remap the button. It is an independent project, not affiliated with Nothing or Essential Remapper.

## Current scope

- Manually start a 15-second capture while the app is visible.
- Display key code, scan code, DOWN / UP, repeat count, and relative capture time.
- Pass every event through unchanged. Essential Space may still open.
- Stop capture on timeout, leaving the activity, service interruption, or disconnection.
- Keep at most 32 observations in process memory. Clear them on demand; no disk logs or network access.
- Ignore printing keys, virtual input devices, software-keyboard events, volume/power, and common navigation/typing control keys.

There is no hardcoded Essential key code. A capture reports candidate non-typing hardware events, not a verified button identity. Press only the Essential button during a capture. Firmware that exposes it as a virtual/printing key will be excluded by the current privacy filters; investigate that on the connected phone rather than silently broadening collection.

The existing [UI specification](UI-SPEC.md) describes a possible future app. Those features are outside this prototype.

## Stack

Kotlin, Jetpack Compose Material 3, an Android AccessibilityService, and an in-memory StateFlow. Android 12 / API 31 or later, compile SDK 37, target SDK 36, JDK 17. AGP 9.4.1 with built-in Kotlin; Compose compiler plugin 2.4.21; Gradle wrapper 9.7.1 with a pinned distribution checksum. No root, Shizuku, ADB client, database, analytics, or custom fonts in the app.

## Permission model

The accessibility service is protected by Android's `BIND_ACCESSIBILITY_SERVICE` permission. The source manifest requests no app permissions; AndroidX adds an app-specific signature permission for protecting internal receivers during manifest merging. It grants no internet, storage, or screen access. The service declares key-filtering capability, but requests filtering only during capture. It does not request window-content access, screen-event subscriptions, screenshots, gesture injection, or overlays. `onKeyEvent()` always returns `false`, so it never consumes events.

Before opening accessibility settings, the app explains exactly what metadata it displays and asks the user to continue. The service can be turned off in Android settings. It is correctly declared as `isAccessibilityTool="false"`.

## Build and physical-device test later

No emulator is needed. APK building, installation, and hardware testing are reserved for when the CMF Phone 2 Pro is connected.

1. Open this folder in Android Studio or set `ANDROID_HOME` to an installed Android SDK. Install SDK platform 37 if needed. Use JDK 17.
2. When ready, build with `./gradlew :app:assembleDebug`.
3. Connect the phone by USB, enable USB debugging, and authorize this computer on the phone.
4. Check `adb devices -l`. If multiple devices are attached, select the intended serial explicitly.
5. Install with `adb -s SERIAL install -r app/build/outputs/apk/debug/app-debug.apk`.
6. Launch **Nothing Essential Detector**. Read the disclosure and enable **Essential button detector** in Android accessibility settings. If Android blocks a sideloaded app's accessibility setting, review the app's provenance before manually allowing restricted settings in its App info menu. The app does not bypass this protection.
7. Return to the detector. Confirm **Service connected**, start capture, then press and release the Essential button. Repeat with a hold to inspect repeated DOWN events.
8. Confirm ordinary power and volume behavior remains unchanged. Confirm capture stops when leaving the app and after 15 seconds.

Host unit checks, which do not run an emulator or produce an installable APK:

```sh
./gradlew :app:testDebugUnitTest
```

## If no event arrives

Do not assume accessibility access is broken or change packages automatically. The following need to be distinguished on the actual phone:

- Accessibility service not connected, or capture already stopped.
- Another service requesting key filtering. Android does not guarantee delivery to every enabled service.
- Nothing OS intercepting the Essential button before this service sees it.
- The button opening Essential Space and sending this activity to the background, which intentionally stops capture.
- A firmware-specific key mapping excluded by the prototype's privacy filter.

First record the phone's Android / Nothing OS version and inspect the stock behavior. Community documentation names `com.nothing.ntessentialspace` and `com.nothing.ntessentialrecorder`, but those names and their current enabled states must be verified on this phone before considering any change. This prototype contains no package-disabling or shell-execution code. Any later reversible experiment must include restoring the original package states; do not uninstall system packages.

## Research and verification

See [docs/RESEARCH.md](docs/RESEARCH.md) for the Android contracts, source links, and remaining device-specific uncertainties. Unit tests cover capture boundaries and privacy gating. Successful unit checks cannot establish that CMF firmware delivers the Essential key. That requires the physical phone.

Initial validation on 2026-10-23: `:app:testDebugUnitTest` passed all six tests and compiled the Android/Kotlin sources. XML permission-boundary checks also passed. No emulator, installable APK, or device test was run.

## License

Original project code is licensed under [GPL-3.0-only](LICENSE). Gradle-generated wrapper files retain their own license notices. Dependencies retain their respective licenses.

