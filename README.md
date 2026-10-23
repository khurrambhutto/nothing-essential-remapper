# Nothing Essential Detector

A free, open-source Android prototype for observing the physical Essential button and recognizing its gestures on a CMF Phone 2 Pro. It does not remap the button or run actions. It is an independent project, not affiliated with Nothing or Essential Remapper.

## Current scope

- Manually start a 15-second capture while the app is visible.
- Display key code, scan code, DOWN / UP, repeat count, and relative capture time.
- Display single, double, and long presses for the CMF test profile: key code `0`, scan code `250`, observed on the connected A001 phone.
- Pass every event through unchanged. Essential Space may still open.
- Stop capture on timeout, leaving the activity, service interruption, or disconnection.
- Keep at most 32 observations in process memory. Clear them on demand; no disk logs or network access.
- Ignore printing keys, virtual input devices, software-keyboard events, volume/power, and common navigation/typing control keys.

Raw capture still reports candidate non-typing hardware events. Only key code `0` with scan code `250` enters gesture recognition. This is a device-specific test mapping, not a universal Nothing button identity. Press only the Essential button during a capture. Firmware that exposes it as a virtual/printing key will be excluded by the current privacy filters; investigate that on the connected phone rather than silently broadening collection.

## Gesture behavior

- A short press is reported as **Single press** 300 ms after release, unless a second press starts before that deadline.
- A second short press starting within that interval produces **Double press** on release. No extra single is produced, even if the second release occurs after the original deadline.
- A hold reaches **Long press** after 600 ms without needing repeated DOWN events. Repeats and the eventual release add no more gestures.
- A short click followed by a hold produces a single plus a long, rather than a double.
- Stopping capture, leaving the activity, clearing results, a canceled key event, or losing the service discards incomplete gestures. The last instant of a capture can contain raw events with no completed gesture.
- Gesture history holds up to 16 results in memory and resets with each capture.

Recognition is a pure Kotlin state machine. Handler callbacks wake it at the next deadline; no polling or background actions are used. Timing uses Android's monotonic uptime clock. Thresholds are fixed in this prototype.

The existing [UI specification](UI-SPEC.md) describes a possible future app. Those features are outside this prototype.

## Stack

Kotlin, Jetpack Compose Material 3, an Android AccessibilityService, and an in-memory StateFlow. Android 12 / API 31 or later, compile SDK 37, target SDK 36, JDK 17. AGP 9.4.1 with built-in Kotlin; Compose compiler plugin 2.4.21; Gradle wrapper 9.7.1 with a pinned distribution checksum. No root, Shizuku, ADB client, database, analytics, or custom fonts in the app.

## Permission model

The accessibility service is protected by Android's `BIND_ACCESSIBILITY_SERVICE` permission. The source manifest requests no app permissions; AndroidX adds an app-specific signature permission for protecting internal receivers during manifest merging. It grants no internet, storage, or screen access. The service declares key-filtering capability, but requests filtering only during capture. It does not request window-content access, screen-event subscriptions, screenshots, gesture injection, or overlays. `onKeyEvent()` always returns `false`, so it never consumes events.

Before opening accessibility settings, the app explains exactly what metadata it displays and asks the user to continue. The service can be turned off in Android settings. It is correctly declared as `isAccessibilityTool="false"`.

## Build and physical-device test

No emulator is needed. APK building, installation, and hardware testing use the connected CMF Phone 2 Pro.

1. Open this folder in Android Studio or set `ANDROID_HOME` to an installed Android SDK. Install SDK platform 37 if needed. Use JDK 17.
2. When ready, build with `./gradlew :app:assembleDebug`.
3. Connect the phone by USB, enable USB debugging, and authorize this computer on the phone.
4. Check `adb devices -l`. If multiple devices are attached, select the intended serial explicitly.
5. Install with `adb -s SERIAL install -r app/build/outputs/apk/debug/app-debug.apk`.
6. Launch **Nothing Essential Detector**. Read the disclosure and enable **Essential button detector** in Android accessibility settings. If Android blocks a sideloaded app's accessibility setting, review the app's provenance before manually allowing restricted settings in its App info menu. The app does not bypass this protection.
7. Return to the detector. Confirm **Service connected**, start capture, then try a single press, two quick presses, and a hold over 600 ms. Check the latest gesture and history. Other keys should not produce gesture results.
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

Version 0.2.0 validation on the same date: `:app:testDebugUnitTest :app:assembleDebug` passed all 19 unit tests and produced a debug APK, installed successfully on the connected A001. Raw key delivery was observed with the previous prototype; single/double/long gesture behavior still needs physical-button confirmation. No emulator or system-package changes were used.

## License

Original project code is licensed under [GPL-3.0-only](LICENSE). Gradle-generated wrapper files retain their own license notices. Dependencies retain their respective licenses.

