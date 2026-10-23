# Nothing Essential Detector

A free, open-source Android prototype for recognizing the Essential button's gestures on a CMF Phone 2 Pro, with an optional double-press flashlight action. It is an independent project, not affiliated with Nothing or Essential Remapper.

## Current scope

- Manually start a 15-second capture while the app is visible.
- Display key code, scan code, DOWN / UP, repeat count, and relative capture time.
- Display single, double, and long presses for the CMF test profile: key code `0`, scan code `250`, observed on the connected A001 phone.
- Actions are off by default. In this mode, pass every event through unchanged.
- With actions enabled, consume the CMF test key during capture and toggle the flashlight on double press. Single and long presses only display results.
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

## Flashlight experiment

1. Tap **Allow flashlight access** and grant Android's Camera permission. This app uses the torch API without opening a camera or recording images/video.
2. Turn on **Enable actions**, then start capture. Changing the switch stops any existing capture.
3. Double press to turn the flashlight on. Wait for the reported state to settle, then double press again to turn it off. Camera callbacks confirm the state; an unavailable or busy torch does not accept another toggle.
4. Check whether Essential Space stays closed. Consuming events is an experiment on this firmware, not a guarantee that Nothing's system handler is suppressed.
5. Single and long presses should remain diagnostic. Other buttons should retain their normal behavior.

The switch resets off when leaving the activity. Stopping capture, switching actions off, clearing results, or leaving the screen attempts to turn off a light this app last enabled. It leaves a light already on before this app's actions alone. If cleanup fails, the UI directs the user to Android Quick Settings. No root, package disabling, background actions, or automatic permission grants are used.

For consistent Android input, a consumed DOWN retains the same consumption decision for repeats and its matching UP, including when capture stops during a hold. Filtering can briefly remain enabled to finish that pair, with actions disabled. Unrelated events pass through. Service disconnection clears the pairing state.

The existing [UI specification](UI-SPEC.md) describes a possible future app. Those features are outside this prototype.

## Stack

Kotlin, Jetpack Compose Material 3, an Android AccessibilityService, and an in-memory StateFlow. Android 12 / API 31 or later, compile SDK 37, target SDK 36, JDK 17. AGP 9.4.1 with built-in Kotlin; Compose compiler plugin 2.4.21; Gradle wrapper 9.7.1 with a pinned distribution checksum. No root, Shizuku, ADB client, database, analytics, or custom fonts in the app.

## Permission model

The accessibility service is protected by Android's `BIND_ACCESSIBILITY_SERVICE` permission. The source manifest requests `CAMERA` solely for optional torch control; the user grants it through Android's runtime permission dialog. AndroidX also adds an app-specific signature permission for protecting internal receivers. No internet, storage, or screen-access permissions are requested. Key filtering is requested during capture, with a brief exception to finish an already consumed press/release pair. It does not request window-content access, screen-event subscriptions, screenshots, gesture injection, or overlays. Only the CMF test key is consumed when actions are explicitly enabled; everything else passes through.

Before opening accessibility settings, the app explains exactly what metadata it displays and asks the user to continue. The service can be turned off in Android settings. It is correctly declared as `isAccessibilityTool="false"`.

## Build and physical-device test

No emulator is needed. APK building, installation, and hardware testing use the connected CMF Phone 2 Pro.

Current device experiment: the user authorized disabling stock Essential Space for Android user 0 after it still opened during the flashlight test. See [docs/DEVICE-TEST.md](docs/DEVICE-TEST.md) for the exact change and restore command. The app itself does not change system packages.

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

Version 0.2.0 validation on the same date: `:app:testDebugUnitTest :app:assembleDebug` passed all 19 unit tests and produced a debug APK, installed successfully on the connected A001. The user then performed single, double, and long presses; screenshots confirmed the expected gesture and timing for each, with no extra single after the double or long. See the measured results in [docs/RESEARCH.md](docs/RESEARCH.md). This verifies these foreground examples on one phone, not background, lock-screen, or other firmware behavior. No emulator or system-package changes were used by the development workflow.

Version 0.3.0 host validation on the same date: all 27 unit tests passed and the debug APK built successfully. New tests cover consistent press/release consumption, repeats, unrelated keys, device identity, and capture ending during a hold. Source permission checks confirm Camera is the only requested app permission and screen-content access remains disabled. After reconnecting the phone, version 0.3.0 installed and opened successfully on A001. The initial test briefly illuminated the light but still opened Essential Space. After the user authorized disabling that package, the user confirmed successful foreground flashlight behavior; screenshots confirmed the detector remained visible with the service connected and actions enabled. See [docs/DEVICE-TEST.md](docs/DEVICE-TEST.md) for the results, limits, and restore command. Background and lock-screen use remain untested.

## License

Original project code is licensed under [GPL-3.0-only](LICENSE). Gradle-generated wrapper files retain their own license notices. Dependencies retain their respective licenses.

