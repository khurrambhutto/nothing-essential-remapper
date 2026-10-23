# CMF Phone 2 Pro test configuration

## Essential Space experiment, 2026-10-23

After the flashlight briefly illuminated and stock Essential Space appeared during the v0.3.0 test, the user explicitly authorized temporarily disabling Essential Space.

Verified current Android user: `0`. Before the change, `com.nothing.ntessentialspace` was installed, enabled in its default state (`enabled=0`), version 2.2.0.

Executed:

```sh
adb -s SERIAL shell pm disable-user --user 0 com.nothing.ntessentialspace
```

Android reported `disabled-user`. The package was not uninstalled and its data was not cleared. The recorder and overlay packages were left unchanged.

## Foreground flashlight retest

After that change, the user reported that the flashlight experiment worked correctly. ADB screenshots confirmed the detector remained on screen, Service connected, Enable actions on, Camera permission granted, flashlight status off at inspection, and further key code 0 / scan code 250 events retained in the capture. The disabled-package listing still contained Essential Space. The user's report confirms the physical light behavior; a screenshot alone does not measure the emitted light or prove every on/off transition.

This passes the current foreground flashlight experiment on this phone with Essential Space disabled. It does not establish background, lock-screen, reboot, long-term reliability, or behavior with stock Essential Space enabled.

## Version 0.4.0 background test procedure

Version 0.4.0 installed on A001 after 35 host unit tests passed. The Android-bound accessibility service is running. Notification permission is initially denied, background control is off by default, and Camera permission is no longer declared. Essential Space remains disabled; the recorder package has not been changed by this workflow.

Physical check protocol:

1. Unlock, open the detector, allow status notifications, then enable actions and **Use in other apps**.
2. Confirm the ongoing notification appears. Press Home, use another app, and double press the Essential button to toggle on, then off. Wait beyond 15 seconds and repeat to check independence from diagnostic capture.
3. Use the notification's **Stop** action. Check the light is off, the notification is removed, and further presses do not trigger this app's actions.
4. Reenable background mode, then turn off/lock the screen. Verify no button action runs and any app-enabled light turns off. Unlock and verify a fresh double press works, without any stale gesture from before lock.
5. Confirm ordinary keys and the Camera app remain usable; camera resource conflicts should show an unavailable status rather than crash or retry continuously.

### Background test result

On 2026-10-23, the user reported that background control worked. ADB screenshots confirmed notification permission granted, Service connected, and eight retained double presses at 1420, 4919, 5709, 6887, 13033, 13985, 15187, and 16024 ms relative to monitoring start. Results beyond 15000 ms demonstrate recognition continuing past the diagnostic capture limit. The flashlight was reported off at inspection, with both control switches off. The user's report is the evidence for physical light behavior while using other apps; screenshots inspect the retained results rather than independently recording that whole interaction.

The unlocked background toggle test is user-confirmed. The inspection does not establish which Stop control was used. Dedicated notification Stop, lock/pause/resume, camera-conflict, reboot, and long-term service survival checks remain pending. No lock-screen flashlight action is implemented or claimed.

To restore the original package state on the same phone and Android user:

```sh
adb -s SERIAL shell pm default-state --user 0 com.nothing.ntessentialspace
```

Replace `SERIAL` with this phone's identifier from `adb devices -l`. `default-state` restores the original default state rather than forcing a new explicitly enabled state. Verify with:

```sh
adb -s SERIAL shell dumpsys package com.nothing.ntessentialspace
```

Look for `User 0` with `enabled=0`. The detector app does not perform this package change or restoration itself.

## Version 0.4.1 pause/resume and Stop checks

On 2026-10-23, a screen-off/wake test on v0.4.0 exposed a stale paused state: Android reported the current device unlocked while the app still displayed paused. A deterministic host regression reproduced the missing follow-up state check twice before the fix. SCREEN_ON may precede keyguard dismissal without another USER_PRESENT notification reaching the monitor. Version 0.4.1 performs a bounded series of checks at 100, 300, 750, 1500, and 3000 ms after wake. It never assumes unlock, and cancels checks on Stop, screen-off, or confirmed unlock. Foreground readiness after wake uses the same recovery boundary.

All 49 host tests passed before installation. New lifecycle tests execute the real CaptureMonitor and Handler deadlines under Robolectric, with fake lock/notification/torch callbacks. They cover pausing with a light on, resuming after unlock, clearing pending short/long presses, late unlock, bounded retries, Stop, notification revocation, service reconnect, and foreground cleanup. No emulator is used.

The physical CMF check used:

```sh
python3 scripts/check-device-state.py --serial SERIAL --wake-cycle
```

Observed sequence on v0.4.1:

- Active: actions/background enabled, monitor/device unlocked, no diagnostic capture, gesture count 6.
- Screen off: actions/background still armed, monitor/device locked or noninteractive, flashlight reported off, gesture count unchanged at 6.
- Wake: monitor/device unlocked again, actions/background still armed, gesture count still 6. The command returned PASS without reopening the app to recover.

The light was off at the start of that particular cycle, so physical cleanup of an illuminated light is not established by that cycle; the corresponding host lifecycle test does start with the light on.

The user then tapped the notification's Stop action. `--expect stopped` confirmed actions false, background false, flashlight false, diagnostic capture false, service still connected. Android's active notification records no longer contained the app's status notification. This verifies the delivered Stop action as well as the host receiver test.

Avoid `uiautomator dump` during these checks: attaching UI automation interrupted the accessibility service in this session and disarmed background mode. The app's inherited service dump reports only its own boolean runtime state and gesture count, without reading other apps' content. Use the read-only diagnostic script and screenshots instead.
