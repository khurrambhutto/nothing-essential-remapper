# Button detection research

Checked on 2026-10-23. The purpose of this prototype is to verify delivery of key events on the user's physical CMF Phone 2 Pro before implementing remapping.

## Official Android contracts

- [AccessibilityService.onKeyEvent](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService#onKeyEvent(android.view.KeyEvent)): receives copies of key events before applications. Returning `false` passes them through; returning `true` consumes them. Android warns against inconsistent DOWN / UP filtering. This prototype always returns `false` and copies only primitive metadata into its own model.
- [AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS](https://developer.android.com/reference/android/accessibilityservice/AccessibilityServiceInfo#FLAG_REQUEST_FILTER_KEY_EVENTS): requests delivery; requires `canRequestFilterKeyEvents` in service metadata. Capability alone does not request delivery. This prototype turns the flag on only during an active foreground capture.
- [AOSP AccessibilityServiceInfo source](https://android.googlesource.com/platform/frameworks/base/+/master/core/java/android/accessibilityservice/AccessibilityServiceInfo.java): key-filter delivery is not guaranteed when another service owns filtering. This is a troubleshooting possibility, not a reason to disable a user's accessibility tools automatically.
- [Accessibility service guide/API](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService): window-content retrieval is a separate capability. It is disabled here; no accessibility window-event types are requested.
- [Google Play AccessibilityService policy](https://support.google.com/googleplay/android-developer/answer/10964491?hl=en): general-purpose utilities must not falsely claim to be disability accessibility tools. The service uses `isAccessibilityTool=false` and includes a separate in-app disclosure before opening settings. Store submission is outside this prototype.

## Toolchain

- [AGP 9.4 compatibility](https://developer.android.com/build/releases/agp-9-4-0-release-notes): supports up to API 37 and requires Gradle 9.6.0 or newer and JDK 17. This project pins AGP 9.4.1 and Gradle 9.7.1. Compile SDK 37 satisfies the Compose libraries' AAR metadata; target SDK remains 36 and minimum SDK remains 31.
- [Built-in Kotlin](https://developer.android.com/build/migrate-to-built-in-kotlin): AGP 9 enables Kotlin support directly, so the project does not apply `org.jetbrains.kotlin.android`.
- [Compose compiler setup](https://developer.android.com/develop/ui/compose/compiler): use the Compose compiler Gradle plugin and align its version with Kotlin. Both plugins resolve in the root plugin classpath.
- [Compose BOM mapping](https://developer.android.com/develop/ui/compose/bom/bom-mapping): the stable 2026.09.00 BOM coordinates Compose libraries, including Material 3 1.4.0.
- [Official Gradle distribution checksum](https://services.gradle.org/distributions/gradle-9.7.1-bin.zip.sha256): pinned in wrapper properties.

## CMF / Nothing evidence and limits

[A community author's Essential-key guide](https://github.com/z3phydev/How-to-remap-or-disable-the-Essential-Key) describes using another key mapper after disabling two Nothing packages. This is a user-authored procedure, not an official compatibility guarantee. It does not prove the key code or service behavior on the user's OS version. It was consulted as evidence of the system-interception problem; no source code was copied from it.

The [reference video's developer](https://www.youtube.com/watch?v=YhY94x3HL7o) also describes disabling Essential Space before remapping. We are intentionally leaving packages alone for the first hardware test.

Do not guess a vendor key code from the video or another model. Report observed codes and verify that they correspond to deliberate Essential-button presses. A missing callback is inconclusive until accessibility connection, filtering contention, app foreground status, stock-system interception, and input-device classification have been checked.

The prototype's capture privacy filter excludes virtual devices and printing keys. This can cause false negatives if the OS synthesizes the Essential key through a virtual device or maps it as a printing key. Confirm input behavior on the physical device before changing that boundary.
