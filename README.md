# Nothing Essential Detector

Nothing Essential Detector is an Android prototype for the Essential button on a CMF Phone 2 Pro. It recognizes single, double, and long presses. Double press can toggle the flashlight. Background control can keep that working in other apps while the phone is unlocked.

Tested on one CMF A001. Not a general Essential button remapper. Nothing and Essential Remapper have no involvement.

Actions are off by default. Every key passes through untouched until you enable them.

## What it does

- A 15-second capture shows key code, scan code, DOWN or UP, repeat count, and elapsed time.
- The CMF profile matches key code `0` with scan code `250`, the pair seen on the connected A001.
- With actions on, the app consumes that key. Double press toggles the flashlight; single and long press only display results.
- Background control is opt-in. It pauses on screen off or lock and resets off on service disconnect, force-stop, process death, reboot, or update.
- The app keeps at most 32 hardware events and 16 gestures in memory. Nothing goes to disk or the network.
- The filter ignores typing keys, virtual input, software keyboards, volume, power, and common navigation keys.

Raw capture reports non-typing hardware events. Only the CMF key reaches gesture recognition. If firmware reports the button differently, check that on the phone before widening the filter.

## Gestures and background

- A short press becomes **Single press** 300 ms after release.
- A second press inside that window becomes **Double press** on release.
- A 600 ms hold becomes **Long press**.
- A short press followed by a hold gives single then long, not double.
- Stopping capture, leaving the screen, clearing results, a canceled key, or losing the service discards unfinished gestures.

Recognition is a pure Kotlin state machine. Handler callbacks wake it at the next deadline. Timing uses Android's monotonic uptime clock.

Background control needs notification permission and a status notification with Stop. Turn on **Enable double press**, then **Use in other apps**. Leave the app and double press while unlocked. Stop from the notification, either switch, or **Stop background control**.

The accessibility service owns the torch controller, so the activity can close. There is no foreground service, wake lock, polling loop, battery exemption, camera session, or restart alarm. Screen off or lock pauses actions and turns off a light this app turned on. Android and the OEM control service lifecycle, so uninterrupted execution is not guaranteed.

Only the confirmed key enters background recognition. Raw events are kept only during a visible 15-second capture. A consumed DOWN keeps its decision through repeats and the matching UP, even if monitoring stops during a hold.

The torch API needs no Camera permission on stock Android. The app never opens a camera or records media. Torch errors report busy or unavailable hardware without retrying in a loop.

The UI is one diagnostic screen using Material 3 Expressive. Change the palette, typography, or shapes in `app/src/main/java/org/nothingessential/detector/ui/theme/Theme.kt`. [UI-SPEC.md](UI-SPEC.md) is a future design reference, not this build.

## Stack

Kotlin, Jetpack Compose with Material 3 Expressive (`material3` 1.5.0-beta02), AccessibilityService, and in-memory StateFlow. Android 12 / API 31+, compile SDK 37, target SDK 36, JDK 17. AGP 9.4.1, Compose compiler 2.4.21, Gradle 9.7.1 with a pinned checksum. No root, Shizuku, ADB client, database, analytics, or custom fonts.

The 2026.09.00 BOM pins Material 3 1.4.0, so the build pins `1.5.0-beta02` for the expressive components. That dependency is beta.

## Permissions

The manifest asks only for `POST_NOTIFICATIONS`, for the optional background status. It does not ask for Camera, internet, storage, screenshots, or screen content. The accessibility service uses `BIND_ACCESSIBILITY_SERVICE`; the notification Stop receiver is not exported.

Key filtering runs only while an unlocked monitoring mode is active, plus the tail of an already consumed pair. The service does not request window events, gesture injection, or overlays. Every action checks screen, keyguard, and device state. The manifest declares `isAccessibilityTool="false"`.

The app explains its access in a dialog before opening Android accessibility settings.

## Build and test

1. Open the project in Android Studio or set `ANDROID_HOME`. Use SDK platform 37 and JDK 17.
2. Build: `./gradlew :app:assembleDebug`
3. Connect the CMF over USB, enable USB debugging, and authorize the computer.
4. Check `adb devices -l`, then install: `adb -s SERIAL install -r app/build/outputs/apk/debug/app-debug.apk`
5. Launch **Nothing Essential Detector**, read the disclosure, and enable **Essential button detector** in accessibility settings.
6. Check **Accessibility on**, start a capture, and try single, double, and long presses. Power and volume should behave normally.
7. Check that capture stops when you leave the app and after 15 seconds.

Host checks: `./gradlew :app:testDebugUnitTest`

## Validation

Host checks cannot prove the CMF firmware delivers the key. Only the phone can. No emulator was used.

| Version | Host checks | Device evidence |
| --- | --- | --- |
| 0.2.0 | 19 tests, build | A001: single, double, long; timing screenshots |
| 0.3.0 | 27 tests, build | A001: foreground flashlight after Essential Space disabled |
| 0.4.0 | 35 tests, build | A001: background gestures, 8 double presses; notification Stop not yet checked |
| 0.4.1 | 49 tests, build | A001: pause/resume, notification Stop, light off |

Not covered: long-term service survival, lock-screen actions, other phones or firmware. Background and lock behavior were not tested in 0.3.0. See [docs/DEVICE-TEST.md](docs/DEVICE-TEST.md) for the package change and restore command. The app never changes system packages.

## If no event arrives

Check that accessibility is connected and capture is running. Another service may own key filtering, Nothing OS may intercept the button, or the firmware may use a mapping the filter drops. Record the Android and Nothing OS versions before changing anything. Do not disable or uninstall system packages.

[docs/RESEARCH.md](docs/RESEARCH.md) has the Android contracts and open questions.

## License

Original code is GPL-3.0-only. Wrapper files retain their notices. Dependencies retain their licenses.
