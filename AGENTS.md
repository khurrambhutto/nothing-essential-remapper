# Nothing Essential

## Project idea

Build a free, open-source Android app that lets Nothing and CMF owners assign useful actions to the Essential button. Start with reliable single, double, and long press recognition on the CMF Phone 2 Pro, then add actions incrementally. The first action is double press to toggle the flashlight.

The app should feel native, stay understandable, and work with minimal permissions. Background control should work after leaving the activity or swiping its task away, with a visible status notification and an immediate Stop action. Treat survival after task removal as a requirement to verify on the physical phone. Android force-stop is a separate system boundary; do not promise execution through it.

Use the reference video as inspiration for features and visual direction. Develop original code and use assets and fonts with suitable licenses. Original project code is GPL-3.0-only.

## Product rules

> Cut taglines, helper text and secondary labels. If a screen needs them to make sense, fix the layout.

> Make the best decision that will elevate all three: the user experience (UX), developer experience (DX), and agent experience (AX) always, without breaking anything.

Apply these rules to each change: make controls clear through layout and naming, keep implementation easy to maintain, and provide focused instructions and reproducible checks for agents. Keep required permission disclosures, accessible control names, and actionable error messages.

## Implementation boundaries

- Use Kotlin and Jetpack Compose with the existing Material 3 Expressive theme. Put shared visual decisions in the theme rather than styling each screen independently.
- Keep gesture recognition independent of Android UI code. The system-bound accessibility service owns background button handling; activity lifecycle changes must not disable an explicitly enabled background mode.
- Intercept only the confirmed Essential key. Preserve ordinary power, volume, typing, and unrelated key behavior. Retain raw diagnostic events only during the explicit, time-limited capture.
- Keep actions opt-in. Pause actions while the screen is off or locked, clear incomplete gestures, and recheck device state before executing an action. Lock-screen actions require a separately scoped feature.
- Stop must disable control and turn off a flashlight last enabled by this app. Service/process restart currently resets control off; preserve that behavior unless intentionally implementing and testing recovery.
- Keep permissions tied to implemented features. The current app needs notification permission, not camera recording, internet, storage, or accessibility window-content access.
- System-package changes are device experiments, not app behavior. Preserve original states and restore commands; obtain explicit authorization for new package changes.

## Development and verification

- Inspect Git state before editing and preserve unrelated changes. Commit cohesive changes when managing Git; do not include another task's work.
- Use host tests for gesture, lifecycle, and permission boundaries. Run `./gradlew :app:testDebugUnitTest :app:assembleDebug` for relevant code changes.
- Do not run emulators. Install and test on the user's connected CMF Phone 2 Pro, selecting its serial explicitly after `adb devices -l`.
- Use `scripts/check-device-state.py` for app state inspection. Avoid `uiautomator dump`: it can interrupt the accessibility service and invalidate a test.
- Report host checks and physical-device evidence separately. Verify task removal, lock/unlock, and Stop when changing background lifecycle behavior; distinguish an untested requirement from working behavior.

## Read when relevant

- Setup, build, or current feature behavior: [README.md](README.md).
- Device experiments, package state, restore commands, or physical test history: [docs/DEVICE-TEST.md](docs/DEVICE-TEST.md).
- Android API or OEM behavior changes: [docs/RESEARCH.md](docs/RESEARCH.md); verify uncertain contracts against current primary sources.
- UI or feature planning: [UI-SPEC.md](UI-SPEC.md) and [reference notes](docs/reference/README.md). These describe inspiration and future possibilities, not a claim that every feature is implemented. The product rules above take precedence over reference-screen copy patterns.
