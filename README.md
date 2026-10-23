# Nothing Essential Detector

A free, open-source Android prototype for recognizing the Essential button's gestures on a CMF Phone 2 Pro, with an optional double-press flashlight action and opt-in background control. It is an independent project, not affiliated with Nothing or Essential Remapper.

## Current scope

- Manually start a 15-second raw diagnostic capture, or explicitly enable background control while unlocked.
- Display key code, scan code, DOWN / UP, repeat count, and relative capture time.
- Display single, double, and long presses for the CMF test profile: key code `0`, scan code `250`, observed on the connected A001 phone.
- Actions are off by default. In this mode, pass every event through unchanged.
- With actions enabled, consume the CMF test key in the selected monitoring mode and toggle the flashlight on double press. Single and long presses only display results.
- Stop raw capture on timeout or leaving the activity. Background mode continues across other apps, pauses on screen-off/lock, and resets off on service disconnection or process restart.
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

## Foreground flashlight test

1. Enable **Enable actions**, then start the 15-second capture.
2. Double press to turn the flashlight on, wait for status to settle, then double press again to turn it off.
3. Single and long presses remain diagnostic. Power, volume, and unrelated keys retain their normal behavior.
4. Stop capture to turn off a light last enabled by this app. Without background mode, leaving the activity also disables actions.

This experiment passed on the CMF A001 after the user authorized disabling stock Essential Space. See [docs/DEVICE-TEST.md](docs/DEVICE-TEST.md) for the device change and restore command. The app does not disable system packages itself.

## Background mode, version 0.4.0

1. Allow **status notifications** when prompted. They provide an ongoing status and Stop action. Background mode requires notifications to be enabled for its channel.
2. Turn on **Enable actions**, then **Use in other apps**. No capture timer is needed.
3. Press Home, use another app with the phone unlocked, and double press to toggle the flashlight. Return to inspect the last 16 gesture results.
4. Tap **Stop** in the notification, turn off either switch, or use **Stop background control** in the app to stop monitoring and turn off a light last enabled by this app. On Android versions that allow dismissing ongoing notifications, dismissing it also stops control.

The system-bound accessibility service owns the torch controller using an application context. The activity can close without releasing that controller. No extra foreground service, wake lock, polling, battery-optimization exemption, camera opening, or automatic restart is added.

Screen off or lock pauses background recognition, clears pending gestures, and turns off a light last enabled by the app. Background mode stays armed and resumes after unlocking. Locked and screen-off button actions are deliberately disabled. Mode and action settings are process-local and reset off after service disconnection, force-stop, process death, reboot, or app update. Android/OEM service lifecycle behavior still needs physical testing; no guarantee of uninterrupted execution is made.

Only the confirmed Essential key enters background recognition. Raw hardware events are retained only during a visible 15-second diagnostic capture, never continuously in other apps. Stopping or clearing diagnostics stops the current monitoring mode. A consumed DOWN retains the same consumption decision through repeats and its matching UP, even if monitoring stops during a hold; filtering may briefly remain enabled to finish that pair, with actions disabled.

The torch API does not require Camera permission on stock Android. Version 0.4.0 removes the earlier Camera permission and permission request. The app does not open a camera or record media. Torch callbacks report actual state; busy/unavailable hardware is reported without retry loops. OEM restrictions, if any, must be checked on the phone rather than bypassed.

The existing [UI specification](UI-SPEC.md) describes a possible future app, not the current feature set.

The user's full timestamped [reference-video transcript](docs/reference/essential-remapper-transcript.txt) is saved verbatim. See [reference notes](docs/reference/README.md) for provenance and the distinction between product inspiration and verified Android behavior.

## Stack

Kotlin, Jetpack Compose Material 3, an Android AccessibilityService, and an in-memory StateFlow. Android 12 / API 31 or later, compile SDK 37, target SDK 36, JDK 17. AGP 9.4.1 with built-in Kotlin; Compose compiler plugin 2.4.21; Gradle wrapper 9.7.1 with a pinned distribution checksum. No root, Shizuku, ADB client, database, analytics, or custom fonts in the app.

## Permission model

The accessibility service is protected by Android's `BIND_ACCESSIBILITY_SERVICE` permission. The source manifest requests only `POST_NOTIFICATIONS` for opt-in background status on Android 13+. It requests no Camera, internet, storage, screenshot, or screen-content access. AndroidX adds an app-specific signature permission for internal receiver protection. Notification Stop uses an immutable PendingIntent targeting a non-exported receiver.

Key filtering runs only when an unlocked monitoring mode is active, except for finishing an already consumed press/release pair. No accessibility window events, gesture injection, or overlays are requested. Every action rechecks that the screen is interactive and the keyguard/device is unlocked. Screen-state broadcasts reset pending recognition; no background action is allowed to cross a lock boundary.
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

Version 0.4.0 host validation on 2026-10-23: all 35 unit tests passed and `:app:assembleDebug` succeeded. Source checks confirm notifications are the only requested app permission, the Stop receiver is not exported, and window-content retrieval is disabled. Version 0.4.0 installed successfully on A001; the accessibility service process is running. The phone was dozing at inspection, and notification permission is not yet granted. The user subsequently confirmed background control works. Retained history shows eight double presses, including events at 15187 and 16024 ms beyond the diagnostic timeout; inspection found both controls off and flashlight status off. Dedicated notification Stop and lock/pause/resume checks remain pending. See docs/DEVICE-TEST.md for the evidence and limits. No emulator or further system-package changes were used.

## License

Original project code is licensed under [GPL-3.0-only](LICENSE). Gradle-generated wrapper files retain their own license notices. Dependencies retain their respective licenses.

